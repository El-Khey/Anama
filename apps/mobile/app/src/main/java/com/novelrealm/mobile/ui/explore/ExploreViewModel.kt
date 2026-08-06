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

/** Tri du catalogue. Les identifiants sont ceux attendus par `GET /api/novels?sort=`. */
enum class ExploreSort(val id: String, val label: String) {
    RECENT("recent", "Récents"),
    POPULARITY("popularity", "Les plus suivis"),
    RATING("rating", "Mieux notés"),
    TITLE("title", "Titre A-Z");

    companion object {
        val DEFAULT = RECENT
        fun fromId(id: String?): ExploreSort = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Filtre de statut du roman. */
enum class ExploreStatus(val id: String?, val label: String) {
    ALL(null, "Tous"),
    ONGOING("ONGOING", "En cours"),
    COMPLETED("COMPLETED", "Terminés"),
}

data class ExploreUiState(
    val novels: List<NovelDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val query: String = "",
    val selectedGenreId: Long? = null,
    val sort: ExploreSort = ExploreSort.DEFAULT,
    val status: ExploreStatus = ExploreStatus.ALL,
    val isLoading: Boolean = false,     // chargement de la 1re page
    val isLoadingMore: Boolean = false, // pagination (pages suivantes)
    val error: String? = null,          // échec bloquant (aucun résultat affiché)
    val pageError: String? = null,      // échec d'une page suivante (contenu déjà à l'écran)
    val endReached: Boolean = false,
    val totalResults: Long = 0,
    /** Romans déjà suivis, pour marquer les cartes du catalogue. */
    val libraryNovelIds: Set<Long> = emptySet(),
) {
    /** Un filtre est actif dès qu'on s'écarte de la vue par défaut (recherche comprise). */
    val filtersActive: Boolean
        get() = query.isNotBlank() ||
            selectedGenreId != null ||
            status != ExploreStatus.ALL ||
            sort != ExploreSort.DEFAULT

    val selectedGenre: GenreDto? get() = genres.firstOrNull { it.id == selectedGenreId }
}

// Catalogue paginé (#35) : `GET /api/novels` avec recherche débouncée, filtre par genre,
// filtre par statut et tri — tous les paramètres exposés par le back.
class ExploreViewModel : ViewModel() {

    private val repository = ServiceLocator.novelRepository
    private val libraryRepo = ServiceLocator.libraryRepository

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private var nextPage = 0
    private var searchJob: Job? = null

    /** Faux tant que l'onglet n'a pas été quitté puis rouvert. Voir [refreshOnReturn]. */
    private var seenFirstComposition = false

    init {
        refresh()
        loadGenres()
        refreshLibraryFlags()
    }

    /**
     * Recharge les cœurs au **retour** sur l'onglet — et seulement là.
     *
     * <p>L'écran appelle ceci à chaque composition, la première comprise, où l'`init`
     * ci-dessus vient déjà de le faire : `GET /api/library` partait donc deux fois à
     * chaque premier affichage du catalogue.
     */
    fun refreshOnReturn() {
        if (!seenFirstComposition) {
            seenFirstComposition = true
            return
        }
        refreshLibraryFlags()
    }

    private fun loadGenres() {
        viewModelScope.launch {
            (repository.getGenres() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(genres = r.data) }
            }
        }
    }

    /**
     * Recharge la liste des romans suivis (appelée au retour sur l'onglet) : un roman
     * ajouté depuis sa fiche doit apparaître coché en revenant ici, sans quoi le cœur
     * mentirait jusqu'au prochain redémarrage.
     */
    fun refreshLibraryFlags() {
        viewModelScope.launch {
            (libraryRepo.getLibrary() as? ApiResult.Success)?.let { r ->
                _state.update { s -> s.copy(libraryNovelIds = r.data.map { it.novel.id }.toSet()) }
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
    fun onGenreSelected(genreId: Long?) {
        _state.update {
            it.copy(selectedGenreId = if (it.selectedGenreId == genreId) null else genreId)
        }
        refresh()
    }

    fun onSortSelected(sort: ExploreSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        refresh()
    }

    fun onStatusSelected(status: ExploreStatus) {
        if (_state.value.status == status) return
        _state.update { it.copy(status = status) }
        refresh()
    }

    /** Remet le catalogue à sa vue par défaut (recherche comprise). */
    fun clearFilters() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                selectedGenreId = null,
                sort = ExploreSort.DEFAULT,
                status = ExploreStatus.ALL,
            )
        }
        refresh()
    }

    fun refresh() {
        nextPage = 0
        _state.update { it.copy(isLoading = true, error = null, pageError = null, endReached = false) }
        viewModelScope.launch { load(reset = true) }
    }

    fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        _state.update { it.copy(isLoadingMore = true, pageError = null) }
        viewModelScope.launch { load(reset = false) }
    }

    private suspend fun load(reset: Boolean) {
        val snapshot = _state.value
        val result = repository.getNovels(
            page = nextPage,
            query = snapshot.query,
            genreId = snapshot.selectedGenreId,
            status = snapshot.status.id,
            sort = snapshot.sort.id,
        )
        when (result) {
            is ApiResult.Success -> {
                val page = result.data
                _state.update { state ->
                    // `distinctBy` : la pagination par offset peut renvoyer deux fois le
                    // même roman si le catalogue bouge entre deux pages, et une clé
                    // dupliquée fait planter une grille Lazy (« Key was already used »).
                    val merged = if (reset) {
                        page.content.distinctBy { it.id }
                    } else {
                        (state.novels + page.content).distinctBy { it.id }
                    }
                    state.copy(
                        novels = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        error = null,
                        pageError = null,
                        endReached = page.page >= page.totalPages - 1,
                        totalResults = page.totalElements,
                    )
                }
                nextPage += 1
            }
            // Un échec de page suivante ne doit pas effacer ce qui est déjà affiché :
            // on le signale en pied de grille, avec de quoi réessayer.
            is ApiResult.Error -> _state.update {
                if (reset) {
                    it.copy(isLoading = false, isLoadingMore = false, error = result.userMessage())
                } else {
                    it.copy(isLoading = false, isLoadingMore = false, pageError = result.userMessage())
                }
            }
        }
    }
}
