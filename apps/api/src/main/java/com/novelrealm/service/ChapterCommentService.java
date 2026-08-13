package com.novelrealm.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.ChapterCommentResponse;
import com.novelrealm.dto.CreateChapterCommentRequest;
import com.novelrealm.dto.MentionResponse;
import com.novelrealm.exception.ChapterCommentNotFoundException;
import com.novelrealm.exception.CommentNotOwnedException;
import com.novelrealm.exception.CommentRateLimitedException;
import com.novelrealm.exception.InvalidCommentException;
import com.novelrealm.model.Chapter;
import com.novelrealm.model.ChapterComment;
import com.novelrealm.model.CommentMention;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterCommentRepository;

/**
 * Messages laissés en fin de chapitre (#41).
 *
 * <p>Contrairement aux autres services, celui-ci construit lui-même ses DTO au
 * lieu de laisser le contrôleur s'en charger : un fil est une structure à deux
 * niveaux, et ses réponses sont chargées en une requête pour toute la page. Faire
 * l'assemblage ailleurs rouvrirait la porte au N+1 qu'on cherche à éviter.
 *
 * <p>Depuis l'issue #45 : mentions {@code @pseudo} (§2), notifications de réponse
 * et de mention (§3), GIF joint (§5). Tout part dans la transaction du message —
 * un message publié sans ses mentions ou sans sa notification, ça n'existe pas.
 */
@Service
public class ChapterCommentService {

    /** Garde-fou de pagination : au-delà, on plafonne. */
    private static final int MAX_PAGE_SIZE = 50;

    /** Délai minimal entre deux messages d'un même utilisateur (anti-rafale). */
    private static final Duration MIN_INTERVAL = Duration.ofSeconds(15);

    private final ChapterCommentRepository commentRepository;
    private final ChapterService chapterService;
    private final NovelService novelService;
    private final UserService userService;
    private final MentionService mentionService;
    private final NotificationService notificationService;

    public ChapterCommentService(
            ChapterCommentRepository commentRepository,
            ChapterService chapterService,
            NovelService novelService,
            UserService userService,
            MentionService mentionService,
            NotificationService notificationService) {
        this.commentRepository = commentRepository;
        this.chapterService = chapterService;
        this.novelService = novelService;
        this.userService = userService;
        this.mentionService = mentionService;
        this.notificationService = notificationService;
    }

    /** Tris proposés au lecteur. */
    public enum Sort {
        /** Les fils les plus récents d'abord — le défaut. */
        RECENT,
        /** Les fils qui ont le plus de réponses d'abord : « là où ça parle ». */
        DISCUSSED;

        /** Tolérant : une valeur inconnue ou absente retombe sur {@link #RECENT}. */
        public static Sort parse(String raw) {
            if (raw == null) {
                return RECENT;
            }
            return "discussed".equalsIgnoreCase(raw) ? DISCUSSED : RECENT;
        }
    }

    /**
     * Une page de fils du chapitre, réponses incluses. {@code email} peut être
     * null (lecture anonyme) : aucun message ne sera alors marqué comme sien.
     */
    @Transactional(readOnly = true)
    public Page<ChapterCommentResponse> listThreads(
            String email, Long chapterId, Sort sort, int page, int size) {
        chapterService.findById(chapterId); // 404 si le chapitre n'existe pas

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Page<ChapterComment> roots = switch (sort) {
            case DISCUSSED -> commentRepository.findRootsDiscussed(chapterId, pageable);
            case RECENT -> commentRepository.findRootsRecent(chapterId, pageable);
        };

        Long currentUserId = currentUserId(email);
        Map<Long, List<ChapterComment>> repliesByRoot = loadReplies(roots.getContent());

        // Les mentions de TOUTE la page (racines + réponses) en une requête.
        List<Long> allIds = Stream.concat(
                        roots.getContent().stream().map(ChapterComment::getId),
                        repliesByRoot.values().stream().flatMap(List::stream)
                                .map(ChapterComment::getId))
                .toList();
        Map<Long, List<CommentMention>> mentionsBySource =
                mentionService.forSources(CommentMention.SourceKind.CHAPTER_COMMENT, allIds);

        return roots.map(root -> toResponse(
                root,
                currentUserId,
                repliesByRoot.getOrDefault(root.getId(), List.of()),
                mentionsBySource));
    }

    /** Nombre de messages du chapitre, réponses comprises. */
    @Transactional(readOnly = true)
    public long count(Long chapterId) {
        chapterService.findById(chapterId);
        return commentRepository.countByChapter_IdAndDeletedAtIsNull(chapterId);
    }

