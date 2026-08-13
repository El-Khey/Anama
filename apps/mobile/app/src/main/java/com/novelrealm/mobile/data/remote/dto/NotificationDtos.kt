package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Miroir de `NotificationResponse` (back) : une ligne de la cloche (issue #45, §3).
 *
 * Tout ce qu'il faut pour l'afficher ET pour y aller : `chapterId` ouvre le
 * lecteur, `commentKind` dit si la discussion est en fin de chapitre
 * (`CHAPTER_COMMENT`) ou sur un passage (`PASSAGE_COMMENT`), `blockIndex`
 * (passages) désigne le paragraphe tel qu'il s'affichait au moment de
 * l'événement — le lecteur sait déjà s'y rendre via `?block=`.
 *
 * Les champs d'acteur / roman / chapitre sont nullables : un compte ou un roman
 * supprimé ne fait pas disparaître la notification, elle devient juste moins
 * cliquable.
 */
@Serializable
data class NotificationDto(
    val id: Long,
    /** `COMMENT_REPLY`, `MENTION` — `NEW_CHAPTER` arrivera avec l'issue #22. */
    val type: String = "",
    val actorId: Long? = null,
    val actorPseudo: String? = null,
    val actorAvatarUrl: String? = null,
    val novelId: Long? = null,
    val novelTitle: String? = null,
    val novelCoverUrl: String? = null,
    val chapterId: Long? = null,
    val chapterNumber: Int? = null,
    val chapterTitle: String? = null,
    val commentKind: String? = null,
    val commentId: Long? = null,
    val blockIndex: Int? = null,
    val excerpt: String? = null,
    val read: Boolean = false,
    val createdAt: String? = null,
)

/** Miroir de `UnreadCountResponse` (back) : le badge de la cloche. */
@Serializable
data class UnreadCountDto(
    val count: Long = 0,
)
