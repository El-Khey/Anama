package com.novelrealm.mobile.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.CategoryDto
import com.novelrealm.mobile.data.remote.dto.ChapterDto
import com.novelrealm.mobile.data.remote.dto.ChapterProgressDto
import com.novelrealm.mobile.data.remote.dto.NovelDetailDto
import com.novelrealm.mobile.data.remote.dto.displayTitle
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.NovelCover
import com.novelrealm.mobile.ui.components.selectionStyle
import com.novelrealm.mobile.ui.util.ReadingStatus
import com.novelrealm.mobile.ui.util.vmFactory
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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

    // Au retour du lecteur, la progression et les signets ont pu changer. Le ViewModel
    // ignore le tout premier appel : son chargement initial vient de le faire.
    LaunchedEffect(Unit) { viewModel.refreshOnReturn() }

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
            // Les actions de masse sont irréversibles et partent au premier appui. Au-delà
            // d'une poignée de chapitres, on demande confirmation — remettre 300 chapitres
            // en « non lu » par erreur ne se rattrape pas.
            var pendingBulk by remember { mutableStateOf<BulkAction?>(null) }

            fun requestBulk(action: BulkAction) {
                if (state.selectedChapterIds.size > BULK_CONFIRM_THRESHOLD) pendingBulk = action
                else action.execute(viewModel, state)
            }

            SelectionBar(
                count = state.selectedChapterIds.size,
                total = state.chapters.size,
                allFavorited = state.selectedChapterIds.all { it in state.favoriteChapterIds },
                onClose = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onInvert = viewModel::invertSelection,
                onMarkRead = { requestBulk(BulkAction.MarkRead) },
                onMarkUnread = { requestBulk(BulkAction.MarkUnread) },
                onMarkUpToHere = viewModel::markUpToSelection,
                onToggleBookmark = { favorite ->
                    viewModel.setChaptersFavorite(state.selectedChapterIds.toList(), favorite)
                },
            )

            pendingBulk?.let { action ->
                AlertDialog(
                    onDismissRequest = { pendingBulk = null },
                    title = { Text("${state.selectedChapterIds.size} chapitres") },
                    text = { Text(action.confirmMessage(state.selectedChapterIds.size)) },
                    confirmButton = {
                        TextButton(onClick = {
                            action.execute(viewModel, state)
                            pendingBulk = null
                        }) { Text("Confirmer") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingBulk = null }) { Text("Annuler") }
                    },
                )
            }
        } else {
            TopChrome(onBack = onBack)
        }
    }
}

/** Seuil au-delà duquel une action de masse demande confirmation. */
private const val BULK_CONFIRM_THRESHOLD = 15

/**
 * Action de masse assez lourde pour mériter une confirmation. Le couple « ce qu'on fait »
 * et « comment on l'annonce » vit au même endroit : deux `when` séparés finiraient par
 * dire deux choses différentes.
 */
private enum class BulkAction {
    MarkRead {
        override fun execute(vm: NovelDetailViewModel, state: NovelDetailUiState) {
            vm.markChapters(state.selectedChapterIds.toList(), read = true)
        }

        override fun confirmMessage(count: Int) = "Marquer ces $count chapitres comme lus ?"
    },
    MarkUnread {
        override fun execute(vm: NovelDetailViewModel, state: NovelDetailUiState) {
            vm.markChapters(state.selectedChapterIds.toList(), read = false)
        }

        override fun confirmMessage(count: Int) =
            "Marquer ces $count chapitres comme non lus ?\nTa progression sur chacun sera perdue."
    };

    abstract fun execute(vm: NovelDetailViewModel, state: NovelDetailUiState)
    abstract fun confirmMessage(count: Int): String
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
                    commentCount = state.commentCounts[chapter.id] ?: 0L,
                    selected = chapter.id in state.selectedChapterIds,
                    selectionMode = state.isSelecting,
                    onClick = {
                        if (state.isSelecting) viewModel.toggleSelection(chapter.id)
                        else onOpenReader(novel.id, chapter.id)
                    },
                    // Appui long : ouvre la sélection, puis étend la plage depuis le
                    // dernier chapitre coché. C'est le seul moyen tenable d'en prendre
                    // quarante d'un coup.
                    onLongClick = { viewModel.selectRangeTo(chapter.id) },
                    onToggleRead = { read -> viewModel.markChapterRead(chapter.id, read) },
                    onToggleBookmark = { viewModel.toggleChapterFavorite(chapter.id) },
                    onMarkUpToHere = { viewModel.markUpToRead(chapter.id) },
                )
            }
        }

        // Avec plusieurs centaines de chapitres, faire défiler au doigt est interminable :
        // la pastille se saisit et parcourt toute la liste d'un geste.
        //
        // Les chapitres sont les seuls items indexés par un identifiant numérique — les
        // blocs d'en-tête ont des clés textuelles. La barre s'en sert pour savoir quand
        // elle survole la liste, sans qu'on ait à compter les items qui la précèdent.
        ChapterFastScroller(
            listState = listState,
            isChapterKey = { key -> key is Long },
        )

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

