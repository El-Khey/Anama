package com.novelrealm.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelrealm.mobile.data.local.ReaderFont
import com.novelrealm.mobile.data.local.ReaderPrefs
import com.novelrealm.mobile.data.local.ReaderTheme
import com.novelrealm.mobile.di.ServiceLocator
import com.novelrealm.mobile.ui.components.SegmentedChoice
import com.novelrealm.mobile.ui.components.SettingsDivider
import com.novelrealm.mobile.ui.components.SettingsScaffold
import com.novelrealm.mobile.ui.components.SettingsSection
import com.novelrealm.mobile.ui.components.SettingsSwitchRow
import com.novelrealm.mobile.ui.reader.readerColors
import kotlin.math.roundToInt

private const val PREVIEW_TEXT =
    "Le vent se leva sur les remparts, portant avec lui l'odeur âcre de la pluie à venir. " +
        "Elle resserra sa cape et regarda l'horizon, là où les torches de l'armée ennemie " +
        "traçaient une ligne orange dans la nuit."

/**
 * Préférences de lecture. Un **aperçu figé en haut** montre en direct le rendu de
 * chaque réglage : l'utilisateur voit ce qu'il obtient sans ouvrir un chapitre.
 */
@Composable
fun ReaderSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val store = ServiceLocator.preferencesStore
    val prefs by store.state.collectAsState()
    val reader = prefs.reader
    var confirmReset by remember { mutableStateOf(false) }
    val isDefault = reader == ReaderPrefs.DEFAULT

    SettingsScaffold(
        title = "Lecture",
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = { confirmReset = true }, enabled = !isDefault) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Réinitialiser",
                    tint = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            ReaderPreview(reader)

            Spacer(Modifier.height(24.dp))
            SettingsSection(title = "Texte") {
                SliderSetting(
                    label = "Taille du texte",
                    valueLabel = "${reader.fontSize} sp",
                    value = reader.fontSize.toFloat(),
                    range = ReaderPrefs.FONT_SIZE_RANGE,
                    steps = 15,
                    onValueChange = { v ->
                        store.updateReader { it.copy(fontSize = v.roundToInt()) }
                    },
                )
                SettingsDivider()
                SliderSetting(
                    label = "Interligne",
                    valueLabel = String.format("%.1f", reader.lineHeight),
                    value = reader.lineHeight,
                    range = ReaderPrefs.LINE_HEIGHT_RANGE,
                    steps = 10,
                    onValueChange = { v ->
                        store.updateReader { it.copy(lineHeight = (v * 10).roundToInt() / 10f) }
                    },
                )
                SettingsDivider()
                SliderSetting(
                    label = "Espace entre paragraphes",
                    valueLabel = String.format("%.1f", reader.paragraphGap),
                    value = reader.paragraphGap,
                    range = ReaderPrefs.PARAGRAPH_GAP_RANGE,
                    steps = 9,
                    onValueChange = { v ->
                        store.updateReader { it.copy(paragraphGap = (v * 10).roundToInt() / 10f) }
                    },
                )
                SettingsDivider()
                SliderSetting(
                    label = "Marges latérales",
                    valueLabel = if (reader.margin == 0) "Aucune" else "${reader.margin} dp",
                    value = reader.margin.toFloat(),
                    range = ReaderPrefs.MARGIN_RANGE,
                    steps = 19,
                    onValueChange = { v ->
                        store.updateReader { it.copy(margin = v.roundToInt()) }
                    },
                )
                SettingsDivider()
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Police",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    SegmentedChoice(
                        options = ReaderFont.entries.toList(),
                        selected = reader.font,
                        onSelect = { font -> store.updateReader { it.copy(font = font) } },
                        label = { it.label },
                    )
                }
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.FormatAlignJustify,
                    title = "Texte justifié",
                    subtitle = "Aligne le texte sur les deux marges",
                    checked = reader.justify,
                    onCheckedChange = { v -> store.updateReader { it.copy(justify = v) } },
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsSection(title = "Ambiance") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Fond de lecture",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Indépendant du thème de l'application",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ReaderTheme.entries.forEach { theme ->
                            ReaderThemeSwatch(
                                theme = theme,
                                selected = reader.theme == theme,
                                onClick = { store.updateReader { it.copy(theme = theme) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            SettingsSection(title = "Confort") {
                SettingsSwitchRow(
                    icon = Icons.Filled.Visibility,
                    title = "Garder l'écran allumé",
                    subtitle = "Évite la mise en veille pendant la lecture",
                    checked = reader.keepScreenOn,
                    onCheckedChange = { v -> store.updateReader { it.copy(keepScreenOn = v) } },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    icon = Icons.Filled.Fullscreen,
                    title = "Plein écran",
                    subtitle = "Masque les barres système pendant la lecture",
                    checked = reader.fullscreen,
                    onCheckedChange = { v -> store.updateReader { it.copy(fullscreen = v) } },
                )
            }

            Spacer(Modifier.height(20.dp))
            SettingsSection(title = "Discussions") {
                // Réglage de COMPTE, pas d'appareil : il part dans `preferences` avec
                // les autres, donc le couper ici le coupe sur tous ses écrans.
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    title = "Commentaires dans le texte",
                    subtitle = "Marques en marge des paragraphes commentés. " +
                        "Les réactions et les citations restent.",
                    checked = reader.inTextComments,
                    onCheckedChange = { v -> store.updateReader { it.copy(inTextComments = v) } },
                )
                SettingsSwitchRow(
                    icon = Icons.Outlined.EmojiEmotions,
                    title = "Réactions sous les paragraphes",
                    subtitle = "Petites puces emoji façon Discord, et le double tap " +
                        "pour réagir. Indépendant des commentaires.",
                    checked = reader.inTextReactions,
                    onCheckedChange = { v -> store.updateReader { it.copy(inTextReactions = v) } },
                )
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = !isDefault,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(50.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (isDefault) "Réglages par défaut" else "Réinitialiser les réglages")
            }
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
            title = { Text("Réinitialiser ?") },
            text = { Text("Les réglages de lecture reviendront à leurs valeurs d'origine. Le thème de l'application n'est pas touché.") },
            confirmButton = {
                TextButton(onClick = { store.resetReader(); confirmReset = false }) {
                    Text("Réinitialiser")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Annuler") }
            },
        )
    }
}

/** Aperçu du texte tel qu'il sera rendu dans le lecteur (mêmes calculs de style). */
@Composable
private fun ReaderPreview(prefs: ReaderPrefs) {
    val style = com.novelrealm.mobile.ui.reader.rememberReaderStyle(prefs)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = style.background,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(20.dp),
            ),
    ) {
        // L'aperçu applique aussi la marge : c'est justement le réglage qu'on ne peut pas
        // juger sans le voir.
        Column(modifier = Modifier.padding(horizontal = style.horizontalPadding, vertical = 20.dp)) {
            Text(
                text = "Chapitre 12",
                style = MaterialTheme.typography.labelMedium,
                color = style.foreground.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = PREVIEW_TEXT,
                style = style.textStyle,
                color = style.foreground,
            )
            Spacer(Modifier.height(style.paragraphSpacing))
            Text(
                text = "Elle savait qu'il ne restait plus beaucoup de temps.",
                style = style.textStyle,
                color = style.foreground,
            )
        }
    }
}

/** Curseur de réglage avec sa valeur affichée à droite du libellé. */
@Composable
private fun SliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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

/** Vignette d'ambiance : montre le vrai couple fond / texte, avec « Aa ». */
@Composable
private fun ReaderThemeSwatch(
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
                .height(56.dp)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp),
                )
                .background(bg, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Sélectionné",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = theme.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
