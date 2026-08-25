package com.novelrealm.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.novelrealm.model.Chapter;
import com.novelrealm.model.Genre;
import com.novelrealm.model.Novel;
import com.novelrealm.model.Novel.NovelStatus;
import com.novelrealm.repository.ChapterRepository;
import com.novelrealm.repository.GenreRepository;
import com.novelrealm.repository.NovelRepository;

/**
 * Orchestration de l'ingestion : scrape → mappe vers les entités → upsert.
 * Idempotent : un roman est identifié par son slug, les chapitres déjà
 * présents (même numéro) sont ignorés. Ce n'est PAS dans le chemin de requête
 * (déclenché par IngestionRunner au démarrage, via une propriété).
 */
@Service
public class NovelIngestionService {

    private static final Logger log = LoggerFactory.getLogger(NovelIngestionService.class);

    private final LightNovelWorldScraper scraper;
    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    private final GenreRepository genreRepository;

    @Value("${novelrealm.ingestion.max-chapters:50}")
    private int maxChapters;

    @Value("${novelrealm.ingestion.delay-ms:500}")
    private long delayMs;

    public NovelIngestionService(LightNovelWorldScraper scraper, NovelRepository novelRepository,
            ChapterRepository chapterRepository, GenreRepository genreRepository) {
        this.scraper = scraper;
        this.novelRepository = novelRepository;
        this.chapterRepository = chapterRepository;
        this.genreRepository = genreRepository;
    }

    public void ingest(String slug) throws IOException, InterruptedException {
        log.info("Ingestion '{}' : métadonnées…", slug);
        Novel novel = upsertNovel(scraper.scrapeNovel(slug));

        log.info("Ingestion '{}' : liste des chapitres (max {})…", slug, maxChapters);
        List<ScrapedChapterRef> refs = scraper.scrapeChapterRefs(slug, maxChapters);

        Set<Integer> existing = chapterRepository.findByNovelIdOrderByChapterNumber(novel.getId())
                .stream().map(Chapter::getChapterNumber).collect(Collectors.toSet());

        int imported = 0;
        for (ScrapedChapterRef ref : refs) {
            if (existing.contains(ref.chapterNumber())) {
                continue; // déjà en base → idempotent
            }
            ScrapedChapter sc = scraper.scrapeChapter(ref);
            if (sc.content().isBlank()) {
                // On n'enregistre PAS un chapitre vide : sinon l'idempotence le
                // sauterait à jamais. Non enregistré = re-tenté au prochain run.
                log.warn("  ! chapitre {} ignoré (contenu vide — page protégée ou sélecteur à revoir)",
                        sc.chapterNumber());
                continue;
            }
            chapterRepository.save(new Chapter(novel, sc.chapterNumber(), sc.title(), sc.content()));
            imported++;
            log.info("  + chapitre {} — {}", sc.chapterNumber(), sc.title());
            Thread.sleep(delayMs); // politesse : on n'inonde pas le site
        }

        log.info("Ingestion '{}' terminée : {} nouveau(x) chapitre(s).", slug, imported);
    }

    private Novel upsertNovel(ScrapedNovel sn) {
        NovelStatus status = mapStatus(sn.status());
        Novel novel = novelRepository.findBySlug(sn.slug()).orElse(null);

        if (novel == null) {
            novel = new Novel(sn.slug(), sn.title(), sn.author(), sn.description(), sn.coverUrl(), status);
            novel.setGenres(resolveGenres(sn.genres()));
        } else {
            // Re-run : on met à jour les champs scalaires (les genres ne bougent pas).
            novel.setTitle(sn.title());
            novel.setAuthor(sn.author());
            novel.setDescription(sn.description());
            novel.setCoverImageUrl(sn.coverUrl());
            novel.setStatus(status);
        }
        return novelRepository.save(novel);
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

    private NovelStatus mapStatus(String raw) {
        if (raw != null && raw.toLowerCase().contains("complet")) {
            return NovelStatus.COMPLETED;
        }
        return NovelStatus.ONGOING; // "Ongoing", "Hiatus", … → ONGOING
    }
}
