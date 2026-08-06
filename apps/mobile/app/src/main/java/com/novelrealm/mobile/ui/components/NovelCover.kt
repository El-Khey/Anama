package com.novelrealm.mobile.ui.components

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.resolveImageUrl

// Ratios de couverture façon Mihon : livre 2:3 (grilles, détail) et carré 1:1 (listes compactes).
const val COVER_RATIO_BOOK = 2f / 3f
const val COVER_RATIO_SQUARE = 1f

// Couverture de roman : source unique de vérité pour toutes les images de couverture.
// Placeholder gris pendant le chargement / en cas d'erreur, recadrage crop, coins arrondis.
@Composable
fun NovelCover(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    ratio: Float = COVER_RATIO_BOOK,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    // `remember` : cette couverture est répétée par dizaines dans une grille qui défile,
    // et sans lui chaque recomposition rallouait un painter pourtant toujours identique.
    val placeholder = remember { ColorPainter(Color(0x1F888888)) }
    AsyncImage(
        model = resolveImageUrl(coverUrl),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        modifier = modifier
            .aspectRatio(ratio)
            .clip(shape),
    )
}
