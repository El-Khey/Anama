package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.GenreDto
import com.novelrealm.mobile.data.remote.dto.NovelDetailDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Endpoints du catalogue de romans + genres. Tous authentifiés (401 sans token) —
// l'interceptor Bearer s'en charge. Le paramètre `q` cherche dans le titre ET l'auteur.
interface NovelApi {

    @GET("api/novels")
    suspend fun getNovels(
        @Query("q") query: String? = null,
        @Query("genreId") genreId: Long? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String? = null,       // recent (défaut) | title | popularity
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): PageDto<NovelDto>

    @GET("api/novels/{id}")
    suspend fun getNovel(@Path("id") id: Long): NovelDetailDto

    @GET("api/genres")
    suspend fun getGenres(): List<GenreDto>
}
