package com.novelrealm.dto;

import java.time.Instant;
import java.util.List;

/**
 * Un message accroché à un passage. L'auteur est réduit à ce qui sert à le montrer
 * (pseudo + avatar) — jamais son email.
 *
 * <p>{@code mine} est calculé côté serveur à partir du jeton : le client n'a donc pas
 * besoin de connaître son propre identifiant pour savoir quoi rendre supprimable.
 *
 * <p>{@code replies} n'est rempli que sur les messages racines — l'arbre ne descend
 * jamais plus bas d'un niveau, et une réponse porte donc toujours une liste vide.
 *
 * <p>{@code mentions} (issue #45, §2) : qui est visé par les {@code @pseudo} du
 * texte. {@code gifUrl}/{@code gifPreviewUrl} (issue #45, §5) : le GIF joint ;
 * {@code body} peut alors être vide.
 */
public record PassageCommentResponse(
        Long id,
        Long userId,
        String pseudo,
        String avatarUrl,
        String body,
        boolean spoiler,
        boolean mine,
        Instant createdAt,
        List<MentionResponse> mentions,
        String gifUrl,
        String gifPreviewUrl,
        List<PassageCommentResponse> replies
) {}
