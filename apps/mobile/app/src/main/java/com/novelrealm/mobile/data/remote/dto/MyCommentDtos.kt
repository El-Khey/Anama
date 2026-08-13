package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Miroir de `MyCommentResponse` (back) : un de MES commentaires dans le flux
 * unifié du profil (issue #45, §4).
 *
 * `kind` (`CHAPTER` | `PASSAGE`) porte deux décisions : où mène l'appui (les
 * commentaires du chapitre, ou le fil du passage via `blockIndex`), et quelle
 * route supprime le message (`DELETE /api/comments/{id}` ou
 * `DELETE /api/passages/annotations/{id}`).
 *
 * `passageExcerpt` (passages seulement) : l'extrait du paragraphe commenté, sans
 * lequel le message est incompréhensible hors contexte. Null si le passage a
 * disparu du chapitre après ré-ingestion.
 */
@Serializable
data class MyCommentDto(
    val kind: String = "",
    val id: Long,
    val body: String? = null,
    val gifUrl: String? = null,
    val gifPreviewUrl: String? = null,
    val spoiler: Boolean = false,
    val reply: Boolean = false,
    val novelId: Long? = null,
    val novelTitle: String? = null,
    val novelCoverUrl: String? = null,
    val chapterId: Long? = null,
    val chapterNumber: Int = 0,
    val chapterTitle: String? = null,
    /** Index RÉSOLU sur la version actuelle du chapitre — prêt pour `?block=`. */
    val blockIndex: Int? = null,
    val passageExcerpt: String? = null,
    val createdAt: String? = null,
) {
    val isPassage: Boolean get() = kind == "PASSAGE"
}
