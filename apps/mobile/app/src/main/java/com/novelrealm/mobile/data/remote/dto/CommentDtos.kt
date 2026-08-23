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
    /** Qui est visé par les `@pseudo` du texte (issue #45, §2). */
    val mentions: List<MentionDto> = emptyList(),
    /** GIF joint (issue #45, §5) : `body` peut alors être vide. */
    val gifUrl: String? = null,
    val gifPreviewUrl: String? = null,
    /** Réactions emoji posées sur ce message (les plus posées d'abord). */
    val reactions: List<EmojiTallyDto> = emptyList(),
    /** Les emojis que MOI j'ai posés, pour surligner mes puces. */
    val myReactions: List<String> = emptyList(),
    /** Pouces verts / rouges posés sur ce message. */
    val likes: Long = 0,
    val dislikes: Long = 0,
    /** Le sens de MON vote : +1 pour, -1 contre, 0 si je n'ai pas voté. */
    val myVote: Int = 0,
    val replies: List<ChapterCommentDto> = emptyList(),
)

/**
 * Miroir de `MentionResponse` (back). `handle` = le pseudo tel qu'il figurait dans
 * le texte à la publication — c'est LUI qu'on cherche dans le corps pour la mise
 * en évidence ; `pseudo` = le pseudo actuel, pour l'affichage du profil.
 */
@Serializable
data class MentionDto(
    val userId: Long,
    val handle: String = "",
    val pseudo: String? = null,
)

// Corps de `POST /api/chapters/{id}/comments`. `parentId` répond à un message :
// le serveur re-rattache toujours la réponse à la racine du fil.
// `mentionedUserIds` : résolus par l'autocomplétion — le serveur re-vérifie tout
// (plafond de 5, auto-mention, présence réelle du @pseudo dans le texte).
@Serializable
data class CreateCommentRequestDto(
    val body: String,
    val parentId: Long? = null,
    val mentionedUserIds: List<Long> = emptyList(),
    val gifUrl: String? = null,
    val gifPreviewUrl: String? = null,
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

// Corps de `PUT /api/comments/{id}/reactions` et de la variante passage. Un seul
// appel pour poser OU retirer : le serveur déduit le geste de l'état courant. On
// peut cumuler plusieurs emojis sur le même message (façon Discord).
@Serializable
data class ReactToCommentRequestDto(
    val emoji: String,
)

// Miroir de `CommentReactionsResponse` (back) : l'état des réactions d'un message
// après un geste, de quoi rafraîchir ses puces sans recharger tout le fil.
@Serializable
data class CommentReactionsDto(
    val commentId: Long,
    val reactions: List<EmojiTallyDto> = emptyList(),
    val myReactions: List<String> = emptyList(),
)

// Corps de `PUT /api/comments/{id}/vote` et de la variante passage. `value` vaut +1
// (pouce vert) ou -1 (pouce rouge). Le serveur déduit le geste de l'état courant :
// revoter le même sens retire, voter l'autre bascule — le neutre est un résultat, pas
// une valeur envoyée.
@Serializable
data class VoteRequestDto(
    val value: Int,
)

// Miroir de `CommentVotesResponse` (back) : l'état des votes d'un message après un
// geste, de quoi rafraîchir ses deux pouces sans recharger tout le fil.
@Serializable
data class CommentVotesDto(
    val commentId: Long,
    val likes: Long = 0,
    val dislikes: Long = 0,
    val myVote: Int = 0,
)