/**
 * En-tête **immersif** de la fiche : la couverture agrandie et floutée occupe tout le
 * fond comme une bannière, la vraie couverture FLOTTE par-dessus (ombre portée), titre et
 * métadonnées à droite.
 *
 * <p><b>Une vraie scène, pas une image effacée.</b> Le fond est nettement plus présent
 * qu'avant (moins transparent, flou plus doux) : on veut sentir l'univers du roman dès le
 * haut de page. Un dégradé le laisse respirer en haut puis le fond proprement vers la page
 * en bas, pour que le texte s'y pose sans heurt.
 *
 * <p><b>Robuste au vieux matériel.</b> `Modifier.blur` ne floute rien avant Android 12 ;
 * on ne compte donc pas QUE sur lui. Un scrim sombre par-dessus le fond assure une
 * bannière élégante même sans flou — l'image y paraît alors « assombrie et cadrée »
 * plutôt que ratée.
 */
@Composable
private fun DetailHeader(novel: NovelDetailDto, chapterCount: Int) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Bannière de fond : la couverture agrandie, floutée, bien présente.
        AsyncImage(
            model = resolveImageUrl(novel.coverImageUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .blur(28.dp)
                .alpha(0.55f),
        )
        // Scrim sombre : ancre la scène et garantit un rendu classe même si le flou
        // n'opère pas (Android < 12). Léger en haut, plus dense au centre.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.55f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.45f),
                    ),
                ),
        )
        // Fondu vers le fond de la page : le tiers bas de la bannière se dissout dans la
        // couleur de fond, pour que le contenu qui suit ne « bute » pas sur une arête.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 64.dp, bottom = 22.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // La couverture FLOTTE : une ombre portée la détache du fond flouté et lui
            // donne le relief d'une carte posée sur la scène. `clip = false` pour que
            // l'ombre déborde du cadre au lieu d'être rognée à ses bords.
            NovelCover(
                coverUrl = novel.coverImageUrl,
                contentDescription = novel.title,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .width(124.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(12.dp),
                        clip = false,
                    ),
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = novel.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    lineHeight = MaterialTheme.typography.headlineMedium.fontSize * 1.1f,
                )
                if (!novel.author.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    MetaLine(icon = Icons.Filled.Person, text = novel.author)
                }
                Spacer(Modifier.height(5.dp))
                MetaLine(
                    icon = if (novel.status == "COMPLETED") Icons.Filled.DoneAll else Icons.Filled.Schedule,
                    text = buildString {
                        append(statusLabel(novel.status))
                        if (chapterCount > 0) append(" · $chapterCount ch.")
                    },
                )
                if (novel.ratingCount > 0) {
                    Spacer(Modifier.height(5.dp))
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

/**
 * Ligne « icône + texte » des métadonnées du roman, DANS la bannière immersive. Le texte
 * est blanc atténué (pas `onBackground`) : la bannière a un scrim sombre quel que soit le
 * thème, un gris de thème clair y deviendrait illisible.
 */
@Composable
private fun MetaLine(icon: ImageVector, text: String, iconTint: Color? = null) {
    val muted = Color.White.copy(alpha = 0.82f)
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
    // `bottom` : sans lui les pastilles touchent le synopsis, et les deux blocs se
    // lisent comme un seul pavé.
    Column(modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)) {
        Text(
            text = "STATUT DE LECTURE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            // Accent, comme tous les autres intitulés de section de l'app : c'était le
            // seul en gris, et il ne se lisait pas comme un titre.
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = ReadingStatus.entries.toList(), key = { it.id }) { status ->
                val selected = status.id == current
                val shape = RoundedCornerShape(12.dp)
                val style = selectionStyle(selected)
                Surface(
                    color = style.container,
                    shape = shape,
                    modifier = Modifier.border(1.dp, style.border, shape),
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
                            tint = style.content,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = style.content,
                        )
                    }
                }
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
                    text = { Text("Tout sélectionner") },
                    leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                    onClick = { menuOpen = false; onSelectAll() },
                )
            }
        }
    }
}

