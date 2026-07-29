package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.ProgressApi
import com.novelrealm.mobile.data.remote.dto.BatchMarkChaptersReadRequestDto
import com.novelrealm.mobile.data.remote.dto.ChapterProgressDto
import com.novelrealm.mobile.data.remote.dto.MarkChapterReadRequestDto
import com.novelrealm.mobile.data.remote.dto.NovelProgressSummaryDto
import com.novelrealm.mobile.data.remote.dto.SaveChapterPositionRequestDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Progression de lecture : marquage lu/non-lu, position de reprise, résumé par roman.
class ProgressRepository(private val progressApi: ProgressApi) {

    suspend fun getNovelProgress(novelId: Long): ApiResult<List<ChapterProgressDto>> =
        safeApiCall { progressApi.getNovelProgress(novelId) }

    suspend fun getSummary(): ApiResult<List<NovelProgressSummaryDto>> =
        safeApiCall { progressApi.getSummary() }

    suspend fun markRead(chapterId: Long, read: Boolean): ApiResult<ChapterProgressDto> =
        safeApiCall { progressApi.markRead(chapterId, MarkChapterReadRequestDto(read)) }

    suspend fun markBatch(chapterIds: List<Long>, read: Boolean): ApiResult<List<ChapterProgressDto>> =
        safeApiCall { progressApi.markBatch(BatchMarkChaptersReadRequestDto(chapterIds, read)) }

    suspend fun savePosition(chapterId: Long, percent: Int): ApiResult<ChapterProgressDto> =
        safeApiCall { progressApi.savePosition(chapterId, SaveChapterPositionRequestDto(percent)) }
}
