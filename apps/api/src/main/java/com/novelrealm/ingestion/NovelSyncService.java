package com.novelrealm.ingestion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.novelrealm.ingestion.SyncResults.DiscoverResult;
import com.novelrealm.ingestion.SyncResults.ImportResult;
import com.novelrealm.ingestion.SyncResults.SyncResult;
import com.novelrealm.ingestion.SyncResults.SyncRunSummary;
import com.novelrealm.ingestion.source.SourceAdapter;
import com.novelrealm.ingestion.source.dto.SourceChapterBody;
import com.novelrealm.ingestion.source.dto.SourceChapterPage;
import com.novelrealm.ingestion.source.dto.SourceChapterRef;
import com.novelrealm.ingestion.source.dto.SourceCatalogPage;
import com.novelrealm.ingestion.source.dto.SourceNovel;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.Genre;
import com.novelrealm.model.Novel;
import com.novelrealm.model.Novel.NovelStatus;
import com.novelrealm.repository.ChapterRepository;
import com.novelrealm.repository.GenreRepository;
import com.novelrealm.repository.NovelRepository;

/**
 * Cœur de l'ingestion V2 : importer, maintenir et découvrir des romans depuis
 * une {@link SourceAdapter} (chikari). Objectif : un MIROIR COMPLET du catalogue,
 * atteint par vagues, puis maintenu. Voir docs/INGESTION_V2.md.
 *
 * <p><b>Idempotence.</b> Un roman est identifié par {@code (source, source_id)} ;
 * un chapitre par {@code (novel, chapter_number)}. Ré-exécuter ne crée aucun
 * doublon : ce qui est déjà stocké est sauté.
 *
 * <p><b>Non transactionnel, save par item — VOLONTAIRE.</b> Comme l'ancien
 * service : chaque {@code save} de chapitre est sa propre transaction (auto-commit).
 * Un crash au chapitre 1800/3000 laisse 1799 chapitres durablement en base et le
 * run suivant reprend. Une transaction géante autour d'un import de 3000 chapitres
 * serait une écriture longue et perdrait tout à la moindre erreur.
 *
 * <p><b>Robuste par conception.</b> Une source instable ne fait jamais tomber
 * l'application : {@link #syncExisting()} et {@link #discoverNew(int)} attrapent
 * les échecs PAR roman et poursuivent ; le job planifié attrape au sommet.
 */
@Service
public class NovelSyncService {

    private static final Logger log = LoggerFactory.getLogger(NovelSyncService.class);

    /** Taille de page pour lister les chapitres d'un roman (max accepté par chikari). */
    private static final int CHAPTER_PAGE_SIZE = 100;

    /** Garde-fou : un roman monstrueux ne doit pas boucler à l'infini sur la pagination. */
    private static final int MAX_CHAPTER_PAGES = 1000;

    private final SourceAdapter source;
    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final GenreRepository genreRepository;
    private final IngestionProperties props;
    private final IngestionProgressTracker progress;

    public NovelSyncService(SourceAdapter source, NovelRepository novelRepository,
            ChapterRepository chapterRepository, GenreRepository genreRepository,
            IngestionProperties props, IngestionProgressTracker progress) {
        this.source = source;
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        this.genreRepository = genreRepository;
        this.props = props;
        this.progress = progress;
    }

    // ── Cycle complet (scheduler + admin /sync) ─────────────────────────────

    /**
     * Un cycle complet : d'abord découvrir/ajouter de nouveaux titres (dans la
     * limite du plafond), puis mettre à jour les chapitres des romans existants.
     */
    public SyncRunSummary runFullSync() {
        log.info("Ingestion : début du cycle complet.");
        DiscoverResult discovery = discoverNew(props.getDiscoveryCap());
        SyncResult maintenance = syncExisting();
        log.info("Ingestion : cycle terminé — découverte(ajoutés={}, échecs={}), "
                + "maintenance(maj={}, inchangés={}, +chap={}, échecs={}).",
                discovery.added(), discovery.failed(),
                maintenance.novelsUpdated(), maintenance.novelsUnchanged(),
                maintenance.importedChapters(), maintenance.failed());
        return new SyncRunSummary(discovery, maintenance);
    }

    // ── Import initial d'un titre ───────────────────────────────────────────

