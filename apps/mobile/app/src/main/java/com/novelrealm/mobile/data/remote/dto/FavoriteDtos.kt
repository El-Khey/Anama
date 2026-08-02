package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `ChapterFavoriteResponse` (back) : un chapitre mis en signet.
@Serializable
data class ChapterFavoriteDto(
    val chapterId: Long,
    val novelId: Long,
    val chapterNumber: Int = 0,
    val title: String? = null,
    val favoritedAt: String? = null,
)
