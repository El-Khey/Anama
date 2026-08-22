package com.novelrealm.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.dto.CommentReactionsResponse;
import com.novelrealm.dto.EmojiTallyResponse;
import com.novelrealm.exception.ChapterCommentNotFoundException;
import com.novelrealm.exception.InvalidCommentException;
import com.novelrealm.exception.PassageAnnotationNotFoundException;
import com.novelrealm.model.ChapterComment;
import com.novelrealm.model.CommentMention.SourceKind;
import com.novelrealm.model.CommentReaction;
import com.novelrealm.model.PassageAnnotation;
import com.novelrealm.model.User;
import com.novelrealm.repository.ChapterCommentRepository;
import com.novelrealm.repository.CommentReactionRepository;
import com.novelrealm.repository.PassageAnnotationRepository;

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

    /**
     * Longueur maximale d'un emoji, en points de code. Un emoji composé (drapeau,
     * famille, teinte de peau) tient largement sous cette borne ; au-delà, ce n'est
     * plus un emoji mais du texte, et on refuse.
     */
    private static final int MAX_EMOJI_CODEPOINTS = 8;

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
     * Accepte un emoji, refuse une chaîne arbitraire. Contrairement aux réactions de
     * passage (jeu fermé de six), le bouton « + » ouvre le clavier complet : on ne
     * peut pas comparer à une liste. On borne donc la longueur et on exige que ce
     * soit fait d'emojis — un client modifié ne doit pas pouvoir stocker du texte
     * ici, qui s'afficherait comme une fausse réaction pour tout le monde.
     */
    private static String sanitizeEmoji(String raw) {
        String emoji = raw == null ? "" : raw.strip();
        int[] codePoints = emoji.codePoints().toArray();
        if (codePoints.length == 0 || codePoints.length > MAX_EMOJI_CODEPOINTS) {
            throw new InvalidCommentException("Ce n'est pas un emoji valide");
        }
        boolean anyEmoji = false;
        for (int cp : codePoints) {
            if (isEmojiScalar(cp)) {
                anyEmoji = true;
            } else if (!isEmojiModifierOrJoiner(cp)) {
                // Une lettre, un chiffre, une ponctuation ordinaire : refusé.
                throw new InvalidCommentException("Ce n'est pas un emoji valide");
            }
        }
        if (!anyEmoji) {
            throw new InvalidCommentException("Ce n'est pas un emoji valide");
        }
        return emoji;
    }

    /** Un point de code qui, à lui seul, dessine un emoji (pictogrammes, symboles). */
    private static boolean isEmojiScalar(int cp) {
        int type = Character.getType(cp);
        if (type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL) {
            return true;
        }
        // Emoji « clavier » construits sur des caractères ASCII (chiffres, #, *)
        // suivis du sélecteur de variante — le chiffre seul est accepté ici parce
        // qu'il ne passe QUE s'il est accompagné d'un point de code emoji réel dans
        // la même séquence (anyEmoji), un « 3 » nu échouant à la fin.
        return (cp >= 0x1F000 && cp <= 0x1FAFF)   // blocs Supplemental Symbols/Pictographs
                || (cp >= 0x2600 && cp <= 0x27BF)  // Misc Symbols + Dingbats
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF); // indicateurs régionaux (drapeaux)
    }

    /** Modificateurs et liants d'une séquence emoji : teinte, jointure ZWJ, variante. */
    private static boolean isEmojiModifierOrJoiner(int cp) {
        return cp == 0x200D                       // Zero Width Joiner
                || cp == 0xFE0F || cp == 0xFE0E    // sélecteurs de variante
                || (cp >= 0x1F3FB && cp <= 0x1F3FF) // teintes de peau
                || (cp >= 0x20E0 && cp <= 0x20FF)  // combinants (keycap enclosant)
                || (cp >= 0xE0020 && cp <= 0xE007F); // tags (drapeaux régionaux)
    }

    /**
     * Résumé des réactions d'un commentaire : le décompte par emoji (trié) et les
     * emojis que le lecteur courant a lui-même posés.
     */
    public record Summary(List<EmojiTallyResponse> tallies, List<String> mine) {
        public static final Summary EMPTY = new Summary(List.of(), List.of());
    }
}
