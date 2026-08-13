package com.novelrealm.mobile.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Le badge de la cloche (issue #45, §3) — un seul nombre, rafraîchi quand
 * l'écran qui porte la cloche revient à l'écran. Pas de polling : un COUNT à
 * chaque retour sur l'onglet suffit largement pour un badge, et le serveur
 * n'est pas martelé toutes les deux secondes.
 */
class NotificationBellViewModel : ViewModel() {

    private val notificationRepo = ServiceLocator.notificationRepository

    private val _unreadCount = MutableStateFlow(0L)
    val unreadCount: StateFlow<Long> = _unreadCount.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            (notificationRepo.unreadCount() as? ApiResult.Success)?.let { result ->
                _unreadCount.value = result.data
            }
            // Une erreur laisse l'ancien badge : mieux vaut un chiffre périmé
            // qu'un badge qui clignote au gré du réseau.
        }
    }
}
