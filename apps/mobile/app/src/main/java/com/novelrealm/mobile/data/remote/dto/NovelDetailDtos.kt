package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `GenreResponse` (back).
@Serializable
data class GenreDto(
    val id: Long,
    val name: String,
)

// Miroir de `NovelDetailResponse` (back) : le roman + ses genres + le résumé des notes.
@Serializable
data class NovelDetailDto(
    val id: Long,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val status: String? = null,          // ONGOING | COMPLETED
    val createdAt: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val averageRating: Double = 0.0,
    val ratingCount: Long = 0,
)
