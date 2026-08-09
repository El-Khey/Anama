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
)

// Bibliothèque (#35) : romans suivis (avec badge non-lus) + étagères personnelles,
// présentés en onglets façon Mihon (catégories = onglets).
class LibraryViewModel : ViewModel() {

    private val libraryRepo = ServiceLocator.libraryRepository
    private val progressRepo = ServiceLocator.progressRepository
    private val categoryRepo = ServiceLocator.categoryRepository
    private val chapterRepo = ServiceLocator.chapterRepository

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

    // Le statut de lecture ne se change PAS depuis la bibliothèque : il se règle sur la
    // fiche du roman (`NovelDetailViewModel.setLibraryStatus`). C'est un choix qu'on pose
    // délibérément, pas au passage sur une couverture. Ici, il ne sert qu'à filtrer.

    /**
     * Retire un roman de la bibliothèque (il reste consultable depuis Explorer).
     *
     * <p>Le retrait vide AUSSI les étagères : le serveur en fait autant depuis le
     * correctif de `LibraryEntryService.remove`. Sans ce nettoyage local, le roman
     * disparaissait de « Tous » mais restait affiché dans son étagère jusqu'au prochain
     * rechargement — et l'écart entre les deux onglets était visible à l'œil nu.
     */
    fun removeFromLibrary(novelId: Long) {
        _state.update { state ->
            state.copy(
                entries = state.entries.filterNot { it.novel.id == novelId },
                categories = state.categories.map { shelf ->
                    shelf.copy(novels = shelf.novels.filterNot { it.id == novelId })
                },
            )
        }
        viewModelScope.launch {
            if (libraryRepo.remove(novelId) is ApiResult.Error) refresh()
        }
    }

    /**
     * Marque tout un roman comme lu depuis la bibliothèque, sans passer par sa fiche.
     *
     * <p>La bibliothèque ne connaît pas les chapitres — elle n'affiche que des couvertures.
     * On va donc les chercher juste avant le marquage : un aller-retour de plus, mais
     * uniquement quand l'action est réellement demandée, alors que les charger d'avance
     * pour chaque roman suivi coûterait une requête par couverture à chaque ouverture.
     */
    fun markAllRead(novelId: Long) {
        viewModelScope.launch {
            val chapters = (chapterRepo.getChapters(novelId) as? ApiResult.Success)?.data.orEmpty()
            if (chapters.isEmpty()) return@launch
            progressRepo.markBatch(chapters.map { it.id }, read = true)
            // Recharge : c'est le résumé de progression qui alimente la pastille de
            // non-lus et la barre des cartes, et il vient d'une autre route.
            refresh()
        }
    }

    // ── Étagères ──

    /**
     * Range ou retire le roman d'une étagère, depuis la feuille d'actions rapides.
     *
     * <p>Appliqué immédiatement, contrairement au dialogue de la fiche qui attend
     * « Valider » : ici on coche une seule étagère à la fois, il n'y a pas de brouillon
     * à confirmer.
     */
    fun toggleShelf(novelId: Long, categoryId: Long) {
        val shelf = _state.value.categories.firstOrNull { it.id == categoryId } ?: return
        val novel = _state.value.entries.firstOrNull { it.novel.id == novelId }?.novel ?: return
        val wasIn = shelf.novels.any { it.id == novelId }

        _state.update { state ->
            state.copy(
                categories = state.categories.map { c ->
                    when {
                        c.id != categoryId -> c
                        wasIn -> c.copy(novels = c.novels.filterNot { it.id == novelId })
                        else -> c.copy(novels = c.novels + novel)
                    }
                },
            )
        }
        viewModelScope.launch {
            // Les deux routes ne renvoient pas le même type : on teste chaque appel sur
            // place plutôt que de chercher un supertype commun aux deux résultats.
            val failed = if (wasIn) {
                categoryRepo.removeNovel(categoryId, novelId) is ApiResult.Error
            } else {
                categoryRepo.addNovel(categoryId, novelId) is ApiResult.Error
            }
            if (failed) refresh()
        }
    }

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

    /**
     * Crée une étagère **et y range aussitôt le roman**, depuis la feuille d'actions.
     *
     * <p>Distincte de [createShelf], qui sert au bouton d'en-tête : là on crée une
     * étagère pour elle-même. Ici on la crée POUR y mettre ce roman — la laisser vide
     * obligerait à rouvrir la feuille pour cocher la case qu'on vient de créer.
     */
    fun createShelfAndAdd(novelId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val created = categoryRepo.create(trimmed)) {
                is ApiResult.Success -> when (val filled = categoryRepo.addNovel(created.data.id, novelId)) {
                    // La réponse de `addNovel` contient déjà l'étagère avec son roman :
                    // on l'ajoute telle quelle, sans rechargement.
                    is ApiResult.Success -> _state.update { it.copy(categories = it.categories + filled.data) }
                    // L'étagère existe, mais vide : mieux vaut l'afficher que la perdre.
                    is ApiResult.Error -> _state.update { it.copy(categories = it.categories + created.data) }
                }
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