/**
 * Ligne de chapitre. Appui simple = lire (ou cocher en mode sélection), appui long =
 * entrer en sélection puis étendre la plage, menu ⋮ = signet / lu / marquer les
 * précédents comme lus.
 *
 * <p>L'appui long utilise `detectTapGestures` et non `combinedClickable`, encore
 * expérimental — même précaution que pour les autres composants du projet.
 *
 * <p><b>Le mode sélection ne change qu'une seule chose dans la liste : le fond des lignes
 * cochées.</b> La pastille « non lu » et le menu ⋮ restent en place. Une version
 * précédente remplaçait la pastille par une coche et escamotait le ⋮ : on perdait d'un
 * coup l'état de lecture de toutes les lignes — celui-là même qui sert à choisir quoi
 * cocher — et la coche faisait doublon avec le fond teinté.
 *
 * <p>Le **signet** est une icône posée devant le titre du chapitre.
 */
@Composable
private fun ChapterRow(
    chapter: ChapterDto,
    progress: ChapterProgressDto?,
    favorited: Boolean,
    commentCount: Long,
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
        // Repère de gauche : pastille « non lu », en place quoi qu'il arrive.
        if (!read) {
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
                    text = chapter.displayTitle,
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
                // « Où ça discute » : rien du tout quand personne n'a écrit — un « 0 »
                // sur chaque ligne encombrerait la liste sans rien apprendre.
                if (commentCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.Comment,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "$commentCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }

        // Toujours présent, sélection comprise : le bouton est un enfant de la ligne, et
        // Compose sert les pointeurs aux enfants d'abord — l'appui sur le ⋮ est donc
        // consommé ici et ne coche pas la ligne au passage.
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

// ── Défilement rapide ──────────────────────────────────────────────────────────

private val ThumbLength = 48.dp
private val ThumbThickness = 10.dp

/** Marge autour de la pastille : elle décolle du bord ET élargit la zone d'accroche. */
private val ThumbPadding = 8.dp

/** Délai d'inactivité avant que la pastille ne s'efface, puis durée du fondu. */
private const val ThumbFadeDelayMillis = 1500L
private const val ThumbFadeOutMillis = 300

/**
 * Barre de défilement rapide, sur le modèle de celle de Mihon (dont l'approche est
 * reprise ici, pas le code).
 *
 * Quatre partis pris, qui sont ce qui la rend agréable :
 *
 * 1. **Pastille de taille fixe**, superposée à la liste. Elle ne réserve aucune place :
 *    les lignes de chapitre occupent toute la largeur, comme avant.
 * 2. **Un seul état partagé**, `thumbOffsetY`, piloté dans les deux sens — on tire la
 *    pastille et la liste suit, on fait défiler la liste et la pastille suit. Un seul
 *    état, donc aucune boucle de rétroaction entre les deux.
 * 3. **Estimation en pixels**, pas en index : la course totale vaut « taille moyenne
 *    d'un item × nombre d'items ». On vise donc une position continue, là où un calcul
 *    par index faisait sauter la liste de chapitre en chapitre.
 * 4. **`draggable` plutôt qu'un suivi du doigt** : le geste travaille en *delta*, donc
 *    la pastille ne saute jamais sous le doigt au moment de la saisie, et seule la
 *    pastille elle-même est saisissable (48 dp de haut) — pas une bande sur toute la
 *    hauteur qui volerait les appuis destinés aux lignes.
 *
 * @param isChapterKey reconnaît un item « chapitre » à sa clé. La pastille reste
 *   masquée tant que le haut de l'écran n'est pas un chapitre : elle n'a pas à flotter
 *   par-dessus la couverture du roman.
 */
@Composable
private fun ChapterFastScroller(
    listState: LazyListState,
    isChapterKey: (Any) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty() || layoutInfo.totalItemsCount == 0) return

    val density = LocalDensity.current
    val thumbLengthPx = with(density) { ThumbLength.toPx() }

    // Hauteur réellement parcourable par la pastille (hors barres système).
    var trackAreaPx by remember { mutableStateOf(0f) }
    val trackLengthPx = (trackAreaPx - thumbLengthPx).coerceAtLeast(1f)

    var thumbOffsetY by remember { mutableStateOf(0f) }
    val dragSource = remember { MutableInteractionSource() }
    val isDragged by dragSource.collectIsDraggedAsState()

    // Estimation de la course : la taille moyenne des items mesurés à l'écran, étendue
    // au total. Compose ne connaît pas la hauteur de ce qu'il n'a pas composé ; les
    // lignes de chapitre étant régulières, l'estimation se stabilise dès qu'on est dans
    // la liste — et c'est justement le seul moment où la pastille est visible.
    val averageItemSizePx = layoutInfo.averageItemSize()
    val extraScrollPx = (averageItemSizePx * layoutInfo.totalItemsCount - trackAreaPx)
        .coerceAtLeast(1f)

    val scrollable = averageItemSizePx > 0f &&
        visibleItems.size < layoutInfo.totalItemsCount
    val overChapters = visibleItems.first().key.let(isChapterKey)
    val thumbAllowed = scrollable && overChapters

    // La liste bouge → la pastille suit.
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (isDragged) return@LaunchedEffect
        val scrolledPx = listState.firstVisibleItemIndex * averageItemSizePx +
            listState.firstVisibleItemScrollOffset
        thumbOffsetY = (scrolledPx / extraScrollPx).coerceIn(0f, 1f) * trackLengthPx
    }

    // La pastille bouge → la liste suit. Le reste de la division devient un décalage en
    // pixels DANS l'item visé : c'est lui qui rend le glissement continu.
    LaunchedEffect(thumbOffsetY) {
        if (!isDragged || averageItemSizePx <= 0f) return@LaunchedEffect
        val targetPx = (thumbOffsetY / trackLengthPx).coerceIn(0f, 1f) * extraScrollPx
        val index = (targetPx / averageItemSizePx).toInt()
            .coerceIn(0, layoutInfo.totalItemsCount - 1)
        val offset = (targetPx - index * averageItemSizePx).roundToInt().coerceAtLeast(0)
        listState.scrollToItem(index, offset)
    }

    // Apparition immédiate au premier geste, effacement après un temps mort.
    val alpha = remember { Animatable(0f) }
    val isThumbVisible = alpha.value > 0f
    LaunchedEffect(thumbAllowed, listState.isScrollInProgress, isDragged) {
        when {
            !thumbAllowed -> alpha.animateTo(0f, tween(ThumbFadeOutMillis))
            listState.isScrollInProgress || isDragged -> alpha.snapTo(1f)
            else -> {
                alpha.snapTo(1f)
                delay(ThumbFadeDelayMillis)
                alpha.animateTo(0f, tween(ThumbFadeOutMillis))
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { trackAreaPx = it.height.toFloat() },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                .then(
                    // Pas saisissable pendant que la liste défile d'elle-même : on
                    // attraperait la pastille en pleine inertie.
                    if (isThumbVisible && !listState.isScrollInProgress) {
                        Modifier.draggable(
                            interactionSource = dragSource,
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                thumbOffsetY = (thumbOffsetY + delta).coerceIn(0f, trackLengthPx)
                            },
                        )
                    } else {
                        Modifier
                    },
                )
                .then(
                    // Sans ça, en navigation par gestes, tirer la pastille près du bord
                    // déclencherait le retour système.
                    if (isThumbVisible) Modifier.systemGestureExclusion() else Modifier,
                )
                .height(ThumbLength)
                .padding(horizontal = ThumbPadding)
                .width(ThumbThickness)
                .alpha(alpha.value)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(percent = 50),
                ),
        )
    }
}

