package com.novelrealm.mobile.ui.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
 * Onglet Explorer — pensé comme la vitrine d'un store, pas comme un mur de vignettes.
 *
 * ## Deux modes, un seul écran
 *
 * **Navigation libre** (rien de coché) : une entrée éditoriale — un **héros** à la une,
 * puis des **rangées** horizontales (Nouveautés, Populaires) —, suivie de la grille
 * complète. C'est ce qu'on voit en arrivant : de quoi flâner, pas seulement chercher.
 *
 * **Recherche / filtre actif** : l'éditorial s'efface, l'écran se replie sur la seule
 * grille de résultats. Entre une requête et sa réponse, un héros serait du bruit.
 *
 * Tout vit dans une **unique grille défilante** : l'en-tête, la recherche, la bande de
 * genres et les sections éditoriales sont des lignes pleine largeur (`span`), puis
 * viennent les cartes. Un seul défilement, donc, et la recherche remonte avec le reste
 * plutôt que de rester collée en haut — le catalogue est ce qu'on vient lire.
 *
 * Le **cœur** de chaque carte suit ou arrête de suivre d'un appui, sans ouvrir la fiche.
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
        when {
            state.isLoading && state.novels.isEmpty() && !state.showEditorial -> {
                Column(Modifier.fillMaxSize()) {
                    ExploreHeader(
                        total = state.totalResults,
                        showTotal = false,
                        filtersActive = state.filtersActive,
                        onOpenFilters = { filtersOpen = true },
                    )
                    LoadingScreen()
                }
            }

            state.error != null && state.novels.isEmpty() -> {
                Column(Modifier.fillMaxSize()) {
                    ExploreHeader(
                        total = state.totalResults,
                        showTotal = false,
                        filtersActive = state.filtersActive,
                        onOpenFilters = { filtersOpen = true },
                    )
                    EmptyScreen(
                        message = state.error ?: "",
                        actionLabel = "Réessayer",
                        onAction = viewModel::refresh,
                    )
                }
            }

            else -> ExploreContent(
                state = state,
                onOpenFilters = { filtersOpen = true },
                onQueryChange = viewModel::onQueryChange,
                onGenreSelected = viewModel::onGenreSelected,
                onSortSelected = viewModel::onSortSelected,
                onStatusSelected = viewModel::onStatusSelected,
                onClearFilters = viewModel::clearFilters,
                onNovelClick = onNovelClick,
                onToggleLibrary = viewModel::toggleLibrary,
                onLoadMore = viewModel::loadNextPage,
            )
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

/**
 * Une ligne pleine largeur de la grille (en-tête, héros, section, recherche…). Elle
 * occupe toutes les colonnes (`maxLineSpan`) : c'est ce qui laisse en-tête et carrousels
 * cohabiter avec les cartes dans une seule grille défilante.
 */
private fun LazyGridScope.fullSpan(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

// ── Contenu défilant (en-tête + éditorial + grille, d'un seul tenant) ────────────

@Composable
private fun ExploreContent(
    state: ExploreUiState,
    onOpenFilters: () -> Unit,
    onQueryChange: (String) -> Unit,
    onGenreSelected: (Long?) -> Unit,
    onSortSelected: (ExploreSort) -> Unit,
    onStatusSelected: (ExploreStatus) -> Unit,
    onClearFilters: () -> Unit,
    onNovelClick: (Long) -> Unit,
    onToggleLibrary: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    // Pagination : charge la page suivante quand on approche de la fin.
    val reachedEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) onLoadMore() }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 118.dp),
        // Marge latérale portée par la grille : les cartes s'y calent proprement. Les
        // lignes pleine largeur (héros, carrousels) reçoivent ce même retrait — leurs
        // paddings internes sont donc réglés pour que le total retombe sur nos 16-20 dp.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        // ── En-tête (toujours) ──────────────────────────────────────────────────
        fullSpan("header") {
            ExploreHeader(
                total = state.totalResults,
                showTotal = !state.showEditorial && state.novels.isNotEmpty(),
                filtersActive = state.filtersActive,
                onOpenFilters = onOpenFilters,
            )
        }

        // ── Vitrine éditoriale (navigation libre seulement) ─────────────────────
        if (state.showEditorial) {
            if (state.featuredList.isNotEmpty()) {
                fullSpan("hero") {
                    NovelHeroCarousel(
                        novels = state.featuredList,
                        libraryNovelIds = state.libraryNovelIds,
                        onNovelClick = onNovelClick,
                        onToggleLibrary = onToggleLibrary,
                        // La grille inset déjà de 16 dp : le carrousel s'aligne donc aux
                        // cartes sans padding horizontal propre.
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            if (state.popularRow.isNotEmpty()) {
                fullSpan("h-popular") {
                    SectionHeader(title = "Populaires", modifier = Modifier.padding(top = 8.dp))
                }
                fullSpan("row-popular") {
                    Spacer(Modifier.height(2.dp))
                    NovelRow(
                        novels = state.popularRow,
                        libraryNovelIds = state.libraryNovelIds,
                        onNovelClick = onNovelClick,
                        onToggleLibrary = onToggleLibrary,
                    )
                }
            }

            if (state.recentRow.isNotEmpty()) {
                fullSpan("h-recent") {
                    SectionHeader(title = "Nouveautés", modifier = Modifier.padding(top = 12.dp))
                }
                fullSpan("row-recent") {
                    Spacer(Modifier.height(2.dp))
                    NovelRow(
                        novels = state.recentRow,
                        libraryNovelIds = state.libraryNovelIds,
                        onNovelClick = onNovelClick,
                        onToggleLibrary = onToggleLibrary,
                    )
                }
            }
        }

        // ── Recherche + genres (toujours, mais sous l'éditorial) ────────────────
        fullSpan("search") {
            Column {
                if (state.showEditorial) {
                    SectionHeader(title = "Tout le catalogue", modifier = Modifier.padding(top = 16.dp))
                    Spacer(Modifier.height(10.dp))
                }
                SearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    // Aligné aux cartes : la grille pose déjà le retrait de 16 dp.
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.genres.isNotEmpty()) {
                    GenreStrip(
                        genres = state.genres,
                        selectedGenreId = state.selectedGenreId,
                        onGenreSelected = onGenreSelected,
                    )
                }
                ActiveFilterRow(
                    state = state,
                    onSortSelected = onSortSelected,
                    onStatusSelected = onStatusSelected,
                )
            }
        }

        // ── Grille de résultats ─────────────────────────────────────────────────
        if (state.novels.isEmpty() && !state.isLoading) {
            fullSpan("empty") {
                EmptyResults(filtersActive = state.filtersActive, onClear = onClearFilters)
            }
        } else {
            itemsIndexed(state.novels) { index, novel ->
                StaggeredCard(index = index) {
                    NovelGridItem(
                        title = novel.title,
                        coverUrl = novel.coverImageUrl,
                        author = novel.author,
                        statusLabel = novelStatusLabel(novel.status),
                        inLibrary = novel.id in state.libraryNovelIds,
                        onToggleLibrary = { onToggleLibrary(novel.id) },
                        onClick = { onNovelClick(novel.id) },
                    )
                }
            }
        }

        if (state.isLoadingMore) {
            fullSpan("loading-more") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                }
            }
        }

        if (state.pageError != null) {
            fullSpan("page-error") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    Text(
                        text = state.pageError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = onLoadMore) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Charger la suite")
                    }
                }
            }
        }

        if (state.endReached && state.pageError == null && state.novels.isNotEmpty()) {
            fullSpan("end") {
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

/**
 * Enveloppe une carte dans une entrée en fondu + translation, décalée selon son rang :
 * les cartes « montent » l'une après l'autre plutôt que d'apparaître d'un bloc. Le
 * décalage est plafonné pour que le bas d'une longue page ne traîne pas.
 */
@Composable
private fun StaggeredCard(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 320,
            delayMillis = (index % 12) * 28,
        ),
        label = "cardStagger",
    )
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 26f
        },
    ) {
        content()
    }
}

