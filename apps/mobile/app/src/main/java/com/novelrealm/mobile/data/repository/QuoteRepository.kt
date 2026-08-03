package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.QuoteApi
import com.novelrealm.mobile.data.remote.dto.CreateQuoteRequestDto
import com.novelrealm.mobile.data.remote.dto.NovelQuoteCountDto
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.dto.QuoteAnchorDto
import com.novelrealm.mobile.data.remote.dto.QuoteDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Collection de citations : création depuis le lecteur, consultation, résolution d'ancre.
class QuoteRepository(private val quoteApi: QuoteApi) {

    /**
     * Range un passage dans la collection. On transmet la position du bloc et les
     * bornes dans ce bloc — le texte est extrait côté serveur.
     */
    suspend fun create(
        chapterId: Long,
        blockIndex: Int,
        startOffset: Int,
        endOffset: Int,
    ): ApiResult<QuoteDto> = safeApiCall {
        quoteApi.create(chapterId, CreateQuoteRequestDto(blockIndex, startOffset, endOffset))
    }

    suspend fun list(
        novelId: Long?,
        query: String?,
        page: Int,
    ): ApiResult<PageDto<QuoteDto>> = safeApiCall {
        quoteApi.list(novelId = novelId, query = query?.takeIf { it.isNotBlank() }, page = page)
    }

    suspend fun counts(): ApiResult<List<NovelQuoteCountDto>> = safeApiCall { quoteApi.counts() }

    suspend fun anchor(quoteId: Long): ApiResult<QuoteAnchorDto> =
        safeApiCall { quoteApi.anchor(quoteId) }

    suspend fun delete(quoteId: Long): ApiResult<Unit> = safeApiCall { quoteApi.delete(quoteId) }
}
