package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.ChapterApi
import com.novelrealm.mobile.data.remote.dto.ChapterDetailDto
import com.novelrealm.mobile.data.remote.dto.ChapterDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Accès aux chapitres : liste d'un roman, et détail avec contenu (lecture).
class ChapterRepository(private val chapterApi: ChapterApi) {

    suspend fun getChapters(novelId: Long): ApiResult<List<ChapterDto>> =
        safeApiCall { chapterApi.getChaptersByNovel(novelId) }

    suspend fun getChapter(id: Long): ApiResult<ChapterDetailDto> =
        safeApiCall { chapterApi.getChapter(id) }
}
