package com.novelrealm.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.BlockActivityResponse;
import com.novelrealm.dto.ChapterActivityResponse;
import com.novelrealm.dto.CreatePassageCommentRequest;
import com.novelrealm.dto.EmojiTallyResponse;
import com.novelrealm.dto.MentionResponse;
import com.novelrealm.dto.PassageCommentResponse;
import com.novelrealm.dto.PassageReactionResponse;
import com.novelrealm.exception.CommentRateLimitedException;
import com.novelrealm.exception.InvalidPassageException;
import com.novelrealm.exception.PassageAnnotationNotFoundException;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.CommentMention;
import com.novelrealm.model.PassageAnnotation;
import com.novelrealm.model.User;
import com.novelrealm.repository.PassageAnnotationRepository;

/**
 * Réactions et commentaires accrochés à un <b>passage</b> (#41, §4).
 *
 * <p>Trois décisions structurent ce service :
 *
 * <ul>
 *   <li><b>Un seul appel d'agrégats par chapitre.</b> Un chapitre compte couramment
 *       cinquante blocs et le lecteur les fait défiler d'un geste : demander l'activité
 *       bloc par bloc serait intenable. Les compteurs de tout le chapitre partent à
 *       l'ouverture ; un fil n'est chargé que si quelqu'un l'ouvre vraiment.</li>
 *   <li><b>Le client ne connaît que des index vivants.</b> Il envoie « bloc 12 » au
 *       sens de ce qu'il affiche, et reçoit des index recalés sur la version actuelle du
 *       chapitre. Les empreintes ne sortent jamais de cette couche — l'app n'a pas à
 *       savoir qu'elles existent.</li>
 *   <li><b>Une ancre morte ne pointe jamais ailleurs.</b> Un message dont le passage a
 *       disparu devient orphelin et se signale en fin de chapitre, plutôt que de
 *       s'afficher sur un paragraphe qui n'est pas le sien.</li>
 * </ul>
 */
@Service
public class PassageSocialService {

    /**
     * Les six emojis de la <b>barre rapide</b> — ceux proposés d'emblée au double tap,
     * avant même d'ouvrir le clavier complet.
     *
     * <p><b>N'est plus un jeu fermé.</b> Les réactions de bloc sont désormais multi-emoji
     * et le clavier complet est accessible via le bouton « + » ; ces six-là ne sont donc
     * que le raccourci, pas la limite. La validation « c'est bien un emoji » vit dans
     * {@link EmojiValidation}, plus dans cette liste.
     *
     * <p><b>Doit rester identique à {@code PassageRepository.EMOJIS} côté mobile</b> —
     * c'est la barre rapide partagée avec les réactions de commentaire.
     *
     * <p>Le jeu est tourné vers la <b>lecture</b> : ❤️ j'adore · 😂 drôle ·
     * 🤯 le retournement · 😭 ça m'a brisé · 🔥 ça envoie · 😱 glaçant.
     */
    public static final List<String> ALLOWED_EMOJIS = List.of("❤️", "😂", "🤯", "😭", "🔥", "😱");

    /**
     * Délai minimal entre deux commentaires de passage. Plus court que les 15 s de fin
     * de chapitre : ces messages sont brefs, et on réagit parfois à deux répliques
     * d'affilée. Il s'agit d'arrêter une rafale, pas de faire patienter un lecteur.
     */
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(8);

    private final PassageAnnotationRepository annotationRepository;
    private final ChapterService chapterService;
    private final UserService userService;
    private final MentionService mentionService;
    private final NotificationService notificationService;
    private final CommentReactionService reactionService;

    public PassageSocialService(
            PassageAnnotationRepository annotationRepository,
            ChapterService chapterService,
            UserService userService,
            MentionService mentionService,
            NotificationService notificationService,
            CommentReactionService reactionService) {
        this.annotationRepository = annotationRepository;
        this.chapterService = chapterService;
        this.userService = userService;
        this.mentionService = mentionService;
        this.notificationService = notificationService;
        this.reactionService = reactionService;
    }

    // ── Lecture ───────────────────────────────────────────────────────────────

