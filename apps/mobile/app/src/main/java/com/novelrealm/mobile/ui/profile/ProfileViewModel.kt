package com.novelrealm.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import com.novelrealm.mobile.data.remote.userMessage
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
    val user: UserDto? = null,
    val stats: UserStatsDto? = null,
    val isSaving: Boolean = false,
)

// Onglet Profil (#35) : profil complet + stats de lecture + édition pseudo/bio.
class ProfileViewModel : ViewModel() {

    private val userRepo = ServiceLocator.userRepository

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.user == null, error = null) }
            val meDef = async { userRepo.getMe() }
            val statsDef = async { userRepo.getMyStats() }

            when (val me = meDef.await()) {
                is ApiResult.Success -> {
                    val stats = (statsDef.await() as? ApiResult.Success)?.data
                    _state.update {
                        it.copy(isLoading = false, user = me.data, stats = stats)
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoading = false, error = me.userMessage())
                }
            }
        }
    }

    fun saveProfile(pseudo: String, bio: String) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // bio "" = effacée côté back ; pseudo inchangé si identique.
            when (val result = userRepo.updateProfile(pseudo = pseudo.trim(), bio = bio)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isSaving = false, user = result.data, error = null)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.userMessage())
                }
            }
        }
    }
}
