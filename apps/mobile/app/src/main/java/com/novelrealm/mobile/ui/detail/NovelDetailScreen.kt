package com.novelrealm.mobile.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.novelrealm.mobile.ui.util.vmFactory

// Fiche d'un roman (#35), calquée sur l'écran détail de Mihon : header à couverture
// floutée, rangée d'actions, description dépliable, chips de genres, liste des
// chapitres (état lu / signet / progression) et FAB « Commencer / Reprendre ».
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

    Box(modifier = modifier.fillMaxSize()) {
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
        TopChrome(title = state.novel?.title ?: "", onBack = onBack)
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
    var showShelvesDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") { DetailHeader(novel = novel) }
            item(key = "actions") {
                DetailActionRow(
                    novel = novel,
                    libraryStatus = state.libraryStatus,
                    onAddToLibrary = viewModel::addToLibrary,
                    onSetStatus = viewModel::setLibraryStatus,
                    onRemoveFromLibrary = viewModel::removeFromLibrary,
                    onOpenReviews = onOpenReviews,
                    onOpenShelves = { showShelvesDialog = true },
                )
            }
            item(key = "description") { ExpandableDescription(novel = novel) }
            item(key = "chapterHeader") {
                ChapterHeaderRow(
                    count = state.chapters.size,
                    onMarkAllRead = viewModel::markAllRead,
                )
            }
            items(items = state.chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    progress = state.progress[chapter.id],
                    favorited = chapter.id in state.favoriteChapterIds,
                    onClick = { onOpenReader(novel.id, chapter.id) },
                    onToggleRead = { read -> viewModel.markChapterRead(chapter.id, read) },
                )
            }
        }

        // FAB « Commencer / Reprendre » : premier chapitre non lu.
        val resumeChapter = state.chapters.firstOrNull { state.progress[it.id]?.read != true }
        val anyRead = state.progress.values.any { it.read }
        if (resumeChapter != null) {
            ExtendedFloatingActionButton(
                onClick = { onOpenReader(novel.id, resumeChapter.id) },
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                text = { Text(if (anyRead) "Reprendre" else "Commencer") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }

    if (showShelvesDialog) {
        ShelvesDialog(
            categories = state.categories,
            novelId = novel.id,
            onToggle = viewModel::toggleShelf,
            onCreate = viewModel::createShelfAndAdd,
            onDismiss = { showShelvesDialog = false },
        )
    }
}

// Header façon Mihon : la couverture floutée en toile de fond, la vraie couverture +
// titre / auteur / statut / note au premier plan.
@Composable
private fun DetailHeader(novel: NovelDetailDto) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = resolveImageUrl(novel.coverImageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(4.dp)
                .alpha(0.2f),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            NovelCover(
                coverUrl = novel.coverImageUrl,
                contentDescription = novel.title,
                modifier = Modifier.width(100.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (!novel.author.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = novel.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusIcon = if (novel.status == "COMPLETED") Icons.Filled.DoneAll else Icons.Filled.Schedule
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = statusLabel(novel.status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    )
                    if (novel.ratingCount > 0) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        )
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "%.1f (%d)".format(novel.averageRating, novel.ratingCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
    }
}

// Rangée d'actions façon Mihon : Bibliothèque (avec menu de statut) / Avis / Étagères.
@Composable
private fun DetailActionRow(
    novel: NovelDetailDto,
    libraryStatus: String?,
    onAddToLibrary: () -> Unit,
    onSetStatus: (String) -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onOpenReviews: () -> Unit,
    onOpenShelves: () -> Unit,
) {
    var statusMenuOpen by remember { mutableStateOf(false) }
    val inLibrary = libraryStatus != null
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            DetailActionButton(
                icon = if (inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (inLibrary) statusShortLabel(libraryStatus) else "Ajouter",
                tint = if (inLibrary) activeColor else inactiveColor,
                onClick = { if (inLibrary) statusMenuOpen = true else onAddToLibrary() },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownMenu(expanded = statusMenuOpen, onDismissRequest = { statusMenuOpen = false }) {
                READING_STATUSES.forEach { (status, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = {
                            if (status == libraryStatus) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            statusMenuOpen = false
                            onSetStatus(status)
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Retirer de la bibliothèque", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        statusMenuOpen = false
                        onRemoveFromLibrary()
                    },
                )
            }
        }
        DetailActionButton(
            icon = Icons.Filled.Star,
            label = if (novel.ratingCount > 0) "Avis (%d)".format(novel.ratingCount) else "Avis",
            tint = inactiveColor,
            onClick = onOpenReviews,
            modifier = Modifier.weight(1f),
        )
        DetailActionButton(
            icon = Icons.Filled.Folder,
            label = "Étagères",
            tint = inactiveColor,
            onClick = onOpenShelves,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Description dépliable (3 lignes → tout) + chips de genres, façon Mihon.
@Composable
private fun ExpandableDescription(novel: NovelDetailDto) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
    ) {
        if (!novel.description.isNullOrBlank()) {
            Column(
                modifier = Modifier.clickable { expanded = !expanded },
            ) {
                Text(
                    text = novel.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Réduire" else "Développer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
        if (novel.genres.isNotEmpty()) {
            // Chips de genres en rangée défilable (API stable, contrairement à FlowRow
            // dont la signature varie selon la version de compose-foundation).
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items = novel.genres, key = { it.id }) { genre ->
                    SuggestionChip(onClick = {}, label = { Text(genre.name) })
                }
            }
        }
    }
}

@Composable
private fun ChapterHeaderRow(count: Int, onMarkAllRead: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count <= 1) "$count chapitre" else "$count chapitres",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMarkAllRead) {
            Icon(
                Icons.Filled.DoneAll,
                contentDescription = "Tout marquer comme lu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Ligne de chapitre façon Mihon : point « non lu », signet, titre (grisé si lu),
// sous-titre progression, et bascule lu/non-lu en bout de ligne.
@Composable
private fun ChapterRow(
    chapter: ChapterDto,
    progress: ChapterProgressDto?,
    favorited: Boolean,
    onClick: () -> Unit,
    onToggleRead: (Boolean) -> Unit,
) {
    val read = progress?.read == true
    val titleAlpha = if (read) 0.5f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!read) {
                    Icon(
                        Icons.Filled.Circle,
                        contentDescription = null,
                        modifier = Modifier.size(8.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                }
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
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = titleAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val subtitleParts = buildList {
                add("Chapitre ${chapter.chapterNumber}")
                val position = progress?.scrollPosition ?: 0
                if (!read && position in 1..99) add("$position %")
            }
            Text(
                text = subtitleParts.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f * titleAlpha + 0.2f),
            )
        }
        IconButton(onClick = { onToggleRead(!read) }) {
            Icon(
                imageVector = if (read) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                contentDescription = if (read) "Marquer non lu" else "Marquer lu",
                tint = if (read) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

// Bouton retour flottant sur pastille translucide (façon Mihon).
@Composable
private fun TopChrome(title: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
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

// Dialogue « Étagères » : cocher/décocher les étagères du roman + en créer une nouvelle.
@Composable
private fun ShelvesDialog(
    categories: List<CategoryDto>,
    novelId: Long,
    onToggle: (CategoryDto) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Étagères") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (categories.isEmpty()) {
                    Text(
                        text = "Aucune étagère pour l'instant. Crée la première !",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                categories.forEach { category ->
                    val checked = category.novels.any { it.id == novelId }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(category) },
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(category) })
                        Text(category.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nouvelle étagère") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onCreate(newName)
                            newName = ""
                        },
                        enabled = newName.isNotBlank(),
                    ) {
                        Text("Créer")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

private val READING_STATUSES = listOf(
    "READING" to "En cours de lecture",
    "PLAN_TO_READ" to "À lire",
    "COMPLETED" to "Terminé",
    "PAUSED" to "En pause",
)

private fun statusLabel(status: String?): String = when (status) {
    "COMPLETED" -> "Terminé"
    "ONGOING" -> "En cours"
    else -> "Inconnu"
}

private fun statusShortLabel(status: String?): String = when (status) {
    "READING" -> "En cours"
    "PLAN_TO_READ" -> "À lire"
    "COMPLETED" -> "Terminé"
    "PAUSED" -> "En pause"
    else -> "Suivi"
}
