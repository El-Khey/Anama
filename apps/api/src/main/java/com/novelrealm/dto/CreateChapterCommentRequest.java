package com.novelrealm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Publication d'un message en fin de chapitre. L'auteur est toujours
 * l'utilisateur connecté — il n'est jamais transmis par le client.
 *
 * <p>{@code parentId} répond à un message existant. Si l'on répond à une réponse,
 * le serveur re-rattache le message à la racine du fil : un seul niveau
 * d'indentation, sinon c'est illisible sur un téléphone.
 */
public record CreateChapterCommentRequest(
        @NotBlank(message = "Le message ne peut pas être vide")
        @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères")
        String body,

        Long parentId
) {}
