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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Onglet Bibliothèque, structuré comme celui de Mihon : des onglets défilables au-dessus
 * d'un **pager** — on passe donc d'une catégorie à l'autre en glissant le doigt, et le
 * soulignement de l'onglet suit le geste.
 *
 * Les onglets ne contiennent **que** « Tous » et les étagères créées par l'utilisateur,
 * toutes renommables et supprimables. Le statut de lecture (À lire / En cours / En pause
 * / Terminé) n'est pas une étagère : c'est un **filtre**, dans l'en-tête, qui s'applique
 * à l'onglet affiché — on peut donc voir « les romans en cours de telle étagère », ce que
 * des onglets séparés ne permettaient pas.
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
    // `rememberSaveable` : le filtre survit à une rotation ou au passage en arrière-plan.
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val tabs = rememberLibraryTabs(state, statusFilter)
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
            statusFilter = statusFilter,
            onStatusFilterChange = { statusFilter = it },
            onCreateShelf = { showCreateDialog = true },
        )

        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = safePage,
                edgePadding = 16.dp,
                // Fond transparent : l'en-tête et les onglets forment un seul bloc, au
                // lieu de deux bandes de teintes voisines qui se cherchent.
                containerColor = Color.Transparent,
                // Le séparateur par défaut est posé DANS la zone défilable : il s'arrêtait
                // donc à la fin des onglets, à quelques dizaines de pixels du bord droit.
                // On le sort de là pour en tracer un vrai, pleine largeur et discret.
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == safePage,
                        // `animateScrollToPage` : le contenu glisse au lieu de sauter,
                        // exactement comme quand on balaie l'écran à la main.
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        // Material 3 donne par défaut la MÊME couleur aux onglets actif et
                        // inactifs : tous ressortaient en accent, et seul le soulignement
                        // distinguait la sélection. On rend les inactifs sourds.
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            TabLabel(
                                label = tab.label,
                                count = tab.novels.size,
                                selected = index == safePage,
                            )
                        },
                    )
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
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
                                statusFilter = statusFilter,
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
 * Construit les pages : « Tous » puis les étagères de l'utilisateur — et rien d'autre,
 * pour que chaque onglet corresponde à quelque chose qu'il a créé et peut supprimer.
 *
 * Le filtre de statut s'applique ici, à toutes les pages d'un coup : c'est une lecture
 * transversale de la bibliothèque, pas une page supplémentaire.
 *
 * `distinctBy` : une clé dupliquée dans une grille Lazy provoque un plantage
 * (« Key was already used »), garde-fou peu coûteux.
 */
@Composable
private fun rememberLibraryTabs(
    state: LibraryUiState,
    statusFilter: String?,
): List<LibraryTab> = remember(state, statusFilter) {
    // Une seule passe sur les entrées : les étagères ne portent pas le statut, il faut
    // le retrouver par roman (sinon on referait une recherche linéaire par couverture).
    val statusByNovel = state.entries.associate { it.novel.id to it.status }
    fun List<NovelDto>.applyFilter(): List<NovelDto> =
        if (statusFilter == null) this else filter { statusByNovel[it.id] == statusFilter }

    buildList {
        add(LibraryTab("Tous", state.entries.map { it.novel }.distinctBy { it.id }.applyFilter()))
        state.categories.forEach { shelf ->
            add(
                LibraryTab(
                    label = shelf.name,
                    novels = shelf.novels.distinctBy { it.id }.applyFilter(),
                    shelf = shelf,
                ),
            )
        }
    }
}

/** Titre + total suivi + filtre par statut + création d'étagère. */
@Composable
private fun LibraryHeader(
    total: Int,
    statusFilter: String?,
    onStatusFilterChange: (String?) -> Unit,
    onCreateShelf: () -> Unit,
) {
    var filterMenuOpen by remember { mutableStateOf(false) }
    val activeStatus = ReadingStatus.fromId(statusFilter)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Bibliothèque",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // Le filtre actif est rappelé ici : sinon une bibliothèque filtrée
                // ressemble à une bibliothèque vide, sans qu'on sache pourquoi.
                text = buildString {
                    append(if (total <= 1) "$total roman suivi" else "$total romans suivis")
                    if (activeStatus != null) append(" · ${activeStatus.label}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (activeStatus != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            // Le filtre se remplit d'accent quand il est actif : l'état se voit sur le
            // bouton lui-même, pas seulement dans le sous-titre.
            HeaderAction(
                icon = if (activeStatus != null) Icons.Filled.FilterListOff
                else Icons.Filled.FilterList,
                contentDescription = "Filtrer par statut de lecture",
                filled = activeStatus != null,
                onClick = { filterMenuOpen = true },
            )
            DropdownMenu(expanded = filterMenuOpen, onDismissRequest = { filterMenuOpen = false }) {
                StatusFilterItem(
                    label = "Tous les statuts",
                    icon = Icons.Filled.FilterListOff,
                    selected = statusFilter == null,
                    onClick = { filterMenuOpen = false; onStatusFilterChange(null) },
                )
                HorizontalDivider()
                ReadingStatus.entries.forEach { status ->
                    StatusFilterItem(
                        label = status.label,
                        icon = status.icon,
                        selected = status.id == statusFilter,
                        // Re-toucher le statut déjà actif le retire : pas besoin de
                        // rouvrir le menu pour revenir à la vue complète.
                        onClick = {
                            filterMenuOpen = false
                            onStatusFilterChange(if (status.id == statusFilter) null else status.id)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(10.dp))
        HeaderAction(
            icon = Icons.Filled.CreateNewFolder,
            contentDescription = "Créer une étagère",
            filled = true,
            onClick = onCreateShelf,
        )
    }
}

/**
 * Bouton d'en-tête : pastille ronde de 42 dp. Les deux actions ont ainsi la même
 * silhouette — une icône nue à côté d'une pastille pleine donnait deux poids visuels
 * différents pour deux commandes de même rang.
 */
@Composable
private fun HeaderAction(
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.clickable(onClick = onClick),
        ) {
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
private fun StatusFilterItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Filtre actif",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}

/**
 * Libellé d'onglet avec son compteur, façon Mihon. Le compteur de l'onglet actif se
 * teinte d'accent : la sélection se lit alors sur deux signaux (soulignement + pastille)
 * plutôt qu'un seul, ce qui reste net même en coup d'œil rapide.
 */
@Composable
private fun TabLabel(label: String, count: Int, selected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(7.dp),
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
private fun emptyMessage(isShelf: Boolean, libraryEmpty: Boolean, statusFilter: String?): String {
    // Le filtre passe avant tout le reste : la page n'est pas vide, elle est filtrée, et
    // proposer « ajoute des romans » à quelqu'un qui en a serait trompeur.
    val status = ReadingStatus.fromId(statusFilter)
    return when {
        status != null ->
            "Aucun roman en « ${status.label} » ici.\n" +
                "Appuie longuement sur une couverture pour changer son statut."
        isShelf -> "Cette étagère est vide.\nOuvre un roman puis appuie sur ♥ pour l'y ranger."
        libraryEmpty -> "Ta bibliothèque est vide.\nAjoute des romans depuis l'onglet Explorer !"
        else -> "Aucun roman ici pour le moment."
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
