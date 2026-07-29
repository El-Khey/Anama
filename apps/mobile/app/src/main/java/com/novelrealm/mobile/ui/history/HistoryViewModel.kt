package com.novelrealm.mobile.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.HistoryEntryDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<HistoryEntryDto> = emptyList(),
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
)

// Historique de lecture (#35) : liste paginée triée par date, effacement par roman ou total.
class HistoryViewModel : ViewModel() {

    private val historyRepo = ServiceLocator.historyRepository

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    private var nextPage = 0

    fun refresh() {
        nextPage = 0
        _state.update { it.copy(isLoading = it.entries.isEmpty(), error = null, endReached = false) }
        viewModelScope.launch {
            when (val page = historyRepo.getHistory(page = 0)) {
                is ApiResult.Success -> {
                    nextPage = 1
                    _state.update {
                        it.copy(
                            isLoading = false,
                            entries = page.data.content,
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoading = false, error = page.userMessage())
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val page = historyRepo.getHistory(page = nextPage)) {
                is ApiResult.Success -> {
                    nextPage += 1
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            entries = it.entries + page.data.content,
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            when (historyRepo.clearAll()) {
                is ApiResult.Success -> _state.update { it.copy(entries = emptyList(), endReached = true) }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun clearNovel(novelId: Long) {
        viewModelScope.launch {
            when (historyRepo.clearNovel(novelId)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(entries = state.entries.filterNot { it.novelId == novelId })
                }
                is ApiResult.Error -> Unit
            }
        }
    }
}
