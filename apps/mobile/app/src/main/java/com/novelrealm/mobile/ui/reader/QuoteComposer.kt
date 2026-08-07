package com.novelrealm.mobile.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Longueur maximale d'une citation — doit rester alignée sur `PassageAnnotation`. */
private const val MaxQuoteLength = 500

/** Une phrase du bloc : ses bornes dans le texte du bloc ({@code end} exclu). */
data class Sentence(val start: Int, val end: Int)

/**
 * Découpe un bloc en phrases pour servir de **grille de sélection**.
 *
 * <p>Attention : ce découpage n'est qu'une aide au geste. L'ancre, elle, reste le
 * couple bloc + bornes — jamais « la phrase n° 3 », qui ne survivrait pas au moindre
 * reformatage de la source.
 *
 * Une coupure a lieu après `.`, `!`, `?` ou `…`, une fois refermés les guillemets qui
 * suivent, et suivie d'une espace. Les fragments d'un seul caractère sont recollés au
 * précédent : ils viennent d'abréviations (« M. Dupont »), pas de vraies phrases.
 */
fun splitSentences(text: String): List<Sentence> {
    if (text.isBlank()) return emptyList()

    val sentences = mutableListOf<Sentence>()
    var start = 0
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '.' || c == '!' || c == '?' || c == '…') {
            var end = i + 1
            // Emporte la ponctuation fermante collée au point : « … ! » »
            while (end < text.length && text[end] in "»\"”'’)]") end++
            // Puis les espaces, pour que la phrase suivante commence sur un mot.
            while (end < text.length && text[end].isWhitespace()) end++
            if (end - start >= 2) {
                sentences.add(Sentence(start, end))
                start = end
            }
            i = end
        } else {
            i++
        }
    }
    if (start < text.length) sentences.add(Sentence(start, text.length))
    return sentences.ifEmpty { listOf(Sentence(0, text.length)) }
}

/**
 * Panneau de création d'une citation (#41, §3).
 *
 * <p><b>Pourquoi une sélection par phrases et non la sélection système.</b> Compose
 * n'expose aucun moyen public de récupérer le texte sélectionné dans un
 * `SelectionContainer` : les contournements connus passent par le presse-papiers, ce
 * qui écraserait celui du lecteur. Et des poignées de sélection à faire glisser dans
 * une page qui défile, au doigt, est un geste pénible. Toucher une phrase est net,
 * rattrapable, et suffit à ce qu'on cite en pratique.
 *
 * <p>Le geste : tout le paragraphe est retenu au départ ; toucher une phrase HORS de
 * la sélection l'étend jusqu'à elle, toucher une phrase DEDANS y ramène la borne la
 * plus proche. On agrandit et on rétrécit sans jamais avoir à viser juste.
 */
@Composable
fun QuoteComposerSheet(
    blockText: String,
    isSaving: Boolean,
    error: String?,
    onConfirm: (startOffset: Int, endOffset: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sentences = remember(blockText) { splitSentences(blockText) }
    var firstSentence by remember(blockText) { mutableStateOf(0) }
    var lastSentence by remember(blockText) { mutableStateOf(sentences.lastIndex) }
    var layout by remember(blockText) { mutableStateOf<TextLayoutResult?>(null) }

    val startOffset = sentences.getOrNull(firstSentence)?.start ?: 0
    val endOffset = sentences.getOrNull(lastSentence)?.end ?: blockText.length
    val selected = blockText.substring(startOffset, endOffset).trim()
    val tooLong = selected.length > MaxQuoteLength

    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    val annotated = remember(blockText, startOffset, endOffset, highlight) {
        buildAnnotatedString {
            append(blockText)
            addStyle(SpanStyle(background = highlight), startOffset, endOffset)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            // Absorbe les taps : le lecteur bascule ses barres au moindre tap sur le fond.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 16.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "CRÉER UNE CITATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (sentences.size > 1) {
                    "Touche une phrase pour ajuster ce que tu gardes."
                } else {
                    "Ce passage sera rangé dans « Mes citations »."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    // Un long paragraphe ne doit pas repousser les boutons hors de
                    // l'écran : le texte défile, le panneau garde sa taille.
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    onTextLayout = { layout = it },
                    modifier = Modifier.pointerInput(sentences) {
                        detectTapGestures { position ->
                            val result = layout ?: return@detectTapGestures
                            val offset = result.getOffsetForPosition(position)
                            val tapped = sentences.indexOfFirst {
                                offset >= it.start && offset < it.end
                            }
                            if (tapped < 0) return@detectTapGestures
                            when {
                                tapped < firstSentence -> firstSentence = tapped
                                tapped > lastSentence -> lastSentence = tapped
                                // Touche DANS la sélection : on ramène la borne la plus
                                // proche, ce qui rétrécit du côté attendu.
                                tapped - firstSentence <= lastSentence - tapped ->
                                    firstSentence = tapped
                                else -> lastSentence = tapped
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${selected.length} / $MaxQuoteLength",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tooLong) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (sentences.size > 1 &&
                    (firstSentence != 0 || lastSentence != sentences.lastIndex)
                ) {
                    SheetTextButton(label = "Tout le paragraphe") {
                        firstSentence = 0
                        lastSentence = sentences.lastIndex
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (tooLong) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sélection trop longue — retire une phrase.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetButton(
                    label = "Annuler",
                    filled = false,
                    enabled = !isSaving,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                SheetButton(
                    label = "Citer",
                    filled = true,
                    enabled = !isSaving && !tooLong && selected.isNotEmpty(),
                    loading = isSaving,
                    onClick = { onConfirm(startOffset, endOffset) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SheetTextButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun SheetButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val container = when {
        !filled -> MaterialTheme.colorScheme.surfaceVariant
        enabled || loading -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val content = when {
        !filled -> MaterialTheme.colorScheme.onSurfaceVariant
        enabled || loading -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Surface(color = container, shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 13.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = content,
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                )
            }
        }
    }
}
