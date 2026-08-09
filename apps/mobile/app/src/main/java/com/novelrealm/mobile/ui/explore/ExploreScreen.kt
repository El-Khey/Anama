package com.novelrealm.mobile.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.GenreDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelGridItem
import com.novelrealm.mobile.ui.components.SegmentedChoice
import com.novelrealm.mobile.ui.components.selectionStyle
import com.novelrealm.mobile.ui.components.SheetScrim

/**
 * Onglet Explorer : le catalogue complet.
 *
 * L'organisation sépare ce qu'on touche souvent de ce qu'on règle une fois. Restent
 * toujours à l'écran la **recherche** et la **bande de genres** ; le tri et le statut,
 * eux, vivent dans un panneau qu'on ouvre — ils encombraient la barre pour un usage bien
 * plus rare.
 *
 * Le **cœur** de chaque carte suit ou arrête de suivre le roman d'un seul appui : on voit
 * ce qu'on suit déjà, et on l'ajoute sans jamais ouvrir la fiche.
 */
@Composable
fun ExploreScreen(
    onNovelClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var filtersOpen by remember { mutableStateOf(false) }

    // Au retour sur l'onglet, un roman a pu être ajouté en bibliothèque depuis sa fiche.
    // Le ViewModel ignore le tout premier appel : son chargement initial vient de le faire.
    LaunchedEffect(Unit) { viewModel.refreshOnReturn() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExploreHeader(
                total = state.totalResults,
                showTotal = !state.isLoading && state.novels.isNotEmpty(),
                filtersActive = state.filtersActive,
                onOpenFilters = { filtersOpen = true },
            )

            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            if (state.genres.isNotEmpty()) {
                GenreStrip(
                    genres = state.genres,
                    selectedGenreId = state.selectedGenreId,
                    onGenreSelected = viewModel::onGenreSelected,
                )
            }

            ActiveFilterRow(
                state = state,
                onSortSelected = viewModel::onSortSelected,
                onStatusSelected = viewModel::onStatusSelected,
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.novels.isEmpty() -> LoadingScreen()

                    state.error != null && state.novels.isEmpty() -> EmptyScreen(
                        message = state.error ?: "",
                        actionLabel = "Réessayer",
                        onAction = viewModel::refresh,
                    )

                    state.novels.isEmpty() -> EmptyScreen(
                        message = if (state.filtersActive) {
                            "Aucun roman ne correspond.\nEssaie d'élargir la recherche."
                        } else {
                            "Le catalogue est vide pour l'instant."
                        },
                        actionLabel = if (state.filtersActive) "Réinitialiser" else null,
                        onAction = if (state.filtersActive) {
                            { viewModel.clearFilters() }
                        } else {
                            null
                        },
                    )

                    else -> NovelGrid(
                        novels = state.novels,
                        libraryNovelIds = state.libraryNovelIds,
                        isLoadingMore = state.isLoadingMore,
                        pageError = state.pageError,
                        endReached = state.endReached,
                        onNovelClick = onNovelClick,
                        onToggleLibrary = viewModel::toggleLibrary,
                        onLoadMore = viewModel::loadNextPage,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = filtersOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            SheetScrim(onDismiss = { filtersOpen = false }, modifier = Modifier.fillMaxSize())
        }
        AnimatedVisibility(
            visible = filtersOpen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FilterSheet(
                state = state,
                onSortSelected = viewModel::onSortSelected,
                onStatusSelected = viewModel::onStatusSelected,
                onReset = { viewModel.clearFilters(); filtersOpen = false },
            )
        }
    }
}

// ── En-tête ────────────────────────────────────────────────────────────────────

@Composable
private fun ExploreHeader(
    total: Long,
    showTotal: Boolean,
    filtersActive: Boolean,
    onOpenFilters: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Explorer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Le compte de résultats est le retour direct des filtres : sans lui, on
                // ne sait pas si un filtre a mordu sur trois romans ou sur trois cents.
                text = when {
                    !showTotal -> "Tout le catalogue"
                    total <= 1 -> "$total roman"
                    else -> "$total romans"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (filtersActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CircleAction(
            icon = if (filtersActive) Icons.Filled.FilterListOff else Icons.Filled.FilterList,
            contentDescription = "Tri et filtres",
            filled = filtersActive,
            onClick = onOpenFilters,
        )
    }
}

/** Bouton rond de barre de titre, identique à celui de la Bibliothèque. */
@Composable
private fun CircleAction(
    icon: ImageVector,
    contentDescription: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (filled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        modifier = Modifier.size(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.clickable(onClick = onClick)) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Effacer")
                }
            }
        },
        placeholder = { Text("Titre ou auteur…") },
        modifier = modifier,
    )
}

// ── Genres ─────────────────────────────────────────────────────────────────────

