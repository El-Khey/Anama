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

    /**
     * Faux tant que l'écran n'a pas encore été quitté puis rouvert. Voir [refreshOnReturn].
     */
    private var seenFirstComposition = false

    init {
        load()
    }

    /**
     * Resynchronise ce que le lecteur a pu changer — appelé à **chaque** composition de
     * l'écran, y compris la toute première.
     *
     * <p>Or à la première, [load] vient de charger exactement ces trois choses : l'appel
     * rejouait donc `progress`, `favorites` et `comment-counts` une seconde fois, ~70 ms
     * après les premières, à chaque ouverture d'un roman. On saute ce premier passage ;
     * les suivants sont de vrais retours du lecteur, où le rafraîchissement est utile.
     *
     * <p>Le drapeau vit dans le ViewModel et non dans la composition : sa durée de vie
     * est exactement celle qu'on veut suivre — l'entrée de pile de navigation. Un
     * `remember` serait effacé en allant dans le lecteur, donc toujours vrai au retour.
     */
    fun refreshOnReturn() {
        if (!seenFirstComposition) {
            seenFirstComposition = true
            return
        }
        refreshProgress()
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

    /**
     * Dernier chapitre coché à la main : c'est l'**ancre** des sélections par plage.
     *
     * <p>Dans le ViewModel et non dans l'état : ce n'est pas une donnée d'affichage, rien
     * à l'écran ne la représente. La sortir dans `NovelDetailUiState` ferait recomposer
     * toute la liste à chaque appui, pour une valeur que personne ne lit.
     */
    private var selectionAnchorId: Long? = null

    fun toggleSelection(chapterId: Long) {
        selectionAnchorId = chapterId
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

    /**
     * Coche d'un coup tous les chapitres entre l'ancre et celui-ci, bornes comprises.
     *
     * <p>C'est le geste qui rend la sélection utilisable : cocher quarante chapitres un
     * par un ne se fait pas. La plage est calculée sur l'ordre **naturel** des chapitres,
     * pas sur l'ordre d'affichage — inverser le tri ne change pas l'ensemble compris
     * entre deux chapitres, seulement le sens dans lequel on le parcourt.
     *
     * <p>Sans ancre (premier appui long), on retombe simplement sur une sélection simple.
     */
    fun selectRangeTo(chapterId: Long) {
        val snapshot = _state.value
        val anchor = selectionAnchorId
        if (anchor == null || snapshot.selectedChapterIds.isEmpty()) {
            toggleSelection(chapterId)
            return
        }
        val from = snapshot.chapters.indexOfFirst { it.id == anchor }
        val to = snapshot.chapters.indexOfFirst { it.id == chapterId }
        if (from < 0 || to < 0) {
            toggleSelection(chapterId)
            return
        }
        val range = snapshot.chapters
            .subList(minOf(from, to), maxOf(from, to) + 1)
            .map { it.id }
        selectionAnchorId = chapterId
        _state.update { it.copy(selectedChapterIds = it.selectedChapterIds + range) }
    }

    fun selectAll() {
        _state.update { it.copy(selectedChapterIds = it.chapters.map { c -> c.id }.toSet()) }
    }

    /**
     * Coche ce qui ne l'est pas, et inversement. Utile dans le sens « tout sauf ceux-là » :
     * on coche la poignée d'exceptions, puis on inverse.
     */
    fun invertSelection() {
        _state.update { state ->
            state.copy(
                selectedChapterIds = state.chapters.map { it.id }.toSet() - state.selectedChapterIds,
            )
        }
    }

    /**
     * Marque comme lus tous les chapitres jusqu'au **plus avancé** de la sélection.
     *
     * <p>Le repère est le numéro de chapitre, pas la position dans la liste affichée :
     * l'action doit donner le même résultat quel que soit le sens du tri.
     */
    fun markUpToSelection() {
        val selected = _state.value.selectedChapterIds
        val furthest = _state.value.chapters
            .filter { it.id in selected }
            .maxByOrNull { it.chapterNumber }
            ?: return
        markUpToRead(furthest.id)
    }

    fun clearSelection() {
        selectionAnchorId = null
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
