package com.novelrealm.service.comment;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.novelrealm.exception.comment.InvalidMentionException;
import com.novelrealm.model.CommentMention;
import com.novelrealm.model.CommentMention.SourceKind;
import com.novelrealm.model.User;
import com.novelrealm.repository.CommentMentionRepository;
import com.novelrealm.repository.UserRepository;

/**
 * Mentions {@code @pseudo} dans les commentaires (issue #45, §2).
 *
 * <p><b>C'est le client qui résout, c'est le serveur qui vérifie.</b>
 * L'autocomplétion (via {@code GET /api/users/search}) donne au client des
 * identifiants ; il les envoie avec le message. Le serveur ne fait pas confiance
 * pour autant : il replafonne le nombre, écarte l'auto-mention, ignore les
 * identifiants inconnus, et surtout exige que « @pseudo » figure réellement dans
 * le texte — sans quoi un client modifié pourrait notifier des gens à leur insu,
 * sans rien d'apparent dans le message.
 */
@Service
public class MentionService {

    /**
     * Plafond de mentions par message. Au-delà, ce n'est plus une conversation,
     * c'est une liste de diffusion — et chaque mention déclenche une
     * notification : le plafond est d'abord un anti-spam.
     */
    public static final int MAX_MENTIONS = 5;

    private final CommentMentionRepository mentionRepository;
    private final UserRepository userRepository;

    public MentionService(
            CommentMentionRepository mentionRepository,
            UserRepository userRepository) {
        this.mentionRepository = mentionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Vérifie et enregistre les mentions d'un message fraîchement créé, et renvoie
     * les lignes <b>réellement</b> retenues — c'est cette liste, pas celle du
     * client, qui déclenche les notifications ({@code getMentioned()} donne
     * chaque destinataire).
     *
     * <p>Toujours appelé dans la transaction du message : un message sans ses
     * mentions ou des mentions sans leur message, ça n'existe pas.
     *
     * @throws InvalidMentionException si le client dépasse {@link #MAX_MENTIONS}.
     */
    @Transactional
    public List<CommentMention> attach(
            SourceKind kind, Long sourceId, User author, List<Long> mentionedUserIds, String body) {
        if (mentionedUserIds == null || mentionedUserIds.isEmpty()) {
            return List.of();
        }

        List<Long> candidates = mentionedUserIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> !id.equals(author.getId()))
                .toList();
        if (candidates.size() > MAX_MENTIONS) {
            throw new InvalidMentionException(
                    "Un message ne peut pas mentionner plus de " + MAX_MENTIONS + " personnes");
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        // Un id inconnu est ignoré sans erreur (compte supprimé entre
        // l'autocomplétion et l'envoi) ; une mention absente du texte est écartée
        // (rien à mettre en évidence → rien à notifier).
        List<User> retained = userRepository.findAllById(candidates).stream()
                .filter(user -> body != null && body.contains("@" + user.getPseudo()))
                .toList();

        return mentionRepository.saveAll(retained.stream()
                .map(user -> new CommentMention(kind, sourceId, user, user.getPseudo()))
                .toList());
    }

    /**
     * Les mentions d'un lot de commentaires, groupées par commentaire — une seule
     * requête pour toute une page de fils. Clé absente = aucune mention.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<CommentMention>> forSources(SourceKind kind, Collection<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Map.of();
        }
        return mentionRepository.findBySourceKindAndSourceIdIn(kind, sourceIds).stream()
                .collect(Collectors.groupingBy(CommentMention::getSourceId));
    }

    /** Purge les mentions de commentaires dont la ligne va vraiment disparaître. */
    @Transactional
    public void deleteFor(SourceKind kind, Collection<Long> sourceIds) {
        if (!sourceIds.isEmpty()) {
            mentionRepository.deleteBySourceKindAndSourceIdIn(kind, sourceIds);
        }
    }
}
