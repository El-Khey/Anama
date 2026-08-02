package com.novelrealm.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import com.novelrealm.mobile.data.local.AccentId

// ── Identité NovelRealm (couleurs alignées sur le front web) ──────────
val NovelRose = Color(0xFFF43F5E)               // --primary (rose 500) : accent de marque
val NovelRoseActive = Color(0xFFE11D48)         // --primary-active (rose 600) : pressed / plus foncé
val NovelRoseLight = Color(0xFFFB7185)          // rose 400 : accent clair (thème sombre)
val NovelRoseContainerLight = Color(0xFFFFD9DE) // conteneur rosé doux (thème clair)

// Neutres sombres (le dark UI du web : quasi-noir + surfaces gris foncé)
val NovelBackgroundDark = Color(0xFF0A0A0C)
val NovelSurfaceDark = Color(0xFF17171B)
val NovelSurfaceVariantDark = Color(0xFF232329)

// Neutres clairs
val NovelBackgroundLight = Color(0xFFFAFAFA)
val NovelSurfaceLight = Color(0xFFFFFFFF)
val NovelSurfaceVariantLight = Color(0xFFF4F4F5)

// Textes / icônes sur fonds neutres
val OnDark = Color(0xFFF4F4F5)
val OnLight = Color(0xFF0A0A0C)

/**
 * Nuancier d'un accent : les 4 teintes dont Material a besoin. Les valeurs suivent
 * l'échelle Tailwind (400/500/600) utilisée par le web, pour que les deux plateformes
 * rendent la même couleur.
 */
data class AccentPalette(
    val base: Color,        // teinte 500 — couleur principale
    val active: Color,      // teinte 600 — état pressé / contraste sur fond clair
    val light: Color,       // teinte 400 — version lisible sur fond sombre
    val container: Color,   // conteneur pastel (fond de puce, badge…)
)

/** Nuancier associé à chaque accent proposé dans les réglages d'apparence. */
val AccentId.palette: AccentPalette
    get() = when (this) {
        AccentId.CRIMSON -> AccentPalette(
            base = Color(0xFFF43F5E), active = Color(0xFFE11D48),
            light = Color(0xFFFB7185), container = Color(0xFFFFD9DE),
        )
        AccentId.EMBER -> AccentPalette(
            base = Color(0xFFF97316), active = Color(0xFFEA580C),
            light = Color(0xFFFB923C), container = Color(0xFFFFE0C7),
        )
        AccentId.AMBER -> AccentPalette(
            base = Color(0xFFF59E0B), active = Color(0xFFD97706),
            light = Color(0xFFFBBF24), container = Color(0xFFFCEBC4),
        )
        AccentId.EMERALD -> AccentPalette(
            base = Color(0xFF10B981), active = Color(0xFF059669),
            light = Color(0xFF34D399), container = Color(0xFFC9F2E2),
        )
        AccentId.AZURE -> AccentPalette(
            base = Color(0xFF3B82F6), active = Color(0xFF2563EB),
            light = Color(0xFF60A5FA), container = Color(0xFFD4E3FF),
        )
        AccentId.VIOLET -> AccentPalette(
            base = Color(0xFF8B5CF6), active = Color(0xFF7C3AED),
            light = Color(0xFFA78BFA), container = Color(0xFFE6DCFD),
        )
    }