// ── En-tête ──────────────────────────────────────────────────────────────────

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
            // Le Scaffold parent applique déjà l'inset de barre d'état ; on ne pose ici
            // que le retrait interne. `start = 4` compense le contentPadding de 16 dp de
            // la grille pour que le titre s'aligne à 20 dp comme les sections.
            .padding(start = 4.dp, end = 0.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Explorer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = when {
                    !showTotal -> "Ta prochaine lecture t'attend"
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

/**
 * Champ de recherche « maison » : une pastille arrondie assortie aux filtres, plutôt que
 * l'`OutlinedTextField` de Material dont le liseré tranchait avec le reste de l'écran.
 * Il ne prend PAS le focus tout seul : on arrive sur Explorer pour flâner, pas pour taper.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Titre ou auteur…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
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
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onQueryChange("") },
                    ),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Effacer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
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
        // Pas de retrait horizontal ici : la grille l'applique déjà à cette ligne. On ne
        // garde que le retrait vertical propre à la bande.
        contentPadding = PaddingValues(vertical = 12.dp),
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
        // Aligné aux cartes : retrait horizontal déjà posé par la grille.
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
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

/** L'état « aucun résultat » propre à la grille filtrée (l'éditorial est déjà masqué). */
@Composable
private fun EmptyResults(filtersActive: Boolean, onClear: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Text(
            text = if (filtersActive) {
                "Aucun roman ne correspond.\nEssaie d'élargir la recherche."
            } else {
                "Le catalogue est vide pour l'instant."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (filtersActive) {
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClear) { Text("Réinitialiser les filtres") }
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
