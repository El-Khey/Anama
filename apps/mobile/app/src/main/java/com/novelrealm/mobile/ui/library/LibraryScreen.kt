package com.novelrealm.mobile.ui.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelGridItem
import com.novelrealm.mobile.ui.util.ReadingStatus
import kotlinx.coroutines.launch

/**
 * Onglet Bibliothèque, structuré comme celui de Mihon : des onglets défilables (statuts
 * de lecture puis étagères personnelles, chacun avec son compteur) au-dessus d'un
 * **pager** — on passe donc d'une catégorie à l'autre en glissant le doigt, et le
 * soulignement de l'onglet suit le geste.
 *
 * Un **appui long** sur une couverture ouvre les actions rapides (changer de statut,
 * retirer) : classer un roman ne demande pas d'ouvrir sa fiche.
 */
@Composable
fun LibraryScreen(
    onNovelClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Rafraîchit à chaque retour sur l'onglet (la bibliothèque évolue depuis le détail).
    LaunchedEffect(Unit) { viewModel.refresh() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var quickActionsFor by remember { mutableStateOf<NovelDto?>(null) }

    val tabs = rememberLibraryTabs(state)
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Si une étagère est supprimée, la page courante peut dépasser la liste le temps
    // d'une recomposition. Le test `isEmpty` n'est pas décoratif : sur une liste vide,
    // `coerceIn(0, -1)` lève une exception (et l'indicateur par défaut de
    // ScrollableTabRow indexe `tabPositions[selectedTabIndex]`).
    val safePage = if (tabs.isEmpty()) 0 else pagerState.currentPage.coerceIn(0, tabs.lastIndex)
    val selectedShelf = tabs.getOrNull(safePage)?.shelf

    Column(modifier = modifier.fillMaxSize()) {
        LibraryHeader(
            total = state.entries.size,
            onCreateShelf = { showCreateDialog = true },
        )

        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = safePage,
                edgePadding = 12.dp,
                divider = { HorizontalDivider() },
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == safePage,
                        // `animateScrollToPage` : le contenu glisse au lieu de sauter,
                        // exactement comme quand on balaie l'écran à la main.
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { TabLabel(label = tab.label, count = tab.novels.size) },
                    )
                }
            }
        }

        // Actions de l'étagère affichée (renommer / supprimer).
        if (selectedShelf != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
            ) {
                Text(
                    text = "Étagère « ${selectedShelf.name} »",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRenameDialog = true }) {
                    Icon(
                        Icons.Filled.DriveFileRenameOutline,
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

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && state.entries.isEmpty() -> LoadingScreen()
                state.error != null && state.entries.isEmpty() -> EmptyScreen(
                    message = state.error ?: "",
                    actionLabel = "Réessayer",
                    onAction = viewModel::refresh,
                )
                else -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // Prépare la page voisine pour que le glissement reste fluide.
                    beyondViewportPageCount = 1,
                ) { page ->
                    val tab = tabs.getOrNull(page) ?: return@HorizontalPager
                    if (tab.novels.isEmpty()) {
                        EmptyScreen(
                            message = emptyMessage(
                                isShelf = tab.shelf != null,
                                libraryEmpty = state.entries.isEmpty(),
                                filterLabel = tab.label,
                            ),
                        )
                    } else {
                        LibraryGrid(
                            novels = tab.novels,
                            unreadByNovel = state.unreadByNovel,
                            readFractionByNovel = state.readFractionByNovel,
                            onNovelClick = onNovelClick,
                            onNovelLongClick = { quickActionsFor = it },
                        )
                    }
                }
            }
        }
    }

    // ── Dialogues ──

    quickActionsFor?.let { novel ->
        QuickActionsDialog(
            novel = novel,
            currentStatus = state.statusOf(novel.id),
            onSetStatus = { status ->
                viewModel.setStatus(novel.id, status)
                quickActionsFor = null
            },
            onRemove = {
                viewModel.removeFromLibrary(novel.id)
                quickActionsFor = null
            },
            onOpen = {
                quickActionsFor = null
                onNovelClick(novel.id)
            },
            onDismiss = { quickActionsFor = null },
        )
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
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text("Supprimer « ${selectedShelf.name} » ?") },
            text = { Text("Les romans resteront dans ta bibliothèque, seule l'étagère disparaît.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteShelf(selectedShelf.id)
                    scope.launch { pagerState.animateScrollToPage(0) }
                    showDeleteDialog = false
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") }
            },
        )
    }
}

