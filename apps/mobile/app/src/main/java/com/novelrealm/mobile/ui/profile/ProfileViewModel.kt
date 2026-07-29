package com.novelrealm.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.data.repository.ImageKind
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val user: UserDto? = null,
    val stats: UserStatsDto? = null,
    val isSaving: Boolean = false,
    /** Image en cours d'envoi (pour afficher un voile de chargement au bon endroit). */
    val uploading: ImageKind? = null,
    /** Passe à true après un enregistrement réussi → l'écran d'édition peut se fermer. */
    val savedSuccessfully: Boolean = false,
)

// Profil : chargement (compte + stats), édition pseudo/bio, photo et bannière.
// Partagé par le hub (ProfileScreen) et l'écran d'édition, chacun avec sa propre
// instance — ils rechargent à l'affichage, donc les données restent fraîches.
class ProfileViewModel : ViewModel() {

    private val userRepo = ServiceLocator.userRepository
    private val preferencesStore = ServiceLocator.preferencesStore

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.user == null, error = null) }
            val meDef = async { userRepo.getMe() }
            val statsDef = async { userRepo.getMyStats() }

            when (val me = meDef.await()) {
                is ApiResult.Success -> {
                    // Les réglages enregistrés sur le compte (web compris) font foi.
                    preferencesStore.hydrateFromRemote(me.data.preferences)
                    val stats = (statsDef.await() as? ApiResult.Success)?.data
                    _state.update { it.copy(isLoading = false, user = me.data, stats = stats) }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoading = false, error = me.userMessage())
                }
            }
        }
    }

    fun saveProfile(pseudo: String, bio: String) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            // bio "" = effacée côté back ; le pseudo est validé avant l'appel.
            when (val result = userRepo.updateProfile(pseudo = pseudo.trim(), bio = bio)) {
                is ApiResult.Success -> _state.update {
                    it.copy(
                        isSaving = false,
                        user = result.data,
                        savedSuccessfully = true,
                        message = "Profil mis à jour",
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.userMessage())
                }
            }
        }
    }

    /**
     * Envoie une nouvelle image. Les octets sont lus par l'écran (qui a le Context) —
     * le ViewModel n'en manipule jamais, pour éviter toute fuite de Context.
     */
    fun uploadImage(kind: ImageKind, bytes: ByteArray, mimeType: String?) {
        if (_state.value.uploading != null) return
        _state.update { it.copy(uploading = kind, error = null) }
        viewModelScope.launch {
            when (val result = userRepo.uploadImage(kind, bytes, mimeType)) {
                is ApiResult.Success -> _state.update {
                    it.copy(uploading = null, user = result.data, message = "Image mise à jour")
                }
                is ApiResult.Error -> _state.update {
                    it.copy(uploading = null, error = result.userMessage())
                }
            }
        }
    }

    fun deleteImage(kind: ImageKind) {
        if (_state.value.uploading != null) return
        _state.update { it.copy(uploading = kind, error = null) }
        viewModelScope.launch {
            when (val result = userRepo.deleteImage(kind)) {
                is ApiResult.Success -> _state.update {
                    it.copy(uploading = null, user = result.data, message = "Image supprimée")
                }
                is ApiResult.Error -> _state.update {
                    it.copy(uploading = null, error = result.userMessage())
                }
            }
        }
    }

    /** Efface le bandeau d'information/erreur après affichage. */
    fun consumeFeedback() {
        _state.update { it.copy(error = null, message = null) }
    }
}
