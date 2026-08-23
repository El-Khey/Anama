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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * La barre de réaction rapide **dans le flux** — les six emojis rapides
 * ([EmojiCatalog.QUICK]) + le bouton « + » —, compacte et sobre.
 *
 * <p>La MÊME barre partout : sous un paragraphe (double tap sur un bloc) comme sous un
 * commentaire (bouton « + » des puces). On avait un temps une variante flottante en
 * `Popup` pour les commentaires — trop grosse, façon carte posée par-dessus la page.
 * Le lecteur a demandé la barre du paragraphe partout : c'est celle-ci, et c'est tout.
 *
 * <p>Elle vit au fil du texte : plus petite et moins arrondie qu'une carte flottante,
 * le [background] peint ici (pas de Surface) pour rester discret. Comme elle est dans
 * le flux, l'appelant l'insère là où il veut la voir (au-dessus des marques, sous les
 * puces…) et la retire d'un état — elle ne se ferme pas « au tap à côté » toute seule.
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
