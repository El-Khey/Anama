package com.novelrealm.mobile.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.PublicUserDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublicProfileUiState(
    val isLoading: Boolean = true,
    val user: PublicUserDto? = null,
    val error: String? = null,
)

/** Charge le profil public d'un lecteur (issue #45, §2) — lecture seule. */
class PublicProfileViewModel(private val userId: Long) : ViewModel() {

    private val userRepo = ServiceLocator.userRepository

    private val _state = MutableStateFlow(PublicProfileUiState())
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = userRepo.getPublicProfile(userId)) {
                is ApiResult.Success ->
                    _state.update { it.copy(isLoading = false, user = result.data) }
                is ApiResult.Error ->
                    _state.update { it.copy(isLoading = false, error = result.userMessage()) }
            }
        }
    }
}
