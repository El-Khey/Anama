package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.UpdateProfileRequestDto
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

// Profil de l'utilisateur courant (`/me` = identifié par le JWT).
interface UserApi {

    @GET("api/users/me")
    suspend fun getMe(): UserDto

    @GET("api/users/me/stats")
    suspend fun getMyStats(): UserStatsDto

    @PATCH("api/users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): UserDto
}
