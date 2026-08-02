package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
// IMPORTANT : ne PAS ajouter `preferences` ici — un null explicite les EFFACERAIT côté
// back (il distingue « absent » de « null »). La mise à jour des préférences passe par
// [UpdatePreferencesRequestDto], qui envoie toujours l'objet complet.
@Serializable
data class UpdateProfileRequestDto(
    val pseudo: String? = null,
    val bio: String? = null,
)

// Corps de `PATCH /api/users/me` dédié aux préférences. Le back REMPLACE le champ en
// bloc → l'objet envoyé doit être complet (fusion faite par le PreferencesStore).
@Serializable
data class UpdatePreferencesRequestDto(
    val preferences: JsonObject,
)

// Corps de `PUT /api/users/me/password` → 204 si OK.
// Erreurs : 401 mot de passe actuel faux, 409 compte Google, 400 validation (min 8).
@Serializable
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String,
)
