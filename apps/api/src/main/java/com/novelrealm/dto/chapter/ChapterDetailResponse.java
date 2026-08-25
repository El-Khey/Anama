package com.novelrealm.dto.chapter;

import java.time.Instant;

// Version "détail" : avec le contenu, pour la lecture d'un chapitre.
public record ChapterDetailResponse(Long id, Long novelId, Integer chapterNumber, String title, String content,
        Instant createdAt) {
}
