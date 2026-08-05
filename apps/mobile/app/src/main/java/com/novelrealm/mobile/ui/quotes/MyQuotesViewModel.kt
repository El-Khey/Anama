package com.novelrealm.mobile.ui.quotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.NovelQuoteCountDto
import com.novelrealm.mobile.data.remote.dto.QuoteDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Délai d'attente avant de lancer une recherche, pour ne pas interroger à chaque touche. */
private const val SearchDebounceMillis = 350L

/**
 * Ordre d'affichage de la collection.
 *
 * <p>Deux entrées seulement, et c'est voulu : une collection de citations se parcourt
 * dans le temps. Trier par roman ferait doublon avec le filtre par roman, et trier par
 * longueur ne répond à aucune question qu'on se pose vraiment.
 */
enum class QuoteSort(val apiValue: String, val label: String) {
    Recent("recent", "Plus récentes d'abord"),
    Oldest("oldest", "Plus anciennes d'abord"),
}

/**
 * Fenêtre de temps retenue. Exprimée en **jours glissants** plutôt qu'en mois
 * calendaires : « ce mois-ci » ne veut rien dire le 1er du mois, et dépendrait du
 * fuseau horaire de l'appareil. Un nombre de jours se calcule pareil partout.
 */
enum class QuotePeriod(val days: Int, val label: String) {
    All(0, "Depuis toujours"),
    Week(7, "7 derniers jours"),
    Month(30, "30 derniers jours"),
    Year(365, "12 derniers mois"),
}

/** Où le lecteur doit s'ouvrir après « Aller au passage ». */
data class PassageTarget(
    val novelId: Long,
    val chapterId: Long,
    val blockIndex: Int,
)

data class MyQuotesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val quotes: List<QuoteDto> = emptyList(),
    /** Nombre de citations par roman — alimente le filtre par roman. */
    val counts: List<NovelQuoteCountDto> = emptyList(),
    val selectedNovelId: Long? = null,
    val sort: QuoteSort = QuoteSort.Recent,
    val period: QuotePeriod = QuotePeriod.All,
    val search: String = "",
    /** Le panneau de filtres est-il déployé. */
    val filtersOpen: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val total: Long = 0,
    /** Erreur d'une action ponctuelle — n'efface pas la collection affichée. */
    val actionError: String? = null,
    /** Passage à ouvrir ; consommé par l'écran puis remis à null. */
    val navigateTo: PassageTarget? = null,
) {
    /**
     * Nombre de critères écartés de leur valeur par défaut. Sert de pastille sur le
     * bouton « Filtrer » : sans elle, un filtre actif oublié fait croire à une
     * collection vide.
     *
     * <p>La recherche n'y figure pas — elle a son propre champ, toujours visible.
     */
    val activeFilterCount: Int
        get() = listOf(
            selectedNovelId != null,
            sort != QuoteSort.Recent,
            period != QuotePeriod.All,
        ).count { it }

    /** Y a-t-il de quoi expliquer une liste vide autrement que par « rien capturé ». */
    val isFiltered: Boolean get() = activeFilterCount > 0 || search.isNotBlank()

    /** Le roman actuellement filtré, s'il en reste trace dans les compteurs. */
    val selectedNovel: NovelQuoteCountDto?
        get() = counts.firstOrNull { it.novelId == selectedNovelId }
}

/**
 * Collection personnelle de citations (#41, §3).
 *
 * <p>C'est elle qui donne son intérêt au geste de citer : sans endroit où les
 * retrouver, capturer une phrase ne sert à rien.
 *
 * <p><b>Tous les critères partent au serveur.</b> La collection est paginée ; filtrer
 * ou trier la page reçue ne porterait que sur elle. Chaque changement de critère
 * relance donc la liste depuis la première page.
 */
class MyQuotesViewModel : ViewModel() {

    private val quoteRepo = ServiceLocator.quoteRepository

    private val _state = MutableStateFlow(MyQuotesUiState())
    val state: StateFlow<MyQuotesUiState> = _state.asStateFlow()

    private var nextPage = 0
    private var searchJob: Job? = null

