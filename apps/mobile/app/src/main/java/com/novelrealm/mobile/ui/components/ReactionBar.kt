package com.novelrealm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * La barre flottante des six emojis rapides ([EmojiCatalog.QUICK]) + le bouton « + ».
 *
 * <p>Partagée par les réactions de COMMENTAIRE (appui long sur un message) et de BLOC
 * (double tap sur un paragraphe) — un seul geste rapide, une seule barre. Positionnée
 * en [Popup] plutôt que dans le flux : elle ne pousse rien et se referme au tap à côté.
 *
 * @param onPick un des six emojis rapides a été touché
 * @param onMore le bouton « + » — ouvrir le sélecteur complet ([EmojiPickerSheet])
 * @param onDismiss un tap à côté a fermé la barre
 */
@Composable
fun ReactionBarPopup(
    onPick: (String) -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 14.dp,
            modifier = Modifier.padding(8.dp),
        ) {
            ReactionBarContent(
                onPick = onPick,
                onMore = onMore,
                tileSize = 42.dp,
                emojiSize = 23.sp,
                addIconSize = 20.dp,
                padding = 6.dp,
                spacing = 4.dp,
            )
        }
    }
}

/**
 * La barre de réaction **dans le flux** (pas en Popup) — compacte, pour se poser sur la
 * ligne des marques sous un paragraphe, entre les puces à gauche et le 💬 à droite.
 *
 * <p>Plus petite et moins arrondie que la version en Popup : elle vit au fil du texte,
 * elle ne doit pas peser comme une carte flottante. Le [background] est peint ici (pas
 * de Surface) pour rester sobre.
 */
@Composable
fun ReactionBarInline(
    onPick: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        ReactionBarContent(
            onPick = onPick,
            onMore = onMore,
            tileSize = 28.dp,
            emojiSize = 16.sp,
            addIconSize = 15.dp,
            padding = 0.dp,
            spacing = 2.dp,
        )
    }
}

/** Le contenu commun aux deux barres : les six emojis + le bouton « + ». */
@Composable
private fun ReactionBarContent(
    onPick: (String) -> Unit,
    onMore: () -> Unit,
    tileSize: Dp,
    emojiSize: TextUnit,
    addIconSize: Dp,
    padding: Dp,
    spacing: Dp,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier.padding(padding),
    ) {
        EmojiCatalog.QUICK.forEach { emoji ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(tileSize)
                    .clip(CircleShape)
                    .clickable { onPick(emoji) },
            ) {
                Text(text = emoji, fontSize = emojiSize)
            }
        }
        // Le « + » vers le clavier complet, comme sur Discord.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(tileSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                .clickable(onClick = onMore),
        ) {
            Icon(
                imageVector = Icons.Outlined.AddReaction,
                contentDescription = "Plus d'emojis",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(addIconSize),
            )
        }
    }
}
