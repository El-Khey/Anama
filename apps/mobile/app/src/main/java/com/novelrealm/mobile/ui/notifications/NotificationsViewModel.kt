package com.novelrealm.mobile.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.NotificationDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val notifications: List<NotificationDto> = emptyList(),
    /** N'afficher que les non-lues (filtre de l'en-tête). */
    val unreadOnly: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val unreadCount: Long = 0,
)

/**
 * La cloche (issue #45, §3) : liste paginée, filtre non-lues, marquage lu.
 *
 * Ouvrir une notification la marque comme lue **localement d'abord** : le point
 * s'éteint sous le doigt, le serveur suit. Un échec silencieux laisse au pire un
 * point rallumé au prochain chargement — moins grave qu'une liste qui attend le
 * réseau pour réagir.
 */
class NotificationsViewModel : ViewModel() {

    private val notificationRepo = ServiceLocator.notificationRepository

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var nextPage = 0

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, error = null, endReached = false) }
        nextPage = 0
        viewModelScope.launch {
            val unreadOnly = _state.value.unreadOnly
            when (val page = notificationRepo.list(unreadOnly, page = 0)) {
                is ApiResult.Success -> {
                    nextPage = 1
                    _state.update {
                        it.copy(
                            isLoading = false,
                            notifications = page.data.content,
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update {
                    it.copy(isLoading = false, error = page.userMessage())
                }
            }
            refreshUnreadCount()
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val page = notificationRepo.list(current.unreadOnly, page = nextPage)) {
                is ApiResult.Success -> {
                    nextPage += 1
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            notifications = (it.notifications + page.data.content)
                                .distinctBy { n -> n.id },
                            endReached = page.data.page >= page.data.totalPages - 1,
                        )
                    }
                }
                is ApiResult.Error -> _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setUnreadOnly(unreadOnly: Boolean) {
        if (_state.value.unreadOnly == unreadOnly) return
        _state.update { it.copy(unreadOnly = unreadOnly) }
        load()
    }

    /** Éteint UNE notification — localement tout de suite, serveur ensuite. */
    fun markRead(notification: NotificationDto) {
        if (notification.read) return
        _state.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.id == notification.id) it.copy(read = true) else it
                },
                unreadCount = (state.unreadCount - 1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch { notificationRepo.markRead(notification.id) }
    }

    /** Éteint toute la cloche. */
    fun markAllRead() {
        _state.update { state ->
            state.copy(
                notifications = state.notifications.map { it.copy(read = true) },
                unreadCount = 0,
            )
        }
        viewModelScope.launch { notificationRepo.markAllRead() }
    }

    private suspend fun refreshUnreadCount() {
        (notificationRepo.unreadCount() as? ApiResult.Success)?.let { result ->
            _state.update { it.copy(unreadCount = result.data) }
        }
    }
}
