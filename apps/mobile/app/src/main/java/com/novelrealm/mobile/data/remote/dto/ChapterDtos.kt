package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `ChapterResponse` (back) : métadonnées d'un chapitre, sans le contenu.
@Serializable
data class ChapterDto(
    val id: Long,
    val novelId: Long,
    val chapterNumber: Int,
    val title: String? = null,
)

// Miroir de `ChapterDetailResponse` (back) : le chapitre AVEC son contenu (page de lecture).
@Serializable
data class ChapterDetailDto(
    val id: Long,
    val novelId: Long,
    val chapterNumber: Int,
    val title: String? = null,
    val content: String = "",
    val createdAt: String? = null,
)
