package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.ChapterFavoriteDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

// Signets de chapitres de l'utilisateur courant.
interface FavoriteApi {

    @GET("api/favorites/novels/{novelId}")
    suspend fun getForNovel(@Path("novelId") novelId: Long): List<ChapterFavoriteDto>

    @POST("api/favorites/chapters/{chapterId}")
    suspend fun add(@Path("chapterId") chapterId: Long): ChapterFavoriteDto

    @DELETE("api/favorites/chapters/{chapterId}")
    suspend fun remove(@Path("chapterId") chapterId: Long)
}
