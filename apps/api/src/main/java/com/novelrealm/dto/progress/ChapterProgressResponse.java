package com.novelrealm.dto.progress;

import java.time.Instant;

/** Progression de lecture d'un chapitre telle que renvoyée au front. */
public record ChapterProgressResponse(
        Long chapterId,
        boolean read,
        int scrollPosition,
        Instant readAt
) {}
