package com.novelrealm.mobile.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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

/** Diamètre du point d'une poignée de sélection. */
private val HandleDotSize = 13.dp

/**
 * Zone d'accroche d'une poignée. Bien plus large que le point : une poignée de 13 dp se
 * saisirait à la loupe, alors que la cible confortable au doigt est de 32 dp environ.
 */
private val HandleTouchSize = 34.dp

/**
 * Panneau de création d'une citation (#41, §3).
 *
 * <p><b>Deux gestes, deux précisions.</b> Toucher une phrase cadre grossièrement ce
 * qu'on garde ; tirer l'une des deux **poignées** ajuste au caractère près. Le premier
 * suffit la plupart du temps, le second permet de citer une formule au milieu d'une
 * phrase — ce que la version par phrases seules interdisait.
 *
 * <p><b>Pourquoi pas la sélection système.</b> Compose n'expose aucun moyen public de
 * récupérer le texte sélectionné dans un `SelectionContainer` : les contournements
 * connus passent par le presse-papiers, ce qui écraserait celui du lecteur. On pose
 * donc nos propres poignées, en lisant les positions dans le `TextLayoutResult` du
 * paragraphe — `getCursorRect` pour savoir où dessiner, `getOffsetForPosition` pour
 * savoir sur quel caractère le doigt se trouve.
 *
 * <p>L'ancre enregistrée reste le couple **bloc + bornes**, jamais « la phrase n° 3 » :
 * c'est ce qui permet à une citation de survivre au reformatage de la source.
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
    var layout by remember(blockText) { mutableStateOf<TextLayoutResult?>(null) }

    // Bornes brutes, en index de caractères. C'est le changement de fond : la sélection
    // n'est plus « de la phrase i à la phrase j », elle peut s'arrêter n'importe où.
    var startOffset by remember(blockText) { mutableStateOf(0) }
    var endOffset by remember(blockText) { mutableStateOf(blockText.length) }

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
                    "Touche une phrase, ou tire les poignées pour couper au mot près."
                } else {
                    "Tire les poignées pour ne garder qu'une partie du passage."
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
                // Les poignées vivent DANS la zone défilable, en frères du texte : elles
                // suivent donc le paragraphe quand il défile, au lieu de rester collées
                // à un endroit de l'écran que le texte a quitté.
                //
                // La marge basse leur réserve la place : `offset` déplace sans agrandir,
                // donc une poignée accrochée à la dernière ligne dépasserait le bas du
                // bloc — et la zone défilable, elle, rogne ce qui dépasse.
                Box(modifier = Modifier.padding(bottom = HandleTouchSize / 2)) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        onTextLayout = { layout = it },
                        modifier = Modifier.pointerInput(sentences) {
                            detectTapGestures { position ->
                                val result = layout ?: return@detectTapGestures
                                val tapped = result.getOffsetForPosition(position)
                                val sentence = sentences.firstOrNull {
                                    tapped >= it.start && tapped < it.end
                                } ?: return@detectTapGestures
                                when {
                                    sentence.start < startOffset -> startOffset = sentence.start
                                    sentence.end > endOffset -> endOffset = sentence.end
                                    // Touche DANS la sélection : on ramène la borne la plus
                                    // proche, ce qui rétrécit du côté attendu.
                                    tapped - startOffset <= endOffset - tapped ->
                                        startOffset = sentence.start
                                    else -> endOffset = sentence.end
                                }
                            }
                        },
                    )

                    layout?.let { result ->
                        SelectionHandle(
                            layout = result,
                            offset = startOffset,
                            // Une borne ne peut pas rejoindre l'autre : une citation vide
                            // n'aurait rien à enregistrer.
                            onOffsetChange = { startOffset = it.coerceIn(0, endOffset - 1) },
                        )
                        SelectionHandle(
                            layout = result,
                            offset = endOffset,
                            onOffsetChange = {
                                endOffset = it.coerceIn(startOffset + 1, blockText.length)
                            },
                        )
                    }
                }
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
                if (startOffset != 0 || endOffset != blockText.length) {
                    SheetTextButton(label = "Tout le paragraphe") {
                        startOffset = 0
                        endOffset = blockText.length
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
                    text = "Sélection trop longue — resserre-la avec les poignées.",
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

/**
 * Poignée de bornage, suspendue sous le caractère qu'elle marque.
 *
 * <p><b>Le glissement travaille en position absolue, pas en delta cumulé sur l'index.</b>
 * On note où se trouve le caractère au moment de la saisie, on y ajoute le déplacement du
 * doigt, et on redemande au `TextLayoutResult` quel caractère occupe ce point. C'est ce
 * qui permet de changer de ligne en glissant vers le bas : un simple compteur de
 * caractères ne saurait pas ce qu'est un retour à la ligne.
 *
 * <p>Le `pointerInput` n'est **pas** relancé sur `offset`. Il change à chaque pixel
 * parcouru : le prendre comme clé redémarrerait la détection au premier mouvement, et le
 * geste serait annulé aussitôt commencé. On le lit donc au travers d'un
 * `rememberUpdatedState`, qui donne la valeur courante sans redémarrer quoi que ce soit.
 */
@Composable
private fun SelectionHandle(
    layout: TextLayoutResult,
    offset: Int,
    onOffsetChange: (Int) -> Unit,
) {
    val caret = remember(layout, offset) { layout.getCursorRect(offset) }
    val currentCaret by rememberUpdatedState(caret)
    val color = MaterialTheme.colorScheme.primary

    // Position du doigt dans le repère du texte, entretenue pendant tout le glissement.
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (caret.left - HandleTouchSize.toPx() / 2f).roundToInt(),
                    // Sous la ligne : au-dessus, la poignée masquerait le mot qu'on vise.
                    y = (caret.bottom - HandleTouchSize.toPx() / 2f).roundToInt(),
                )
            }
            .size(HandleTouchSize)
            .pointerInput(layout) {
                detectDragGestures(
                    onDragStart = {
                        dragPosition = Offset(currentCaret.left, currentCaret.center.y)
                    },
                ) { change, delta ->
                    // Consommé : sans ça, le même geste ferait aussi défiler le paragraphe
                    // sous la poignée.
                    change.consume()
                    dragPosition += delta
                    onOffsetChange(layout.getOffsetForPosition(dragPosition))
                }
            },
    ) {
        Box(
            modifier = Modifier
                .size(HandleDotSize)
                .clip(CircleShape)
                .background(color),
        )
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