    /**
     * Nombre de messages par chapitre pour un roman entier — un seul appel pour
     * toute la liste des chapitres. Les chapitres sans message sont absents : au
     * client d'afficher zéro (ou rien) pour les clés manquantes.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> countsByNovel(Long novelId) {
        novelService.findById(novelId); // 404 si le roman n'existe pas
        return commentRepository.countByNovel(novelId).stream()
                .collect(Collectors.toMap(
                        ChapterCommentRepository.ChapterCommentCount::getChapterId,
                        ChapterCommentRepository.ChapterCommentCount::getCount));
    }

    /**
     * Publie un message. Si {@code parentId} désigne une réponse, le message est
     * re-rattaché à la racine du fil : l'invariant « un seul niveau » est tenu
     * ici, et non par la confiance faite au client.
     *
     * <p>Notifications (issue #45, §3) : l'auteur du fil est prévenu d'une
     * réponse ; les personnes mentionnées sont prévenues d'une mention. Jamais
     * deux notifications pour la même personne sur le même message — la réponse
     * l'emporte sur la mention.
     */
    @Transactional
    public ChapterCommentResponse create(
            String email, Long chapterId, CreateChapterCommentRequest request) {
        User author = userService.findByEmail(email);
        Chapter chapter = chapterService.findById(chapterId);
        enforceRateLimit(author.getId());

        String body = request.body() == null ? "" : request.body().strip();
        String gifUrl = GifService.requireGifUrl(request.gifUrl());
        String gifPreviewUrl = GifService.requireGifUrl(request.gifPreviewUrl());
        if (body.isEmpty() && gifUrl == null) {
            throw new InvalidCommentException("Le message ne peut pas être vide");
        }

        ChapterComment parent = resolveParent(request.parentId(), chapterId);
        ChapterComment comment = new ChapterComment(chapter, author, parent, body);
        if (gifUrl != null) {
            comment.attachGif(gifUrl, gifPreviewUrl != null ? gifPreviewUrl : gifUrl);
        }
        ChapterComment saved = commentRepository.save(comment);

        List<CommentMention> mentions = mentionService.attach(
                CommentMention.SourceKind.CHAPTER_COMMENT,
                saved.getId(), author, request.mentionedUserIds(), body);

        // Un parent supprimé (pierre tombale) n'est plus dans la conversation :
        // il n'a rien demandé, on ne le notifie pas.
        User threadAuthor = (parent != null && !parent.isDeleted()) ? parent.getUser() : null;
        if (threadAuthor != null) {
            notificationService.notifyReply(
                    threadAuthor, author, chapter,
                    CommentMention.SourceKind.CHAPTER_COMMENT, saved.getId(), null, body);
        }
        for (CommentMention mention : mentions) {
            User user = mention.getMentioned();
            if (threadAuthor != null && user.getId().equals(threadAuthor.getId())) {
                continue; // déjà prévenu par la réponse
            }
            notificationService.notifyMention(
                    user, author, chapter,
                    CommentMention.SourceKind.CHAPTER_COMMENT, saved.getId(), null, body);
        }

        return toResponse(saved, author.getId(), List.of(), Map.of(saved.getId(), mentions));
    }

    /** Modifie SON message. 403 si ce n'est pas le sien, 404 s'il est supprimé. */
    @Transactional
    public ChapterCommentResponse update(String email, Long commentId, String body) {
        User author = userService.findByEmail(email);
        ChapterComment comment = findOwned(author, commentId);
        comment.setBody(body.strip());
        // @Transactional → le flush écrit la modification et met à jour updatedAt.
        // Les mentions ne sont PAS rééditables : en changer après coup notifierait
        // des gens sur un message qu'ils n'ont jamais reçu tel quel.
        Map<Long, List<CommentMention>> mentions = mentionService.forSources(
                CommentMention.SourceKind.CHAPTER_COMMENT, List.of(comment.getId()));
        return toResponse(comment, author.getId(), loadRepliesOf(comment), mentions);
    }

    /**
     * Supprime SON message — en douceur. La ligne reste : les réponses qui y sont
     * accrochées perdraient sinon leur point d'attache et disparaîtraient du fil.
     */
    @Transactional
    public void delete(String email, Long commentId) {
        User author = userService.findByEmail(email);
        findOwned(author, commentId).softDelete();
    }

    // ── Interne ───────────────────────────────────────────────────────────────

