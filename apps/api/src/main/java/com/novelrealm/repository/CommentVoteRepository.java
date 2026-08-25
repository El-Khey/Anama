package com.novelrealm.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.novelrealm.model.CommentMention.SourceKind;
import com.novelrealm.model.CommentVote;

/**
 * Accès aux votes (pouce vert / pouce rouge) des commentaires. Calqué sur
 * {@link CommentReactionRepository} : mêmes clés polymorphes, même chargement par lot.
 */
public interface CommentVoteRepository extends JpaRepository<CommentVote, Long> {

    /**
     * Tous les votes d'un lot de commentaires — UNE requête pour toute une page de
     * fils, jamais un par message. {@code @EntityGraph} embarque l'auteur du vote : le
     * service a besoin de son identifiant pour dire au client quel est SON vote, sans
     * une requête de plus par ligne.
     */
    @EntityGraph(attributePaths = "user")
    List<CommentVote> findBySourceKindAndSourceIdIn(
            SourceKind sourceKind, Collection<Long> sourceIds);

    /**
     * MON vote sur CE commentaire, s'il y en a un — sa présence et son sens décident du
     * geste : revoter le même sens le retire, voter l'autre sens le bascule.
     */
    Optional<CommentVote> findBySourceKindAndSourceIdAndUser_Id(
            SourceKind sourceKind, Long sourceId, Long userId);

    /**
     * Efface les votes de commentaires supprimés — pour les commentaires de PASSAGE
     * dont la ligne disparaît vraiment, ET désormais pour ceux de fin de chapitre, qui
     * sont eux aussi supprimés en dur (avec leurs réponses).
     */
    void deleteBySourceKindAndSourceIdIn(SourceKind sourceKind, Collection<Long> sourceIds);
}
