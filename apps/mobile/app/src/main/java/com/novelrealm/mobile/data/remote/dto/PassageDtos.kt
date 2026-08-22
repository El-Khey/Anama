package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Réactions et commentaires accrochés à un passage (#41, §4).
 *
 * <p>Les `blockIndex` échangés ici sont ceux de la version **actuelle** du chapitre —
 * exactement ceux que le lecteur affiche. Le recalage des ancres après une
 * ré-ingestion est entièrement à la charge du serveur : l'app n'a jamais à connaître
 * les empreintes de texte.
 */

/** Un emoji et le nombre de lecteurs qui l'ont posé sur un passage. */
@Serializable
data class EmojiTallyDto(
    val emoji: String = "",
    val count: Long = 0,
)

/**
 * L'activité d'un bloc, telle que la marge doit la montrer.
 *
 * `myEmoji` est `null` tant que le lecteur n'a pas réagi — le serveur le calcule à
 * partir du jeton, l'app n'a donc pas besoin de connaître son propre identifiant.
 */
@Serializable
data class BlockActivityDto(
    val blockIndex: Int = -1,
    val commentCount: Long = 0,
    val reactions: List<EmojiTallyDto> = emptyList(),
    val myEmoji: String? = null,
)

/**
 * Toute l'activité d'un chapitre en un appel.
 *
 * `orphanedComments` compte les messages dont le passage a disparu du chapitre. Ils ne
 * sont accrochés nulle part plutôt que de se poser sur le mauvais paragraphe.
 */
@Serializable
data class ChapterActivityDto(
    val blocks: List<BlockActivityDto> = emptyList(),
    val orphanedComments: Long = 0,
)

/**
 * Un message accroché à un passage.
 *
 * `replies` n'est rempli que sur les messages racines : l'arbre ne descend jamais
 * au-delà d'un niveau, une réponse porte donc toujours une liste vide.
 */
@Serializable
data class PassageCommentDto(
    val id: Long,
    val userId: Long? = null,
    val pseudo: String? = null,
    val avatarUrl: String? = null,
    val body: String = "",
    val spoiler: Boolean = false,
    val mine: Boolean = false,
    val createdAt: String? = null,
    /** Qui est visé par les `@pseudo` du texte (issue #45, §2). */
    val mentions: List<MentionDto> = emptyList(),
    /** GIF joint (issue #45, §5) : `body` peut alors être vide. */
    val gifUrl: String? = null,
    val gifPreviewUrl: String? = null,
    /** Réactions emoji posées sur ce message (les plus posées d'abord). */
    val reactions: List<EmojiTallyDto> = emptyList(),
    /** Les emojis que MOI j'ai posés, pour surligner mes puces. */
    val myReactions: List<String> = emptyList(),
    val replies: List<PassageCommentDto> = emptyList(),
)

/**
 * Corps de `POST /api/chapters/{id}/passages/comments`.
 *
 * `spoiler` n'a **volontairement pas de valeur par défaut** : `kotlinx.serialization`
 * omet du JSON tout champ laissé à sa valeur par défaut, et le champ arrivait donc
 * absent au serveur. Sans défaut, il est toujours encodé. Le serveur sait de son côté
 * traiter l'absence, mais mieux vaut ne pas la produire.
 */
@Serializable
data class CreatePassageCommentRequestDto(
    val blockIndex: Int,
    val body: String,
    val spoiler: Boolean,
    /** Message auquel on répond, ou `null` pour ouvrir un fil. */
    val parentId: Long? = null,
    /** Mentions résolues par l'autocomplétion (issue #45, §2). */
    val mentionedUserIds: List<Long> = emptyList(),
    /** GIF joint (issue #45, §5). */
    val gifUrl: String? = null,
    val gifPreviewUrl: String? = null,
)

/**
 * Corps de `PUT /api/chapters/{id}/passages/reactions`.
 *
 * Un seul appel pour poser, remplacer ou retirer : côté lecteur c'est un seul geste,
 * toucher un emoji. Le serveur déduit lequel des trois d'après l'état courant.
 */
@Serializable
data class ReactToPassageRequestDto(
    val blockIndex: Int,
    val emoji: String,
)

/** État d'un bloc après une réaction — de quoi rafraîchir la marge sans tout redemander. */
@Serializable
data class PassageReactionDto(
    val blockIndex: Int = -1,
    val myEmoji: String? = null,
    val reactions: List<EmojiTallyDto> = emptyList(),
)
