package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.ChapterDetailDto
import com.novelrealm.mobile.data.remote.dto.ChapterDto
import retrofit2.http.GET
import retrofit2.http.Path

// Endpoints des chapitres : liste (métadonnées) et détail (avec contenu, pour la lecture).
interface ChapterApi {

    @GET("api/chapters/novel/{novelId}")
    suspend fun getChaptersByNovel(@Path("novelId") novelId: Long): List<ChapterDto>

    @GET("api/chapters/{id}")
    suspend fun getChapter(@Path("id") id: Long): ChapterDetailDto
}
