package com.novelrealm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.novelrealm.model.CommentMention.SourceKind;
import com.novelrealm.model.CommentReaction;

/**
 * Accès aux réactions emoji des commentaires. Calqué sur
 * {@link CommentMentionRepository} : mêmes clés polymorphes, même chargement par lot.
 */
public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    /**
     * Toutes les réactions d'un lot de commentaires — UNE requête pour toute une
     * page de fils, jamais une par message. {@code @EntityGraph} embarque l'auteur
     * de la réaction : le service a besoin de son identifiant pour dire au client
     * lesquelles sont les siennes, sans une requête de plus par ligne.
     */
    @EntityGraph(attributePaths = "user")
    List<CommentReaction> findBySourceKindAndSourceIdIn(
            SourceKind sourceKind, Collection<Long> sourceIds);

    /**
     * MA réaction avec CET emoji sur CE commentaire, s'il y en a une — la présence
     * décide du sens du geste : elle existe → on la retire, sinon → on la pose.
     */
    Optional<CommentReaction> findBySourceKindAndSourceIdAndUser_IdAndEmoji(
            SourceKind sourceKind, Long sourceId, Long userId, String emoji);

    /**
     * Efface les réactions de commentaires supprimés. Nécessaire uniquement pour les
     * commentaires de PASSAGE, dont la ligne disparaît vraiment — ceux de fin de
     * chapitre sont en suppression douce (leur corps n'est plus affiché, leurs
     * réactions ne se voient donc plus).
     */
    void deleteBySourceKindAndSourceIdIn(SourceKind sourceKind, Collection<Long> sourceIds);
}
