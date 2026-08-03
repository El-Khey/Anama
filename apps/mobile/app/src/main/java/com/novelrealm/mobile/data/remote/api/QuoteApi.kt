package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.CreateQuoteRequestDto
import com.novelrealm.mobile.data.remote.dto.NovelQuoteCountDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.dto.QuoteAnchorDto
import com.novelrealm.mobile.data.remote.dto.QuoteDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Citations personnelles (#41). La collection est celle de l'utilisateur connecté :
// aucun id d'utilisateur ne circule dans les URL.
interface QuoteApi {

    @POST("api/chapters/{chapterId}/quotes")
    suspend fun create(
        @Path("chapterId") chapterId: Long,
        @Body body: CreateQuoteRequestDto,
    ): QuoteDto

    /** `novelId` filtre sur un roman, `q` cherche dans le texte cité. */
    @GET("api/me/quotes")
    suspend fun list(
        @Query("novelId") novelId: Long? = null,
        @Query("q") query: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<QuoteDto>

    @GET("api/me/quotes/counts")
    suspend fun counts(): List<NovelQuoteCountDto>

    /**
     * Résout l'ancre : c'est le seul appel qui fait lire le chapitre entier au
     * serveur, il n'est donc lancé qu'au moment d'aller réellement au passage.
     */
    @GET("api/quotes/{quoteId}/anchor")
    suspend fun anchor(@Path("quoteId") quoteId: Long): QuoteAnchorDto

    @DELETE("api/quotes/{quoteId}")
    suspend fun delete(@Path("quoteId") quoteId: Long)
}
