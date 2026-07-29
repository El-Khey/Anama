package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.PageDto
import com.novelrealm.mobile.data.remote.dto.ReviewDto
import com.novelrealm.mobile.data.remote.dto.ReviewSummaryDto
import com.novelrealm.mobile.data.remote.dto.UpsertReviewRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Avis d'un roman. L'auteur de l'avis est toujours l'utilisateur courant (côté back).
interface ReviewApi {

    @GET("api/novels/{novelId}/reviews")
    suspend fun getReviews(
        @Path("novelId") novelId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 10,
    ): PageDto<ReviewDto>

    @GET("api/novels/{novelId}/reviews/summary")
    suspend fun getSummary(@Path("novelId") novelId: Long): ReviewSummaryDto

    // 404 si l'utilisateur n'a pas encore d'avis sur ce roman.
    @GET("api/novels/{novelId}/reviews/me")
    suspend fun getMyReview(@Path("novelId") novelId: Long): ReviewDto

    @PUT("api/novels/{novelId}/reviews")
    suspend fun upsert(
        @Path("novelId") novelId: Long,
        @Body body: UpsertReviewRequestDto,
    ): ReviewDto

    @DELETE("api/novels/{novelId}/reviews/me")
    suspend fun deleteMine(@Path("novelId") novelId: Long)
}
