package com.novelrealm.mobile.data.remote

import com.novelrealm.mobile.BuildConfig

// Résout une URL d'image renvoyée par le back. Les couvertures / avatars peuvent être :
//  - relatifs au serveur (« /uploads/... ») → on préfixe par la base de l'API ;
//  - déjà absolus (« http... », ex. couverture externe d'un scraper) → renvoyés tels quels.
// Les fichiers /uploads/** sont publics côté back : aucune authentification requise pour les charger.
fun resolveImageUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val base = BuildConfig.BASE_URL.trimEnd('/')
    val suffix = if (path.startsWith("/")) path else "/$path"
    return base + suffix
}
