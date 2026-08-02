package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.AddNovelToCategoryRequestDto
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.CategoryNameRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

// Étagères personnelles (catégories) de l'utilisateur courant.
interface CategoryApi {

    @GET("api/categories")
    suspend fun getCategories(): List<CategoryDto>

    @POST("api/categories")
    suspend fun create(@Body body: CategoryNameRequestDto): CategoryDto

    @PATCH("api/categories/{id}")
    suspend fun rename(@Path("id") id: Long, @Body body: CategoryNameRequestDto): CategoryDto

    @DELETE("api/categories/{id}")
    suspend fun delete(@Path("id") id: Long)

    @POST("api/categories/{id}/novels")
    suspend fun addNovel(
        @Path("id") id: Long,
        @Body body: AddNovelToCategoryRequestDto,
    ): CategoryDto

    @DELETE("api/categories/{id}/novels/{novelId}")
    suspend fun removeNovel(@Path("id") id: Long, @Path("novelId") novelId: Long)
}
