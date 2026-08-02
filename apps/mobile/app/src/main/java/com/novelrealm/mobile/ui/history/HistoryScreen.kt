package com.novelrealm.mobile.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.HistoryEntryDto
import com.novelrealm.mobile.ui.components.COVER_RATIO_SQUARE
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelCover
import com.novelrealm.mobile.ui.util.relativeDayLabel
import com.novelrealm.mobile.ui.util.timeLabel

// Onglet Historique (#35), façon Mihon : entrées groupées par jour, tap = reprendre la
// lecture au chapitre, suppression par roman ou effacement complet.
@Composable
fun HistoryScreen(
    onOpenReader: (novelId: Long, chapterId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var showClearAllDialog by remember { mutableStateOf(false) }
    var pendingClearNovel by remember { mutableStateOf<HistoryEntryDto?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Pagination.
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(reachedEnd) { if (reachedEnd) viewModel.loadMore() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        ) {
            Text(
                text = "Historique",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (state.entries.isNotEmpty()) {
                IconButton(onClick = { showClearAllDialog = true }) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Tout effacer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> LoadingScreen()
                state.error != null && state.entries.isEmpty() -> EmptyScreen(
                    message = state.error ?: "",
                    actionLabel = "Réessayer",
                    onAction = viewModel::refresh,
                )
                state.entries.isEmpty() -> EmptyScreen(
                    message = "Rien dans l'historique pour l'instant.\nCommence un chapitre pour le voir ici !",
                )
                else -> {
                    val grouped = state.entries.groupBy { relativeDayLabel(it.readAt) }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (dayLabel, dayEntries) ->
                            item {
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            dayEntries.forEach { entry ->
                                item {
                                    HistoryRow(
                                        entry = entry,
                                        onClick = { onOpenReader(entry.novelId, entry.chapterId) },
                                        onDelete = { pendingClearNovel = entry },
                                    )
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Effacer tout l'historique ?") },
            text = { Text("Cette action est définitive.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearAll()
                    showClearAllDialog = false
                }) { Text("Effacer") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Annuler") }
            },
        )
    }
    pendingClearNovel?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingClearNovel = null },
            title = { Text("Retirer « ${entry.novelTitle} » ?") },
            text = { Text("Tout l'historique de ce roman sera effacé.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearNovel(entry.novelId)
                    pendingClearNovel = null
                }) { Text("Retirer") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClearNovel = null }) { Text("Annuler") }
            },
        )
    }
}

// Ligne d'historique façon Mihon : mini-couverture carrée, titre, « Chapitre N • HH:mm ».
@Composable
private fun HistoryRow(
    entry: HistoryEntryDto,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        NovelCover(
            coverUrl = entry.novelCoverImageUrl,
            contentDescription = entry.novelTitle,
            ratio = COVER_RATIO_SQUARE,
            modifier = Modifier.width(52.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.novelTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = buildList {
                add("Chapitre ${entry.chapterNumber}")
                val time = timeLabel(entry.readAt)
                if (time.isNotBlank()) add(time)
                if (!entry.read && entry.scrollPosition in 1..99) add("${entry.scrollPosition} %")
            }
            Text(
                text = details.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Retirer de l'historique",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