    /**
     * Import (ou complétion) d'un roman précis : upsert du roman + genres, puis
     * pagination de tous ses chapitres avec téléchargement des corps manquants.
     * Idempotent. Utilisé par l'endpoint admin et par la découverte.
     */
    public ImportResult importNovel(String slug) {
        log.info("Import '{}' : détail…", slug);
        progress.start(slug, slug, 0); // titre provisoire = slug, total inconnu tant que le détail n'est pas là
        try {
            return doImportNovel(slug);
        } finally {
            progress.finish(slug); // quoi qu'il arrive, on libère l'actif
        }
    }

    private ImportResult doImportNovel(String slug) {
        SourceNovel sn = source.fetchNovel(slug);
        progress.phase("listing-chapters", sn.title(), sn.chapterCount());

        boolean existedBefore = sn.sourceId() != 0
                && novelRepository.findBySourceAndSourceId(source.sourceKey(), sn.sourceId()).isPresent();
        Novel novel = upsertNovel(sn);
        boolean created = !existedBefore;

        Set<Integer> existing = new HashSet<>(chapterRepository.findChapterNumbersByNovelId(novel.getId()));
        // Les chapitres déjà en base comptent comme « faits » : la barre reflète l'état réel du roman.
        progress.progress(existing.size(), 0);
        progress.phase("downloading", sn.title(), sn.chapterCount());

        int imported = 0;
        int skippedLocked = 0;
        int skippedDecimal = 0;
        int failed = 0;

        int offset = 0;
        int pages = 0;
        boolean more = true;
        while (more && pages < MAX_CHAPTER_PAGES) {
            SourceChapterPage page = source.listChapters(slug, offset, CHAPTER_PAGE_SIZE);
            for (SourceChapterRef ref : page.items()) {
                Integer number = toIntChapterNumber(ref.number());
                if (number == null) {
                    skippedDecimal++;
                    progress.progress(0, 1);
                    continue; // numéro décimal : non mappable vers notre INT
                }
                if (existing.contains(number)) {
                    continue; // déjà en base → idempotent (déjà compté comme fait)
                }
                try {
                    if (persistChapter(novel, slug, ref.number(), number)) {
                        existing.add(number);
                        imported++;
                        progress.progress(1, 0);
                    } else {
                        skippedLocked++;
                        progress.progress(0, 1);
                    }
                } catch (SourceUnavailableException ex) {
                    // Un chapitre injoignable ne doit pas tuer l'import du roman :
                    // on le laisse pour un prochain run (non enregistré = re-tenté).
                    log.warn("  ! chapitre {} de '{}' non récupéré : {}", number, slug, ex.getMessage());
                    failed++;
                }
                throttle();
            }
            more = page.hasMore();
            offset += CHAPTER_PAGE_SIZE;
            pages++;
        }

        progress.phase("finishing", sn.title(), sn.chapterCount());
        updateSyncSnapshot(novel, sn);
        log.info("Import '{}' terminé : +{} chapitre(s) (verrouillés/vides={}, décimaux={}, échecs={}).",
                slug, imported, skippedLocked, skippedDecimal, failed);
        return new ImportResult(slug, created, imported, skippedLocked, skippedDecimal, failed);
    }

    // ── Maintenance des romans existants ────────────────────────────────────

