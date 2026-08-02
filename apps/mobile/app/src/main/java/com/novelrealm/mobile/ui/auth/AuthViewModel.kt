package com.novelrealm.mobile.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État d'un formulaire d'auth (login ou register) en cours de soumission. */
data class AuthFormState(
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

/**
 * Cerveau de l'authentification (#33). Instancié par `viewModel()` → **partagé** par
 * la porte d'entrée (`AppRoot`) et les écrans login/register (même portée Activity).
 *
 * L'état « connecté / déconnecté » n'est **pas** détenu ici : il est délégué au
 * [com.novelrealm.mobile.data.local.SessionManager] partagé. Ainsi, une expiration de
 * token détectée par la couche réseau (401 → auto-déconnexion) bascule le même flux et
 * renvoie l'utilisateur au login, même s'il n'était pas sur un écran d'auth (#35).
 */
class AuthViewModel : ViewModel() {

    private val repository = ServiceLocator.authRepository
    private val session = ServiceLocator.sessionManager

    /** Observés directement depuis le [SessionManager] : une seule source de vérité. */
    val isAuthenticated: StateFlow<Boolean> = session.isAuthenticated
    val pseudo: StateFlow<String?> = session.pseudo

    /** `true` quand la dernière déconnexion vient d'une expiration (401), pas d'un logout. */
    val sessionExpired: StateFlow<Boolean> = session.sessionExpired

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    fun login(email: String, password: String) =
        submit { repository.login(email, password) }

    fun register(pseudo: String, email: String, password: String) =
        submit { repository.register(pseudo, email, password) }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _form.value = AuthFormState()
        }
    }

    /** Efface l'erreur affichée (ex. au changement d'écran ou à la saisie). */
    fun clearError() {
        if (_form.value.error != null) {
            _form.value = _form.value.copy(error = null)
        }
    }

    /** Accuse réception du message « session expirée » une fois affiché. */
    fun acknowledgeExpiry() = session.acknowledgeExpiry()

    /** Exécute une action d'auth en gérant l'état (chargement → succès/erreur). */
    private fun submit(action: suspend () -> ApiResult<Unit>) {
        if (_form.value.isSubmitting) return
        _form.value = AuthFormState(isSubmitting = true)
        viewModelScope.launch {
            _form.value = when (val result = action()) {
                is ApiResult.Success -> AuthFormState()   // la session est ouverte par le repository
                is ApiResult.Error -> AuthFormState(error = friendlyMessage(result))
            }
        }
    }

    /** Traduit une erreur API en message clair pour l'utilisateur. */
    private fun friendlyMessage(error: ApiResult.Error): String = when (error.code) {
        null -> "Impossible de joindre le serveur. Vérifie ta connexion."
        401 -> error.message.ifBlank { "Email ou mot de passe incorrect." }
        409 -> error.message.ifBlank { "Cet email est déjà utilisé." }
        400 -> error.message.ifBlank { "Informations invalides." }
        else -> error.message.ifBlank { "Une erreur est survenue (${error.code})." }
    }
}
