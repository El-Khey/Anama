package com.novelrealm.dto.comment;

import java.util.List;
import jakarta.validation.constraints.Size;

/**
 * Publication d'un message en fin de chapitre. L'auteur est toujours
 * l'utilisateur connecté — il n'est jamais transmis par le client.
 *
 * <p>{@code parentId} répond à un message existant. Si l'on répond à une réponse,
 * le serveur re-rattache le message à la racine du fil : un seul niveau
 * d'indentation, sinon c'est illisible sur un téléphone.
 *
 * <p><b>{@code body} n'est plus {@code @NotBlank}</b> depuis l'issue #45, §5 : un
 * message peut être un GIF seul. La règle « du texte OU un GIF, jamais ni l'un ni
 * l'autre » est inter-champs, donc tenue par le service — Bean Validation ne
 * valide qu'un champ à la fois.
 *
 * <p>{@code mentionedUserIds} (issue #45, §2) : les utilisateurs résolus par
 * l'autocomplétion. Le serveur re-vérifie tout — plafond, auto-mention, présence
 * réelle du {@code @pseudo} dans le texte — voir {@code MentionService}.
 */
public record CreateChapterCommentRequest(
        @Size(max = 2000, message = "Le message ne peut pas dépasser 2000 caractères")
        String body,

        Long parentId,

        List<Long> mentionedUserIds,

        /** URL du fournisseur pour la version animée (voir GifService pour la validation). */
        @Size(max = 500, message = "URL de GIF trop longue")
        String gifUrl,

        /** URL du fournisseur pour l'image figée affichée par défaut. */
        @Size(max = 500, message = "URL de GIF trop longue")
        String gifPreviewUrl
) {}