    /**
     * L'activité de tous les blocs d'un chapitre, ancres résolues, en un appel.
     *
     * <p>Le texte du chapitre est lu ici — c'est le prix de la résolution d'ancres, et
     * il est modeste : une ligne, une fois par ouverture de chapitre, alors que l'app
     * vient justement de charger le même texte pour l'afficher. Sans cette lecture, un
     * chapitre ré-ingéré afficherait ses marques sur les mauvais paragraphes.
     */
    @Transactional(readOnly = true)
    public ChapterActivityResponse activity(String email, Long chapterId) {
        User user = userService.findByEmail(email);
        Chapter chapter = chapterService.findById(chapterId);
        List<String> blocks = ChapterBlocks.split(chapter.getContent());

        // Une ancre résolue une fois sert à toutes les lignes qui la partagent :
        // `resolve` peut balayer le chapitre entier, on ne le fait pas six fois.
        Map<String, Integer> resolvedByAnchor = new HashMap<>();
        Map<Integer, BlockActivity> live = new TreeMap<>();
        long orphanedComments = 0;

        for (var tally : annotationRepository.tallyComments(chapterId)) {
            int index = resolve(resolvedByAnchor, blocks, tally.getBlockIndex(), tally.getTextHash());
            if (index < 0) {
                orphanedComments += tally.getTotal();
                continue;
            }
            live.computeIfAbsent(index, key -> new BlockActivity()).comments += tally.getTotal();
        }

        for (var tally : annotationRepository.tallyReactions(chapterId, user.getId())) {
            int index = resolve(resolvedByAnchor, blocks, tally.getBlockIndex(), tally.getTextHash());
            // Une réaction orpheline est simplement perdue, et ce n'est pas grave :
            // « 3 réactions sur un passage disparu » n'apprendrait rien à personne,
            // alors qu'un message orphelin, lui, contient encore des mots à lire.
            if (index < 0) {
                continue;
            }
            BlockActivity activity = live.computeIfAbsent(index, key -> new BlockActivity());
            activity.add(tally.getEmoji(), tally.getTotal(), tally.getMine() > 0);
        }

        List<BlockActivityResponse> responses = new ArrayList<>(live.size());
        live.forEach((index, activity) -> responses.add(activity.toResponse(index)));
        return new ChapterActivityResponse(responses, orphanedComments);
    }

    /**
     * Le fil d'un passage, désigné par l'index tel que l'app l'affiche aujourd'hui.
     *
     * <p>La recherche se fait par empreinte, puis on écarte les homonymes : deux blocs
     * rigoureusement identiques dans un même chapitre (une ligne de séparation, par
     * exemple) partagent la même empreinte, et leurs fils ne doivent pas se mélanger.
     */
    @Transactional(readOnly = true)
    public List<PassageCommentResponse> thread(String email, Long chapterId, int blockIndex) {
        User user = userService.findByEmail(email);
        Chapter chapter = chapterService.findById(chapterId);
        List<String> blocks = requireBlock(chapter, blockIndex);
        String hash = ChapterBlocks.hash(blocks.get(blockIndex));

        List<PassageAnnotation> rows = annotationRepository.findThread(chapterId, hash).stream()
                .filter(annotation -> ChapterBlocks
                        .resolve(blocks, annotation.getBlockIndex(), annotation.getTextHash())
                        .blockIndex() == blockIndex)
                .toList();

        // Les réponses sont indexées en une passe, puis rattachées : le fil entier est
        // déjà en mémoire, aller rechercher les réponses message par message
        // ressusciterait le N+1 que la requête unique vient d'éviter.
        Map<Long, List<PassageAnnotation>> repliesByRoot = rows.stream()
                .filter(row -> !row.isRoot())
                .collect(Collectors.groupingBy(row -> row.getParent().getId()));

        // Les mentions et les réactions de tout le fil, chacune en une requête.
        List<Long> ids = rows.stream().map(PassageAnnotation::getId).toList();
        Map<Long, List<CommentMention>> mentionsBySource = mentionService.forSources(
                CommentMention.SourceKind.PASSAGE_COMMENT, ids);
        Map<Long, CommentReactionService.Summary> reactionsBySource =
                reactionService.summarize(
                        CommentMention.SourceKind.PASSAGE_COMMENT, ids, user.getId());

        return rows.stream()
                .filter(PassageAnnotation::isRoot)
                .map(root -> toResponse(
                        root,
                        user.getId(),
                        repliesByRoot.getOrDefault(root.getId(), List.of()),
                        mentionsBySource,
                        reactionsBySource))
                .toList();
    }

    // ── Écriture ──────────────────────────────────────────────────────────────

