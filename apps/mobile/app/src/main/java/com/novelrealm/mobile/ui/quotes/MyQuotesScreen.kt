package com.novelrealm.mobile.ui.quotes

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import com.novelrealm.mobile.data.remote.dto.NovelQuoteCountDto
import com.novelrealm.mobile.data.remote.dto.QuoteDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.SettingsScaffold
import com.novelrealm.mobile.ui.util.dateLabel
import kotlinx.coroutines.delay

/**
 * « Mes citations » (#41, §3) — la collection qui donne son sens au geste de citer.
 *
 * <p>Trois choix d'affichage :
 * <ul>
 *   <li><b>La citation domine la carte</b>, en italique et en grand. La référence
 *       (roman · chapitre · date) passe en pied, discrète : on vient relire une
 *       phrase, pas consulter des métadonnées.</li>
 *   <li><b>Filtre par roman plutôt que sections repliables.</b> La collection est
 *       paginée : des sections par roman se rempliraient au fil des pages et
 *       sauteraient sous le doigt. Un filtre donne le même accès, sans surprise.</li>
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

    SettingsScaffold(title = "Mes citations", onBack = onBack, modifier = modifier) {
        SearchField(
            value = state.search,
            onValueChange = viewModel::setSearch,
        )

        if (state.counts.size > 1) {
            NovelFilterRow(
                counts = state.counts,
                selectedNovelId = state.selectedNovelId,
                onSelect = viewModel::selectNovel,
            )
        }

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
                    onAction = {
                        viewModel.selectNovel(null)
                        viewModel.setSearch("")
                    },
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

@Composable
private fun NovelFilterRow(
    counts: List<NovelQuoteCountDto>,
    selectedNovelId: Long?,
    onSelect: (Long?) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.padding(bottom = 8.dp),
    ) {
        item(key = "all") {
            FilterPill(
                label = "Tous",
                count = counts.sumOf { it.count },
                selected = selectedNovelId == null,
                onClick = { onSelect(null) },
            )
        }
        items(items = counts, key = { it.novelId }) { entry ->
            FilterPill(
                label = entry.novelTitle,
                count = entry.count,
                selected = selectedNovelId == entry.novelId,
                onClick = { onSelect(entry.novelId) },
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, count: Long, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "$label · $count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

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
