package com.novelrealm.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Rangée de 5 étoiles. En lecture seule par défaut ; passe `onRate` pour en faire un sélecteur.
@Composable
fun RatingStars(
    rating: Int,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    onRate: ((Int) -> Unit)? = null,
) {
    Row(modifier = modifier) {
        for (i in 1..5) {
            val active = i <= rating
            var starModifier = Modifier.size(size)
            if (onRate != null) {
                starModifier = starModifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onRate(i) }
            }
            Icon(
                imageVector = if (active) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = if (active) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = starModifier,
            )
        }
    }
}
