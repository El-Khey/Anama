package com.novelrealm.mobile.ui.profile

// Routes des écrans de réglages, ouverts en plein écran par-dessus la barre d'onglets
// (schéma Mihon). Regroupées ici pour éviter les chaînes en dur des deux côtés de la
// navigation (hub → AppNavHost).
object SettingsRoutes {
    const val EDIT_PROFILE = "settings/profile"
    const val ACCOUNT = "settings/account"
    const val APPEARANCE = "settings/appearance"
    const val READER = "settings/reader"

    // Pas un réglage, mais ouvert depuis l'onglet Profil par le même mécanisme :
    // la collection de citations (#41, §3).
    const val MY_QUOTES = "me/quotes"

    // Tous ses commentaires, au même endroit (issue #45, §4).
    const val MY_COMMENTS = "me/comments"
}
