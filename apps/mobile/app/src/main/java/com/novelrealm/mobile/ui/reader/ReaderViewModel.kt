package com.novelrealm.mobile.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.ChapterDetailDto
import com.novelrealm.mobile.data.remote.dto.ChapterDto
import com.novelrealm.mobile.data.remote.dto.ChapterProgressDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val chapter: ChapterDetailDto? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val initialPercent: Int = 0,        // position de reprise à restaurer (0 si déjà lu)
    val isFavorite: Boolean = false,
    val isRead: Boolean = false,
    /** Chapitres voisins — gardés entiers (et pas juste un booléen) pour pouvoir les nommer. */
    val previousChapter: ChapterDto? = null,
    val nextChapter: ChapterDto? = null,
) {
    val hasPrevious: Boolean get() = previousChapter != null
    val hasNext: Boolean get() = nextChapter != null

    /** Rang du chapitre courant dans le roman, 1-based ; 0 si inconnu. */
    val position: Int get() = chapters.indexOfFirst { it.id == chapter?.id } + 1
}

// Lecteur (#35) : charge le contenu du chapitre, restaure la position de reprise,
// sauvegarde la progression en continu et marque le chapitre lu en fin de lecture.
// Les écritures passent par appScope pour survivre à la fermeture de l'écran.
class ReaderViewModel(
    private val novelId: Long,
    private val initialChapterId: Long,
) : ViewModel() {

    private val chapterRepo = ServiceLocator.chapterRepository
    private val progressRepo = ServiceLocator.progressRepository
    private val favoriteRepo = ServiceLocator.favoriteRepository
    private val appScope = ServiceLocator.appScope

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var progressByChapter = mutableMapOf<Long, ChapterProgressDto>()
    private var favoriteIds = mutableSetOf<Long>()
    private var lastSavedPercent = -1
    private var markedRead = false

    init {
        viewModelScope.launch {
            val chaptersDef = async { chapterRepo.getChapters(novelId) }
            val progressDef = async { progressRepo.getNovelProgress(novelId) }
            val favoritesDef = async { favoriteRepo.getForNovel(novelId) }

            (chaptersDef.await() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(chapters = r.data.sortedBy { c -> c.chapterNumber }) }
            }
            (progressDef.await() as? ApiResult.Success)?.let { r ->
                progressByChapter = r.data.associateBy { it.chapterId }.toMutableMap()
            }
            (favoritesDef.await() as? ApiResult.Success)?.let { r ->
                favoriteIds = r.data.map { it.chapterId }.toMutableSet()
            }
            loadChapter(initialChapterId)
        }
    }

    fun loadChapter(chapterId: Long) {
        markedRead = false
        lastSavedPercent = -1
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = chapterRepo.getChapter(chapterId)) {
                is ApiResult.Success -> {
                    val progress = progressByChapter[chapterId]
                    val chapters = _state.value.chapters
                    val index = chapters.indexOfFirst { it.id == chapterId }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            chapter = result.data,
                            initialPercent = if (progress?.read == true) 0 else (progress?.scrollPosition ?: 0),
                            isFavorite = chapterId in favoriteIds,
                            isRead = progress?.read == true,
                            // `index` vaut -1 si la liste des chapitres n'a pas pu être
                            // chargée : les deux voisins sont alors nuls, et la navigation
                            // par flèches comme par balayage se désactive d'elle-même.
                            previousChapter = if (index > 0) chapters[index - 1] else null,
                            nextChapter = if (index >= 0) chapters.getOrNull(index + 1) else null,
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoading = false, error = result.userMessage())
                }
            }
        }
    }

    fun openPrevious() {
        _state.value.previousChapter?.let { loadChapter(it.id) }
    }

    fun openNext() {
        _state.value.nextChapter?.let { loadChapter(it.id) }
    }

    // Appelé (débouncé) par l'écran quand le pourcentage de lecture change.
    fun onScrollPercent(percent: Int) {
        val chapter = _state.value.chapter ?: return

        // Fin de chapitre → marqué lu automatiquement (une seule fois).
        if (percent >= 97 && !markedRead && !_state.value.isRead) {
            markedRead = true
            _state.update { it.copy(isRead = true) }
            val current = progressByChapter[chapter.id] ?: ChapterProgressDto(chapterId = chapter.id)
            progressByChapter[chapter.id] = current.copy(read = true, scrollPosition = percent)
            appScope.launch { progressRepo.markRead(chapter.id, true) }
        }

        // Position de reprise : on n'écrit que si ça a bougé d'au moins 3 points.
        if (percent != lastSavedPercent &&
            (lastSavedPercent < 0 || kotlin.math.abs(percent - lastSavedPercent) >= 3 || percent >= 97)
        ) {
            lastSavedPercent = percent
            val current = progressByChapter[chapter.id] ?: ChapterProgressDto(chapterId = chapter.id)
            progressByChapter[chapter.id] = current.copy(scrollPosition = percent)
            appScope.launch { progressRepo.savePosition(chapter.id, percent) }
        }
    }

    fun toggleFavorite() {
        val chapter = _state.value.chapter ?: return
        val wasFavorite = _state.value.isFavorite
        _state.update { it.copy(isFavorite = !wasFavorite) }
        if (wasFavorite) favoriteIds.remove(chapter.id) else favoriteIds.add(chapter.id)
        appScope.launch {
            if (wasFavorite) favoriteRepo.remove(chapter.id) else favoriteRepo.add(chapter.id)
        }
    }
}