/**
 * Bande de genres. « Tous » ouvre la marche pour qu'un retour en arrière soit toujours à
 * portée de pouce, sans avoir à retrouver le genre coché parmi trente pour le décocher.
 */
@Composable
private fun GenreStrip(
    genres: List<GenreDto>,
    selectedGenreId: Long?,
    onGenreSelected: (Long?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            Pill(
                label = "Tous",
                selected = selectedGenreId == null,
                onClick = { onGenreSelected(null) },
            )
        }
        lazyRowItems(items = genres.distinctBy { it.id }, key = { it.id }) { genre ->
            Pill(
                label = genre.name,
                selected = genre.id == selectedGenreId,
                onClick = { onGenreSelected(genre.id) },
            )
        }
    }
}

/**
 * Pastille de filtre, rectangle arrondi — écrite à la main plutôt qu'avec `FilterChip` :
 * ce projet évite les composants Material 3 dont la signature bouge d'une version à
 * l'autre, et cela garde le même galbe que les filtres de la Bibliothèque.
 */
@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val style = selectionStyle(selected)
    Surface(
        color = style.container,
        shape = shape,
        modifier = modifier.border(1.dp, style.border, shape),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = style.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        )
    }
}

/**
 * Rappel des filtres non visibles (tri, statut) sous forme d'étiquettes qu'on retire d'un
 * tap. Les genres n'y figurent pas : leur bande est juste au-dessus, déjà surlignée.
 */
@Composable
private fun ActiveFilterRow(
    state: ExploreUiState,
    onSortSelected: (ExploreSort) -> Unit,
    onStatusSelected: (ExploreStatus) -> Unit,
) {
    val sortActive = state.sort != ExploreSort.DEFAULT
    val statusActive = state.status != ExploreStatus.ALL
    if (!sortActive && !statusActive) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
    ) {
        if (sortActive) {
            RemovableTag(
                label = state.sort.label,
                onRemove = { onSortSelected(ExploreSort.DEFAULT) },
            )
        }
        if (statusActive) {
            RemovableTag(
                label = state.status.label,
                onRemove = { onStatusSelected(ExploreStatus.ALL) },
            )
        }
    }
}

@Composable
private fun RemovableTag(label: String, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "Retirer le filtre $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ── Grille ─────────────────────────────────────────────────────────────────────

@Composable
private fun NovelGrid(
    novels: List<NovelDto>,
    libraryNovelIds: Set<Long>,
    isLoadingMore: Boolean,
    pageError: String?,
    endReached: Boolean,
    onNovelClick: (Long) -> Unit,
    onToggleLibrary: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    // Déclenche le chargement de la page suivante quand on approche de la fin.
    val reachedEnd by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(reachedEnd) {
        if (reachedEnd) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 118.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = novels, key = { it.id }) { novel ->
            NovelGridItem(
                title = novel.title,
                coverUrl = novel.coverImageUrl,
                inLibrary = novel.id in libraryNovelIds,
                onToggleLibrary = { onToggleLibrary(novel.id) },
                onClick = { onNovelClick(novel.id) },
            )
        }

        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                }
            }
        }

        // Échec d'une page suivante : le contenu déjà chargé reste, et on propose de
        // reprendre là où ça a coincé plutôt que de tout recharger.
        if (pageError != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    Text(
                        text = pageError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onLoadMore) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Charger la suite")
                    }
                }
            }
        }

        if (endReached && pageError == null && novels.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "— Fin du catalogue —",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                )
            }
        }
    }
}

// ── Panneau de tri / statut ────────────────────────────────────────────────────

@Composable
private fun FilterSheet(
    state: ExploreUiState,
    onSortSelected: (ExploreSort) -> Unit,
    onStatusSelected: (ExploreStatus) -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 14.dp),
            ) {
                Text(
                    text = "Trier et filtrer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset, enabled = state.filtersActive) {
                    Text("Réinitialiser", style = MaterialTheme.typography.labelLarge)
                }
            }

            SheetLabel("Trier par")
            // Deux rangées de deux : quatre segments côte à côte tronqueraient les
            // libellés sur un écran étroit.
            ExploreSort.entries.chunked(2).forEach { pair ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    pair.forEach { option ->
                        SortOption(
                            label = option.label,
                            selected = state.sort == option,
                            onClick = { onSortSelected(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Rangée incomplète : on réserve la place manquante pour que les
                    // pastilles de la première rangée gardent la même largeur.
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            SheetLabel("Statut")
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                SegmentedChoice(
                    options = ExploreStatus.entries.toList(),
                    selected = state.status,
                    onSelect = onStatusSelected,
                    label = { it.label },
                )
            }
        }
    }
}

@Composable
private fun SortOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    val style = selectionStyle(selected)
    Surface(
        color = style.container,
        shape = shape,
        modifier = modifier.border(1.dp, style.border, shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = style.content,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = style.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
    )
}
