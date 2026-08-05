package com.novelrealm.dto;

import java.time.Instant;

/**
 * Un message accroché à un passage. L'auteur est réduit à ce qui sert à le montrer
 * (pseudo + avatar) — jamais son email.
 *
 * <p>{@code mine} est calculé côté serveur à partir du jeton : le client n'a donc pas
 * besoin de connaître son propre identifiant pour savoir quoi rendre supprimable.
 */
public record PassageCommentResponse(
        Long id,
        Long userId,
        String pseudo,
        String avatarUrl,
        String body,
        boolean spoiler,
        boolean mine,
        Instant createdAt
) {}