    /**
     * Refuse un message publié trop vite après le précédent. On interroge la base
     * plutôt que de tenir un compteur en mémoire : le garde-fou survit ainsi à un
     * redémarrage, et reste juste si l'API tourne en plusieurs exemplaires.
     */
    private void enforceRateLimit(Long userId) {
        Instant since = Instant.now().minus(MIN_INTERVAL);
        if (commentRepository.existsByUser_IdAndCreatedAtAfter(userId, since)) {
            throw new CommentRateLimitedException(MIN_INTERVAL.toSeconds());
        }
    }

    /**
     * Le parent effectif d'une réponse : le message visé s'il ouvre un fil, sinon
     * la racine de son fil. Un parent d'un autre chapitre est refusé (404) — on
     * ne raccroche pas une discussion à un chapitre où elle ne s'affichera pas.
     */
    private ChapterComment resolveParent(Long parentId, Long chapterId) {
        if (parentId == null) {
            return null;
        }
        ChapterComment target = commentRepository.findById(parentId)
                .filter(c -> !c.isDeleted())
                .filter(c -> c.getChapter().getId().equals(chapterId))
                .orElseThrow(() -> new ChapterCommentNotFoundException(parentId));
        return target.isRoot() ? target : target.getParent();
    }

    /** Le message s'il existe, est vivant, et appartient bien à {@code author}. */
    private ChapterComment findOwned(User author, Long commentId) {
        ChapterComment comment = commentRepository.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ChapterCommentNotFoundException(commentId));
        if (!comment.getUser().getId().equals(author.getId())) {
            throw new CommentNotOwnedException();
        }
        return comment;
    }

    /** Réponses de tous les fils d'une page, groupées par racine (une requête). */
    private Map<Long, List<ChapterComment>> loadReplies(List<ChapterComment> roots) {
        if (roots.isEmpty()) {
            return Map.of();
        }
        List<Long> rootIds = roots.stream().map(ChapterComment::getId).toList();
        Map<Long, List<ChapterComment>> grouped = new HashMap<>();
        for (ChapterComment reply : commentRepository.findRepliesOf(rootIds)) {
            grouped.computeIfAbsent(reply.getParent().getId(), key -> new ArrayList<>())
                    .add(reply);
        }
        return grouped;
    }

    /** Réponses d'un seul message (vide si ce message est lui-même une réponse). */
    private List<ChapterComment> loadRepliesOf(ChapterComment comment) {
        return comment.isRoot()
                ? commentRepository.findRepliesOf(List.of(comment.getId()))
                : List.of();
    }

    private Long currentUserId(String email) {
        return email == null ? null : userService.findByEmail(email).getId();
    }

    /**
     * Entité → DTO. Un message supprimé devient une pierre tombale : ni corps ni
     * auteur, mais ses réponses restent lisibles.
     */
    private static ChapterCommentResponse toResponse(
            ChapterComment comment,
            Long currentUserId,
            List<ChapterComment> replies,
            Map<Long, List<CommentMention>> mentionsBySource) {

        List<ChapterCommentResponse> mappedReplies = replies.stream()
                .map(reply -> toResponse(reply, currentUserId, List.of(), mentionsBySource))
                .toList();

        if (comment.isDeleted()) {
            return new ChapterCommentResponse(
                    comment.getId(), null, null, null, null,
                    true, false, false,
                    comment.getCreatedAt(), comment.getUpdatedAt(),
                    List.of(), null, null, mappedReplies);
        }

        User author = comment.getUser();
        boolean mine = currentUserId != null && currentUserId.equals(author.getId());
        // Une seconde de tolérance : @PrePersist pose createdAt et updatedAt à des
        // instants théoriquement identiques, mais rien ne le garantit au nanoseconde.
        boolean edited = comment.getUpdatedAt().isAfter(comment.getCreatedAt().plusSeconds(1));

        return new ChapterCommentResponse(
                comment.getId(),
                author.getId(),
                author.getPseudo(),
                author.getAvatarUrl(),
                comment.getBody(),
                false,
                mine,
                edited,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                toMentionResponses(mentionsBySource.getOrDefault(comment.getId(), List.of())),
                comment.getGifUrl(),
                comment.getGifPreviewUrl(),
                mappedReplies);
    }

    private static List<MentionResponse> toMentionResponses(List<CommentMention> mentions) {
        return mentions.stream()
                .map(mention -> new MentionResponse(
                        mention.getMentioned().getId(),
                        mention.getHandle(),
                        mention.getMentioned().getPseudo()))
                .toList();
    }
}
