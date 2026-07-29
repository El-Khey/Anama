package com.novelrealm.mobile.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.HistoryEntryDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val continueReading: List<HistoryEntryDto> = emptyList(),
    val popular: List<NovelDto> = emptyList(),
    val recent: List<NovelDto> = emptyList(),
)

// Onglet Accueil (#35) : reprendre la lecture (historique récent, 1 entrée par roman),
// romans populaires (sort=popularity) et nouveautés (sort=recent).
class DiscoverViewModel : ViewModel() {

    private val novelRepo = ServiceLocator.novelRepository
    private val historyRepo = ServiceLocator.historyRepository

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.popular.isEmpty() && it.recent.isEmpty(), error = null) }
            val historyDef = async { historyRepo.getHistory(page = 0, size = 12) }
            val popularDef = async { novelRepo.getNovels(page = 0, sort = "popularity") }
            val recentDef = async { novelRepo.getNovels(page = 0, sort = "recent") }

            val history = (historyDef.await() as? ApiResult.Success)
                ?.data?.content.orEmpty().distinctBy { it.novelId }
            val popularResult = popularDef.await()
            val recentResult = recentDef.await()

            val popular = (popularResult as? ApiResult.Success)?.data?.content.orEmpty()
            val recent = (recentResult as? ApiResult.Success)?.data?.content.orEmpty()

            val error = if (popular.isEmpty() && recent.isEmpty()) {
                (popularResult as? ApiResult.Error)?.userMessage()
                    ?: (recentResult as? ApiResult.Error)?.userMessage()
            } else null

            _state.update {
                it.copy(
                    isLoading = false,
                    error = error,
                    continueReading = history,
                    popular = popular,
                    recent = recent,
                )
            }
        }
    }
}
