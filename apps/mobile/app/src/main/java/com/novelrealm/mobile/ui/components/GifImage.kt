package com.novelrealm.mobile.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * `ImageLoader` capable d'ANIMER les GIF. Coil sans décodeur dédié n'affiche que
 * la première image — c'est d'ailleurs exactement ce qu'on exploite pour les
 * vignettes figées : elles passent par le loader par défaut.
 *
 * Construit à la demande et mémorisé par le contexte applicatif : un seul
 * exemplaire, mais seulement si un GIF est réellement affiché.
 */
@Composable
fun rememberGifLoader(): ImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}

/**
 * Un GIF dans un fil de commentaires (issue #45, §5) — **figé par défaut**.
 *
 * Un GIF qui s'anime tout seul dans une page de lecture est une distraction et un
 * gouffre à données mobiles : on affiche l'image fixe avec un triangle de
 * lecture, et l'animation ne démarre qu'au toucher. Re-toucher fige à nouveau.
 *
 * Le ratio est fixé AVANT chargement par les dimensions renvoyées par l'API :
 * le fil ne saute pas quand l'image arrive.
 */
@Composable
fun CommentGif(
    gifUrl: String,
    previewUrl: String?,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
) {
    var playing by remember(gifUrl) { mutableStateOf(false) }
    val ratio = if (width > 0 && height > 0) width.toFloat() / height else 4f / 3f
    val gifLoader = rememberGifLoader()

    Box(
        modifier = modifier
            .widthIn(max = 240.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable { playing = !playing },
    ) {
        if (playing) {
            AsyncImage(
                model = gifUrl,
                contentDescription = "GIF",
                imageLoader = gifLoader,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            AsyncImage(
                // La vignette figée si Tenor en fournit une ; sinon le GIF passé au
                // loader PAR DÉFAUT de Coil (sans décodeur GIF), qui n'en décode que
                // la première image — exactement l'effet recherché.
                model = previewUrl ?: gifUrl,
                contentDescription = "GIF (toucher pour animer)",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Triangle de lecture : dit « ceci s'anime » sans occuper de place.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            GifBadge(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
    }
}

/** La petite étiquette « GIF », posée sur la vignette figée. */
@Composable
private fun GifBadge(modifier: Modifier = Modifier) {
    Text(
        text = "GIF",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
