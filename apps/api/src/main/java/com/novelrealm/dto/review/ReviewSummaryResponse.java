package com.novelrealm.dto.review;

import java.util.Map;

/**
 * Synthèse des avis d'un roman : moyenne, nombre total et répartition par note.
 *
 * <p>{@code distribution} contient TOUJOURS les cinq clés (1 à 5), à zéro le
 * cas échéant : le client peut dessiner l'histogramme sans compléter les trous.
 */
public record ReviewSummaryResponse(
        double average,
        long count,
        Map<Integer, Long> distribution
) {}
