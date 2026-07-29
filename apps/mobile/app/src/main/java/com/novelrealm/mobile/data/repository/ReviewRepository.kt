package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.ReviewApi
import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.dto.ReviewDto
import com.novelrealm.mobile.data.remote.dto.ReviewSummaryDto
import com.novelrealm.mobile.data.remote.dto.UpsertReviewRequestDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Avis d'un roman : liste paginée, résumé (moyenne + histogramme), et son propre avis.
class ReviewRepository(private val reviewApi: ReviewApi) {

    suspend fun getReviews(novelId: Long, page: Int): ApiResult<PageDto<ReviewDto>> =
        safeApiCall { reviewApi.getReviews(novelId, page = page) }

    suspend fun getSummary(novelId: Long): ApiResult<ReviewSummaryDto> =
        safeApiCall { reviewApi.getSummary(novelId) }

    // Renvoie Error(404) si l'utilisateur n'a pas encore d'avis — à traiter comme « aucun ».
    suspend fun getMyReview(novelId: Long): ApiResult<ReviewDto> =
        safeApiCall { reviewApi.getMyReview(novelId) }

    suspend fun upsertMyReview(novelId: Long, rating: Int, body: String?): ApiResult<ReviewDto> =
        safeApiCall { reviewApi.upsert(novelId, UpsertReviewRequestDto(rating = rating, body = body)) }

    suspend fun deleteMyReview(novelId: Long): ApiResult<Unit> =
        safeApiCall { reviewApi.deleteMine(novelId) }
}