    /**
     * Pour chaque roman de la source en base : un appel détail, puis
     * COURT-CIRCUIT si les trois signaux de fraîcheur sont inchangés (aucun appel
     * chapitre). Sinon, on ne tire que les chapitres au-dessus du plus grand
     * numéro déjà stocké. Chaque roman est isolé dans son try/catch.
     */
    public SyncResult syncExisting() {
        List<Novel> novels = novelRepository.findAllBySource(source.sourceKey());
        log.info("Maintenance : {} roman(s) '{}' à examiner.", novels.size(), source.sourceKey());

        int checked = 0;
        int unchanged = 0;
        int updated = 0;
        int importedChapters = 0;
        int failed = 0;

        for (Novel stored : novels) {
            checked++;
            String slug = stored.getSourceSlug() != null ? stored.getSourceSlug() : stored.getSlug();
            try {
                SourceNovel sn = source.fetchNovel(slug);
                if (isUnchanged(stored, sn)) {
                    stored.setLastSyncedAt(Instant.now());
                    novelRepository.save(stored);
                    unchanged++;
                    continue; // rien de neuf → zéro appel chapitre
                }
                int added = pullNewChapters(stored, slug);
                importedChapters += added;
                // Rafraîchit aussi les scalaires (statut, résumé…) + les signaux.
                applyScalars(stored, sn);
                updateSyncSnapshot(stored, sn);
                if (added > 0) {
                    updated++;
                }
            } catch (SourceUnavailableException ex) {
                log.warn("Maintenance : '{}' ignoré ce cycle ({}).", slug, ex.getMessage());
                failed++;
            } catch (RuntimeException ex) {
                // Filet de sécurité : un roman anormal ne doit pas stopper le lot.
                log.error("Maintenance : erreur inattendue sur '{}' : {}", slug, ex.getMessage(), ex);
                failed++;
            }
        }
        return new SyncResult(checked, unchanged, updated, importedChapters, failed);
    }

    // ── Découverte de nouveaux titres ───────────────────────────────────────

    /**
     * Parcourt le catalogue et importe les titres absents de la base, jusqu'à
     * {@code maxToAdd}. Sert le miroir complet : tant que le catalogue n'est pas
     * entièrement en base, chaque run en rattrape une tranche.
     *
     * <p>On trie par {@code added} (récemment ajoutés d'abord) : en régime
     * permanent ce sont les nouveautés ; en phase de rattrapage, on descend
     * progressivement dans le catalogue au fil des runs (les titres déjà pris
     * sont sautés, donc on avance).
     */
    public DiscoverResult discoverNew(int maxToAdd) {
        if (maxToAdd <= 0) {
            return new DiscoverResult(0, 0, 0);
        }
        log.info("Découverte : jusqu'à {} nouveau(x) titre(s) à ajouter.", maxToAdd);

        int scanned = 0;
        int added = 0;
        int failed = 0;

        int offset = 0;
        int pageSize = props.getCatalogPageSize();
        boolean more = true;
        while (more && added < maxToAdd) {
            SourceCatalogPage page;
            try {
                page = source.listCatalog("added", offset, pageSize);
            } catch (SourceUnavailableException ex) {
                log.warn("Découverte : catalogue injoignable (offset={}) — arrêt du cycle ({}).",
                        offset, ex.getMessage());
                break;
            }
            for (SourceNovel card : page.items()) {
                scanned++;
                if (added >= maxToAdd) {
                    break;
                }
                if (props.isSkipNsfw() && card.nsfw()) {
                    continue; // découverte : on ne rapatrie pas l'adulte automatiquement
                }
                if (card.sourceId() != 0
                        && novelRepository.findBySourceAndSourceId(source.sourceKey(), card.sourceId()).isPresent()) {
                    continue; // déjà en base
                }
                try {
                    importNovel(card.slug());
                    added++;
                } catch (SourceUnavailableException ex) {
                    log.warn("Découverte : import de '{}' échoué ({}).", card.slug(), ex.getMessage());
                    failed++;
                }
            }
            more = page.hasMore();
            offset += pageSize;
        }
        log.info("Découverte terminée : {} examiné(s), {} ajouté(s), {} échec(s).", scanned, added, failed);
        return new DiscoverResult(scanned, added, failed);
    }

    // ── Interne ─────────────────────────────────────────────────────────────

    /** Ne tire que les chapitres au-dessus du plus grand numéro déjà stocké. */
    private int pullNewChapters(Novel novel, String slug) {
        int maxStored = chapterRepository.findMaxChapterNumber(novel.getId());
        int imported = 0;
        int offset = 0;
        int pages = 0;
        boolean more = true;
        while (more && pages < MAX_CHAPTER_PAGES) {
            SourceChapterPage page = source.listChapters(slug, offset, CHAPTER_PAGE_SIZE);
            for (SourceChapterRef ref : page.items()) {
                Integer number = toIntChapterNumber(ref.number());
                if (number == null || number <= maxStored) {
                    continue; // décimal, ou déjà couvert par le seuil
                }
                if (chapterRepository.existsByNovelIdAndChapterNumber(novel.getId(), number)) {
                    continue; // garde d'idempotence (ex-verrouillé entre-temps)
                }
                try {
                    if (persistChapter(novel, slug, ref.number(), number)) {
                        imported++;
                    }
                } catch (SourceUnavailableException ex) {
                    log.warn("  ! chapitre {} de '{}' non récupéré : {}", number, slug, ex.getMessage());
                }
                throttle();
            }
            more = page.hasMore();
            offset += CHAPTER_PAGE_SIZE;
            pages++;
        }
        return imported;
    }

