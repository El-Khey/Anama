package com.novelrealm.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Carte de grille d'un roman (Explorer, Bibliothèque, Accueil) : couverture, titre
 * incrusté en bas sur un dégradé, et — quand l'information existe — une pastille de
 * chapitres non lus et une barre de progression de lecture.
 *
 * L'appui long passe par `pointerInput`/`detectTapGestures` plutôt que par
 * `combinedClickable` : cette dernière est encore expérimentale, or ce projet a déjà
 * subi un crash `NoSuchMethodError` dû au décalage de versions de Compose.
 */
@Composable
fun NovelGridItem(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unreadCount: Long = 0,
    /** Part de chapitres lus (0f..1f) ; null si inconnue → aucune barre affichée. */
    readFraction: Float? = null,
    /** Marque le roman comme déjà suivi (utile dans le catalogue). */
    inLibrary: Boolean = false,
    /**
     * Rend le cœur **actionnable** : il s'affiche alors sur toutes les cartes — plein si
     * le roman est suivi, en contour sinon — et l'appui bascule le suivi sans ouvrir la
     * fiche. Laissé à `null`, le cœur reste un simple témoin, visible seulement quand le
     * roman est déjà en bibliothèque.
     */
    onToggleLibrary: (() -> Unit)? = null,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)

    // Retour visuel à l'appui : la carte se creuse légèrement. `clickable` fournirait
    // un ripple, mais l'appui long passe par `pointerInput` (qui ne l'affiche pas) —
    // cette animation donne donc le même ressenti dans les deux cas.
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        label = "coverPressScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(
                if (onLongClick != null) {
                    Modifier.pointerInput(onClick, onLongClick) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                            onTap = { onClick() },
                            onLongPress = { onLongClick() },
                        )
                    }
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
    ) {
        NovelCover(
            coverUrl = coverUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
        )

        // Dégradé sombre : garantit la lisibilité du titre sur n'importe quelle image.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        0.75f to Color(0x99000000),
                        1f to Color(0xE6000000),
                    ),
                ),
        )

        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = MaterialTheme.typography.labelLarge.fontSize * 1.2f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )

        // Pastille « chapitres non lus », en haut à droite comme dans Mihon.
        if (unreadCount > 0) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            ) {
                Text(
                    text = if (unreadCount > 999) "999+" else "$unreadCount",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        // Cœur en haut à gauche. Simple témoin par défaut ; bouton dès qu'on fournit
        // `onToggleLibrary`, pour suivre un roman sans ouvrir sa fiche.
        //
        // Son `clickable` est posé sur un enfant de la carte : Compose distribue les
        // pointeurs aux enfants d'abord, l'appui sur le cœur est donc consommé ici et ne
        // remonte ni au `detectTapGestures` de la carte, ni à l'appui long.
        if (inLibrary || onToggleLibrary != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    // Pastille plus large quand elle est cliquable : 22 dp restaient sous
                    // les 32 dp qu'un doigt vise sans se concentrer.
                    .size(if (onToggleLibrary != null) 32.dp else 22.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000))
                    .then(
                        if (onToggleLibrary != null) {
                            Modifier.clickable(onClick = onToggleLibrary)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Icon(
                    imageVector = if (inLibrary) Icons.Filled.Favorite
                    else Icons.Filled.FavoriteBorder,
                    contentDescription = when {
                        onToggleLibrary == null -> "Déjà dans ta bibliothèque"
                        inLibrary -> "Retirer de ta bibliothèque"
                        else -> "Ajouter à ta bibliothèque"
                    },
                    tint = if (inLibrary) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(if (onToggleLibrary != null) 17.dp else 13.dp),
                )
            }
        }

        // Barre de progression collée au bas de la couverture (masquée si rien de lu,
        // ou si tout est lu : dans ces deux cas elle n'apprend rien).
        if (readFraction != null && readFraction > 0f && readFraction < 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(readFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        // Voile d'accent quand la carte est sélectionnée (mode sélection multiple).
        if (selected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            )
        }
    }
}