    /**
     * Accroche un message au bloc désigné.
     *
     * <p>Depuis l'issue #45 : mentions (§2), GIF joint (§5, le corps peut alors
     * être vide), et notifications (§3) — l'auteur du fil est prévenu d'une
     * réponse, les mentionnés d'une mention, jamais deux fois la même personne.
     */
    @Transactional
    public PassageCommentResponse comment(
            String email, Long chapterId, CreatePassageCommentRequest request) {
        int blockIndex = request.blockIndex();
        User user = userService.findByEmail(email);
        Chapter chapter = chapterService.findById(chapterId);
        List<String> blocks = requireBlock(chapter, blockIndex);
        PassageAnnotation parent = resolveParent(chapterId, request.parentId());

        String cleaned = request.body() == null ? "" : request.body().strip();
        String gifUrl = GifService.requireGifUrl(request.gifUrl());
        String gifPreviewUrl = GifService.requireGifUrl(request.gifPreviewUrl());
        if (cleaned.isEmpty() && gifUrl == null) {
            throw new InvalidPassageException("Le message ne peut pas être vide");
        }
        if (cleaned.length() > PassageAnnotation.MAX_BODY_LENGTH) {
            throw new InvalidPassageException(
                    "Le message ne peut pas dépasser "
                            + PassageAnnotation.MAX_BODY_LENGTH + " caractères");
        }
        requireNotFlooding(user);

        PassageAnnotation annotation = PassageAnnotation.comment(
                chapter,
                user,
                // Une réponse hérite de l'ancre de son fil : elle appartient au même
                // passage, et doit remonter dans la même requête que lui.
                parent != null ? parent.getBlockIndex() : blockIndex,
                parent != null
                        ? parent.getTextHash()
                        : ChapterBlocks.hash(blocks.get(blockIndex)),
                cleaned,
                request.isSpoiler(),
                parent);
        if (gifUrl != null) {
            annotation.attachGif(gifUrl, gifPreviewUrl != null ? gifPreviewUrl : gifUrl);
        }
        PassageAnnotation saved = annotationRepository.save(annotation);

        List<CommentMention> mentions = mentionService.attach(
                CommentMention.SourceKind.PASSAGE_COMMENT,
                saved.getId(), user, request.mentionedUserIds(), cleaned);

        // L'index transmis est celui que le lecteur affiche EN CE MOMENT : c'est
        // le meilleur lien profond possible pour la notification.
        User threadAuthor = parent != null ? parent.getUser() : null;
        if (threadAuthor != null) {
            notificationService.notifyReply(
                    threadAuthor, user, chapter,
                    CommentMention.SourceKind.PASSAGE_COMMENT, saved.getId(), blockIndex, cleaned);
        }
        for (CommentMention mention : mentions) {
            User mentioned = mention.getMentioned();
            if (threadAuthor != null && mentioned.getId().equals(threadAuthor.getId())) {
                continue; // déjà prévenu par la réponse
            }
            notificationService.notifyMention(
                    mentioned, user, chapter,
                    CommentMention.SourceKind.PASSAGE_COMMENT, saved.getId(), blockIndex, cleaned);
        }

        // Un message fraîchement publié ne porte encore aucune réaction.
        return toResponse(
                saved, user.getId(), List.of(), Map.of(saved.getId(), mentions), Map.of());
    }

    /**
     * Le message racine auquel rattacher une réponse, ou {@code null} pour un nouveau fil.
     *
     * <p><b>Répondre à une réponse est accepté et re-rattaché au fil</b> plutôt que
     * refusé : du point de vue du lecteur, le geste est identique, et lui opposer une
     * erreur pour une règle d'affichage serait incompréhensible. C'est aussi ce qui
     * garantit qu'un fil ne descend jamais au-delà d'un niveau, quelle que soit la
     * cible envoyée par le client.
     */
    private PassageAnnotation resolveParent(Long chapterId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        PassageAnnotation parent = annotationRepository.findById(parentId)
                .filter(candidate -> candidate.getKind() == PassageAnnotation.Kind.COMMENT)
                .filter(candidate -> candidate.getChapter().getId().equals(chapterId))
                .orElseThrow(() -> new PassageAnnotationNotFoundException(parentId));
        return parent.isRoot() ? parent : parent.getParent();
    }

