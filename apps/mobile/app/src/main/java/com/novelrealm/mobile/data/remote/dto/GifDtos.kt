package com.novelrealm.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Miroir de `GifResponse` (back) : un GIF proposé par la recherche (issue #45, §5).
 *
 * `url` est la version animée (format « tinygif » de Tenor, léger), `previewUrl`
 * une image FIGÉE du même GIF — c'est elle qu'on affiche par défaut, l'animation
 * ne démarrant qu'au toucher. `width`/`height` fixent le ratio avant chargement,
 * pour que la grille ne saute pas.
 */
@Serializable
data class GifDto(
    val id: String = "",
    val url: String = "",
    val previewUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/** Une page de résultats. `next` = curseur Tenor opaque ; vide = fin. */
@Serializable
data class GifPageDto(
    val results: List<GifDto> = emptyList(),
    val next: String = "",
)

/** La fonctionnalité GIF est-elle configurée sur ce serveur ? */
@Serializable
data class GifAvailabilityDto(
    val available: Boolean = false,
)
