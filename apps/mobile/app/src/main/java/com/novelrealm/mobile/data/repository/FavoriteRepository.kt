package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.FavoriteApi
import com.novelrealm.mobile.data.remote.dto.ChapterFavoriteDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Signets de chapitres.
class FavoriteRepository(private val favoriteApi: FavoriteApi) {

    suspend fun getForNovel(novelId: Long): ApiResult<List<ChapterFavoriteDto>> =
        safeApiCall { favoriteApi.getForNovel(novelId) }

    suspend fun add(chapterId: Long): ApiResult<ChapterFavoriteDto> =
        safeApiCall { favoriteApi.add(chapterId) }

    suspend fun remove(chapterId: Long): ApiResult<Unit> =
        safeApiCall { favoriteApi.remove(chapterId) }
}