    /**
     * Pose, remplace ou retire une réaction — un seul geste côté lecteur, donc un seul
     * appel côté API.
     *
     * <p>Toucher l'emoji déjà posé le retire ; en toucher un autre le remplace. Un
     * lecteur n'a qu'une réaction par passage : la marge n'affiche qu'un compteur, et
     * laisser quelqu'un poser les six emojis sur le même paragraphe gonflerait
     * l'agrégat sans rien exprimer de plus.
     */
    @Transactional
    public PassageReactionResponse react(
            String email, Long chapterId, int blockIndex, String rawEmoji) {
        User user = userService.findByEmail(email);
        Chapter chapter = chapterService.findById(chapterId);
        List<String> blocks = requireBlock(chapter, blockIndex);

        // Multi-emoji + clavier complet : on ne compare plus à un jeu fermé, mais on
        // exige que ce soit bien un emoji (EmojiValidation, partagée avec les réactions
        // de commentaire) — un client modifié ne doit pas glisser du texte arbitraire.
        String emoji = EmojiValidation.sanitize(rawEmoji)
                .orElseThrow(() -> new InvalidPassageException("Ce n'est pas un emoji valide"));

        String hash = ChapterBlocks.hash(blocks.get(blockIndex));
        Optional<PassageAnnotation> existing =
                annotationRepository.findMyReaction(chapterId, hash, user.getId(), emoji);

        // Bascule pur : cet emoji déjà posé → on le retire, sinon → on l'ajoute. Plus
        // de « remplace » — poser un second emoji ne défait plus le premier.
        if (existing.isPresent()) {
            annotationRepository.delete(existing.get());
        } else {
            annotationRepository.save(
                    PassageAnnotation.reaction(chapter, user, blockIndex, hash, emoji));
        }
        // Vidé vers la base avant de recompter : sans ça, l'agrégat renverrait l'état
        // d'avant le geste et la puce reviendrait en arrière sous le doigt.
        annotationRepository.flush();

        return tallyOf(chapterId, blockIndex, hash, user.getId(), blocks);
    }

