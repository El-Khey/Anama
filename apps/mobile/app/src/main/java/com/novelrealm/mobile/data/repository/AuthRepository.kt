package com.novelrealm.mobile.data.repository

import com.novelrealm.mobile.data.local.SessionManager
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.api.AuthApi
import com.novelrealm.mobile.data.remote.dto.LoginRequestDto
import com.novelrealm.mobile.data.remote.dto.RegisterRequestDto
import com.novelrealm.mobile.data.remote.safeApiCall

/**
 * Point d'entrée unique pour l'authentification (#33). Cache la mécanique réseau au
 * reste de l'app : les écrans/ViewModels ne manipulent que des [ApiResult].
 *
 * L'état de session (token + « connecté ») est délégué au [SessionManager] partagé,
 * pour qu'une expiration détectée par la couche réseau (401) et une connexion/logout
 * volontaire modifient **le même** état observable.
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val session: SessionManager,
) {

    /** Connexion : récupère le JWT et ouvre la session. */
    suspend fun login(email: String, password: String): ApiResult<Unit> =
        when (val result = safeApiCall {
            authApi.login(LoginRequestDto(email = email.trim(), password = password))
        }) {
            is ApiResult.Success -> {
                session.onAuthenticated(result.data.token, result.data.pseudo)
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }

    /**
     * Inscription puis **auto-login** : l'endpoint `register` ne renvoie pas de
     * token (juste l'utilisateur créé), on enchaîne donc un `login` avec les mêmes
     * identifiants pour ouvrir la session directement.
     */
    suspend fun register(pseudo: String, email: String, password: String): ApiResult<Unit> {
        val registration = safeApiCall {
            authApi.register(RegisterRequestDto(pseudo = pseudo.trim(), email = email.trim(), password = password))
        }
        return when (registration) {
            is ApiResult.Error -> registration
            is ApiResult.Success -> login(email, password)
        }
    }

    /**
     * Déconnexion volontaire : on prévient le back (best-effort, il efface le cookie
     * web) puis on jette le token local — le résultat de l'appel réseau n'est pas
     * bloquant (même hors ligne, la session locale doit se fermer).
     */
    suspend fun logout() {
        safeApiCall { authApi.logout() }
        session.onLoggedOut()
    }
}
