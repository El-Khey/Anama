package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.NovelApi
import com.novelrealm.mobile.data.remote.dto.GenreDto
import com.novelrealm.mobile.data.remote.dto.NovelDetailDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Accès au catalogue de romans + genres (#35). Enveloppe les appels réseau en [ApiResult].
class NovelRepository(private val novelApi: NovelApi) {

    suspend fun getNovels(
        page: Int,
        query: String? = null,
        genreId: Long? = null,
        status: String? = null,
        sort: String? = null,
    ): ApiResult<PageDto<NovelDto>> =
        safeApiCall {
            novelApi.getNovels(
                query = query?.ifBlank { null },
                genreId = genreId,
                status = status,
                sort = sort,
                page = page,
            )
        }

    suspend fun getNovelDetail(id: Long): ApiResult<NovelDetailDto> =
        safeApiCall { novelApi.getNovel(id) }

    suspend fun getGenres(): ApiResult<List<GenreDto>> =
        safeApiCall { novelApi.getGenres() }
}
