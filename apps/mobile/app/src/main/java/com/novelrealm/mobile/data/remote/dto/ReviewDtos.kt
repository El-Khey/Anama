package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `ReviewResponse` (back) : un avis (auteur = pseudo + avatar, jamais l'email).
@Serializable
data class ReviewDto(
    val id: Long,
    val userId: Long,
    val pseudo: String = "",
    val avatarUrl: String? = null,
    val rating: Int = 0,
    val body: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// Miroir de `ReviewSummaryResponse` (back). `distribution` : clés "1".."5" toujours présentes.
@Serializable
data class ReviewSummaryDto(
    val average: Double = 0.0,
    val count: Long = 0,
    val distribution: Map<String, Long> = emptyMap(),
)

// Corps de `PUT /api/novels/{novelId}/reviews` (upsert de son propre avis).
@Serializable
data class UpsertReviewRequestDto(
    val rating: Int,
    val body: String? = null,
)
