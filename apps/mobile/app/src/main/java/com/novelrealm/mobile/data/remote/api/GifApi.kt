package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.GifAvailabilityDto
import com.novelrealm.mobile.data.remote.dto.GifPageDto
import retrofit2.http.GET
import retrofit2.http.Query

// Recherche de GIF (issue #45, §5) — proxy KLIPY côté back : l'app ne parle
// jamais au fournisseur et n'embarque aucune clé d'API.
interface GifApi {

    /** La fonctionnalité est-elle configurée sur ce serveur ? (sinon : bouton masqué) */
    @GET("api/gifs/availability")
    suspend fun availability(): GifAvailabilityDto

    @GET("api/gifs/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 24,
        @Query("pos") pos: String? = null,
    ): GifPageDto

    /** Les GIF du moment — ce que montre le sélecteur avant toute saisie. */
    @GET("api/gifs/featured")
    suspend fun featured(
        @Query("limit") limit: Int = 24,
        @Query("pos") pos: String? = null,
    ): GifPageDto
}
