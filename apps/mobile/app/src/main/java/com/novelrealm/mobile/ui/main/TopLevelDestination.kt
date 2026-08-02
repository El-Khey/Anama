package com.novelrealm.mobile.ui.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

// Les 4 onglets de la barre de navigation du bas (#34), inspirés de Mihon
// (Library / Browse / History / More) adaptés à NovelRealm. L'ordre est celui de
// l'affichage, et le PREMIER sert de destination de départ. `route` sert de clé
// au NavHost.
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Library(route = "library", label = "Bibliothèque", icon = Icons.Filled.CollectionsBookmark),
    Explore(route = "explore", label = "Explorer", icon = Icons.Filled.Explore),
    History(route = "history", label = "Historique", icon = Icons.Filled.History),
    Profile(route = "profile", label = "Profil", icon = Icons.Filled.Person),
}
