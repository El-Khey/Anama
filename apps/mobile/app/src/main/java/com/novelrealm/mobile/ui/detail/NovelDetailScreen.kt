package com.novelrealm.mobile.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.ChapterDto
import com.novelrealm.mobile.data.remote.dto.ChapterProgressDto
import com.novelrealm.mobile.data.remote.dto.NovelDetailDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelCover
import com.novelrealm.mobile.ui.util.ReadingStatus
import com.novelrealm.mobile.ui.util.vmFactory

/**
 * Fiche d'un roman : en-tête à couverture floutée, actions, avancement de lecture,
 * description et liste des chapitres.
 *
 * Les fonctionnalités sont volontairement rendues **visibles** plutôt que cachées :
 * le statut de lecture est une rangée de pastilles (et non un menu), chaque chapitre a
 * son menu (signet, lu/non lu, « marquer les précédents »), et un appui long ouvre la
 * sélection multiple pour agir sur plusieurs chapitres d'un coup.
 */
@Composable
fun NovelDetailScreen(
    novelId: Long,
    onBack: () -> Unit,
    onOpenReader: (novelId: Long, chapterId: Long) -> Unit,
    onOpenReviews: (novelId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NovelDetailViewModel = viewModel(factory = vmFactory { NovelDetailViewModel(novelId) }),
) {
    val state by viewModel.state.collectAsState()

    // Au retour du lecteur, la progression et les signets ont pu changer.
    LaunchedEffect(Unit) { viewModel.refreshProgress() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> EmptyScreen(
                message = state.error ?: "",
                actionLabel = "Réessayer",
                onAction = viewModel::load,
            )
            state.novel != null -> DetailContent(
                state = state,
                viewModel = viewModel,
                onOpenReader = onOpenReader,
                onOpenReviews = { onOpenReviews(novelId) },
            )
        }

        // Barre de sélection : remplace le bouton retour tant que des chapitres sont cochés.
        if (state.isSelecting) {
            SelectionBar(
                count = state.selectedChapterIds.size,
                allFavorited = state.selectedChapterIds.all { it in state.favoriteChapterIds },
                onClose = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onMarkRead = {
                    viewModel.markChapters(state.selectedChapterIds.toList(), read = true)
                    viewModel.clearSelection()
                },
                onMarkUnread = {
                    viewModel.markChapters(state.selectedChapterIds.toList(), read = false)
                    viewModel.clearSelection()
                },
                onToggleBookmark = { favorite ->
                    viewModel.setChaptersFavorite(state.selectedChapterIds.toList(), favorite)
                    viewModel.clearSelection()
                },
            )
        } else {
            TopChrome(onBack = onBack)
        }
    }
}

