package com.novelrealm.service.comment;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.comment.CommentVotesResponse;
import com.novelrealm.exception.comment.ChapterCommentNotFoundException;
import com.novelrealm.exception.comment.InvalidCommentException;
import com.novelrealm.exception.passage.PassageAnnotationNotFoundException;
import com.novelrealm.model.ChapterComment;
import com.novelrealm.model.CommentMention.SourceKind;
import com.novelrealm.model.CommentVote;
import com.novelrealm.model.PassageAnnotation;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterCommentRepository;
import com.novelrealm.repository.CommentVoteRepository;
import com.novelrealm.repository.PassageAnnotationRepository;
import com.novelrealm.service.user.UserService;

/**
 * Votes (pouce vert / pouce rouge) sur les commentaires, pour les deux emplacements —
 * fin de chapitre et passage. Calqué sur {@link CommentReactionService} : polymorphe,
 * chargé par lot, purgé par les services qui suppriment vraiment un commentaire.
 *
 * <p><b>Un geste, un appel.</b> Voter dans le sens qu'on a déjà retire son vote (retour
 * au neutre) ; voter dans l'autre sens le bascule ; voter pour la première fois le pose.
 * Un seul vote par lecteur et par message est possible (unicité en base), ce qui rend le
 * geste idempotent.
 */
@Service
public class CommentVoteService {

    private final CommentVoteRepository voteRepository;
    private final ChapterCommentRepository chapterCommentRepository;
    private final PassageAnnotationRepository annotationRepository;
    private final UserService userService;

    public CommentVoteService(
            CommentVoteRepository voteRepository,
            ChapterCommentRepository chapterCommentRepository,
            PassageAnnotationRepository annotationRepository,
            UserService userService) {
        this.voteRepository = voteRepository;
        this.chapterCommentRepository = chapterCommentRepository;
        this.annotationRepository = annotationRepository;
        this.userService = userService;
    }

    /**
     * Vote (ou dévote) sur un commentaire de fin de chapitre, et renvoie l'état à jour
     * de ses compteurs.
     */
    @Transactional
    public CommentVotesResponse voteOnChapterComment(String email, Long commentId, int value) {
        ChapterComment comment = chapterCommentRepository.findById(commentId)
                .filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ChapterCommentNotFoundException(commentId));
        return vote(email, SourceKind.CHAPTER_COMMENT, comment.getId(), value);
    }

    /**
     * Vote (ou dévote) sur un commentaire accroché à un passage, et renvoie l'état à
     * jour. Refuse une réaction, une citation ou un surlignage : on ne vote que sur un
     * vrai message.
     */
    @Transactional
    public CommentVotesResponse voteOnPassageComment(String email, Long annotationId, int value) {
        PassageAnnotation comment = annotationRepository.findById(annotationId)
                .filter(a -> a.getKind() == PassageAnnotation.Kind.COMMENT)
                .orElseThrow(() -> new PassageAnnotationNotFoundException(annotationId));
        return vote(email, SourceKind.PASSAGE_COMMENT, comment.getId(), value);
    }

    private CommentVotesResponse vote(String email, SourceKind kind, Long sourceId, int rawValue) {
        short value = sanitizeValue(rawValue);
        User user = userService.findByEmail(email);

        voteRepository
                .findBySourceKindAndSourceIdAndUser_Id(kind, sourceId, user.getId())
                .ifPresentOrElse(
                        existing -> {
                            if (existing.getValue() == value) {
                                // Revoter le même sens : on retire (retour au neutre).
                                voteRepository.delete(existing);
                            } else {
                                // Changer d'avis : la même ligne bascule.
                                existing.setValue(value);
                            }
                        },
                        () -> voteRepository.save(new CommentVote(kind, sourceId, user, value)));
        // Vidé vers la base avant de recompter : sinon l'agrégat renverrait l'état
        // d'avant le geste et le compteur reviendrait en arrière sous le doigt.
        voteRepository.flush();

        Map<Long, Summary> summaries = summarize(kind, List.of(sourceId), user.getId());
        Summary summary = summaries.getOrDefault(sourceId, Summary.EMPTY);
        return new CommentVotesResponse(sourceId, summary.likes(), summary.dislikes(), summary.myVote());
    }

    /**
     * Les votes d'un lot de commentaires, groupés par commentaire — une seule requête
     * pour toute une page de fils. {@code currentUserId} peut être null (lecture
     * anonyme) : {@code myVote} vaut alors toujours 0. Clé absente = aucun vote sur ce
     * commentaire (0 like, 0 dislike, 0 pour moi).
     */
    @Transactional(readOnly = true)
    public Map<Long, Summary> summarize(
            SourceKind kind, Collection<Long> sourceIds, Long currentUserId) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }

        List<CommentVote> rows = voteRepository.findBySourceKindAndSourceIdIn(kind, sourceIds);

        Map<Long, long[]> countsByComment = new LinkedHashMap<>(); // [likes, dislikes]
        Map<Long, Integer> mineByComment = new LinkedHashMap<>();
        for (CommentVote row : rows) {
            long[] counts = countsByComment.computeIfAbsent(row.getSourceId(), k -> new long[2]);
            if (row.getValue() > 0) {
                counts[0]++;
            } else {
                counts[1]++;
            }
            if (currentUserId != null && currentUserId.equals(row.getUser().getId())) {
                mineByComment.put(row.getSourceId(), (int) row.getValue());
            }
        }

        Map<Long, Summary> result = new LinkedHashMap<>();
        for (Map.Entry<Long, long[]> entry : countsByComment.entrySet()) {
            long[] counts = entry.getValue();
            result.put(entry.getKey(), new Summary(
                    counts[0], counts[1], mineByComment.getOrDefault(entry.getKey(), 0)));
        }
        return result;
    }

    /** Purge les votes de commentaires dont la ligne va vraiment disparaître. */
    @Transactional
    public void deleteFor(SourceKind kind, Collection<Long> sourceIds) {
        if (!sourceIds.isEmpty()) {
            voteRepository.deleteBySourceKindAndSourceIdIn(kind, sourceIds);
        }
    }

    /** Accepte {@code +1} ou {@code -1} ; refuse tout autre valeur (dont 0). */
    private static short sanitizeValue(int raw) {
        if (raw == CommentVote.UP) {
            return CommentVote.UP;
        }
        if (raw == CommentVote.DOWN) {
            return CommentVote.DOWN;
        }
        throw new InvalidCommentException("Un vote vaut +1 ou -1");
    }

    /**
     * Résumé des votes d'un commentaire : le nombre de pouces verts, de pouces rouges,
     * et le sens du vote du lecteur courant ({@code +1}, {@code -1}, ou {@code 0}).
     */
    public record Summary(long likes, long dislikes, int myVote) {
        public static final Summary EMPTY = new Summary(0L, 0L, 0);
    }
}
