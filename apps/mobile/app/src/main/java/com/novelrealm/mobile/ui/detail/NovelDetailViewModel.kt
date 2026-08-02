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
    /**
     * Nombre de messages par chapitre (#41). Les chapitres sans discussion sont
     * absents de la table : le compteur ne s'affiche que là où il y a à lire.
     */
    val commentCounts: Map<Long, Long> = emptyMap(),
    /** Chapitres cochés en mode sélection multiple (vide = mode inactif). */
    val selectedChapterIds: Set<Long> = emptySet(),
    /** Ordre d'affichage des chapitres ; false = du plus récent au plus ancien. */
    val ascending: Boolean = true,
) {
    val isSelecting: Boolean get() = selectedChapterIds.isNotEmpty()

    /** Chapitres dans l'ordre d'affichage choisi. */
    val orderedChapters: List<ChapterDto>
        get() = if (ascending) chapters else chapters.asReversed()

    val readCount: Int get() = chapters.count { progress[it.id]?.read == true }

    /** Premier chapitre non lu, dans l'ordre naturel — cible du bouton Reprendre. */
    val resumeChapter: ChapterDto?
        get() = chapters.firstOrNull { progress[it.id]?.read != true }
}

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
    private val commentRepo = ServiceLocator.commentRepository

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
            val commentsDef = async { commentRepo.getCountsByNovel(novelId) }

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
            val commentCounts = (commentsDef.await() as? ApiResult.Success)?.data.orEmpty()

            _state.update {
                it.copy(
                    isLoading = false,
                    chapters = chapters,
                    progress = progress,
                    favoriteChapterIds = favorites,
                    libraryStatus = libraryStatus,
                    reviewSummary = summary,
                    categories = categories,
                    commentCounts = commentCounts,
                )
            }
        }
    }

    // Rafraîchit progression, signets et compteurs de messages (au retour du lecteur :
    // on vient peut-être d'y commenter).
    fun refreshProgress() {
        viewModelScope.launch {
            val progressDef = async { progressRepo.getNovelProgress(novelId) }
            val favoritesDef = async { favoriteRepo.getForNovel(novelId) }
            val commentsDef = async { commentRepo.getCountsByNovel(novelId) }
            (progressDef.await() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(progress = r.data.associateBy { p -> p.chapterId }) }
            }
            (favoritesDef.await() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(favoriteChapterIds = r.data.map { f -> f.chapterId }.toSet()) }
            }
            (commentsDef.await() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(commentCounts = r.data) }
            }
        }
    }

    // ── Bibliothèque ──

    /**
     * Choisit le statut de lecture. Si le roman n'est pas encore suivi, il est **ajouté**
     * directement avec ce statut : choisir « En cours » suffit, sans devoir d'abord
     * appuyer sur le cœur.
     */
    fun setLibraryStatus(status: String) {
        val wasInLibrary = _state.value.libraryStatus != null
        _state.update { it.copy(libraryStatus = status) }   // optimiste
        viewModelScope.launch {
            val result = if (wasInLibrary) {
                libraryRepo.updateStatus(novelId, status)
            } else {
                libraryRepo.add(novelId, status)
            }
            if (result is ApiResult.Error) {
                // 409 = déjà présent : on rattrape en mettant simplement à jour le statut.
                if (result.code == 409) libraryRepo.updateStatus(novelId, status)
                else _state.update { it.copy(libraryStatus = if (wasInLibrary) it.libraryStatus else null) }
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

    /** Marque tous les chapitres du roman comme lus (ou non lus). */
    fun markAllRead(read: Boolean = true) {
        markChapters(_state.value.chapters.map { it.id }, read)
    }

    /**
     * Marque comme lus tous les chapitres jusqu'à celui-ci **inclus**. C'est l'action la
     * plus utile quand on reprend une série commencée ailleurs : sans elle, il faudrait
     * cocher les chapitres un par un.
     */
    fun markUpToRead(chapterId: Long) {
        val chapters = _state.value.chapters
        val target = chapters.firstOrNull { it.id == chapterId } ?: return
        val ids = chapters.filter { it.chapterNumber <= target.chapterNumber }.map { it.id }
        markChapters(ids, read = true)
    }

    /** Marque une sélection de chapitres comme lus / non lus (un seul appel réseau). */
    fun markChapters(chapterIds: List<Long>, read: Boolean) {
        if (chapterIds.isEmpty()) return
        // Optimiste : l'UI répond immédiatement, le serveur suit.
        _state.update { state ->
            val updated = state.progress.toMutableMap()
            chapterIds.forEach { id ->
                val current = updated[id] ?: ChapterProgressDto(chapterId = id)
                updated[id] = current.copy(read = read)
            }
            state.copy(progress = updated)
        }
        viewModelScope.launch {
            if (progressRepo.markBatch(chapterIds, read) is ApiResult.Error) refreshProgress()
        }
    }

    // ── Signets de chapitres ──

    /** Ajoute ou retire le signet d'un chapitre. */
    fun toggleChapterFavorite(chapterId: Long) {
        val wasFavorite = chapterId in _state.value.favoriteChapterIds
        _state.update { state ->
            state.copy(
                favoriteChapterIds = if (wasFavorite) state.favoriteChapterIds - chapterId
                else state.favoriteChapterIds + chapterId,
            )
        }
        viewModelScope.launch {
            val result = if (wasFavorite) favoriteRepo.remove(chapterId) else favoriteRepo.add(chapterId)
            if (result is ApiResult.Error) refreshProgress()   // resynchronise sur échec
        }
    }

    /**
     * Applique le même signet à toute une sélection. L'API ne traite qu'un chapitre à la
     * fois : les appels sont donc lancés en parallèle plutôt qu'en file d'attente.
     */
    fun setChaptersFavorite(chapterIds: List<Long>, favorite: Boolean) {
        if (chapterIds.isEmpty()) return
        _state.update { state ->
            state.copy(
                favoriteChapterIds = if (favorite) state.favoriteChapterIds + chapterIds
                else state.favoriteChapterIds - chapterIds.toSet(),
            )
        }
        viewModelScope.launch {
            chapterIds.map { id ->
                async { if (favorite) favoriteRepo.add(id) else favoriteRepo.remove(id) }
            }.forEach { it.await() }
            refreshProgress()
        }
    }

    // ── Sélection multiple ──

    fun toggleSelection(chapterId: Long) {
        _state.update { state ->
            state.copy(
                selectedChapterIds = if (chapterId in state.selectedChapterIds) {
                    state.selectedChapterIds - chapterId
                } else {
                    state.selectedChapterIds + chapterId
                },
            )
        }
    }

    fun selectAll() {
        _state.update { it.copy(selectedChapterIds = it.chapters.map { c -> c.id }.toSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedChapterIds = emptySet()) }
    }

    fun toggleSortOrder() {
        _state.update { it.copy(ascending = !it.ascending) }
    }

    // ── Étagères ──

    /**
     * Applique **en une fois** la sélection du dialogue « Définir la catégorie » : seules
     * les cases réellement changées déclenchent un appel réseau. Rien n'est envoyé tant
     * que l'utilisateur n'a pas validé — il peut donc cocher/décocher librement, puis
     * annuler sans conséquence.
     */
    fun applyShelves(selectedIds: Set<Long>) {
        val categories = _state.value.categories
        val toAdd = categories.filter { c ->
            c.id in selectedIds && c.novels.none { it.id == novelId }
        }
        val toRemove = categories.filter { c ->
            c.id !in selectedIds && c.novels.any { it.id == novelId }
        }
        if (toAdd.isEmpty() && toRemove.isEmpty()) return

        viewModelScope.launch {
            toAdd.map { async { categoryRepo.addNovel(it.id, novelId) } }.forEach { it.await() }
            toRemove.map { async { categoryRepo.removeNovel(it.id, novelId) } }.forEach { it.await() }
            // Rechargement complet : plus fiable que de recoller l'état à la main après
            // plusieurs ajouts/retraits en parallèle.
            (categoryRepo.getCategories() as? ApiResult.Success)?.let { result ->
                _state.update { it.copy(categories = result.data) }
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
