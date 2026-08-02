package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.HistoryApi
import com.novelrealm.mobile.data.remote.dto.HistoryEntryDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Historique de lecture (paginé) + effacement global ou par roman.
class HistoryRepository(private val historyApi: HistoryApi) {

    suspend fun getHistory(page: Int, size: Int = 20): ApiResult<PageDto<HistoryEntryDto>> =
        safeApiCall { historyApi.getHistory(page = page, size = size) }

    suspend fun clearAll(): ApiResult<Unit> =
        safeApiCall { historyApi.clearAll() }

    suspend fun clearNovel(novelId: Long): ApiResult<Unit> =
        safeApiCall { historyApi.clearNovel(novelId) }
}
