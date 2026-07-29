package com.novelrealm.mobile.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.ChapterDto
import com.novelrealm.mobile.data.remote.dto.ChapterProgressDto
import com.novelrealm.mobile.data.remote.dto.NovelDetailDto
import com.novelrealm.mobile.data.remote.dto.ReviewSummaryDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NovelDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val novel: NovelDetailDto? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val progress: Map<Long, ChapterProgressDto> = emptyMap(),
    val favoriteChapterIds: Set<Long> = emptySet(),
    val libraryStatus: String? = null,          // null = pas dans la bibliothèque
    val reviewSummary: ReviewSummaryDto? = null,
    val categories: List<CategoryDto> = emptyList(),
)

// Écran détail d'un roman (#35) : agrège 7 domaines du back (roman, chapitres, progression,
// signets, bibliothèque, avis, étagères). Les échecs secondaires sont silencieux — seul
// l'échec du roman lui-même est bloquant.
class NovelDetailViewModel(private val novelId: Long) : ViewModel() {

    private val novelRepo = ServiceLocator.novelRepository
    private val chapterRepo = ServiceLocator.chapterRepository
    private val progressRepo = ServiceLocator.progressRepository
    private val favoriteRepo = ServiceLocator.favoriteRepository
    private val libraryRepo = ServiceLocator.libraryRepository
    private val reviewRepo = ServiceLocator.reviewRepository
    private val categoryRepo = ServiceLocator.categoryRepository

    private val _state = MutableStateFlow(NovelDetailUiState())
    val state: StateFlow<NovelDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val detailDef = async { novelRepo.getNovelDetail(novelId) }
            val chaptersDef = async { chapterRepo.getChapters(novelId) }
            val progressDef = async { progressRepo.getNovelProgress(novelId) }
            val favoritesDef = async { favoriteRepo.getForNovel(novelId) }
            val libraryDef = async { libraryRepo.getLibrary() }
            val summaryDef = async { reviewRepo.getSummary(novelId) }
            val categoriesDef = async { categoryRepo.getCategories() }

            when (val detail = detailDef.await()) {
                is ApiResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = detail.userMessage()) }
                    return@launch
                }
                is ApiResult.Success -> _state.update { it.copy(novel = detail.data) }
            }

            val chapters = (chaptersDef.await() as? ApiResult.Success)?.data.orEmpty()
                .sortedBy { it.chapterNumber }
            val progress = (progressDef.await() as? ApiResult.Success)?.data.orEmpty()
                .associateBy { it.chapterId }
            val favorites = (favoritesDef.await() as? ApiResult.Success)?.data.orEmpty()
                .map { it.chapterId }.toSet()
            val libraryStatus = (libraryDef.await() as? ApiResult.Success)?.data
                ?.firstOrNull { it.novel.id == novelId }?.status
            val summary = (summaryDef.await() as? ApiResult.Success)?.data
            val categories = (categoriesDef.await() as? ApiResult.Success)?.data.orEmpty()

            _state.update {
                it.copy(
                    isLoading = false,
                    chapters = chapters,
                    progress = progress,
                    favoriteChapterIds = favorites,
                    libraryStatus = libraryStatus,
                    reviewSummary = summary,
                    categories = categories,
                )
            }
        }
    }

    // Rafraîchit uniquement la progression + les signets (au retour du lecteur).
    fun refreshProgress() {
        viewModelScope.launch {
            val progressDef = async { progressRepo.getNovelProgress(novelId) }
            val favoritesDef = async { favoriteRepo.getForNovel(novelId) }
            (progressDef.await() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(progress = r.data.associateBy { p -> p.chapterId }) }
            }
            (favoritesDef.await() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(favoriteChapterIds = r.data.map { f -> f.chapterId }.toSet()) }
            }
        }
    }

    // ── Bibliothèque ──

    fun addToLibrary() {
        viewModelScope.launch {
            when (val result = libraryRepo.add(novelId)) {
                is ApiResult.Success -> _state.update { it.copy(libraryStatus = result.data.status) }
                is ApiResult.Error -> if (result.code == 409) {
                    // Déjà dans la bibliothèque (état local désynchronisé) → resynchronise.
                    _state.update { it.copy(libraryStatus = "PLAN_TO_READ") }
                }
            }
        }
    }

    fun setLibraryStatus(status: String) {
        viewModelScope.launch {
            when (val result = libraryRepo.updateStatus(novelId, status)) {
                is ApiResult.Success -> _state.update { it.copy(libraryStatus = result.data.status) }
                is ApiResult.Error -> Unit
            }
        }
    }

    fun removeFromLibrary() {
        viewModelScope.launch {
            when (libraryRepo.remove(novelId)) {
                is ApiResult.Success -> _state.update { it.copy(libraryStatus = null) }
                is ApiResult.Error -> Unit
            }
        }
    }

    // ── Progression ──

    fun markChapterRead(chapterId: Long, read: Boolean) {
        // Optimiste : on met l'UI à jour tout de suite, le serveur suit.
        _state.update { state ->
            val current = state.progress[chapterId] ?: ChapterProgressDto(chapterId = chapterId)
            state.copy(progress = state.progress + (chapterId to current.copy(read = read)))
        }
        viewModelScope.launch { progressRepo.markRead(chapterId, read) }
    }

    fun markAllRead() {
        val ids = _state.value.chapters.map { it.id }
        if (ids.isEmpty()) return
        _state.update { state ->
            val updated = state.progress.toMutableMap()
            ids.forEach { id ->
                val current = updated[id] ?: ChapterProgressDto(chapterId = id)
                updated[id] = current.copy(read = true)
            }
            state.copy(progress = updated)
        }
        viewModelScope.launch { progressRepo.markBatch(ids, read = true) }
    }

    // ── Étagères ──

    fun toggleShelf(category: CategoryDto) {
        val inShelf = category.novels.any { it.id == novelId }
        viewModelScope.launch {
            if (inShelf) {
                when (categoryRepo.removeNovel(category.id, novelId)) {
                    is ApiResult.Success -> _state.update { state ->
                        state.copy(
                            categories = state.categories.map { c ->
                                if (c.id == category.id) c.copy(novels = c.novels.filterNot { it.id == novelId })
                                else c
                            },
                        )
                    }
                    is ApiResult.Error -> Unit
                }
            } else {
                when (val result = categoryRepo.addNovel(category.id, novelId)) {
                    is ApiResult.Success -> _state.update { state ->
                        state.copy(categories = state.categories.map { c -> if (c.id == category.id) result.data else c })
                    }
                    is ApiResult.Error -> Unit
                }
            }
        }
    }

    fun createShelfAndAdd(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val created = categoryRepo.create(trimmed)) {
                is ApiResult.Success -> {
                    when (val withNovel = categoryRepo.addNovel(created.data.id, novelId)) {
                        is ApiResult.Success -> _state.update { it.copy(categories = it.categories + withNovel.data) }
                        is ApiResult.Error -> _state.update { it.copy(categories = it.categories + created.data) }
                    }
                }
                is ApiResult.Error -> Unit
            }
        }
    }
}
