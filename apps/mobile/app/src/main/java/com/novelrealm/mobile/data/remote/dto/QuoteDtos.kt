package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Miroir de `QuoteResponse` (back) : une citation de la collection personnelle.
 *
 * `quotedText` a été figé au moment de la capture — c'est ce qui fait que la
 * collection reste lisible même si le chapitre a changé depuis.
 */
@Serializable
data class QuoteDto(
    val id: Long,
    val quotedText: String = "",
    val novelId: Long,
    val novelTitle: String = "",
    val novelCoverUrl: String? = null,
    val chapterId: Long,
    val chapterNumber: Int = 0,
    val chapterTitle: String? = null,
    val createdAt: String? = null,
)

/**
 * Corps de `POST /api/chapters/{id}/quotes` : on envoie des **coordonnées**, jamais
 * le texte. C'est le serveur qui extrait le passage du chapitre et fige la citation.
 */
@Serializable
data class CreateQuoteRequestDto(
    val blockIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Miroir de `QuoteAnchorResponse` (back) : où retrouver la citation aujourd'hui.
 * `alive = false` → le bloc a disparu, on ne peut plus y retourner.
 */
@Serializable
data class QuoteAnchorDto(
    val alive: Boolean = false,
    val blockIndex: Int = -1,
    val chapterId: Long,
    val novelId: Long,
)

/** Miroir de `NovelQuoteCountResponse` (back) : mes citations pour un roman. */
@Serializable
data class NovelQuoteCountDto(
    val novelId: Long,
    val novelTitle: String = "",
    val count: Long = 0,
)
