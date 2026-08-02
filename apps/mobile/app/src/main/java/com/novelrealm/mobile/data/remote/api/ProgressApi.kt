package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.BatchMarkChaptersReadRequestDto
import com.novelrealm.mobile.data.remote.dto.ChapterProgressDto
import com.novelrealm.mobile.data.remote.dto.MarkChapterReadRequestDto
import com.novelrealm.mobile.data.remote.dto.NovelProgressSummaryDto
import com.novelrealm.mobile.data.remote.dto.SaveChapterPositionRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

// Progression de lecture de l'utilisateur courant (lu/non-lu + position de reprise).
interface ProgressApi {

    @GET("api/progress/novels/{novelId}")
    suspend fun getNovelProgress(@Path("novelId") novelId: Long): List<ChapterProgressDto>

    @GET("api/progress/summary")
    suspend fun getSummary(): List<NovelProgressSummaryDto>

    @PUT("api/progress/chapters/{chapterId}")
    suspend fun markRead(
        @Path("chapterId") chapterId: Long,
        @Body body: MarkChapterReadRequestDto,
    ): ChapterProgressDto

    @PUT("api/progress/chapters/batch")
    suspend fun markBatch(@Body body: BatchMarkChaptersReadRequestDto): List<ChapterProgressDto>

    @PUT("api/progress/chapters/{chapterId}/position")
    suspend fun savePosition(
        @Path("chapterId") chapterId: Long,
        @Body body: SaveChapterPositionRequestDto,
    ): ChapterProgressDto
}
