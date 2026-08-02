package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `HistoryEntryResponse` (back) : une entrée « reprendre la lecture ».
@Serializable
data class HistoryEntryDto(
    val chapterId: Long,
    val chapterNumber: Int,
    val chapterTitle: String? = null,
    val novelId: Long,
    val novelTitle: String = "",
    val novelCoverImageUrl: String? = null,
    val read: Boolean = false,
    val scrollPosition: Int = 0,
    val readAt: String? = null,
)
