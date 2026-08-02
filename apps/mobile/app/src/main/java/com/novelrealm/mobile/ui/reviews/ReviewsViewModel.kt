package com.novelrealm.mobile.ui.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.ReviewDto
import com.novelrealm.mobile.data.remote.dto.ReviewSummaryDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val summary: ReviewSummaryDto? = null,
    val reviews: List<ReviewDto> = emptyList(),
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val myReview: ReviewDto? = null,
    val myRating: Int = 0,
    val myBody: String = "",
    val isSaving: Boolean = false,
)

// Avis d'un roman (#35) : résumé (moyenne + histogramme), son propre avis (upsert /
// suppression) et liste paginée des avis des lecteurs.
class ReviewsViewModel(private val novelId: Long) : ViewModel() {

    private val reviewRepo = ServiceLocator.reviewRepository

    private val _state = MutableStateFlow(ReviewsUiState())
    val state: StateFlow<ReviewsUiState> = _state.asStateFlow()

    private var nextPage = 0

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null, endReached = false) }
        nextPage = 0
        viewModelScope.launch {
            val summaryDef = async { reviewRepo.getSummary(novelId) }
            val mineDef = async { reviewRepo.getMyReview(novelId) }
            val pageDef = async { reviewRepo.getReviews(novelId, page = 0) }

            val summary = (summaryDef.await() as? ApiResult.Success)?.data
            val mine = (mineDef.await() as? ApiResult.Success)?.data   // Error(404) = pas d'avis

            when (val page = pageDef.await()) {
                is ApiResult.Success -> {
                    nextPage = 1
                    _state.update {
                        it.copy(
                            isLoading = false,
                            summary = summary,
                            myReview = mine,
                            myRating = mine?.rating ?: it.myRating,
                            myBody = mine?.body ?: it.myBody,
                            reviews = page.data.content,
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
            when (val page = reviewRepo.getReviews(novelId, page = nextPage)) {
                is ApiResult.Success -> {
                    nextPage += 1
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            reviews = it.reviews + page.data.content,
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setMyRating(rating: Int) = _state.update { it.copy(myRating = rating) }

    fun setMyBody(body: String) = _state.update { it.copy(myBody = body) }

    fun saveMyReview() {
        val current = _state.value
        if (current.myRating !in 1..5 || current.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val body = current.myBody.trim().ifBlank { null }
            when (val result = reviewRepo.upsertMyReview(novelId, current.myRating, body)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isSaving = false, myReview = result.data) }
                    reloadSummaryAndList()
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isSaving = false, error = result.userMessage())
                }
            }
        }
    }

    fun deleteMyReview() {
        if (_state.value.myReview == null) return
        viewModelScope.launch {
            when (reviewRepo.deleteMyReview(novelId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(myReview = null, myRating = 0, myBody = "") }
                    reloadSummaryAndList()
                }
                is ApiResult.Error -> Unit
            }
        }
    }

    private fun reloadSummaryAndList() {
        viewModelScope.launch {
            val summaryDef = async { reviewRepo.getSummary(novelId) }
            val pageDef = async { reviewRepo.getReviews(novelId, page = 0) }
            (summaryDef.await() as? ApiResult.Success)?.let { r ->
                _state.update { it.copy(summary = r.data) }
            }
            (pageDef.await() as? ApiResult.Success)?.let { r ->
                nextPage = 1
                _state.update {
                    it.copy(
                        reviews = r.data.content,
                        endReached = r.data.page >= r.data.totalPages - 1,
                    )
                }
            }
        }
    }
}
