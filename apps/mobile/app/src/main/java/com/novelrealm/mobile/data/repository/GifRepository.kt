package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.GifApi
import com.novelrealm.mobile.data.remote.dto.GifPageDto
import com.novelrealm.mobile.data.remote.safeApiCall

/**
 * Recherche de GIF (issue #45, §5).
 *
 * La disponibilité est mémorisée après le premier appel : elle ne change pas
 * pendant la vie du processus (c'est une clé dans le `.env` du serveur), inutile
 * de la redemander à chaque ouverture de composer.
 */
class GifRepository(private val gifApi: GifApi) {

    private var cachedAvailability: Boolean? = null

    /** `false` aussi en cas d'erreur réseau : on masque le bouton plutôt qu'il ne mène à une erreur. */
    suspend fun isAvailable(): Boolean {
        cachedAvailability?.let { return it }
        return when (val result = safeApiCall { gifApi.availability() }) {
            // Vraie réponse du serveur → mémorisée (oui comme non).
            is ApiResult.Success -> result.data.available.also { cachedAvailability = it }
            // Erreur réseau → « non » PROVISOIRE : on retentera à la prochaine ouverture.
            is ApiResult.Error -> false
        }
    }

    suspend fun search(query: String, pos: String? = null): ApiResult<GifPageDto> =
        safeApiCall { gifApi.search(query = query, pos = pos) }

    suspend fun featured(pos: String? = null): ApiResult<GifPageDto> =
        safeApiCall { gifApi.featured(pos = pos) }
}
