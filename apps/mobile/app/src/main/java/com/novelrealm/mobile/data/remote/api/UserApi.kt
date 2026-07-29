package com.novelrealm.mobile.data.remote.api

import com.novelrealm.mobile.data.remote.dto.ChangePasswordRequestDto
import com.novelrealm.mobile.data.remote.dto.UpdatePreferencesRequestDto
import com.novelrealm.mobile.data.remote.dto.UpdateProfileRequestDto
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part

// Profil de l'utilisateur courant (`/me` = identifié par le JWT).
//
// Les endpoints qui répondent 204 (mot de passe, suppression) retournent `Unit` :
// Retrofit court-circuite la conversion sur un corps vide, tout en levant une
// HttpException en cas d'erreur — c'est ce que `safeApiCall` attend.
interface UserApi {

    @GET("api/users/me")
    suspend fun getMe(): UserDto

    @GET("api/users/me/stats")
    suspend fun getMyStats(): UserStatsDto

    @PATCH("api/users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequestDto): UserDto

    @PATCH("api/users/me")
    suspend fun updatePreferences(@Body body: UpdatePreferencesRequestDto): UserDto

    // Le back lit la partie nommée exactement « file » et détecte le format via les
    // octets d'en-tête (JPG/PNG/WebP uniquement). Avatar ≤ 2 Mo, bannière ≤ 4 Mo.
    @Multipart
    @POST("api/users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UserDto

    @DELETE("api/users/me/avatar")
    suspend fun deleteAvatar(): UserDto

    @Multipart
    @POST("api/users/me/banner")
    suspend fun uploadBanner(@Part file: MultipartBody.Part): UserDto

    @DELETE("api/users/me/banner")
    suspend fun deleteBanner(): UserDto

    @PUT("api/users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequestDto)

    @DELETE("api/users/me")
    suspend fun deleteAccount()
}
