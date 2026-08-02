package com.novelrealm.mobile.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Résultat d'un appel réseau : succès typé, ou erreur. `code` est non-null quand
 * le serveur a répondu (ex. 401/404/500) et null quand le réseau est injoignable.
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val code: Int?, val message: String) : ApiResult<Nothing>
}

/** Corps d'erreur standard du back (`ErrorResponse` / `ValidationErrorResponse`). */
@Serializable
private data class ApiErrorBody(val message: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true }

/**
 * Enveloppe un appel Retrofit `suspend` et mappe les exceptions en [ApiResult] :
 * - [HttpException] → Error avec code (le serveur a répondu). On tente d'extraire
 *   le `message` métier du corps d'erreur (ex. « Email ou mot de passe incorrect »)
 *   plutôt que le libellé HTTP générique.
 * - [IOException]   → Error sans code (réseau injoignable / timeout).
 */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        ApiResult.Error(e.code(), serverMessage(e) ?: e.message())
    } catch (e: IOException) {
        ApiResult.Error(null, e.message ?: "Réseau injoignable")
    } catch (e: Exception) {
        ApiResult.Error(null, e.message ?: "Erreur inconnue")
    }

// Message d'erreur présentable à l'utilisateur (mutualisé entre les ViewModels).
fun ApiResult.Error.userMessage(): String = when (code) {
    null -> "Impossible de joindre le serveur. Vérifie ta connexion."
    401 -> "Session expirée — reconnecte-toi."
    else -> message.ifBlank { "Une erreur est survenue ($code)." }
}

/** Extrait le champ `message` du corps d'erreur JSON, ou null si absent/illisible. */
private fun serverMessage(e: HttpException): String? =
    runCatching {
        e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { body ->
            errorJson.decodeFromString<ApiErrorBody>(body).message
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
