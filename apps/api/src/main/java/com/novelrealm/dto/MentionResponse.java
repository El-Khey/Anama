package com.novelrealm.dto;

/**
 * Une mention portée par un commentaire (issue #45, §2).
 *
 * <p>{@code handle} est le pseudo <b>tel qu'il figurait dans le texte à la
 * publication</b> : c'est lui que le client cherche dans le corps du message
 * pour le mettre en évidence. {@code pseudo} est le pseudo <b>actuel</b> : c'est
 * lui qu'on affiche si on ouvre le profil. Ils divergent après un renommage — et
 * c'est exactement pour ça que les deux existent.
 */
public record MentionResponse(
        Long userId,
        String handle,
        String pseudo
) {}
