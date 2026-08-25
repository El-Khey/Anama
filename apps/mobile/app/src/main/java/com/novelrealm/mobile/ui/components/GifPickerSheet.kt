package com.novelrealm.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.ApiResult
import com.novelrealm.mobile.data.remote.dto.GifDto
import com.novelrealm.mobile.data.remote.userMessage
import com.novelrealm.mobile.di.ServiceLocator
import kotlinx.coroutines.delay

/**
 * Sélecteur de GIF (issue #45, §5) — une feuille : un champ de recherche, une
 * grille de vignettes ANIMÉES, et la pagination par le curseur rendu par le back.
 *
 * <p><b>Favoris</b> (stockés localement, [GifFavoritesStore]) : la PREMIÈRE case
 * de la grille est une tuile « Favoris » (façon Klipy/Discord) qui ouvre la
 * collection ; on y revient par une flèche retour. Chaque GIF porte une étoile
 * pour l'ajouter/retirer sans le choisir. Taper une recherche montre les
 * résultats à la place des tendances.
 *
 * L'état vit ici même : le sélecteur est autonome (recherche/favoris → choix →
 * fermé) et n'a rien à laisser derrière lui — le seul résultat qui compte est le
 * [GifDto] remis à [onPick].
 */
@Composable
fun GifPickerSheet(
    onPick: (GifDto) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gifRepo = ServiceLocator.gifRepository
    val favoritesStore = ServiceLocator.gifFavoritesStore
    val favorites by favoritesStore.favorites.collectAsState()

    var query by remember { mutableStateOf("") }
    // Vue « collection de favoris » ouverte via la tuile Favoris.
    var favoritesView by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<GifDto>>(emptyList()) }
    var nextPos by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val searching = query.isNotBlank()
    // La vue favoris est purement locale (aucun appel réseau). Une recherche en sort.
    val showingFavorites = favoritesView && !searching

    // Réseau : tendances à l'ouverture, recherche débouncée ensuite. La vue favoris
    // ne déclenche aucun appel. `LaunchedEffect(query)` relance à chaque frappe :
    // le `delay` fait office de débounce, la frappe suivante annulant l'attente.
    LaunchedEffect(query) {
        if (showingFavorites) return@LaunchedEffect
        if (searching) delay(350)
        loading = true
        error = null
        val result = if (searching) gifRepo.search(query) else gifRepo.featured()
        when (result) {
            is ApiResult.Success -> {
                results = result.data.results
                nextPos = result.data.next
            }
            is ApiResult.Error -> error = result.userMessage()
        }
        loading = false
    }

    suspend fun loadMore() {
        if (nextPos.isBlank() || loadingMore) return
        loadingMore = true
        val result = if (searching) gifRepo.search(query, pos = nextPos)
        else gifRepo.featured(pos = nextPos)
        if (result is ApiResult.Success) {
            results = (results + result.data.results).distinctBy { it.id }
            nextPos = result.data.next
        }
        loadingMore = false
    }
    var wantMore by remember { mutableStateOf(0) }
    LaunchedEffect(wantMore) { if (wantMore > 0) loadMore() }

    // Contenu de la grille + pagination.
    val gifs = if (showingFavorites) favorites else results
    val paginable = !showingFavorites && nextPos.isNotBlank()
    // La tuile « Favoris » n'apparaît qu'en vue normale (pas en recherche, pas déjà
    // dans les favoris) — comme la première case de la capture de référence.
    val showFavoritesTile = !searching && !showingFavorites

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 12.dp),
        ) {
            // Poignée + fermeture, comme les autres feuilles du lecteur.
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(7.dp)
                        .size(17.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            // En vue favoris : un en-tête « ← Favoris » à la place de la recherche.
            // Sinon : le champ de recherche habituel.
            if (showingFavorites) {
                FavoritesHeader(count = favorites.size, onBack = { favoritesView = false })
            } else {
                SearchField(query = query, onQueryChange = { query = it })
            }

            Spacer(Modifier.height(10.dp))
            when {
                loading && !showingFavorites -> CenteredBox(height = 200.dp) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }

                error != null && !showingFavorites -> CenteredBox(height = 120.dp) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Grille vide ET pas de tuile Favoris à montrer → message d'état.
                gifs.isEmpty() && !showFavoritesTile -> CenteredBox(height = 120.dp) {
                    Text(
                        text = when {
                            searching -> "Aucun GIF pour « $query »"
                            showingFavorites -> "Aucun favori pour l'instant.\nÉtoile un GIF pour le retrouver ici."
                            else -> "Aucun GIF pour le moment"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                ) {
                    // Première case : la tuile Favoris (ouvre la collection).
                    if (showFavoritesTile) {
                        item(key = "favorites-tile") {
                            FavoritesTile(
                                count = favorites.size,
                                onClick = { favoritesView = true },
                            )
                        }
                    }
                    items(gifs, key = { it.id }) { gif ->
                        GifCell(
                            gif = gif,
                            favorite = favorites.any { it.id == gif.id },
                            onClick = { onPick(gif) },
                            onToggleFavorite = { favoritesStore.toggle(gif) },
                        )
                    }
                    if (paginable) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) {
                                if (loadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(
                                        text = "Plus de GIF",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { wantMore += 1 }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "GIF fournis par KLIPY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/** Boîte centrée réutilisée par les états (chargement / erreur / vide). */
@Composable
private fun CenteredBox(height: Dp, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().height(height),
    ) { content() }
}

/**
 * Tuile « Favoris » — la première case de la grille (façon capture Klipy). Occupe
 * exactement la place d'une vignette (même ratio 4:3) et ouvre la collection au tap.
 * Dégradé + étoile + libellé, pour se distinguer nettement des GIF autour.
 */
@Composable
private fun FavoritesTile(count: Int, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    ),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Favoris",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            if (count > 0) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/** En-tête de la vue favoris : flèche retour + titre + compteur. */
@Composable
private fun FavoritesHeader(count: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Retour",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onBack)
                .padding(6.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Favoris",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "· $count",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Le champ de recherche, qui **prend le clavier dès l'ouverture** du sélecteur.
 *
 * Sans ça, la saisie restait branchée sur le champ de commentaire ouvert
 * dessous : on tapait « chat », le clavier était déjà là, et les lettres
 * partaient dans le message. Le sélecteur n'a qu'une raison d'exister —
 * chercher — autant y poser le curseur d'emblée.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    // En effet et non en appel direct : le champ doit être attaché à l'arbre
    // avant qu'on puisse le viser. Le `runCatching` couvre le cas limite d'une
    // feuille refermée dans le même souffle qu'elle s'ouvre — une exception y
    // ferait tomber l'app pour un clavier.
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(23.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Chercher un GIF…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Effacer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
        }
    }
}

/**
 * Une vignette de la grille — **animée**, pour qu'on voie ce que fait le GIF avant de
 * le choisir (une grille figée obligeait à en ouvrir un pour le reconnaître).
 *
 * La grille est une `LazyVerticalGrid` : seules les cellules visibles (plus une frange)
 * sont composées, et Coil libère le décodeur d'une cellule sortie de l'écran. L'animation
 * ne tourne donc, de fait, que sur ce qui est à l'écran — sans qu'on ait à piloter nous-
 * mêmes un « visible/pas visible ». La vignette figée reste dessous : elle occupe le cadre
 * pendant que l'animé se télécharge et évite le clignotement gris.
 *
 * En coin, une **étoile** bascule le favori sans choisir le GIF (le tap y est consommé
 * localement, il ne remonte pas au `clickable` de la cellule).
 */
@Composable
private fun GifCell(
    gif: GifDto,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val ratio = if (gif.width > 0 && gif.height > 0) {
        gif.width.toFloat() / gif.height
    } else {
        4f / 3f
    }
    val gifLoader = rememberGifLoader()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio.coerceIn(0.6f, 2.2f))
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
    ) {
        // Dessous : la vignette figée (loader par défaut, première image), le temps que
        // l'animé arrive.
        AsyncImage(
            model = gif.previewUrl.ifBlank { gif.url },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Dessus : le GIF animé, via le loader à décodeur GIF.
        AsyncImage(
            model = gif.url,
            contentDescription = null,
            imageLoader = gifLoader,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Étoile de favori, en haut à droite. Léger « pop » quand elle devient pleine.
        FavoriteStar(
            favorite = favorite,
            onToggle = onToggleFavorite,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        )
    }
}

/** Bouton étoile posé sur un GIF : creuse (pas favori) / pleine jaune (favori). */
@Composable
private fun FavoriteStar(
    favorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Petit rebond à l'apparition de l'état plein, pour un retour tactile net.
    val pop by animateFloatAsState(targetValue = if (favorite) 1f else 0.9f, label = "starPop")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onToggle),
    ) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
            tint = if (favorite) Color(0xFFFFC53D) else Color.White,
            modifier = Modifier
                .size(16.dp)
                .scale(if (favorite) pop else 1f),
        )
    }
}
