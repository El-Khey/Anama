package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `ChapterProgressResponse` (back). `scrollPosition` = % de reprise (0-100).
@Serializable
data class ChapterProgressDto(
    val chapterId: Long,
    val read: Boolean = false,
    val scrollPosition: Int = 0,
    val readAt: String? = null,
)

// Miroir de `NovelProgressSummary` (back) : totaux lus/total par roman (badges non-lus).
@Serializable
data class NovelProgressSummaryDto(
    val novelId: Long,
    val totalChapters: Long = 0,
    val readChapters: Long = 0,
)

// Corps de `PUT /api/progress/chapters/{id}`.
@Serializable
data class MarkChapterReadRequestDto(
    val read: Boolean,
)

// Corps de `PUT /api/progress/chapters/batch` (tout marquer lu).
@Serializable
data class BatchMarkChaptersReadRequestDto(
    val chapterIds: List<Long>,
    val read: Boolean,
)

// Corps de `PUT /api/progress/chapters/{id}/position` (position de reprise en %).
@Serializable
data class SaveChapterPositionRequestDto(
    val percent: Int,
)
