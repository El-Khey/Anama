package com.novelrealm.mobile.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.novelrealm.mobile.data.remote.dto.GifDto

/**
 * Favoris de GIF, **stockés localement sur l'appareil** (SharedPreferences +
 * kotlinx-serialization). Volontairement local : pas de table ni d'endpoint côté
 * back — c'est une commodité par téléphone, comme [PreferencesStore] pour le thème.
 *
 * <p>On persiste le [GifDto] ENTIER (id, url, previewUrl, width, height), pas juste
 * l'id : la source (tendances/recherche) ne renverra pas forcément un GIF déjà mis
 * en favori, donc on ne pourrait pas le ré-afficher par simple id.
 *
 * <p>Ordre : le plus récemment ajouté en tête (c'est ce qu'on veut voir d'abord
 * dans l'onglet Favoris). Clé d'unicité : [GifDto.id].
 */
class GifFavoritesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // Tolérant : un JSON écrit par une future version (champ en plus) reste lisible.
    private val json = Json { ignoreUnknownKeys = true }

    private val _favorites = MutableStateFlow(readLocal())
    val favorites: StateFlow<List<GifDto>> = _favorites.asStateFlow()

    /** Ce GIF est-il déjà en favori ? */
    fun isFavorite(id: String): Boolean = _favorites.value.any { it.id == id }

    /**
     * Bascule le favori : ajoute en tête s'il n'y est pas, le retire sinon.
     * Idempotent, sans doublon (unicité par [GifDto.id]).
     */
    fun toggle(gif: GifDto) {
        if (gif.id.isBlank()) return
        val current = _favorites.value
        val next = if (current.any { it.id == gif.id }) {
            current.filterNot { it.id == gif.id }
        } else {
            listOf(gif) + current
        }
        _favorites.value = next
        writeLocal(next)
    }

    // ── Persistance locale ───────────────────────────────────────────────────

    private fun readLocal(): List<GifDto> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        // Un JSON illisible (format changé, données corrompues) ne doit pas crasher :
        // on repart d'une liste vide plutôt que de faire tomber le sélecteur.
        return runCatching { json.decodeFromString<List<GifDto>>(raw) }.getOrDefault(emptyList())
    }

    private fun writeLocal(value: List<GifDto>) {
        prefs.edit()
            .putString(KEY_FAVORITES, json.encodeToString(value))
            .apply()
    }

    private companion object {
        const val FILE_NAME = "novelrealm_gif_favorites"
        const val KEY_FAVORITES = "favorites"
    }
}
