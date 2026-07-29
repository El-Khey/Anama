package com.novelrealm.mobile.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.GenreDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val novels: List<NovelDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val query: String = "",
    val selectedGenreId: Long? = null,
    val sort: String = "recent",        // recent | title | popularity
    val status: String? = null,         // null (tous) | ONGOING | COMPLETED
    val isLoading: Boolean = false,     // chargement de la 1re page
    val isLoadingMore: Boolean = false, // pagination (pages suivantes)
    val error: String? = null,
    val endReached: Boolean = false,
)

// Catalogue paginé (#35) : `GET /api/novels` avec recherche débouncée, filtre par genre,
// filtre par statut et tri — tous les paramètres exposés par le back.
class ExploreViewModel : ViewModel() {

    private val repository = ServiceLocator.novelRepository

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private var nextPage = 0
    private var searchJob: Job? = null

    init {
        refresh()
        loadGenres()
    }

    private fun loadGenres() {
        viewModelScope.launch {
            (repository.getGenres() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(genres = r.data) }
            }
        }
    }

    // Recherche débouncée : on relance le catalogue 350 ms après la dernière frappe.
    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            refresh()
        }
    }

    // Tap sur un genre déjà sélectionné = désélection.
    fun onGenreSelected(genreId: Long) {
        _state.update {
            it.copy(selectedGenreId = if (it.selectedGenreId == genreId) null else genreId)
        }
        refresh()
    }

    fun onSortSelected(sort: String) {
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun onStatusSelected(status: String?) {
        _state.update { it.copy(status = status) }
        refresh()
    }

    fun refresh() {
        nextPage = 0
        _state.update { it.copy(isLoading = true, error = null, endReached = false) }
        viewModelScope.launch { load(reset = true) }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch { load(reset = false) }
    }

    private suspend fun load(reset: Boolean) {
        val snapshot = _state.value
        val result = repository.getNovels(
            page = nextPage,
            query = snapshot.query,
            genreId = snapshot.selectedGenreId,
            status = snapshot.status,
            sort = snapshot.sort,
        )
        when (result) {
            is ApiResult.Success -> {
                val page = result.data
                _state.update { state ->
                    val merged = if (reset) page.content else state.novels + page.content
                    state.copy(
                        novels = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        endReached = page.page >= page.totalPages - 1,
                    )
                }
                nextPage += 1
            }
            is ApiResult.Error -> _state.update {
                it.copy(isLoading = false, isLoadingMore = false, error = result.userMessage())
            }
        }
    }
}
