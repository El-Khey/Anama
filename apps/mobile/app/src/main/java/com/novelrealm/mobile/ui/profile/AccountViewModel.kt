package com.novelrealm.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    /** Pseudo à retaper pour confirmer la suppression. */
    val pseudo: String = "",
    /** Un compte Google n'a pas de mot de passe local à changer. */
    val isGoogleAccount: Boolean = false,
    val isChangingPassword: Boolean = false,
    val passwordError: String? = null,
    val passwordChanged: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    /** Le compte a été supprimé : l'écran doit ramener à l'accueil non connecté. */
    val accountDeleted: Boolean = false,
)

// Écran Sécurité : changement de mot de passe et suppression définitive du compte.
class AccountViewModel : ViewModel() {

    private val userRepo = ServiceLocator.userRepository
    private val session = ServiceLocator.sessionManager

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        // L'écran a besoin de savoir à qui il parle : pseudo (confirmation de
        // suppression) et origine du compte (Google = pas de mot de passe local).
        viewModelScope.launch {
            val me = userRepo.getMe()
            if (me is ApiResult.Success) {
                _state.update {
                    it.copy(
                        pseudo = me.data.pseudo,
                        isGoogleAccount = me.data.provider == "GOOGLE",
                    )
                }
            }
        }
    }

    fun changePassword(current: String, new: String) {
        if (_state.value.isChangingPassword) return
        _state.update { it.copy(isChangingPassword = true, passwordError = null, passwordChanged = false) }
        viewModelScope.launch {
            when (val result = userRepo.changePassword(current, new)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isChangingPassword = false, passwordChanged = true)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isChangingPassword = false, passwordError = passwordMessage(result))
                }
            }
        }
    }

    /**
     * Suppression définitive (RGPD). Le back purge le compte, les images et invalide la
     * session ; côté mobile on jette le token pour repasser sur l'écran de connexion.
     */
    fun deleteAccount() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(isDeleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = userRepo.deleteAccount()) {
                is ApiResult.Success -> {
                    session.onLoggedOut()
                    _state.update { it.copy(isDeleting = false, accountDeleted = true) }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isDeleting = false, deleteError = result.userMessage())
                }
            }
        }
    }

    fun clearPasswordFeedback() {
        _state.update { it.copy(passwordError = null, passwordChanged = false) }
    }

    fun clearDeleteError() {
        _state.update { it.copy(deleteError = null) }
    }

    // Messages spécifiques au changement de mot de passe : le 401 ne veut PAS dire
    // « session expirée » ici, mais « mot de passe actuel incorrect ».
    private fun passwordMessage(error: ApiResult.Error): String = when (error.code) {
        null -> "Impossible de joindre le serveur. Vérifie ta connexion."
        401 -> "Mot de passe actuel incorrect."
        409 -> error.message.ifBlank {
            "Ce compte utilise la connexion Google : le mot de passe se gère chez Google."
        }
        400 -> error.message.ifBlank { "Le nouveau mot de passe doit faire au moins 8 caractères." }
        else -> error.message.ifBlank { "Une erreur est survenue (${error.code})." }
    }
}
