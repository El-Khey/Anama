package com.novelrealm.dto.user;

/**
 * Statistiques de lecture résumées, affichées sur la page profil (issue #17 —
 * la page de statistiques détaillée viendra avec l'issue #23).
 *
 * <p>Les « jours de lecture » et les séries s'appuient sur les dates d'activité
 * de {@code chapter_progress} (approximation : une ligne par chapitre, datée de
 * la dernière activité).
 */
public record UserStatsResponse(
        long chaptersRead,
        long novelsFollowed,
        long novelsCompleted,
        long chaptersFavorited,
        long readingDays,
        long currentStreak,
        long longestStreak
) {}
