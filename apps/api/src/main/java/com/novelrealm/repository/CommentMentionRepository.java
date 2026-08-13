package com.novelrealm.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.novelrealm.model.CommentMention;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

    /**
     * Toutes les mentions d'un lot de commentaires — UNE requête pour toute une
     * page de fils, jamais une par message. {@code @EntityGraph} embarque
     * l'utilisateur mentionné : le DTO a besoin de son identifiant et de son
     * pseudo actuel, pas d'une requête de plus par mention.
     */
    @EntityGraph(attributePaths = "mentioned")
    List<CommentMention> findBySourceKindAndSourceIdIn(
            CommentMention.SourceKind sourceKind, Collection<Long> sourceIds);

    /**
     * Efface les mentions de commentaires supprimés. Nécessaire uniquement pour
     * les commentaires de PASSAGE, dont la ligne disparaît vraiment — ceux de fin
     * de chapitre sont en suppression douce et gardent leurs mentions (le corps
     * n'étant plus affiché, elles ne se voient plus).
     */
    void deleteBySourceKindAndSourceIdIn(
            CommentMention.SourceKind sourceKind, Collection<Long> sourceIds);
}
