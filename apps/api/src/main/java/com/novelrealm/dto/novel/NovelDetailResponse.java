package com.novelrealm.dto.novel;

import java.time.Instant;
import java.util.List;

import com.novelrealm.model.Novel.NovelStatus;

/**
 * Détail d'un roman (page « fiche »). Superset de {@link NovelResponse} : ajoute
 * les genres et la note moyenne, exposés uniquement sur {@code GET /api/novels/{id}}.
 *
 * <p>{@code averageRating} vaut 0 tant qu'aucun avis n'a été déposé —
 * {@code ratingCount} permet de distinguer « pas encore noté » de « mal noté ».
 */
public record NovelDetailResponse(
        Long id,
        String title,
        String author,
        String description,
        String coverImageUrl,
        NovelStatus status,
        Instant createdAt,
        List<GenreResponse> genres,
        double averageRating,
        long ratingCount
) {}
