package com.novelrealm.dto.comment;

import jakarta.validation.constraints.NotBlank;

/**
 * Pose ou retire une réaction emoji sur un commentaire (façon Discord).
 *
 * <p>Un seul endpoint, un seul geste côté lecteur : toucher un emoji. Toucher un emoji
 * qu'on a déjà posé le retire, en toucher un nouveau l'ajoute — un lecteur peut en
 * cumuler plusieurs sur le même message, contrairement aux réactions de passage.
 */
public record ReactToCommentRequest(
        @NotBlank(message = "L'emoji est obligatoire")
        String emoji
) {}