/**
 * Taille moyenne d'un item, mesurée sur ceux qui sont à l'écran — la seule information
 * de hauteur dont Compose dispose pour une liste paresseuse.
 */
private fun LazyListLayoutInfo.averageItemSize(): Float {
    val items = visibleItemsInfo
    if (items.isEmpty()) return 0f
    val first = items.first()
    val last = items.last()
    val laidOutArea = (last.offset + last.size) - first.offset
    val laidOutCount = abs(last.index - first.index) + 1
    return laidOutArea.toFloat() / laidOutCount
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
    total: Int,
    allFavorited: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onMarkUpToHere: () -> Unit,
    onToggleBookmark: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .heightIn(min = 56.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Quitter la sélection")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Le total situe la sélection : « 12 » seul ne dit pas si c'est une
                    // poignée dans une longue série ou la quasi-totalité du roman.
                    text = "$count / $total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                // Le geste de plage ne s'annonce nulle part ailleurs, et c'est le seul
                // moyen de cocher quarante chapitres. On le souffle au moment exact où il
                // devient utile : une case cochée, la suivante à choisir.
                if (count == 1) {
                    Text(
                        text = "Appui long ailleurs pour tout prendre jusque-là",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
            // Le reste part dans un menu : six icônes de front ne laisseraient plus la
            // place au compteur sur un écran étroit.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Autres actions")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Marquer les précédents comme lus") },
                        leadingIcon = { Icon(Icons.Filled.DoneAll, contentDescription = null) },
                        onClick = { menuOpen = false; onMarkUpToHere() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Tout sélectionner") },
                        leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null) },
                        onClick = { menuOpen = false; onSelectAll() },
                    )
                    DropdownMenuItem(
                        text = { Text("Inverser la sélection") },
                        leadingIcon = { Icon(Icons.Filled.Deselect, contentDescription = null) },
                        onClick = { menuOpen = false; onInvert() },
                    )
                }
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