    /** Retire un de SES messages. Un message n'est jamais modifiable : trop court pour ça. */
    @Transactional
    public void delete(String email, Long annotationId) {
        User user = userService.findByEmail(email);
        PassageAnnotation annotation = annotationRepository
                .findByIdAndUser_Id(annotationId, user.getId())
                .filter(a -> a.getKind() == PassageAnnotation.Kind.COMMENT
                        || a.getKind() == PassageAnnotation.Kind.REACTION)
                .orElseThrow(() -> new PassageAnnotationNotFoundException(annotationId));

        // Les lignes de passage disparaissent VRAIMENT (pas de pierre tombale) : il
        // faut emporter leurs mentions, y compris celles des réponses que la base
        // supprime en cascade — sinon elles resteraient orphelines pour toujours.
        if (annotation.getKind() == PassageAnnotation.Kind.COMMENT) {
            List<Long> doomed = new ArrayList<>();
            doomed.add(annotation.getId());
            if (annotation.isRoot()) {
                doomed.addAll(annotationRepository.findReplyIds(annotation.getId()));
            }
            mentionService.deleteFor(CommentMention.SourceKind.PASSAGE_COMMENT, doomed);
            // La ligne disparaît vraiment : ses réactions (et celles de ses réponses
            // emportées en cascade) deviendraient orphelines, on les efface aussi.
            reactionService.deleteFor(CommentMention.SourceKind.PASSAGE_COMMENT, doomed);
        }

        annotationRepository.delete(annotation);
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    /** L'état d'un seul bloc après une écriture, sans redemander tout le chapitre. */
    private PassageReactionResponse tallyOf(
            Long chapterId, int blockIndex, String hash, Long userId, List<String> blocks) {

        BlockActivity activity = new BlockActivity();
        for (var tally : annotationRepository.tallyReactions(chapterId, userId)) {
            if (!hash.equals(tally.getTextHash())) {
                continue;
            }
            // Même précaution que pour le fil : deux blocs identiques partagent une
            // empreinte, leurs compteurs ne doivent pas se mélanger.
            if (ChapterBlocks.resolve(blocks, tally.getBlockIndex(), tally.getTextHash())
                    .blockIndex() != blockIndex) {
                continue;
            }
            activity.add(tally.getEmoji(), tally.getTotal(), tally.getMine() > 0);
        }
        return activity.toReactionResponse(blockIndex);
    }

    /** Mémoïse la résolution : une même ancre revient une fois par emoji. */
    private static int resolve(
            Map<String, Integer> cache, List<String> blocks, int storedIndex, String storedHash) {
        return cache.computeIfAbsent(
                storedIndex + "@" + storedHash,
                key -> ChapterBlocks.resolve(blocks, storedIndex, storedHash).blockIndex());
    }

    /** Les blocs du chapitre, après avoir vérifié que l'index en désigne bien un. */
    private static List<String> requireBlock(Chapter chapter, int blockIndex) {
        List<String> blocks = ChapterBlocks.split(chapter.getContent());
        if (blockIndex < 0 || blockIndex >= blocks.size()) {
            // L'app et l'API ont découpé le chapitre différemment, ou le chapitre a
            // changé sous les pieds du lecteur. Refuser vaut mieux qu'accrocher un
            // message à un paragraphe au hasard.
            throw new InvalidPassageException("Ce passage n'existe pas dans ce chapitre");
        }
        return blocks;
    }

    private void requireNotFlooding(User user) {
        boolean tooSoon = annotationRepository.existsByUser_IdAndKindAndCreatedAtAfter(
                user.getId(), PassageAnnotation.Kind.COMMENT, Instant.now().minus(MIN_INTERVAL));
        if (tooSoon) {
            throw new CommentRateLimitedException(MIN_INTERVAL.toSeconds());
        }
    }

    private static PassageCommentResponse toResponse(
            PassageAnnotation annotation,
            Long currentUserId,
            List<PassageAnnotation> replies,
            Map<Long, List<CommentMention>> mentionsBySource,
            Map<Long, CommentReactionService.Summary> reactionsBySource) {
        User author = annotation.getUser();
        List<PassageCommentResponse> mappedReplies = replies.stream()
                .sorted(Comparator.comparing(PassageAnnotation::getCreatedAt))
                .map(reply -> toResponse(
                        reply, currentUserId, List.of(), mentionsBySource, reactionsBySource))
                .toList();
        List<MentionResponse> mentions = mentionsBySource
                .getOrDefault(annotation.getId(), List.of()).stream()
                .map(mention -> new MentionResponse(
                        mention.getMentioned().getId(),
                        mention.getHandle(),
                        mention.getMentioned().getPseudo()))
                .toList();
        CommentReactionService.Summary reactions = reactionsBySource.getOrDefault(
                annotation.getId(), CommentReactionService.Summary.EMPTY);
        return new PassageCommentResponse(
                annotation.getId(),
                author.getId(),
                author.getPseudo(),
                author.getAvatarUrl(),
                annotation.getBody(),
                annotation.isSpoiler(),
                currentUserId != null && currentUserId.equals(author.getId()),
                annotation.getCreatedAt(),
                mentions,
                annotation.getGifUrl(),
                annotation.getGifPreviewUrl(),
                reactions.tallies(),
                reactions.mine(),
                mappedReplies);
    }

    /**
     * Accumulateur d'un bloc pendant l'agrégation.
     *
     * <p>Depuis le passage au multi-emoji, un lecteur peut avoir PLUSIEURS réactions
     * sur le même bloc : {@code myReactions} collecte donc tous ses emojis, là où
     * l'ancien {@code myEmoji} n'en gardait qu'un.
     *
     * <p>Les emojis ne suivent plus un jeu fermé (le clavier est complet) : ils sont
     * rangés par nombre décroissant, l'emoji lui-même départageant les égalités —
     * l'ordre reste donc stable pour un même état, sans clignoter d'un appel à l'autre.
     */
    private static final class BlockActivity {
        private long comments;
        private final Map<String, Long> counts = new LinkedHashMap<>();
        private final List<String> myReactions = new ArrayList<>();

        void add(String emoji, long total, boolean mine) {
            if (emoji == null) {
                return;
            }
            counts.merge(emoji, total, Long::sum);
            if (mine) {
                myReactions.add(emoji);
            }
        }

        List<EmojiTallyResponse> tallies() {
            return counts.entrySet().stream()
                    .sorted(Comparator
                            .comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(e -> new EmojiTallyResponse(e.getKey(), e.getValue()))
                    .toList();
        }

        BlockActivityResponse toResponse(int blockIndex) {
            return new BlockActivityResponse(blockIndex, comments, tallies(), List.copyOf(myReactions));
        }

        /** L'état des réactions d'un seul bloc, renvoyé après un geste de réaction. */
        PassageReactionResponse toReactionResponse(int blockIndex) {
            return new PassageReactionResponse(blockIndex, List.copyOf(myReactions), tallies());
        }
    }
}
