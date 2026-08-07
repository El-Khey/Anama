package com.novelrealm.mobile.ui.quotes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.QuoteDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.SettingsScaffold
import com.novelrealm.mobile.ui.components.SheetScrim
import com.novelrealm.mobile.ui.util.dateLabel
import kotlinx.coroutines.delay

/**
 * « Mes citations » (#41, §3) — la collection qui donne son sens au geste de citer.
 *
 * <p>Quatre choix d'affichage :
 * <ul>
 *   <li><b>La citation domine la carte</b>, en italique et en grand. La référence
 *       (roman · chapitre · date) passe en pied, discrète : on vient relire une
 *       phrase, pas consulter des métadonnées.</li>
 *   <li><b>Les filtres sont dans un panneau, pas en bandeau.</b> Une rangée de
 *       pastilles au-dessus de la liste ne tient qu'un seul critère et vole une bande
 *       d'écran en permanence, y compris quand on ne filtre rien. Le panneau tient
 *       autant de critères qu'il faut et ne coûte qu'un bouton.</li>
 *   <li><b>Aucun filtre ne s'applique localement.</b> La collection est paginée : trier
 *       ou chercher dans la page reçue ne porterait que sur elle. Tout part au serveur,
 *       qui renvoie une première page à jour.</li>
 *   <li><b>La référence est le bouton.</b> Toucher « roman · chapitre » ramène au
 *       passage — c'est le geste qu'on a envie de faire en lisant la carte.</li>
 * </ul>
 */
@Composable
fun MyQuotesScreen(
    onBack: () -> Unit,
    onOpenPassage: (novelId: Long, chapterId: Long, blockIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyQuotesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Le passage est résolu par le serveur avant de naviguer : l'écran ne fait que
    // consommer la cible, une fois.
    val target = state.navigateTo
    LaunchedEffect(target) {
        if (target != null) {
            onOpenPassage(target.novelId, target.chapterId, target.blockIndex)
            viewModel.navigationHandled()
        }
    }

    // Le retour système ferme d'abord le panneau : sinon il quitte l'écran alors qu'on
    // avait seulement ouvert les filtres.
    BackHandler(enabled = state.filtersOpen) { viewModel.closeFilters() }

    Box(modifier = modifier.fillMaxSize()) {
        SettingsScaffold(title = "Mes citations", onBack = onBack) {
            SearchField(value = state.search, onValueChange = viewModel::setSearch)

            FilterBar(state = state, onOpen = viewModel::openFilters)

            state.actionError?.let { message ->
                ActionBanner(message = message, onShown = viewModel::actionErrorShown)
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> LoadingScreen()

                    state.error != null -> EmptyScreen(
                        message = state.error ?: "",
                        actionLabel = "Réessayer",
                        onAction = viewModel::load,
                    )

                    state.quotes.isEmpty() && state.isFiltered -> EmptyScreen(
                        message = "Aucune citation ne correspond.",
                        actionLabel = "Tout afficher",
                        onAction = viewModel::clearAll,
                    )

                    state.quotes.isEmpty() -> EmptyScreen(
                        message = "Ta collection est vide.\n\n" +
                            "Pendant ta lecture, garde le doigt appuyé sur un paragraphe " +
                            "pour en citer un passage.",
                    )

                    else -> QuoteList(
                        state = state,
                        onOpenPassage = viewModel::openPassage,
                        onDelete = viewModel::delete,
                        onLoadMore = viewModel::loadMore,
                    )
                }
            }
        }

        // ── Panneau de filtres ──
        AnimatedVisibility(
            visible = state.filtersOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            SheetScrim(
                onDismiss = viewModel::closeFilters,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible = state.filtersOpen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FilterSheet(
                state = state,
                onSelectNovel = viewModel::selectNovel,
                onSelectSort = viewModel::setSort,
                onSelectPeriod = viewModel::setPeriod,
                onReset = viewModel::resetFilters,
                onClose = viewModel::closeFilters,
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("Chercher dans mes citations…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

// ── Filtres ───────────────────────────────────────────────────────────────────

/**
 * Barre au-dessus de la liste : combien de citations à gauche, bouton d'ouverture du
 * panneau à droite.
 *
 * <p>Le rappel des critères actifs sous le total n'est pas une décoration. Un filtre
 * laissé en place et oublié fait croire à une collection vide ou incomplète ; l'écrire
 * en toutes lettres évite d'aller chercher l'explication dans le panneau.
 */
@Composable
private fun FilterBar(state: MyQuotesUiState, onOpen: () -> Unit) {
    val active = state.activeFilterCount > 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.total <= 1) "${state.total} citation" else "${state.total} citations",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val summary = filterSummary(state)
            if (summary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(12.dp))
        Surface(
            color = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            },
            shape = RoundedCornerShape(50),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                val tint = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (active) "Filtres · ${state.activeFilterCount}" else "Filtrer",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                )
            }
        }
    }
}

/** Résumé des critères actifs, ou `null` s'il n'y en a aucun. */
private fun filterSummary(state: MyQuotesUiState): String? {
    val parts = buildList {
        state.selectedNovel?.let { add(it.novelTitle) }
        if (state.sort != QuoteSort.Recent) add(state.sort.label)
        if (state.period != QuotePeriod.All) add(state.period.label)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Panneau de filtres.
 *
 * <p><b>Chaque choix s'applique immédiatement</b>, sans bouton « Valider ». Le total
 * affiché derrière le panneau se met à jour à chaque touche, ce qui permet d'ajuster
 * un critère en voyant son effet ; un formulaire à valider obligerait à fermer, lire,
 * rouvrir.
 */
@Composable
private fun FilterSheet(
    state: MyQuotesUiState,
    onSelectNovel: (Long?) -> Unit,
    onSelectSort: (QuoteSort) -> Unit,
    onSelectPeriod: (QuotePeriod) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 12.dp),
            ) {
                Text(
                    text = "Filtrer",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (state.activeFilterCount > 0) {
                    Text(
                        text = "Réinitialiser",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onReset)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer les filtres",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                        .size(20.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            // Plafonné puis défilant : avec vingt romans en collection, un panneau non
            // borné couvrirait l'écran entier et masquerait ce qu'il est en train de
            // filtrer.
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                FilterGroup(title = "Roman") {
                    FilterOption(
                        label = "Tous les romans",
                        count = state.counts.sumOf { it.count },
                        selected = state.selectedNovelId == null,
                        onClick = { onSelectNovel(null) },
                    )
                    state.counts.forEach { entry ->
                        FilterOption(
                            label = entry.novelTitle,
                            count = entry.count,
                            selected = state.selectedNovelId == entry.novelId,
                            onClick = { onSelectNovel(entry.novelId) },
                        )
                    }
                }

                FilterGroup(title = "Trier par") {
                    QuoteSort.entries.forEach { option ->
                        FilterOption(
                            label = option.label,
                            selected = state.sort == option,
                            onClick = { onSelectSort(option) },
                        )
                    }
                }

                FilterGroup(title = "Période") {
                    QuotePeriod.entries.forEach { option ->
                        FilterOption(
                            label = option.label,
                            selected = state.period == option,
                            onClick = { onSelectPeriod(option) },
                        )
                    }
                }
            }
        }
    }
}

/** Intitulé de groupe, puis ses options. */
@Composable
private fun FilterGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
    )
    Column(content = content)
}

