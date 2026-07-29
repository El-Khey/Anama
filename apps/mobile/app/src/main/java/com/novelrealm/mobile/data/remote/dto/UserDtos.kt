package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

// Miroir de `UserStatsResponse` (back) : stats de lecture pour le profil.
@Serializable
data class UserStatsDto(
    val chaptersRead: Long = 0,
    val novelsFollowed: Long = 0,
    val novelsCompleted: Long = 0,
    val chaptersFavorited: Long = 0,
    val readingDays: Long = 0,
    val currentStreak: Long = 0,
    val longestStreak: Long = 0,
)

// Corps de `PATCH /api/users/me`. Champ null = inchangé côté back ; bio "" = effacée.
// IMPORTANT : ne PAS ajouter `preferences` ici — un null explicite les EFFACERAIT côté back.
@Serializable
data class UpdateProfileRequestDto(
    val pseudo: String? = null,
    val bio: String? = null,
)
