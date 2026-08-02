package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.AddLibraryEntryRequestDto
import com.novelrealm.mobile.data.remote.dto.LibraryEntryDto
import com.novelrealm.mobile.data.remote.dto.UpdateLibraryStatusRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

// Bibliothèque personnelle de l'utilisateur courant (suivi des romans + statut de lecture).
interface LibraryApi {

    @GET("api/library")
    suspend fun getLibrary(): List<LibraryEntryDto>

    @POST("api/library")
    suspend fun add(@Body body: AddLibraryEntryRequestDto): LibraryEntryDto

    @PATCH("api/library/{novelId}")
    suspend fun updateStatus(
        @Path("novelId") novelId: Long,
        @Body body: UpdateLibraryStatusRequestDto,
    ): LibraryEntryDto

    // 204 No Content — le retour Unit laisse Retrofit lever HttpException sur erreur.
    @DELETE("api/library/{novelId}")
    suspend fun remove(@Path("novelId") novelId: Long)
}