    init {
        load()
        refreshCounts()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null, endReached = false) }
        nextPage = 0
        val current = _state.value
        viewModelScope.launch {
            when (
                val page = quoteRepo.list(
                    novelId = current.selectedNovelId,
                    query = current.search,
                    sort = current.sort.apiValue,
                    days = current.period.days,
                    page = 0,
                )
            ) {
                is ApiResult.Success -> {
                    nextPage = 1
                    _state.update {
                        it.copy(
                            isLoading = false,
                            quotes = page.data.content,
                            total = page.data.totalElements,
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
            when (
                val page = quoteRepo.list(
                    novelId = current.selectedNovelId,
                    query = current.search,
                    sort = current.sort.apiValue,
                    days = current.period.days,
                    page = nextPage,
                )
            ) {
                is ApiResult.Success -> {
                    nextPage += 1
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            quotes = (it.quotes + page.data.content).distinctBy { q -> q.id },
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoadingMore = false, actionError = page.userMessage())
                }
            }
        }
    }

    /**
     * La recherche part côté serveur, mais après une pause : sans elle, taper
     * « lumière » déclencherait sept requêtes dont six inutiles.
     */
    fun setSearch(search: String) {
        _state.update { it.copy(search = search) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SearchDebounceMillis)
            load()
        }
    }

    fun selectNovel(novelId: Long?) {
        if (novelId == _state.value.selectedNovelId) return
        _state.update { it.copy(selectedNovelId = novelId) }
        load()
    }

    fun setSort(sort: QuoteSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        load()
    }

    fun setPeriod(period: QuotePeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        load()
    }

    fun openFilters() = _state.update { it.copy(filtersOpen = true) }

    fun closeFilters() = _state.update { it.copy(filtersOpen = false) }

    /** Remet les trois critères du panneau à zéro. La recherche, elle, n'y est pas. */
    fun resetFilters() {
        val current = _state.value
        if (current.activeFilterCount == 0) return
        _state.update {
            it.copy(
                selectedNovelId = null,
                sort = QuoteSort.Recent,
                period = QuotePeriod.All,
            )
        }
        load()
    }

    /** Sortie de secours d'une liste vide : efface tout, panneau compris. */
    fun clearAll() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                selectedNovelId = null,
                sort = QuoteSort.Recent,
                period = QuotePeriod.All,
                search = "",
            )
        }
        load()
    }

    fun delete(quote: QuoteDto) {
        viewModelScope.launch {
            when (val result = quoteRepo.delete(quote.id)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            quotes = it.quotes.filterNot { q -> q.id == quote.id },
                            total = (it.total - 1).coerceAtLeast(0),
                        )
                    }
                    refreshCounts()
                }
                is ApiResult.Error -> _state.update {
                    it.copy(actionError = result.userMessage())
                }
            }
        }
    }

    /**
     * « Aller au passage » : demande au serveur où se trouve le bloc aujourd'hui.
     *
     * <p>C'est le seul appel qui fait relire le chapitre entier, d'où son
     * déclenchement à la demande. Si l'ancre est morte — chapitre réécrit — on le dit
     * plutôt que d'ouvrir le lecteur n'importe où.
     */
    fun openPassage(quote: QuoteDto) {
        viewModelScope.launch {
            when (val result = quoteRepo.anchor(quote.id)) {
                is ApiResult.Success -> {
                    val anchor = result.data
                    if (anchor.alive) {
                        _state.update {
                            it.copy(
                                navigateTo = PassageTarget(
                                    novelId = anchor.novelId,
                                    chapterId = anchor.chapterId,
                                    blockIndex = anchor.blockIndex,
                                ),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                actionError = "Ce passage n'existe plus dans le chapitre — " +
                                    "la citation, elle, reste ici.",
                            )
                        }
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(actionError = result.userMessage())
                }
            }
        }
    }

    fun navigationHandled() = _state.update { it.copy(navigateTo = null) }

    fun actionErrorShown() = _state.update { it.copy(actionError = null) }

    /**
     * Les compteurs par roman ne sont **pas** filtrés : ils peuplent la liste du
     * panneau. S'ils suivaient le filtre courant, choisir un roman ferait disparaître
     * tous les autres et il deviendrait impossible d'en changer.
     */
    private fun refreshCounts() {
        viewModelScope.launch {
            (quoteRepo.counts() as? ApiResult.Success)?.let { result ->
                _state.update { it.copy(counts = result.data) }
            }
        }
    }
}
