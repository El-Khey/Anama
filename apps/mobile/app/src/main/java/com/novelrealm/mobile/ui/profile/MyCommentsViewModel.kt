package com.novelrealm.mobile.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.MyCommentDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MyCommentsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val comments: List<MyCommentDto> = emptyList(),
    val total: Long = 0,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    /** Échec d'une action ponctuelle (suppression) — n'efface pas la liste. */
    val actionError: String? = null,
)

/**
 * « Mes commentaires » (issue #45, §4) : le flux unifié servi par le back —
 * fin de chapitre et passages déjà fusionnés et triés, l'app ne fait qu'afficher.
 *
 * La suppression passe par la route du BON type ({@code kind}) : les deux
 * familles de commentaires ne vivent pas dans la même table côté serveur, et un
 * message de passage se supprime pour de bon quand un message de chapitre passe
 * en pierre tombale. Dans les deux cas, il sort simplement de cette liste.
 */
class MyCommentsViewModel : ViewModel() {

    private val userRepo = ServiceLocator.userRepository
    private val commentRepo = ServiceLocator.commentRepository
    private val passageRepo = ServiceLocator.passageRepository

    private val _state = MutableStateFlow(MyCommentsUiState())
    val state: StateFlow<MyCommentsUiState> = _state.asStateFlow()

    private var nextPage = 0

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null, endReached = false) }
        nextPage = 0
        viewModelScope.launch {
            when (val page = userRepo.getMyComments(page = 0)) {
                is ApiResult.Success -> {
                    nextPage = 1
                    _state.update {
                        it.copy(
                            isLoading = false,
                            comments = page.data.content,
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
            when (val page = userRepo.getMyComments(page = nextPage)) {
                is ApiResult.Success -> {
                    nextPage += 1
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            comments = (it.comments + page.data.content)
                                .distinctBy { c -> "${c.kind}-${c.id}" },
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    /** Supprime un message — par la route de SON type — puis le retire de la liste. */
    fun delete(comment: MyCommentDto) {
        viewModelScope.launch {
            val result = if (comment.isPassage) {
                passageRepo.delete(comment.id)
            } else {
                commentRepo.delete(comment.id)
            }
            when (result) {
                is ApiResult.Success -> _state.update { state ->
                    state.copy(
                        comments = state.comments.filterNot {
                            it.kind == comment.kind && it.id == comment.id
                        },
                        total = (state.total - 1).coerceAtLeast(0),
                        actionError = null,
                    )
                }
                is ApiResult.Error -> _state.update {
                    it.copy(actionError = result.userMessage())
                }
            }
        }
    }

    fun actionErrorShown() = _state.update { it.copy(actionError = null) }
}
