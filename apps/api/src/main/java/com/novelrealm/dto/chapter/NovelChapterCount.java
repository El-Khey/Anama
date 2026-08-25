package com.novelrealm.dto.chapter;

/**
 * Projection interne d'un comptage de chapitres par roman (résultat de requêtes
 * {@code GROUP BY novel_id}). Sert à la fois au total de chapitres et au nombre
 * de chapitres lus.
 */
public record NovelChapterCount(Long novelId, long count) {}
