package com.novelrealm.mobile.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.LibraryEntryDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val entries: List<LibraryEntryDto> = emptyList(),
    // novelId → (nombre de chapitres non lus)
    val unreadByNovel: Map<Long, Long> = emptyMap(),
    // novelId → part de chapitres lus (0f..1f), pour la barre de progression des cartes
    val readFractionByNovel: Map<Long, Float> = emptyMap(),
    val categories: List<CategoryDto> = emptyList(),
) {
    /** Statut de lecture d'un roman suivi, ou null s'il n'est pas dans la bibliothèque. */
    fun statusOf(novelId: Long): String? =
        entries.firstOrNull { it.novel.id == novelId }?.status
}

// Bibliothèque (#35) : romans suivis (avec badge non-lus) + étagères personnelles,
// présentés en onglets façon Mihon (catégories = onglets).
class LibraryViewModel : ViewModel() {

    private val libraryRepo = ServiceLocator.libraryRepository
    private val progressRepo = ServiceLocator.progressRepository
    private val categoryRepo = ServiceLocator.categoryRepository

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = it.entries.isEmpty(), error = null) }
            val libraryDef = async { libraryRepo.getLibrary() }
            val summaryDef = async { progressRepo.getSummary() }
            val categoriesDef = async { categoryRepo.getCategories() }

            when (val library = libraryDef.await()) {
                is ApiResult.Success -> {
                    val summary = (summaryDef.await() as? ApiResult.Success)?.data.orEmpty()
                    val unread = summary.associate { s ->
                        s.novelId to (s.totalChapters - s.readChapters).coerceAtLeast(0)
                    }
                    val fractions = summary
                        .filter { it.totalChapters > 0 }
                        .associate { s -> s.novelId to (s.readChapters.toFloat() / s.totalChapters) }
                    val categories = (categoriesDef.await() as? ApiResult.Success)?.data.orEmpty()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            entries = library.data,
                            unreadByNovel = unread,
                            readFractionByNovel = fractions,
                            categories = categories,
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoading = false, error = library.userMessage())
                }
            }
        }
    }

    // ── Statut de lecture (accessible directement depuis la grille, appui long) ──

    /** Déplace un roman vers un autre statut (À lire / En cours / En pause / Terminé). */
    fun setStatus(novelId: Long, status: String) {
        // Optimiste : la grille se réorganise tout de suite, le serveur suit.
        _state.update { state ->
            state.copy(
                entries = state.entries.map {
                    if (it.novel.id == novelId) it.copy(status = status) else it
                },
            )
        }
        viewModelScope.launch {
            if (libraryRepo.updateStatus(novelId, status) is ApiResult.Error) refresh()
        }
    }

    /** Retire un roman de la bibliothèque (il reste consultable depuis Explorer). */
    fun removeFromLibrary(novelId: Long) {
        _state.update { state ->
            state.copy(entries = state.entries.filterNot { it.novel.id == novelId })
        }
        viewModelScope.launch {
            if (libraryRepo.remove(novelId) is ApiResult.Error) refresh()
        }
    }

    // ── Étagères ──

    fun createShelf(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val result = categoryRepo.create(trimmed)) {
                is ApiResult.Success -> _state.update { it.copy(categories = it.categories + result.data) }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun renameShelf(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val result = categoryRepo.rename(id, trimmed)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(categories = state.categories.map { if (it.id == id) result.data else it })
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun deleteShelf(id: Long) {
        viewModelScope.launch {
            when (categoryRepo.delete(id)) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(categories = state.categories.filterNot { it.id == id })
                }
                is ApiResult.Error -> Unit
            }
        }
    }
}
