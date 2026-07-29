package com.novelrealm.mobile.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelGridItem

// Onglet Explorer / Catalogue (#35) : grille paginée de romans, recherche titre/auteur,
// filtres par genre (chips), tri et statut (menu) — rendu façon Mihon.
@Composable
fun ExploreScreen(
    onNovelClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        FiltersRow(
            state = state,
            onGenreSelected = viewModel::onGenreSelected,
            onSortSelected = viewModel::onSortSelected,
            onStatusSelected = viewModel::onStatusSelected,
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.novels.isEmpty() -> LoadingScreen()

                state.error != null && state.novels.isEmpty() ->
                    EmptyScreen(
                        message = state.error ?: "",
                        actionLabel = "Réessayer",
                        onAction = viewModel::refresh,
                    )

                state.novels.isEmpty() ->
                    EmptyScreen(message = "Aucun roman trouvé.")

                else -> NovelGrid(
                    novels = state.novels,
                    isLoadingMore = state.isLoadingMore,
                    onNovelClick = onNovelClick,
                    onLoadMore = viewModel::loadNextPage,
                )
            }
        }
    }
}

// Chips de genres + menu tri / statut (icône réglages), façon barre de filtres Mihon.
@Composable
private fun FiltersRow(
    state: ExploreUiState,
    onGenreSelected: (Long) -> Unit,
    onSortSelected: (String) -> Unit,
    onStatusSelected: (String?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val filtersActive = state.selectedGenreId != null || state.status != null || state.sort != "recent"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            lazyRowItems(items = state.genres, key = { it.id }) { genre ->
                FilterChip(
                    selected = genre.id == state.selectedGenreId,
                    onClick = { onGenreSelected(genre.id) },
                    label = { Text(genre.name) },
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Tri et filtres",
                    tint = if (filtersActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Text(
                    text = "Trier par",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                listOf(
                    "recent" to "Récents",
                    "title" to "Titre (A-Z)",
                    "popularity" to "Popularité",
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = {
                            if (state.sort == value) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onSortSelected(value)
                        },
                    )
                }
                HorizontalDivider()
                Text(
                    text = "Statut",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                listOf(
                    null to "Tous",
                    "ONGOING" to "En cours",
                    "COMPLETED" to "Terminés",
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = {
                            if (state.status == value) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onStatusSelected(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelGrid(
    novels: List<NovelDto>,
    isLoadingMore: Boolean,
    onNovelClick: (Long) -> Unit,
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
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = novels, key = { it.id }) { novel ->
            NovelGridItem(
                title = novel.title,
                coverUrl = novel.coverImageUrl,
                onClick = { onNovelClick(novel.id) },
            )
        }
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
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
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Effacer")
                }
            }
        },
        placeholder = { Text("Rechercher un roman…") },
        modifier = modifier,
    )
}
