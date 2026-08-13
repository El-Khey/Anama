package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Le profil PUBLIC d'un autre utilisateur (`GET /api/users/{id}`), tel qu'on
 * l'ouvre depuis une mention ou un pseudo de commentaire (issue #45, §2).
 *
 * Distinct de [UserDto] à dessein : la réponse publique du back n'a NI email NI
 * préférences — `UserDto.email` étant non-nullable, le réutiliser ici planterait
 * la désérialisation.
 */
@Serializable
data class PublicUserDto(
    val id: Long,
    val pseudo: String = "",
    val bio: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val createdAt: String? = null,
)

/** Miroir de `UserSearchResponse` (back) : une ligne d'autocomplétion de mention. */
@Serializable
data class UserSearchDto(
    val id: Long,
    val pseudo: String = "",
    val avatarUrl: String? = null,
)