    /**
     * Télécharge et enregistre un chapitre. Renvoie {@code true} si enregistré,
     * {@code false} s'il était verrouillé/vide (non persisté → re-tenté plus tard,
     * comme la règle « contenu vide » de l'ancien service).
     */
    private boolean persistChapter(Novel novel, String slug, BigDecimal sourceNumber, int number) {
        SourceChapterBody body = source.fetchChapterBody(slug, sourceNumber);
        if (body.isEmptyOrLocked()) {
            return false;
        }
        String title = body.title().isBlank() ? ("Chapitre " + number) : body.title();
        try {
            chapterRepository.save(new Chapter(novel, number, sourceNumber, title, body.body()));
        } catch (DataIntegrityViolationException ex) {
            // Course avec un autre run (contrainte chapters_novel_chapter_uq) :
            // le chapitre existe déjà, on considère l'objectif atteint sans compter.
            log.debug("  = chapitre {} de '{}' déjà présent (course), ignoré.", number, slug);
            return false;
        }
        return true;
    }

    /**
     * Upsert du roman. Ordre de résolution :
     * <ol>
     *   <li>par {@code (source, source_id)} → roman chikari déjà connu : on met à jour ;</li>
     *   <li>sinon par {@code slug} : si un roman existe avec ce slug et <b>sans identité
     *       source</b> (roman hérité de l'ère lightnovelworld), on l'<b>ADOPTE</b> —
     *       on lui rattache l'identité chikari et on complétera ses chapitres, plutôt
     *       que de créer un doublon. S'il a déjà une AUTRE source, c'est une vraie
     *       collision → on suffixe ;</li>
     *   <li>sinon → création.</li>
     * </ol>
     * Dans tous les cas on écrit le snapshot des signaux de fraîcheur TÔT (avant de
     * tirer les chapitres) : un import interrompu laisse ainsi des signaux cohérents.
     */
    private Novel upsertNovel(SourceNovel sn) {
        // 1) déjà connu par identité source ?
        Novel novel = sn.sourceId() != 0
                ? novelRepository.findBySourceAndSourceId(source.sourceKey(), sn.sourceId()).orElse(null)
                : null;

        if (novel != null) {
            applyScalars(novel, sn);
            applySnapshot(novel, sn);
            return novelRepository.save(novel);
        }

        // 2) un roman porte-t-il déjà ce slug ?
        Novel bySlug = novelRepository.findBySlug(sn.slug()).orElse(null);
        if (bySlug != null && bySlug.getSource() == null) {
            // Roman hérité (source NULL) au même slug → on l'ADOPTE.
            log.info("Adoption du roman hérité '{}' par la source {} (id={}).",
                    sn.slug(), source.sourceKey(), sn.sourceId());
            bySlug.setSource(source.sourceKey());
            bySlug.setSourceId(sn.sourceId());
            bySlug.setSourceSlug(sn.slug());
            applyScalars(bySlug, sn);
            applySnapshot(bySlug, sn);
            return novelRepository.save(bySlug);
        }

        // 3) création (slug suffixé seulement en cas de vraie collision avec une autre source)
        String slug = resolveSlug(sn.slug());
        novel = new Novel(slug, safe(sn.title()), safe(sn.author()), safe(sn.description()),
                sn.coverUrl(), mapStatus(sn.rawStatus()));
        novel.setSource(source.sourceKey());
        novel.setSourceId(sn.sourceId());
        novel.setSourceSlug(sn.slug());
        novel.setNsfw(sn.nsfw());
        novel.setGenres(resolveGenres(sn.genres()));
        applySnapshot(novel, sn);
        return novelRepository.save(novel);
    }

