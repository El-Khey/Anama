package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Miroir de `ChapterCommentResponse` (back) : un message de fin de chapitre.
 *
 * `mine` est calculé par le serveur à partir du jeton — l'app n'a donc pas besoin
 * de connaître son propre identifiant pour savoir quel message est modifiable.
 *
 * Un message supprimé qui porte encore des réponses arrive en « pierre tombale » :
 * `deleted = true`, corps et auteur nuls, mais `replies` toujours lisible.
 */
@Serializable
data class ChapterCommentDto(
    val id: Long,
    val userId: Long? = null,
    val pseudo: String? = null,
    val avatarUrl: String? = null,
    val body: String? = null,
    val deleted: Boolean = false,
    val mine: Boolean = false,
    val edited: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val replies: List<ChapterCommentDto> = emptyList(),
)

// Corps de `POST /api/chapters/{id}/comments`. `parentId` répond à un message :
// le serveur re-rattache toujours la réponse à la racine du fil.
@Serializable
data class CreateCommentRequestDto(
    val body: String,
    val parentId: Long? = null,
)

// Corps de `PATCH /api/comments/{id}` (modification de son propre message).
@Serializable
data class UpdateCommentRequestDto(
    val body: String,
)

// Miroir de `CommentCountResponse` (back) : le nombre seul, sans charger la liste.
@Serializable
data class CommentCountDto(
    val count: Long = 0,
)
