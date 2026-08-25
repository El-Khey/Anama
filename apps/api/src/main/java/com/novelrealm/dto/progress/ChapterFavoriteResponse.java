package com.novelrealm.dto.progress;

import java.time.Instant;

/**
 * Chapitre favori tel que renvoyé au front. On imbrique de quoi afficher un
 * marque-page sans recharger le chapitre (roman, numéro, titre).
 */
public record ChapterFavoriteResponse(
        Long chapterId,
        Long novelId,
        Integer chapterNumber,
        String title,
        Instant favoritedAt
) {}