@Composable
private fun DetailContent(
    state: NovelDetailUiState,
    viewModel: NovelDetailViewModel,
    onOpenReader: (Long, Long) -> Unit,
    onOpenReviews: () -> Unit,
) {
    val novel = state.novel ?: return
    val listState = rememberLazyListState()
    var showLibraryDialog by remember { mutableStateOf(false) }
    val shelvesCount = state.categories.count { c -> c.novels.any { it.id == novel.id } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { DetailHeader(novel = novel, chapterCount = state.chapters.size) }

            item(key = "actions") {
                DetailActionRow(
                    inLibrary = state.libraryStatus != null,
                    shelvesCount = shelvesCount,
                    reviewCount = novel.ratingCount,
                    onOpenLibrary = { showLibraryDialog = true },
                    onOpenReviews = onOpenReviews,
                )
            }

            // Le statut de lecture est une rangée de pastilles toujours visible : il ne
            // faut plus deviner qu'il se cache dans un menu derrière le cœur.
            item(key = "status") {
                ReadingStatusRow(
                    current = state.libraryStatus,
                    onSelect = viewModel::setLibraryStatus,
                )
            }

            if (state.chapters.isNotEmpty()) {
                item(key = "progress") {
                    ReadingProgressCard(
                        readCount = state.readCount,
                        total = state.chapters.size,
                        fraction = state.readFraction,
                    )
                }
            }

            item(key = "description") { ExpandableDescription(novel = novel) }

            item(key = "chapterHeader") {
                ChapterHeaderRow(
                    count = state.chapters.size,
                    ascending = state.ascending,
                    onToggleOrder = viewModel::toggleSortOrder,
                    onMarkAllRead = { viewModel.markAllRead(true) },
                    onMarkAllUnread = { viewModel.markAllRead(false) },
                    onSelectAll = viewModel::selectAll,
                )
            }

            items(items = state.orderedChapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    progress = state.progress[chapter.id],
                    favorited = chapter.id in state.favoriteChapterIds,
                    selected = chapter.id in state.selectedChapterIds,
                    selectionMode = state.isSelecting,
                    onClick = {
                        if (state.isSelecting) viewModel.toggleSelection(chapter.id)
                        else onOpenReader(novel.id, chapter.id)
                    },
                    onLongClick = { viewModel.toggleSelection(chapter.id) },
                    onToggleRead = { read -> viewModel.markChapterRead(chapter.id, read) },
                    onToggleBookmark = { viewModel.toggleChapterFavorite(chapter.id) },
                    onMarkUpToHere = { viewModel.markUpToRead(chapter.id) },
                )
            }
        }

        // Bouton principal : masqué pendant la sélection, où il gênerait les actions.
        val resume = state.resumeChapter
        if (resume != null && !state.isSelecting) {
            ExtendedFloatingActionButton(
                onClick = { onOpenReader(novel.id, resume.id) },
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = {
                    Text(
                        if (state.readCount > 0) "Reprendre ch. ${resume.chapterNumber}"
                        else "Commencer",
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }

    if (showLibraryDialog) {
        CategoryPickerDialog(
            categories = state.categories,
            novelId = novel.id,
            inLibrary = state.libraryStatus != null,
            onConfirm = { selectedIds ->
                // Ranger un roman implique de le suivre : on l'ajoute à la bibliothèque
                // au passage, plutôt que de bloquer sur un prérequis invisible.
                if (state.libraryStatus == null) viewModel.setLibraryStatus(ReadingStatus.READING.id)
                viewModel.applyShelves(selectedIds)
                showLibraryDialog = false
            },
            onCreateCategory = viewModel::createShelfAndAdd,
            onRemoveFromLibrary = {
                viewModel.removeFromLibrary()
                showLibraryDialog = false
            },
            onDismiss = { showLibraryDialog = false },
        )
    }
}

// ── En-tête ────────────────────────────────────────────────────────────────────

/** Couverture floutée en toile de fond, vraie couverture + titre / auteur / méta devant. */
@Composable
private fun DetailHeader(novel: NovelDetailDto, chapterCount: Int) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = resolveImageUrl(novel.coverImageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(18.dp)
                .alpha(0.35f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                        0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 60.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NovelCover(
                coverUrl = novel.coverImageUrl,
                contentDescription = novel.title,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.width(118.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!novel.author.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    MetaLine(icon = Icons.Filled.Person, text = novel.author)
                }
                Spacer(Modifier.height(4.dp))
                MetaLine(
                    icon = if (novel.status == "COMPLETED") Icons.Filled.DoneAll else Icons.Filled.Schedule,
                    text = buildString {
                        append(statusLabel(novel.status))
                        if (chapterCount > 0) append(" · $chapterCount ch.")
                    },
                )
                if (novel.ratingCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    MetaLine(
                        icon = Icons.Filled.Star,
                        text = "%.1f · %d avis".format(novel.averageRating, novel.ratingCount),
                        iconTint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Ligne « icône + texte » des métadonnées du roman. */
@Composable
private fun MetaLine(icon: ImageVector, text: String, iconTint: Color? = null) {
    val muted = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = iconTint ?: muted,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Actions ────────────────────────────────────────────────────────────────────

/**
 * Deux actions seulement : le cœur ouvre la bibliothèque (suivi **et** rangement dans
 * les étagères, réunis au même endroit), l'étoile ouvre les avis. Le **statut** de
 * lecture a sa propre rangée juste en dessous.
 */
@Composable
private fun DetailActionRow(
    inLibrary: Boolean,
    shelvesCount: Int,
    reviewCount: Long,
    onOpenLibrary: () -> Unit,
    onOpenReviews: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        DetailActionButton(
            icon = if (inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            label = when {
                !inLibrary -> "Ajouter"
                shelvesCount > 0 -> "Dans ma biblio ($shelvesCount)"
                else -> "Dans ma biblio"
            },
            active = inLibrary,
            onClick = onOpenLibrary,
            modifier = Modifier.weight(1f),
        )
        DetailActionButton(
            icon = Icons.Filled.Star,
            label = if (reviewCount > 0) "Avis ($reviewCount)" else "Donner un avis",
            active = false,
            onClick = onOpenReviews,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rangée de pastilles de statut : un appui suffit, y compris si le roman n'est pas suivi. */
@Composable
private fun ReadingStatusRow(current: String?, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "STATUT DE LECTURE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = ReadingStatus.entries.toList(), key = { it.id }) { status ->
                val selected = status.id == current
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onSelect(status.id) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Icon(
                            status.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Carte d'avancement : combien de chapitres lus, et une barre de progression. */
@Composable
private fun ReadingProgressCard(readCount: Int, total: Int, fraction: Float) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        readCount == 0 -> "Pas encore commencé"
                        readCount >= total -> "Terminé ! 🎉"
                        else -> "$readCount / $total chapitres lus"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(fraction * 100).toInt()} %",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            // Barre dessinée à la main : `LinearProgressIndicator` a changé de signature
            // entre versions de Material 3, deux Box sont insensibles à ce risque.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

// ── Description ────────────────────────────────────────────────────────────────

@Composable
private fun ExpandableDescription(novel: NovelDetailDto) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .animateContentSize(),
    ) {
        if (!novel.description.isNullOrBlank()) {
            Column(modifier = Modifier.clickable { expanded = !expanded }) {
                Text(
                    text = novel.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = if (expanded) "Réduire" else "Lire la suite",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (novel.genres.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            // Rangée défilable (API stable, contrairement à FlowRow dont la signature
            // varie selon la version de compose-foundation).
            // `distinctBy` : une jointure renvoyant deux fois le même genre ferait
            // planter la liste (« Key was already used »). Garde-fou peu coûteux.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = novel.genres.distinctBy { it.id }, key = { it.id }) { genre ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),   // rectangle, comme les filtres
                    ) {
                        Text(
                            text = genre.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Chapitres ──────────────────────────────────────────────────────────────────

@Composable
private fun ChapterHeaderRow(
    count: Int,
    ascending: Boolean,
    onToggleOrder: () -> Unit,
    onMarkAllRead: () -> Unit,
    onMarkAllUnread: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count <= 1) "$count chapitre" else "$count chapitres",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleOrder) {
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = if (ascending) "Trier du plus récent au plus ancien"
                else "Trier du plus ancien au plus récent",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Actions sur les chapitres",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Tout marquer comme lu") },
                    leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                    onClick = { menuOpen = false; onMarkAllRead() },
                )
                DropdownMenuItem(
                    text = { Text("Tout marquer comme non lu") },
                    leadingIcon = { Icon(Icons.Filled.RemoveDone, contentDescription = null) },
                    onClick = { menuOpen = false; onMarkAllUnread() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Sélectionner les chapitres") },
                    leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                    onClick = { menuOpen = false; onSelectAll() },
                )
            }
        }
    }
}

/**
 * Ligne de chapitre. Appui simple = lire (ou cocher en mode sélection), appui long =
 * entrer en sélection, menu ⋮ = signet / lu / marquer les précédents comme lus.
 *
 * L'appui long utilise `detectTapGestures` et non `combinedClickable`, encore
 * expérimental — même précaution que pour les autres composants du projet.
 */
@Composable
private fun ChapterRow(
    chapter: ChapterDto,
    progress: ChapterProgressDto?,
    favorited: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleRead: (Boolean) -> Unit,
    onToggleBookmark: () -> Unit,
    onMarkUpToHere: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val read = progress?.read == true
    val position = progress?.scrollPosition ?: 0

    val rowColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .pointerInput(chapter.id, selectionMode) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            }
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Repère de gauche : coche en mode sélection, sinon pastille « non lu ».
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        } else if (!read) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (favorited) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Chapitre en signet",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapitre ${chapter.chapterNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (read) FontWeight.Normal else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (read) 0.45f else 1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Chapitre ${chapter.chapterNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (read) 0.5f else 1f),
                )
                if (!read && position in 1..99) {
                    Text(
                        text = " · commencé ($position %)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!selectionMode) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Actions du chapitre",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(if (read) "Marquer comme non lu" else "Marquer comme lu") },
                        leadingIcon = {
                            Icon(
                                if (read) Icons.Outlined.CheckCircle else Icons.Filled.CheckCircle,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; onToggleRead(!read) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (favorited) "Retirer le signet" else "Ajouter un signet") },
                        leadingIcon = {
                            Icon(
                                if (favorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuOpen = false; onToggleBookmark() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Marquer les précédents comme lus") },
                        leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                        onClick = { menuOpen = false; onMarkUpToHere() },
                    )
                }
            }
        }
    }
}

// ── Barres flottantes ──────────────────────────────────────────────────────────

/** Bouton retour flottant sur pastille translucide. */
@Composable
private fun TopChrome(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            tonalElevation = 3.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
        }
    }
}

/** Barre d'actions du mode sélection multiple (apparaît dès qu'un chapitre est coché). */
@Composable
private fun SelectionBar(
    count: Int,
    allFavorited: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onToggleBookmark: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Quitter la sélection")
            }
            Text(
                text = "$count sélectionné${if (count > 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onMarkRead) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Marquer comme lus",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onMarkUnread) {
                Icon(Icons.Filled.RemoveDone, contentDescription = "Marquer comme non lus")
            }
            IconButton(onClick = { onToggleBookmark(!allFavorited) }) {
                Icon(
                    if (allFavorited) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (allFavorited) "Retirer les signets" else "Ajouter des signets",
                    tint = if (allFavorited) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Filled.SelectAll, contentDescription = "Tout sélectionner")
            }
        }
    }
}

// ── Étagères ───────────────────────────────────────────────────────────────────

/**
 * Dialogue « Définir la catégorie », déclenché par le cœur — le même principe que dans
 * Mihon : on cases-à-coche tranquillement, **rien n'est envoyé avant « Valider »**, et
 * « Annuler » laisse tout en place. Un seul geste couvre donc « je garde ce roman » et
 * « voilà où je le range ».
 */
@Composable
private fun CategoryPickerDialog(
    categories: List<CategoryDto>,
    novelId: Long,
    inLibrary: Boolean,
    onConfirm: (Set<Long>) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Sélection provisoire, locale au dialogue : l'utilisateur peut se tromper sans
    // conséquence tant qu'il n'a pas validé.
    var selected by remember {
        mutableStateOf(categories.filter { c -> c.novels.any { it.id == novelId } }.map { it.id }.toSet())
    }
    var newName by remember { mutableStateOf("") }

    // Une catégorie créée depuis ce dialogue reçoit le roman côté serveur : on coche donc
    // la nouvelle case. Réagir à la TAILLE évite de réécraser un décochage manuel.
    LaunchedEffect(categories.size) {
        selected = selected + categories
            .filter { c -> c.novels.any { it.id == novelId } }
            .map { it.id }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Définir la catégorie") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (categories.isEmpty()) {
                    Text(
                        text = "Tu n'as pas encore de catégorie. Crée-en une ci-dessous " +
                            "(« Favoris », « À relire »…) pour organiser ta bibliothèque.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                categories.forEach { category ->
                    val checked = category.id in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                selected = if (checked) selected - category.id
                                else selected + category.id
                            }
                            .padding(vertical = 2.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                selected = if (checked) selected - category.id
                                else selected + category.id
                            },
                        )
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nouvelle catégorie") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onCreateCategory(newName); newName = "" },
                        enabled = newName.isNotBlank(),
                    ) { Text("Créer") }
                }

                if (inLibrary) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onRemoveFromLibrary)
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(
                            Icons.Filled.HeartBroken,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Retirer de ma bibliothèque",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("Valider", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

private fun statusLabel(status: String?): String = when (status) {
    "COMPLETED" -> "Terminé"
    "ONGOING" -> "En cours"
    else -> "Statut inconnu"
}
