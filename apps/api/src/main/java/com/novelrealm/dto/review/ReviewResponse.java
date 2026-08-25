package com.novelrealm.dto.review;

import java.time.Instant;

/**
 * Un avis tel qu'il est affiché sur la fiche d'un roman. L'auteur est réduit à
 * ce qui sert à l'afficher (pseudo + avatar) — jamais son email.
 */
public record ReviewResponse(
        Long id,
        Long userId,
        String pseudo,
        String avatarUrl,
        int rating,
        String body,
        Instant createdAt,
        Instant updatedAt
) {}
