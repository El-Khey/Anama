package com.novelrealm.mobile.ui.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelrealm.mobile.data.local.ReaderFont
import com.novelrealm.mobile.data.local.ReaderPrefs
import com.novelrealm.mobile.data.local.ReaderTheme

/**
 * Traduit les préférences de lecture en valeurs d'affichage concrètes (couleurs,
 * typographie, espacements). Mutualisé entre le lecteur et son écran de réglages, pour
 * que l'aperçu montre exactement ce que l'on obtiendra en lisant.
 */
data class ReaderStyle(
    val background: Color,
    val foreground: Color,
    val textStyle: TextStyle,
    val paragraphSpacing: Dp,
    /** Marge latérale du texte ; 0 dp = pleine largeur d'écran. */
    val horizontalPadding: Dp,
)

/** Couleurs d'ambiance du lecteur. « Clair » et « Sombre » suivent le thème de l'app. */
@Composable
fun readerColors(theme: ReaderTheme): Pair<Color, Color> = when (theme) {
    ReaderTheme.LIGHT -> Color(0xFFFDFDFD) to Color(0xFF1A1A1E)
    ReaderTheme.SEPIA -> Color(0xFFF4ECD8) to Color(0xFF433422)
    ReaderTheme.DARK -> Color(0xFF17171B) to Color(0xFFD6D6DB)
    ReaderTheme.OLED -> Color(0xFF000000) to Color(0xFFBFBFC6)
}

private val ReaderFont.fontFamily: FontFamily
    get() = when (this) {
        ReaderFont.SANS -> FontFamily.SansSerif
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.MONO -> FontFamily.Monospace
    }

/** Construit le style complet du corps de texte à partir des préférences. */
@Composable
fun rememberReaderStyle(prefs: ReaderPrefs): ReaderStyle {
    val (bg, fg) = readerColors(prefs.theme)
    // L'écart entre paragraphes est un multiple de la taille du texte (« em »). Il doit
    // donc être calculé à partir de sp — et non de dp — pour suivre l'agrandissement de
    // police réglé dans Android : sinon l'espacement se décale du texte chez les
    // utilisateurs qui grossissent la police du système.
    val paragraphSpacing = with(LocalDensity.current) {
        (prefs.fontSize * prefs.paragraphGap).sp.toDp()
    }
    return ReaderStyle(
        background = bg,
        foreground = fg,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = prefs.fontSize.sp,
            // L'interligne est un multiplicateur (comme sur le web) → converti en sp.
            lineHeight = (prefs.fontSize * prefs.lineHeight).sp,
            fontFamily = prefs.font.fontFamily,
            textAlign = if (prefs.justify) TextAlign.Justify else TextAlign.Start,
            letterSpacing = 0.sp,
        ),
        paragraphSpacing = paragraphSpacing,
        horizontalPadding = prefs.margin.dp,
    )
}
