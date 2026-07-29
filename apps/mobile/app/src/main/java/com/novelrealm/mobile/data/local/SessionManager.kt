package com.novelrealm.mobile.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Source de vérité **unique** de l'état de session (connecté / déconnecté), partagée
 * par toute l'app via [com.novelrealm.mobile.di.ServiceLocator].
 *
 * Pourquoi une classe dédiée plutôt que de laisser l'`AuthViewModel` gérer ça seul :
 * la session peut sauter **hors** d'un écran d'auth — typiquement quand un appel de
 * données quelconque revient en **401** (token JWT expiré). L'intercepteur réseau
 * appelle alors [onUnauthorized], ce qui bascule [isAuthenticated] à `false` ; comme
 * la porte d'entrée (`AppRoot`) observe ce flux, l'utilisateur est **automatiquement**
 * renvoyé vers l'écran de connexion, où qu'il soit dans l'app.
 *
 * Toutes les mutations passent par ici (login, logout, expiration) pour garder le
 * token persistant ([TokenStorage]) et l'état en mémoire toujours cohérents.
 */
class SessionManager(
    private val tokenStorage: TokenStorage,
    /** Nettoyage à faire quand la session se ferme (ex. arrêter la sync des préférences). */
    private val onSessionEnded: () -> Unit = {},
) {

    private val _isAuthenticated = MutableStateFlow(tokenStorage.hasToken())
    /** `true` tant qu'un token est présent et n'a pas été invalidé (401 / logout). */
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _pseudo = MutableStateFlow(tokenStorage.getPseudo())
    /** Pseudo mémorisé de l'utilisateur courant, pour l'affichage sans rappel API. */
    val pseudo: StateFlow<String?> = _pseudo.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    /**
     * Passe à `true` uniquement quand la session a sauté sur un **401** (token périmé),
     * pour afficher un message explicite sur l'écran de login. Remis à `false` dès la
     * prochaine connexion réussie ou une déconnexion volontaire.
     */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    /** Ouvre une session après un login/register réussi. */
    fun onAuthenticated(token: String, pseudo: String?) {
        tokenStorage.saveSession(token, pseudo)
        _pseudo.value = pseudo
        _sessionExpired.value = false
        _isAuthenticated.value = true
    }

    /** Déconnexion **volontaire** (bouton) : on oublie tout, sans message d'expiration. */
    fun onLoggedOut() {
        clearLocal()
        _sessionExpired.value = false
    }

    /**
     * Déconnexion **subie** : déclenchée par l'intercepteur réseau quand un appel
     * authentifié renvoie 401. Idempotent (plusieurs appels parallèles peuvent tomber
     * en 401 en même temps) et sûr à appeler depuis un thread réseau — un [StateFlow]
     * est thread-safe.
     */
    fun onUnauthorized() {
        if (!_isAuthenticated.value) return   // déjà déconnecté : rien à faire
        clearLocal()
        _sessionExpired.value = true
    }

    /** Marque le message d'expiration comme vu (après affichage sur le login). */
    fun acknowledgeExpiry() {
        _sessionExpired.value = false
    }

    private fun clearLocal() {
        tokenStorage.clear()
        _pseudo.value = null
        _isAuthenticated.value = false
        onSessionEnded()
    }
}
