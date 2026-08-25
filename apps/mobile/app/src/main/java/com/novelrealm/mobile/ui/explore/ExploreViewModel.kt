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
    // ── Vitrine éditoriale (visible uniquement en navigation libre) ─────────────
    // Chargées une fois à l'ouverture, indépendamment de la grille filtrée : le
    // héros et les deux rangées donnent à l'onglet une entrée « magazine » plutôt
    // qu'un mur de vignettes. Elles s'effacent dès qu'on cherche ou qu'on filtre.
    /**
     * Les romans du **carrousel à la une** (les plus suivis en tête), défilés en héros.
     * `featured` = le premier d'entre eux, gardé à part pour les tests d'affichage.
     */
    val featuredList: List<NovelDto> = emptyList(),
    /** Rangée horizontale « Nouveautés » (tri par date). */
    val recentRow: List<NovelDto> = emptyList(),
    /** Rangée horizontale « Populaires » (tri par nombre de lecteurs). */
    val popularRow: List<NovelDto> = emptyList(),
) {
    /** Le premier roman à la une — pratique pour trancher l'affichage de la vitrine. */
    val featured: NovelDto? get() = featuredList.firstOrNull()

    /** Un filtre est actif dès qu'on s'écarte de la vue par défaut (recherche comprise). */
    val filtersActive: Boolean
        get() = query.isNotBlank() ||
            selectedGenreId != null ||
            status != ExploreStatus.ALL ||
            sort != ExploreSort.DEFAULT

    /**
     * La vitrine éditoriale (héros + rangées) ne s'affiche qu'en navigation LIBRE : dès
     * qu'un filtre mord, l'écran se replie sur la grille de résultats seule — le héros et
     * les carrousels y seraient du bruit entre la requête et sa réponse.
     */
    val showEditorial: Boolean
        get() = !filtersActive && featured != null

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
        loadEditorial()
    }

    /**
     * Charge la vitrine éditoriale : le héros et les deux rangées horizontales.
     *
     * <p>Deux appels indépendants de la grille filtrée — un tri par date, un tri par
     * popularité —, faits une seule fois à l'ouverture. Ils ne dépendent d'aucun filtre :
     * la vitrine est la même quoi qu'on cherche ensuite (elle disparaît alors de l'écran,
     * mais on la garde en mémoire pour la re-montrer instantanément quand on efface tout).
     *
     * <p>Le carrousel à la une prend les premiers « populaires » (ce qui « marche » mérite
     * la vedette) ; si la popularité ne renvoie rien, on retombe sur les récents. Tolérant
     * à l'échec : une rangée vide se cache toute seule, elle ne bloque jamais le catalogue.
     */
    private fun loadEditorial() {
        viewModelScope.launch {
            val recent = (repository.getNovels(page = 0, sort = ExploreSort.RECENT.id)
                as? ApiResult.Success)?.data?.content.orEmpty().distinctBy { it.id }
            val popular = (repository.getNovels(page = 0, sort = ExploreSort.POPULARITY.id)
                as? ApiResult.Success)?.data?.content.orEmpty().distinctBy { it.id }
            // Jusqu'à cinq romans à la une pour le carrousel : assez pour qu'il vive, pas
            // trop pour qu'on en fasse le tour. On part des populaires, complétés par les
            // récents si la liste est courte.
            val featured = (popular + recent).distinctBy { it.id }.take(FEATURED_COUNT)
            val featuredIds = featured.map { it.id }.toSet()
            _state.update { s ->
                s.copy(
                    featuredList = featured,
                    // On sort les romans à la une des rangées : les revoir juste dessous,
                    // en petit, ferait doublon.
                    recentRow = recent.filter { it.id !in featuredIds },
                    popularRow = popular.filter { it.id !in featuredIds },
                )
            }
        }
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

    /**
     * Suit ou arrête de suivre un roman depuis sa carte, sans ouvrir sa fiche.
     *
     * <p>Le statut initial est laissé au serveur, qui applique `PLAN_TO_READ` : un cœur
     * touché dans le catalogue veut dire « je le garde pour plus tard », pas « je le lis ».
     * Choisir un autre statut reste possible depuis la fiche ou la bibliothèque.
     *
     * <p>Bascule **optimiste** : le cœur se remplit à l'instant. Un aller-retour réseau
     * avant de réagir se sentirait à chaque appui, et l'échec est rattrapé en rechargeant
     * la vérité du serveur.
     */
    fun toggleLibrary(novelId: Long) {
        val wasIn = novelId in _state.value.libraryNovelIds
        _state.update { s ->
            s.copy(
                libraryNovelIds = if (wasIn) s.libraryNovelIds - novelId
                else s.libraryNovelIds + novelId,
            )
        }
        viewModelScope.launch {
            // Les deux routes ne renvoient pas le même type : chaque appel est testé sur
            // place plutôt que de chercher un supertype commun aux deux résultats.
            val failed = if (wasIn) {
                libraryRepo.remove(novelId) is ApiResult.Error
            } else {
                libraryRepo.add(novelId) is ApiResult.Error
            }
            if (failed) refreshLibraryFlags()
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

    private companion object {
        /** Nombre de romans dans le carrousel à la une. */
        const val FEATURED_COUNT = 5
    }
}
