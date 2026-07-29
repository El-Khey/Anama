package com.novelrealm.mobile.ui.discover

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.HistoryEntryDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.ui.NovelRealmWordmark
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelCover
import com.novelrealm.mobile.ui.components.NovelGridItem

// Onglet Accueil (#35) : « Reprendre la lecture », « Populaires » et « Nouveautés »
// en rangées horizontales de couvertures.
@Composable
fun DiscoverScreen(
    onNovelClick: (Long) -> Unit,
    onOpenReader: (novelId: Long, chapterId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> EmptyScreen(
                message = state.error ?: "",
                actionLabel = "Réessayer",
                onAction = viewModel::refresh,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    NovelRealmWordmark(
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                if (state.continueReading.isNotEmpty()) {
                    item { SectionHeader("Reprendre la lecture") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items = state.continueReading, key = { it.chapterId }) { entry ->
                                ContinueCard(
                                    entry = entry,
                                    onClick = { onOpenReader(entry.novelId, entry.chapterId) },
                                )
                            }
                        }
                    }
                }
                if (state.popular.isNotEmpty()) {
                    item { SectionHeader("Populaires") }
                    item { NovelRowStrip(novels = state.popular, onNovelClick = onNovelClick) }
                }
                if (state.recent.isNotEmpty()) {
                    item { SectionHeader("Nouveautés") }
                    item { NovelRowStrip(novels = state.recent, onNovelClick = onNovelClick) }
                }
                if (state.continueReading.isEmpty() && state.popular.isEmpty() && state.recent.isEmpty()) {
                    item {
                        EmptyScreen(message = "Le catalogue est vide pour l'instant.")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

// Rangée horizontale de couvertures (cartes compactes façon Mihon).
@Composable
private fun NovelRowStrip(novels: List<NovelDto>, onNovelClick: (Long) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = novels, key = { it.id }) { novel ->
            NovelGridItem(
                title = novel.title,
                coverUrl = novel.coverImageUrl,
                onClick = { onNovelClick(novel.id) },
                modifier = Modifier.width(120.dp),
            )
        }
    }
}

// Carte « Reprendre » : couverture + pastille lecture + titre + chapitre en cours.
@Composable
private fun ContinueCard(entry: HistoryEntryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            NovelCover(
                coverUrl = entry.novelCoverImageUrl,
                contentDescription = entry.novelTitle,
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reprendre",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(4.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.novelTitle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Chapitre ${entry.chapterNumber}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
