package com.novelrealm.mobile.data.remote

import com.novelrealm.mobile.data.local.TokenStorage
import okhttp3.Interceptor
import okhttp3.Response

// Deux responsabilités autour du JWT (#33 + gestion de session #35) :
//
// 1. Ajoute l'en-tête `Authorization: Bearer <token>` à chaque requête, dès qu'un
//    token est mémorisé. C'est ce qui rend l'app « connectée » : le back valide ce
//    token via son filtre JWT.
//
// 2. Détecte l'expiration : si un appel AUTHENTIFIÉ (token présent, hors /api/auth/)
//    revient en 401, le token n'est plus valide → on notifie `onUnauthorized`, qui
//    déconnecte proprement et renvoie l'utilisateur vers le login. Sans ça, l'app se
//    croyait connectée et chaque écran tombait sur « Session expirée » sans issue.
//
// Les endpoints publics d'auth (/api/auth/** : login, register, logout) sont exclus :
// ils n'exigent pas de token, et un 401 y signifie « mauvais identifiants », pas
// « session expirée » — il ne faut donc pas déclencher la déconnexion globale.
class AuthInterceptor(
    private val tokenStorage: TokenStorage,
    private val onUnauthorized: () -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStorage.getToken()
        val isAuthEndpoint = request.url.encodedPath.contains("/api/auth/")

        val outgoing = if (token.isNullOrBlank() || isAuthEndpoint) {
            request
        } else {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }

        val response = chain.proceed(outgoing)

        // Token joint mais refusé → session expirée côté serveur.
        if (response.code == 401 && !isAuthEndpoint && !token.isNullOrBlank()) {
            onUnauthorized()
        }

        return response
    }
}
