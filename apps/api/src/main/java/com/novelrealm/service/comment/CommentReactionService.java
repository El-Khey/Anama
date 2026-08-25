package com.novelrealm.service.comment;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.comment.CommentReactionsResponse;
import com.novelrealm.dto.comment.EmojiTallyResponse;
import com.novelrealm.exception.comment.ChapterCommentNotFoundException;
import com.novelrealm.exception.comment.InvalidCommentException;
import com.novelrealm.exception.passage.PassageAnnotationNotFoundException;
import com.novelrealm.model.ChapterComment;
import com.novelrealm.model.CommentMention.SourceKind;
import com.novelrealm.model.CommentReaction;
import com.novelrealm.model.PassageAnnotation;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterCommentRepository;
import com.novelrealm.repository.CommentReactionRepository;
import com.novelrealm.repository.PassageAnnotationRepository;
import com.novelrealm.service.common.EmojiValidation;
import com.novelrealm.service.user.UserService;

/**
 * Réactions emoji sur les commentaires (façon Discord), pour les deux emplacements —
 * fin de chapitre et passage. Calqué sur {@link MentionService} : polymorphe, chargé
 * par lot, purgé par les services qui suppriment vraiment un commentaire.
 *
 * <p><b>Un geste, un appel.</b> Toucher un emoji déjà posé le retire, en toucher un
 * nouveau l'ajoute — un lecteur peut en cumuler plusieurs sur le même message. Le
 * quadruplet (source, utilisateur, emoji) est unique en base : l'opération est donc
 * idempotente au sens où un double tap ne crée jamais deux fois la même réaction.
 */
@Service
public class CommentReactionService {

    private final CommentReactionRepository reactionRepository;
    private final ChapterCommentRepository chapterCommentRepository;
    private final PassageAnnotationRepository annotationRepository;
    private final UserService userService;

    public CommentReactionService(
            CommentReactionRepository reactionRepository,
            ChapterCommentRepository chapterCommentRepository,
            PassageAnnotationRepository annotationRepository,
            UserService userService) {
        this.reactionRepository = reactionRepository;
        this.chapterCommentRepository = chapterCommentRepository;
        this.annotationRepository = annotationRepository;
        this.userService = userService;
    }

    /**
     * Pose ou retire une réaction sur un commentaire de fin de chapitre, et renvoie
     * l'état à jour de ses réactions.
     */
    @Transactional
    public CommentReactionsResponse toggleOnChapterComment(
            String email, Long commentId, String emoji) {
        ChapterComment comment = chapterCommentRepository.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ChapterCommentNotFoundException(commentId));
        return toggle(email, SourceKind.CHAPTER_COMMENT, comment.getId(), emoji);
    }

    /**
     * Pose ou retire une réaction sur un commentaire accroché à un passage, et
     * renvoie l'état à jour de ses réactions. Refuse une réaction, une citation ou un
     * surlignage : on ne réagit qu'à un vrai message.
     */
    @Transactional
    public CommentReactionsResponse toggleOnPassageComment(
            String email, Long annotationId, String emoji) {
        PassageAnnotation comment = annotationRepository.findById(annotationId)
                .filter(a -> a.getKind() == PassageAnnotation.Kind.COMMENT)
                .orElseThrow(() -> new PassageAnnotationNotFoundException(annotationId));
        return toggle(email, SourceKind.PASSAGE_COMMENT, comment.getId(), emoji);
    }

    private CommentReactionsResponse toggle(
            String email, SourceKind kind, Long sourceId, String rawEmoji) {
        User user = userService.findByEmail(email);
        String emoji = sanitizeEmoji(rawEmoji);

        reactionRepository
                .findBySourceKindAndSourceIdAndUser_IdAndEmoji(kind, sourceId, user.getId(), emoji)
                .ifPresentOrElse(
                        reactionRepository::delete,
                        () -> reactionRepository.save(
                                new CommentReaction(kind, sourceId, user, emoji)));
        // Vidé vers la base avant de recompter : sans ça, l'agrégat renverrait l'état
        // d'avant le geste et la puce reviendrait en arrière sous le doigt.
        reactionRepository.flush();

        Map<Long, Summary> summaries =
                summarize(kind, List.of(sourceId), user.getId());
        Summary summary = summaries.getOrDefault(sourceId, Summary.EMPTY);
        return new CommentReactionsResponse(sourceId, summary.tallies(), summary.mine());
    }

    /**
     * Les réactions d'un lot de commentaires, groupées par commentaire — une seule
     * requête pour toute une page de fils. {@code currentUserId} peut être null
     * (lecture anonyme) : {@code mine} est alors toujours vide. Clé absente = aucune
     * réaction sur ce commentaire.
     */
    @Transactional(readOnly = true)
    public Map<Long, Summary> summarize(
            SourceKind kind, Collection<Long> sourceIds, Long currentUserId) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        List<CommentReaction> rows =
                reactionRepository.findBySourceKindAndSourceIdIn(kind, sourceIds);

        // Deux niveaux d'agrégat en une passe : par commentaire, puis par emoji.
        // LinkedHashMap pour un ordre stable avant le tri final.
        Map<Long, Map<String, Long>> countsByComment = new LinkedHashMap<>();
        Map<Long, List<String>> mineByComment = new LinkedHashMap<>();
        for (CommentReaction row : rows) {
            countsByComment
                    .computeIfAbsent(row.getSourceId(), k -> new LinkedHashMap<>())
                    .merge(row.getEmoji(), 1L, Long::sum);
            if (currentUserId != null && currentUserId.equals(row.getUser().getId())) {
                mineByComment
                        .computeIfAbsent(row.getSourceId(), k -> new java.util.ArrayList<>())
                        .add(row.getEmoji());
            }
        }

        Map<Long, Summary> result = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, Long>> entry : countsByComment.entrySet()) {
            // Les plus posés d'abord ; à égalité, l'ordre d'apparition (LinkedHashMap)
            // départage — stable d'un appel à l'autre pour un même état.
            List<EmojiTallyResponse> tallies = entry.getValue().entrySet().stream()
                    .sorted(Comparator.comparingLong(
                            (Map.Entry<String, Long> e) -> e.getValue()).reversed())
                    .map(e -> new EmojiTallyResponse(e.getKey(), e.getValue()))
                    .toList();
            result.put(entry.getKey(), new Summary(
                    tallies, mineByComment.getOrDefault(entry.getKey(), List.of())));
        }
        return result;
    }

    /** Purge les réactions de commentaires dont la ligne va vraiment disparaître. */
    @Transactional
    public void deleteFor(SourceKind kind, Collection<Long> sourceIds) {
        if (!sourceIds.isEmpty()) {
            reactionRepository.deleteBySourceKindAndSourceIdIn(kind, sourceIds);
        }
    }

    /**
     * Accepte un emoji, refuse une chaîne arbitraire. La validation elle-même vit dans
     * {@link EmojiValidation} (partagée avec les réactions de bloc) ; ici on ne fait que
     * traduire un refus dans l'erreur du domaine commentaire.
     */
    private static String sanitizeEmoji(String raw) {
        return EmojiValidation.sanitize(raw)
                .orElseThrow(() -> new InvalidCommentException("Ce n'est pas un emoji valide"));
    }

    /**
     * Résumé des réactions d'un commentaire : le décompte par emoji (trié) et les
     * emojis que le lecteur courant a lui-même posés.
     */
    public record Summary(List<EmojiTallyResponse> tallies, List<String> mine) {
        public static final Summary EMPTY = new Summary(List.of(), List.of());
    }
}
