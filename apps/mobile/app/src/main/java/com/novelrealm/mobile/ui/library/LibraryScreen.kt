package com.novelrealm.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelGridItem

// Onglet Bibliothèque (#35), façon Mihon : onglets de statut + étagères personnelles,
// grille de couvertures avec badge « chapitres non lus ».
@Composable
fun LibraryScreen(
    onNovelClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Rafraîchit à chaque retour sur l'onglet (la bibliothèque évolue depuis le détail).
    LaunchedEffect(Unit) { viewModel.refresh() }

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val statusTabs = listOf(
        "Tous" to null,
        "En cours" to "READING",
        "À lire" to "PLAN_TO_READ",
        "Terminés" to "COMPLETED",
        "En pause" to "PAUSED",
    )
    val tabTitles = statusTabs.map { it.first } + state.categories.map { it.name }
    val safeIndex = tabIndex.coerceIn(0, (tabTitles.size - 1).coerceAtLeast(0))
    val selectedShelf = if (safeIndex >= statusTabs.size) {
        state.categories.getOrNull(safeIndex - statusTabs.size)
    } else null

    Column(modifier = modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScrollableTabRow(
                selectedTabIndex = safeIndex,
                edgePadding = 12.dp,
                modifier = Modifier.weight(1f),
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = index == safeIndex,
                        onClick = { tabIndex = index },
                        text = { Text(title, maxLines = 1) },
                    )
                }
            }
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Créer une étagère")
            }
        }

        // Actions de l'étagère sélectionnée (renommer / supprimer).
        if (selectedShelf != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = selectedShelf.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Renommer l'étagère",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Supprimer l'étagère",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        val novels: List<Pair<NovelDto, Long>> = if (selectedShelf != null) {
            selectedShelf.novels.map { it to (state.unreadByNovel[it.id] ?: 0L) }
        } else {
            val status = statusTabs[safeIndex].second
            state.entries
                .filter { status == null || it.status == status }
                .map { it.novel to (state.unreadByNovel[it.novel.id] ?: 0L) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> LoadingScreen()
                state.error != null && state.entries.isEmpty() -> EmptyScreen(
                    message = state.error ?: "",
                    actionLabel = "Réessayer",
                    onAction = viewModel::refresh,
                )
                novels.isEmpty() -> EmptyScreen(
                    message = if (selectedShelf != null) "Cette étagère est vide."
                    else "Ta bibliothèque est vide.\nAjoute des romans depuis l'onglet Explorer !",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items = novels, key = { it.first.id }) { (novel, unread) ->
                        Box {
                            NovelGridItem(
                                title = novel.title,
                                coverUrl = novel.coverImageUrl,
                                onClick = { onNovelClick(novel.id) },
                            )
                            if (unread > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp),
                                ) {
                                    Text(
                                        text = "$unread",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ShelfNameDialog(
            title = "Nouvelle étagère",
            initialValue = "",
            onConfirm = { viewModel.createShelf(it); showCreateDialog = false },
            onDismiss = { showCreateDialog = false },
        )
    }
    if (showRenameDialog && selectedShelf != null) {
        ShelfNameDialog(
            title = "Renommer l'étagère",
            initialValue = selectedShelf.name,
            onConfirm = { viewModel.renameShelf(selectedShelf.id, it); showRenameDialog = false },
            onDismiss = { showRenameDialog = false },
        )
    }
    if (showDeleteDialog && selectedShelf != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer « ${selectedShelf.name} » ?") },
            text = { Text("Les romans resteront dans ta bibliothèque, seule l'étagère disparaît.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteShelf(selectedShelf.id)
                    tabIndex = 0
                    showDeleteDialog = false
                }) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun ShelfNameDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
