package com.novelrealm.mobile.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelrealm.mobile.data.local.ReaderFont
import com.novelrealm.mobile.data.local.ReaderPrefs
import com.novelrealm.mobile.data.local.ReaderTheme
import com.novelrealm.mobile.ui.components.SegmentedChoice
import kotlin.math.roundToInt

/**
 * Réglages de lecture accessibles **sans quitter le chapitre** : ambiance, typographie,
 * espacements et confort. Ce sont exactement les mêmes préférences que l'écran
 * Profil › Lecture — un seul et même stockage, donc un réglage fait ici se retrouve là,
 * et se synchronise avec le compte comme le reste.
 *
 * Écrit à la main plutôt qu'avec `ModalBottomSheet` : cette API de Material 3 est encore
 * expérimentale et sa signature bouge d'une version à l'autre, ce que le décalage entre
 * notre BOM Compose et les libs `lifecycle`/`activity` rend risqué (on a déjà eu un
 * `NoSuchMethodError` de ce genre). Un `Surface` posé en bas donne le même résultat.
 */
@Composable
fun ReaderSettingsSheet(
    prefs: ReaderPrefs,
    onUpdate: ((ReaderPrefs) -> ReaderPrefs) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                // Plafonnée : sur un petit écran le panneau ne doit pas recouvrir tout le
                // texte, sinon on règle à l'aveugle.
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 20.dp),
        ) {
            // Poignée : signale que le panneau se ferme (tap à côté ou sur l'engrenage).
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            ) {
                Text(
                    text = "Réglages de lecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset, enabled = prefs != ReaderPrefs.DEFAULT) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Défaut", style = MaterialTheme.typography.labelLarge)
                }
            }

            SheetLabel("Ambiance")
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                ReaderTheme.entries.forEach { theme ->
                    ThemeSwatch(
                        theme = theme,
                        selected = prefs.theme == theme,
                        onClick = { onUpdate { it.copy(theme = theme) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SheetLabel("Police")
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                SegmentedChoice(
                    options = ReaderFont.entries.toList(),
                    selected = prefs.font,
                    onSelect = { font -> onUpdate { it.copy(font = font) } },
                    label = { it.label },
                )
            }

            SheetLabel("Mise en page")
            SheetSlider(
                label = "Taille",
                valueLabel = "${prefs.fontSize}",
                value = prefs.fontSize.toFloat(),
                range = ReaderPrefs.FONT_SIZE_RANGE,
                steps = 15,
                onValueChange = { v -> onUpdate { it.copy(fontSize = v.roundToInt()) } },
            )
            SheetSlider(
                label = "Interligne",
                valueLabel = String.format("%.1f", prefs.lineHeight),
                value = prefs.lineHeight,
                range = ReaderPrefs.LINE_HEIGHT_RANGE,
                steps = 10,
                onValueChange = { v -> onUpdate { it.copy(lineHeight = (v * 10).roundToInt() / 10f) } },
            )
            SheetSlider(
                label = "Paragraphes",
                valueLabel = String.format("%.1f", prefs.paragraphGap),
                value = prefs.paragraphGap,
                range = ReaderPrefs.PARAGRAPH_GAP_RANGE,
                steps = 9,
                onValueChange = { v -> onUpdate { it.copy(paragraphGap = (v * 10).roundToInt() / 10f) } },
            )
            SheetSlider(
                label = "Marges",
                valueLabel = if (prefs.margin == 0) "Aucune" else "${prefs.margin}",
                value = prefs.margin.toFloat(),
                range = ReaderPrefs.MARGIN_RANGE,
                steps = 19,
                onValueChange = { v -> onUpdate { it.copy(margin = v.roundToInt()) } },
            )

            SheetLabel("Confort")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                ToggleChip(
                    icon = Icons.Filled.FormatAlignJustify,
                    label = "Justifié",
                    active = prefs.justify,
                    onClick = { onUpdate { it.copy(justify = !it.justify) } },
                    modifier = Modifier.weight(1f),
                )
                ToggleChip(
                    icon = Icons.Filled.Visibility,
                    label = "Écran allumé",
                    active = prefs.keepScreenOn,
                    onClick = { onUpdate { it.copy(keepScreenOn = !it.keepScreenOn) } },
                    modifier = Modifier.weight(1f),
                )
                ToggleChip(
                    icon = Icons.Filled.Fullscreen,
                    label = "Plein écran",
                    active = prefs.fullscreen,
                    onClick = { onUpdate { it.copy(fullscreen = !it.fullscreen) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
    )
}

/** Curseur compact : libellé à gauche, valeur en pastille, curseur en dessous. */
@Composable
private fun SheetSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
        )
    }
}

/** Vignette d'ambiance : le vrai couple fond / texte, avec « Aa ». */
@Composable
private fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = readerColors(theme)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(13.dp),
                )
                .clickable(onClick = onClick),
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Sélectionné",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bouton bascule compact — plus lisible que trois interrupteurs empilés dans un panneau. */
@Composable
private fun ToggleChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = if (active) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(13.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
