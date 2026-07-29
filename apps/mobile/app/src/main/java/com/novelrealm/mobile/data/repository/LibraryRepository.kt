package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.LibraryApi
import com.novelrealm.mobile.data.remote.dto.AddLibraryEntryRequestDto
import com.novelrealm.mobile.data.remote.dto.LibraryEntryDto
import com.novelrealm.mobile.data.remote.dto.UpdateLibraryStatusRequestDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Bibliothèque personnelle : suivi des romans avec statut de lecture.
class LibraryRepository(private val libraryApi: LibraryApi) {

    suspend fun getLibrary(): ApiResult<List<LibraryEntryDto>> =
        safeApiCall { libraryApi.getLibrary() }

    suspend fun add(novelId: Long, status: String? = null): ApiResult<LibraryEntryDto> =
        safeApiCall { libraryApi.add(AddLibraryEntryRequestDto(novelId = novelId, status = status)) }

    suspend fun updateStatus(novelId: Long, status: String): ApiResult<LibraryEntryDto> =
        safeApiCall { libraryApi.updateStatus(novelId, UpdateLibraryStatusRequestDto(status)) }

    suspend fun remove(novelId: Long): ApiResult<Unit> =
        safeApiCall { libraryApi.remove(novelId) }
}
