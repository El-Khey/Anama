package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.UserApi
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import com.novelrealm.mobile.data.remote.safeApiCall

// Profil de l'utilisateur courant : lecture, stats, édition partielle.
class UserRepository(private val userApi: UserApi) {

    suspend fun getMe(): ApiResult<UserDto> =
        safeApiCall { userApi.getMe() }

    suspend fun getMyStats(): ApiResult<UserStatsDto> =
        safeApiCall { userApi.getMyStats() }

    suspend fun updateProfile(pseudo: String?, bio: String?): ApiResult<UserDto> =
        safeApiCall {
            userApi.updateProfile(
                com.novelrealm.mobile.data.remote.dto.UpdateProfileRequestDto(
                    pseudo = pseudo,
                    bio = bio,
                ),
            )
        }
}