/**
 * Une option de filtre : intitulé, compte facultatif, coche quand elle est retenue.
 *
 * <p>Des lignes plutôt que des pastilles : un titre de roman peut être long, et une
 * rangée de pastilles le tronquerait ou le pousserait hors de l'écran. Une ligne
 * accepte n'importe quelle longueur et se touche plus facilement.
 */
@Composable
private fun FilterOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Long? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
        }
        // Une case vide de la même taille garde tous les intitulés alignés, qu'ils
        // soient cochés ou non.
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Liste ─────────────────────────────────────────────────────────────────────

@Composable
private fun QuoteList(
    state: MyQuotesUiState,
    onOpenPassage: (QuoteDto) -> Unit,
    onDelete: (QuoteDto) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState, state.quotes.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= state.quotes.size - 3) onLoadMore()
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        items(items = state.quotes, key = { it.id }) { quote ->
            QuoteCard(
                quote = quote,
                onOpenPassage = { onOpenPassage(quote) },
                onDelete = { onDelete(quote) },
            )
        }
        if (state.isLoadingMore) {
            item(key = "loadingMore") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: QuoteDto, onOpenPassage: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Le guillemet ouvrant, en gros et en couleur : il signale « ici, ce n'est
            // pas l'app qui parle » avant même qu'on ait lu le premier mot.
            Text(
                text = "“",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                modifier = Modifier.height(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = quote.quotedText,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val cover = resolveImageUrl(quote.novelCoverUrl)
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 28.dp, height = 40.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                // La référence EST le bouton : c'est le geste qu'on a envie de faire
                // en la lisant.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenPassage)
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = quote.novelTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("Chapitre ${quote.chapterNumber}")
                            val date = dateLabel(quote.createdAt)
                            if (date.isNotBlank()) append(" · $date")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Supprimer la citation",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { confirmDelete = true }
                        .padding(8.dp)
                        .size(18.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Retirer cette citation ?") },
            text = { Text("Elle disparaîtra de ta collection. C'est définitif.") },
            confirmButton = {
                Text(
                    text = "Retirer",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { confirmDelete = false; onDelete() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
            dismissButton = {
                Text(
                    text = "Annuler",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { confirmDelete = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            },
        )
    }
}

/** Bandeau d'erreur passager — il s'efface tout seul, sans bloquer la lecture. */
@Composable
private fun ActionBanner(message: String, onShown: () -> Unit) {
    LaunchedEffect(message) {
        delay(3500)
        onShown()
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
