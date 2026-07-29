package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.UserApi
import com.novelrealm.mobile.data.remote.dto.ChangePasswordRequestDto
import com.novelrealm.mobile.data.remote.dto.UpdatePreferencesRequestDto
import com.novelrealm.mobile.data.remote.dto.UpdateProfileRequestDto
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import com.novelrealm.mobile.data.remote.safeApiCall
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Images acceptées par le back (détection par octets d'en-tête, pas par extension). */
enum class ImageKind(val maxBytes: Long, val label: String) {
    AVATAR(2L * 1024 * 1024, "La photo de profil"),
    BANNER(4L * 1024 * 1024, "La bannière"),
}

// Profil de l'utilisateur courant : lecture, stats, édition, images, sécurité.
class UserRepository(private val userApi: UserApi) {

    suspend fun getMe(): ApiResult<UserDto> =
        safeApiCall { userApi.getMe() }

    suspend fun getMyStats(): ApiResult<UserStatsDto> =
        safeApiCall { userApi.getMyStats() }

    suspend fun updateProfile(pseudo: String?, bio: String?): ApiResult<UserDto> =
        safeApiCall {
            userApi.updateProfile(UpdateProfileRequestDto(pseudo = pseudo, bio = bio))
        }

    /** Envoie l'objet `preferences` COMPLET (le back remplace le champ en bloc). */
    suspend fun updatePreferences(preferences: JsonObject): ApiResult<UserDto> =
        safeApiCall {
            userApi.updatePreferences(UpdatePreferencesRequestDto(preferences))
        }

    /**
     * Téléverse une image de profil. La taille est vérifiée **avant** l'envoi pour
     * éviter un aller-retour réseau inutile et donner un message précis ; le back
     * refait la vérification de son côté (taille + format réel).
     */
    suspend fun uploadImage(
        kind: ImageKind,
        bytes: ByteArray,
        mimeType: String?,
    ): ApiResult<UserDto> {
        if (bytes.isEmpty()) {
            return ApiResult.Error(null, "Image illisible. Choisis un autre fichier.")
        }
        if (bytes.size > kind.maxBytes) {
            val maxMo = kind.maxBytes / (1024 * 1024)
            return ApiResult.Error(null, "${kind.label} ne doit pas dépasser $maxMo Mo.")
        }

        val media = (mimeType ?: "image/*").toMediaTypeOrNull()
        val part = MultipartBody.Part.createFormData(
            name = "file",                       // nom de partie attendu par le back
            filename = "upload",                 // ignoré : le format est détecté par les octets
            body = bytes.toRequestBody(media),
        )
        return safeApiCall {
            when (kind) {
                ImageKind.AVATAR -> userApi.uploadAvatar(part)
                ImageKind.BANNER -> userApi.uploadBanner(part)
            }
        }
    }

    suspend fun deleteImage(kind: ImageKind): ApiResult<UserDto> =
        safeApiCall {
            when (kind) {
                ImageKind.AVATAR -> userApi.deleteAvatar()
                ImageKind.BANNER -> userApi.deleteBanner()
            }
        }

    suspend fun changePassword(current: String, new: String): ApiResult<Unit> =
        safeApiCall {
            userApi.changePassword(ChangePasswordRequestDto(currentPassword = current, newPassword = new))
        }

    /** Suppression définitive du compte (RGPD) : 204, aucun corps, cascade côté back. */
    suspend fun deleteAccount(): ApiResult<Unit> =
        safeApiCall { userApi.deleteAccount() }
}
