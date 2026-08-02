package com.novelrealm.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Voile assombrissant posé sous un panneau : un tap à côté le referme.
 *
 * Mutualisé entre le lecteur et le catalogue — les deux ouvrent un panneau par le bas,
 * et deux voiles écrits séparément finiraient par diverger d'opacité.
 */
@Composable
fun SheetScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                // Sans `indication`, pas d'ondulation sur le voile : il absorbe le tap
                // sans se signaler comme un bouton.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}
