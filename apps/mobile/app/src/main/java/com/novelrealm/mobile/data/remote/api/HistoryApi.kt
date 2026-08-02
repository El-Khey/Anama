package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.HistoryEntryDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Historique de lecture de l'utilisateur courant (paginé, pour « reprendre la lecture »).
interface HistoryApi {

    @GET("api/history")
    suspend fun getHistory(
        @Query("novelId") novelId: Long? = null,
        @Query("sort") sort: String? = null,      // date (défaut) | novel
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<HistoryEntryDto>

    @DELETE("api/history")
    suspend fun clearAll()

    @DELETE("api/history/novels/{novelId}")
    suspend fun clearNovel(@Path("novelId") novelId: Long)
}
