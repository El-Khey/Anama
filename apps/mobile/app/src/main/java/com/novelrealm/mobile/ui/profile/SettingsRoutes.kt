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

    // « Activité » (issue #45, §3 et §4) : volontairement LA MÊME route que la
    // cloche de la bibliothèque. Une page unique, deux portes — un second chemin
    // vers un second écran finirait par diverger de celui-ci.
    const val ACTIVITY = "notifications"
}