/** Une page de la bibliothèque : un statut de lecture, ou une étagère personnelle. */
private data class LibraryTab(
    val label: String,
    val novels: List<NovelDto>,
    /** Non-null seulement pour les étagères (permet renommer / supprimer). */
    val shelf: CategoryDto? = null,
)

/**
 * Construit les pages : « Tous », les 4 statuts, puis les étagères.
 *
 * `distinctBy` : une clé dupliquée dans une grille Lazy provoque un plantage
 * (« Key was already used »), garde-fou peu coûteux.
 */
@Composable
private fun rememberLibraryTabs(state: LibraryUiState): List<LibraryTab> = remember(state) {
    buildList {
        add(LibraryTab("Tous", state.entries.map { it.novel }.distinctBy { it.id }))
        ReadingStatus.entries.forEach { status ->
            add(
                LibraryTab(
                    label = status.label,
                    novels = state.entries
                        .filter { it.status == status.id }
                        .map { it.novel }
                        .distinctBy { it.id },
                ),
            )
        }
        state.categories.forEach { shelf ->
            add(
                LibraryTab(
                    label = shelf.name,
                    novels = shelf.novels.distinctBy { it.id },
                    shelf = shelf,
                ),
            )
        }
    }
}

/** Titre + total suivi + création d'étagère. */
@Composable
private fun LibraryHeader(total: Int, onCreateShelf: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Bibliothèque",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (total <= 1) "$total roman suivi" else "$total romans suivis",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCreateShelf) {
            Icon(
                Icons.Filled.CreateNewFolder,
                contentDescription = "Créer une étagère",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Libellé d'onglet avec son compteur, façon Mihon. */
@Composable
private fun TabLabel(label: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    novels: List<NovelDto>,
    unreadByNovel: Map<Long, Long>,
    readFractionByNovel: Map<Long, Float>,
    onNovelClick: (Long) -> Unit,
    onNovelLongClick: (NovelDto) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = novels, key = { it.id }) { novel ->
            NovelGridItem(
                title = novel.title,
                coverUrl = novel.coverImageUrl,
                unreadCount = unreadByNovel[novel.id] ?: 0L,
                readFraction = readFractionByNovel[novel.id],
                onClick = { onNovelClick(novel.id) },
                onLongClick = { onNovelLongClick(novel) },
            )
        }
    }
}

/** Actions rapides sur un roman de la bibliothèque (appui long sur la couverture). */
@Composable
private fun QuickActionsDialog(
    novel: NovelDto,
    currentStatus: String?,
    onSetStatus: (String) -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = novel.title, maxLines = 2, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column {
                Text(
                    text = "Classer dans",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                ReadingStatus.entries.forEach { status ->
                    val isCurrent = status.id == currentStatus
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetStatus(status.id) }
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(
                            status.icon,
                            contentDescription = null,
                            tint = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCurrent) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Statut actuel",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRemove)
                        .padding(vertical = 12.dp),
                ) {
                    Icon(
                        Icons.Filled.HeartBroken,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = "Retirer de la bibliothèque",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) { Text("Ouvrir la fiche") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

/** Message d'état vide adapté à l'onglet courant (un texte générique n'aiderait pas). */
private fun emptyMessage(isShelf: Boolean, libraryEmpty: Boolean, filterLabel: String): String = when {
    isShelf -> "Cette étagère est vide.\nOuvre un roman puis appuie sur ♥ pour l'y ranger."
    libraryEmpty -> "Ta bibliothèque est vide.\nAjoute des romans depuis l'onglet Explorer !"
    else -> "Aucun roman en « $filterLabel ».\nAppuie longuement sur une couverture pour la classer."
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
                shape = RoundedCornerShape(14.dp),
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
