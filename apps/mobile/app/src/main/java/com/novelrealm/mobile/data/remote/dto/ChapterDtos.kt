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

// Le titre est facultatif côté back (et parfois vide). Le repli est centralisé ici plutôt
// que réécrit sur chaque écran, où il finissait par diverger d'un endroit à l'autre.
val ChapterDto.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() } ?: "Chapitre $chapterNumber"

val ChapterDetailDto.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() } ?: "Chapitre $chapterNumber"