    /** Met à jour les champs scalaires d'un roman existant (genres laissés tels quels). */
    private void applyScalars(Novel novel, SourceNovel sn) {
        novel.setTitle(safe(sn.title()));
        if (!sn.author().isBlank()) {
            novel.setAuthor(safe(sn.author()));
        }
        novel.setDescription(safe(sn.description()));
        if (sn.coverUrl() != null) {
            novel.setCoverImageUrl(sn.coverUrl());
        }
        novel.setStatus(mapStatus(sn.rawStatus()));
        novel.setNsfw(sn.nsfw());
        if (novel.getSourceSlug() == null) {
            novel.setSourceSlug(sn.slug());
        }
    }

    /**
     * Écrit les trois signaux de fraîcheur EN MÉMOIRE (sans save). Appelé TÔT, dès
     * l'upsert : même si l'import des chapitres est ensuite interrompu, le roman
     * garde des signaux cohérents (nombre attendu, dernier numéro, date).
     */
    private void applySnapshot(Novel novel, SourceNovel sn) {
        novel.setSourceChapterCount(sn.chapterCount());
        novel.setSourceLatestNumber(sn.latestNumber());
        novel.setSourceLastChapterAt(sn.lastChapterAt());
    }

    /** Écrit les signaux + la date de sync, puis sauvegarde (fin d'import / de maintenance). */
    private void updateSyncSnapshot(Novel novel, SourceNovel sn) {
        applySnapshot(novel, sn);
        novel.setLastSyncedAt(Instant.now());
        novelRepository.save(novel);
    }

    /**
     * Rien n'a bougé chez la source ? Les trois signaux du détail suffisent à le
     * dire sans tirer un seul chapitre.
     */
    private static boolean isUnchanged(Novel stored, SourceNovel sn) {
        return Objects.equals(stored.getSourceChapterCount(), sn.chapterCount())
                && numericEquals(stored.getSourceLatestNumber(), sn.latestNumber())
                && Objects.equals(stored.getSourceLastChapterAt(), sn.lastChapterAt());
    }

    private static boolean numericEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0; // compareTo : 23 == 23.0
    }

    /**
     * Convertit un numéro source (float) en INT de chapitre, ou {@code null} si
     * non entier (les décimaux sont sautés — cf. docs/INGESTION_V2.md).
     */
    private static Integer toIntChapterNumber(BigDecimal number) {
        if (number == null) {
            return null;
        }
        try {
            return number.intValueExact();
        } catch (ArithmeticException ex) {
            return null; // partie décimale non nulle
        }
    }

    /**
     * Slug libre, sinon suffixé : {@code novels.slug} est unique global et partagé
     * avec les URL du reader ; un slug source pourrait télescoper un titre hérité
     * différent. On garde le slug source si possible, sinon on suffixe.
     */
    private String resolveSlug(String desired) {
        String base = (desired == null || desired.isBlank()) ? "novel" : desired;
        if (!novelRepository.existsBySlug(base)) {
            return base;
        }
        String candidate = base + "-" + source.sourceKey();
        int i = 2;
        while (novelRepository.existsBySlug(candidate)) {
            candidate = base + "-" + source.sourceKey() + "-" + i;
            i++;
        }
        log.info("Slug '{}' déjà pris → '{}'.", base, candidate);
        return candidate;
    }

    /** Find-or-create : réutilise un genre existant, sinon le crée. */
    private Set<Genre> resolveGenres(List<String> names) {
        Set<Genre> result = new HashSet<>();
        for (String name : names) {
            Genre genre = genreRepository.findByName(name)
                    .orElseGet(() -> genreRepository.save(new Genre(name)));
            result.add(genre);
        }
        return result;
    }

    /** Statut source → notre enum. « completed »/« finished » → COMPLETED, sinon ONGOING. */
    private static NovelStatus mapStatus(String raw) {
        if (raw != null) {
            String s = raw.toLowerCase();
            if (s.contains("complet") || s.contains("finish") || s.contains("end")) {
                return NovelStatus.COMPLETED;
            }
        }
        return NovelStatus.ONGOING; // releasing, ongoing, hiatus… → ONGOING
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /** Politesse : on n'inonde pas la source. */
    private void throttle() {
        long ms = props.getDelayMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("Ingestion interrompue pendant la temporisation", ie);
        }
    }
}
