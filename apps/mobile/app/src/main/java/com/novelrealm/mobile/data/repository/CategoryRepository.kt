package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.CategoryApi
import com.novelrealm.mobile.data.remote.dto.AddNovelToCategoryRequestDto
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.CategoryNameRequestDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Étagères personnelles (catégories) : gestion + rangement de romans.
class CategoryRepository(private val categoryApi: CategoryApi) {

    suspend fun getCategories(): ApiResult<List<CategoryDto>> =
        safeApiCall { categoryApi.getCategories() }

    suspend fun create(name: String): ApiResult<CategoryDto> =
        safeApiCall { categoryApi.create(CategoryNameRequestDto(name)) }

    suspend fun rename(id: Long, name: String): ApiResult<CategoryDto> =
        safeApiCall { categoryApi.rename(id, CategoryNameRequestDto(name)) }

    suspend fun delete(id: Long): ApiResult<Unit> =
        safeApiCall { categoryApi.delete(id) }

    suspend fun addNovel(categoryId: Long, novelId: Long): ApiResult<CategoryDto> =
        safeApiCall { categoryApi.addNovel(categoryId, AddNovelToCategoryRequestDto(novelId)) }

    suspend fun removeNovel(categoryId: Long, novelId: Long): ApiResult<Unit> =
        safeApiCall { categoryApi.removeNovel(categoryId, novelId) }
}
