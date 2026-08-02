package com.novelrealm.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.novelrealm.mobile.data.local.AccentId
import com.novelrealm.mobile.data.local.ThemeMode

/**
 * Thème NovelRealm : neutres de la marque + accent choisi par l'utilisateur.
 *
 * La couleur dynamique (Material You) est volontairement absente : on veut l'identité
 * NovelRealm, pas les couleurs du fond d'écran de l'appareil. En revanche l'accent est
 * personnalisable (6 teintes, mêmes que le web) depuis Profil › Apparence.
 */
@Composable
fun NovelRealmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentId = AccentId.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) darkSchemeFor(accent) else lightSchemeFor(accent)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private fun darkSchemeFor(accent: AccentId) = with(accent.palette) {
    darkColorScheme(
        primary = light,                       // teinte 400 : lisible sur fond sombre
        onPrimary = Color(0xFF17171B),
        primaryContainer = active,
        onPrimaryContainer = Color.White,
        secondary = base,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFF2A2A31),
        onSecondaryContainer = OnDark,
        tertiary = light,
        background = NovelBackgroundDark,
        onBackground = OnDark,
        surface = NovelSurfaceDark,
        onSurface = OnDark,
        surfaceVariant = NovelSurfaceVariantDark,
        onSurfaceVariant = Color(0xFFA1A1AA),
        outlineVariant = Color(0xFF3F3F46),
    )
}

private fun lightSchemeFor(accent: AccentId) = with(accent.palette) {
    lightColorScheme(
        primary = base,
        onPrimary = Color.White,
        primaryContainer = container,
        onPrimaryContainer = active,
        secondary = active,
        onSecondary = Color.White,
        secondaryContainer = container,
        onSecondaryContainer = active,
        tertiary = active,
        background = NovelBackgroundLight,
        onBackground = OnLight,
        surface = NovelSurfaceLight,
        onSurface = OnLight,
        surfaceVariant = NovelSurfaceVariantLight,
        onSurfaceVariant = Color(0xFF71717A),
        outlineVariant = Color(0xFFE4E4E7),
    )
}
