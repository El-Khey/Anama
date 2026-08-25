package com.novelrealm.dto.progress;

import java.time.Instant;

/**
 * Une entrée de l'historique de lecture : un chapitre que l'utilisateur a
 * ouvert (lu ou simplement entamé), avec de quoi l'afficher et « reprendre »
 * directement — infos du roman, n°/titre du chapitre, position (%) et date.
 */
public record HistoryEntryResponse(
        Long chapterId,
        Integer chapterNumber,
        String chapterTitle,
        Long novelId,
        String novelTitle,
        String novelCoverImageUrl,
        boolean read,
        int scrollPosition,
        Instant readAt
) {}
