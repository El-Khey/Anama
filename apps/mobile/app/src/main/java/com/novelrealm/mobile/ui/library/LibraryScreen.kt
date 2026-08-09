package com.novelrealm.mobile.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelCover
import com.novelrealm.mobile.ui.components.NovelGridItem
import com.novelrealm.mobile.ui.components.SheetScrim
import com.novelrealm.mobile.ui.components.selectionStyle
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
 * Un **appui long** sur une couverture ouvre les actions rapides — statut de lecture,
 * étagères, tout marquer comme lu, retrait. Classer un roman ne demande donc jamais
 * d'ouvrir sa fiche.
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
    /** Roman en attente d'une étagère à créer (« Nouvelle étagère… » de la feuille). */
    var createShelfForNovel by remember { mutableStateOf<NovelDto?>(null) }
    // `rememberSaveable` : le filtre survit à une rotation ou au passage en arrière-plan.
    var statusFilter by rememberSaveable { mutableStateOf<String?>(null) }

    // Le roman affiché par la feuille, conservé le temps de l'animation de fermeture :
    // `quickActionsFor` repasse à null dès le premier frame de sortie, et la feuille se
    // viderait sous les yeux au lieu de glisser vers le bas. Posé en même temps que lui,
    // pas dans un effet — sinon la feuille s'ouvrirait sur une frame vide.
    var sheetNovel by remember { mutableStateOf<NovelDto?>(null) }

    val tabs = rememberLibraryTabs(state, statusFilter)
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // Si une étagère est supprimée, la page courante peut dépasser la liste le temps
    // d'une recomposition. Le test `isEmpty` n'est pas décoratif : sur une liste vide,
    // `coerceIn(0, -1)` lève une exception (et l'indicateur par défaut de
    // ScrollableTabRow indexe `tabPositions[selectedTabIndex]`).
    val safePage = if (tabs.isEmpty()) 0 else pagerState.currentPage.coerceIn(0, tabs.lastIndex)
    val selectedShelf = tabs.getOrNull(safePage)?.shelf

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                                onNovelLongClick = { quickActionsFor = it; sheetNovel = it },
                            )
                        }
                    }
                }
            }
        }

        // ── Feuille d'actions rapides (appui long sur une couverture) ──
        //
        // Une feuille plutôt qu'un dialogue centré : elle monte sous le pouce, se ferme
        // d'un glissement, et surtout elle a la place d'accueillir la liste des étagères —
        // ce qu'un AlertDialog, contraint en hauteur, ne permettait pas.
        AnimatedVisibility(
            visible = quickActionsFor != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            SheetScrim(onDismiss = { quickActionsFor = null }, modifier = Modifier.fillMaxSize())
        }
        AnimatedVisibility(
            visible = quickActionsFor != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            sheetNovel?.let { novel ->
                NovelActionsSheet(
                    novel = novel,
                    unreadCount = state.unreadByNovel[novel.id] ?: 0L,
                    shelves = state.categories,
                    onToggleShelf = { shelfId -> viewModel.toggleShelf(novel.id, shelfId) },
                    // La feuille se referme, mais le roman est retenu : l'étagère créée
                    // doit l'accueillir dans la foulée, sans qu'on ait à rouvrir pour
                    // cocher la case qu'on vient de créer.
                    onCreateShelf = {
                        quickActionsFor = null
                        createShelfForNovel = novel
                    },
                    onMarkAllRead = {
                        viewModel.markAllRead(novel.id)
                        quickActionsFor = null
                    },
                    onOpen = {
                        quickActionsFor = null
                        onNovelClick(novel.id)
                    },
                    onRemove = {
                        viewModel.removeFromLibrary(novel.id)
                        quickActionsFor = null
                    },
                )
            }
        }
    }

    // ── Dialogues ──

    if (showCreateDialog) {
        ShelfNameDialog(
            title = "Nouvelle étagère",
            initialValue = "",
            onConfirm = { viewModel.createShelf(it); showCreateDialog = false },
            onDismiss = { showCreateDialog = false },
        )
    }
    createShelfForNovel?.let { novel ->
        ShelfNameDialog(
            title = "Ranger « ${novel.title} »",
            initialValue = "",
            onConfirm = {
                viewModel.createShelfAndAdd(novel.id, it)
                createShelfForNovel = null
            },
            onDismiss = { createShelfForNovel = null },
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

// ── Feuille d'actions rapides ──────────────────────────────────────────────────

/**
 * Ce qu'on peut faire d'un roman suivi sans ouvrir sa fiche : le ranger, le marquer lu,
 * le retirer.
 *
 * <p><b>Compacte par construction.</b> Une première version listait tout en pleine
 * largeur — quatre statuts, une ligne par étagère, trois actions — et couvrait l'écran
 * entier. Ici les étagères sont des pastilles sur une seule ligne et les actions trois
 * tuiles côte à côte : la feuille tient en un tiers d'écran, quel que soit le nombre
 * d'étagères, et n'a donc plus besoin de défiler.
 *
 * <p><b>Pas de statut de lecture.</b> Il se règle depuis la fiche du roman, pas d'un
 * appui long : c'est un choix qu'on pose en connaissance de cause, pas au passage sur
 * une couverture.
 *
 * <p>Ranger s'applique sans fermer, pour enchaîner plusieurs étagères. Les trois actions
 * du bas, elles, font quitter la liste et referment.
 */
@Composable
private fun NovelActionsSheet(
    novel: NovelDto,
    unreadCount: Long,
    shelves: List<CategoryDto>,
    onToggleShelf: (Long) -> Unit,
    onCreateShelf: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    // Le retrait vide aussi les étagères : il demande un second appui, contrairement au
    // rangement, qui se rectifie d'un geste.
    var confirmRemove by remember(novel.id) { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Pas de `navigationBarsPadding` ici : l'onglet vit déjà dans la zone que le
        // Scaffold a réservée au-dessus de la barre d'onglets, qui tient elle-même compte
        // de la barre système. L'ajouter creusait un vide mort en bas de la feuille.
        Column(modifier = Modifier.padding(bottom = 18.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )

            // En-tête : la couverture lève le doute après un appui long imprécis — le
            // titre seul ne disait pas toujours quel roman on s'apprête à modifier.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
            ) {
                NovelCover(
                    coverUrl = novel.coverImageUrl,
                    contentDescription = null,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier.width(44.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = novel.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        // L'auteur identifie, le reste à lire informe. Deux choses utiles
                        // pour une ligne, là où le statut n'apprenait rien qu'on ne sache.
                        text = listOfNotNull(
                            novel.author?.takeIf { it.isNotBlank() },
                            if (unreadCount > 0) "$unreadCount non lus" else null,
                        ).joinToString(" · ").ifEmpty { "À jour" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            SheetSectionLabel("Étagères")
            // Une seule ligne qui défile, plutôt qu'une ligne par étagère : la hauteur de
            // la feuille ne dépend plus du nombre d'étagères. `horizontalScroll` et non
            // `FlowRow`, qui reste une API expérimentale.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                shelves.forEach { shelf ->
                    ShelfChip(
                        label = shelf.name,
                        active = shelf.novels.any { it.id == novel.id },
                        onClick = { onToggleShelf(shelf.id) },
                    )
                }
                ShelfChip(label = "Nouvelle", active = false, isNew = true, onClick = onCreateShelf)
            }

            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp),
            ) {
                ActionTile(
                    icon = Icons.Filled.DoneAll,
                    label = "Tout lu",
                    onClick = onMarkAllRead,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    icon = Icons.Filled.OpenInNew,
                    label = "Fiche",
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    icon = Icons.Filled.HeartBroken,
                    // Le second libellé dit ce qui part avec : sans lui, on découvre le
                    // rangement perdu en rouvrant l'étagère.
                    label = if (confirmRemove) "Sûr ?" else "Retirer",
                    destructive = true,
                    armed = confirmRemove,
                    onClick = { if (confirmRemove) onRemove() else confirmRemove = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (confirmRemove) {
                Text(
                    text = "Le roman quittera aussi ses étagères.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** Étagère sous forme de pastille : pleine quand le roman y est rangé. */
@Composable
private fun ShelfChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    isNew: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    val style = selectionStyle(active)
    Surface(color = style.container, shape = shape, modifier = Modifier.border(1.dp, style.border, shape)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            Icon(
                imageVector = when {
                    isNew -> Icons.Filled.CreateNewFolder
                    active -> Icons.Filled.Folder
                    else -> Icons.Outlined.Folder
                },
                contentDescription = null,
                tint = style.content,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = style.content,
                maxLines = 1,
            )
        }
    }
}

/** Action de la feuille : icône au-dessus du libellé, trois par ligne. */
@Composable
private fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    armed: Boolean = false,
) {
    val container = when {
        armed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        armed -> MaterialTheme.colorScheme.onError
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(16.dp), modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(vertical = 13.dp, horizontal = 6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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
        isShelf -> "Cette étagère est vide.\n" +
            "Appuie longuement sur une couverture pour y ranger un roman."
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
