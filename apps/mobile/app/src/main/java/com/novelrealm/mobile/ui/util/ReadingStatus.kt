package com.novelrealm.mobile.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Statuts de lecture d'un roman suivi (`ReadingStatus` côté back).
 *
 * Regroupés ici parce que la bibliothèque ET la fiche d'un roman doivent afficher les
 * mêmes libellés, icônes et ordre — les dupliquer, c'était les voir diverger.
 */
enum class ReadingStatus(
    val id: String,
    val label: String,
    val icon: ImageVector,
) {
    PLAN_TO_READ("PLAN_TO_READ", "À lire", Icons.Filled.Bookmarks),
    READING("READING", "En cours", Icons.AutoMirrored.Filled.MenuBook),
    PAUSED("PAUSED", "En pause", Icons.Filled.PauseCircle),
    COMPLETED("COMPLETED", "Terminé", Icons.Filled.TaskAlt);

    companion object {
        fun fromId(id: String?): ReadingStatus? = entries.firstOrNull { it.id == id }

        /** Libellé affichable d'un statut brut venu du serveur. */
        fun labelOf(id: String?): String = fromId(id)?.label ?: "Suivi"
    }
}
