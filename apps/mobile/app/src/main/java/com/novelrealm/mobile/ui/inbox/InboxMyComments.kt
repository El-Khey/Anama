package com.novelrealm.mobile.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.MyCommentDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.CommentGif
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.profile.MyCommentsViewModel
import com.novelrealm.mobile.ui.util.relativeTimeLabel
import kotlinx.coroutines.delay

/**
 * Onglet « Mes commentaires » (issue #45, §4) — tout ce que j'ai écrit, fin de
 * chapitre et passages confondus, du plus récent au plus ancien.
 *
 * L'ordre de lecture d'une carte est inverse de celui des alertes, et c'est
 * voulu : ici je sais déjà qui parle (moi), ce que je cherche c'est **où** je
 * l'ai dit. D'où le roman en tête, puis le passage cité s'il y en a un — sans lui
 * un commentaire inline ne veut plus rien dire — puis mon message.
 */
@Composable
internal fun MyCommentsList(
    listState: LazyListState,
    onOpenChapter: (novelId: Long, chapterId: Long, blockIndex: Int, openComments: Boolean) -> Unit,
    viewModel: MyCommentsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // L'erreur d'une suppression s'efface seule : un bandeau qui reste devient du
    // décor, et celui-ci n'a pas d'action associée.
    LaunchedEffect(state.actionError) {
        if (state.actionError != null) {
            delay(3500)
            viewModel.actionErrorShown()
        }
    }

    InboxToolbar(
        left = {
            ToolbarCount(
                text = when (state.total) {
                    0L -> ""
                    1L -> "1 message"
                    else -> "${state.total} messages"
                },
            )
        },
    )

    state.actionError?.let { message ->
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding, vertical = 4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            )
        }
    }

    when {
        state.isLoading -> LoadingScreen()
        state.error != null -> EmptyScreen(
            message = state.error ?: "",
            actionLabel = "Réessayer",
            onAction = viewModel::load,
        )
        state.comments.isEmpty() -> InboxEmpty(
            icon = Icons.AutoMirrored.Outlined.Chat,
            title = "Tu n'as encore rien écrit",
            subtitle = "Tes commentaires de chapitre et de passage se retrouveront tous ici.",
        )
        else -> LazyColumn(
            state = listState,
            contentPadding = ListContentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            dayGroupedItems(
                items = state.comments,
                key = { "${it.kind}-${it.id}" },
                dateOf = { it.createdAt },
            ) { comment ->
                MyCommentCard(
                    comment = comment,
                    onOpen = {
                        val novelId = comment.novelId
                        val chapterId = comment.chapterId
                        if (novelId != null && chapterId != null) {
                            onOpenChapter(
                                novelId,
                                chapterId,
                                if (comment.isPassage) comment.blockIndex ?: -1 else -1,
                                !comment.isPassage,
                            )
                        }
                    },
                    onDelete = { viewModel.delete(comment) },
                )
            }

            if (!state.endReached) {
                loadMoreItem(isLoading = state.isLoadingMore, onClick = viewModel::loadMore)
            }
        }
    }
}

@Composable
private fun MyCommentCard(comment: MyCommentDto, onOpen: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }

    InboxCard(onClick = onOpen) {
        // ── Où : couverture, roman, chapitre, quand ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            val cover = resolveImageUrl(comment.novelCoverUrl)
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 32.dp, height = 44.dp)
                        .clip(RoundedCornerShape(7.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 32.dp, height = 44.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.novelTitle ?: "Roman supprimé",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                MetaLine(
                    text = buildString {
                        append("Chap. ${comment.chapterNumber}")
                        val time = relativeTimeLabel(comment.createdAt)
                        if (time.isNotEmpty()) append("  ·  $time")
                    },
                )
            }
            // La corbeille reste à portée mais s'efface : c'est l'action rare, et
            // une action rare qui crie finit par être touchée par erreur.
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "Supprimer ce message",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { confirmDelete = true }
                    .padding(9.dp)
                    .size(17.dp),
            )
        }

        // ── Les nuances : réponse, passage ──
        if (comment.reply || comment.isPassage) {
            Spacer(Modifier.height(10.dp))
            Row {
                if (comment.reply) {
                    TinyPill(label = "Réponse", icon = Icons.AutoMirrored.Filled.Reply)
                    Spacer(Modifier.width(6.dp))
                }
                if (comment.isPassage) {
                    TinyPill(label = "Passage", icon = Icons.Outlined.FormatQuote)
                }
            }
        }

        // ── Le passage commenté : le contexte, pas le message ──
        if (comment.isPassage) {
            Spacer(Modifier.height(10.dp))
            QuoteRail(
                text = comment.passageExcerpt ?: "Passage introuvable (chapitre modifié)",
                italic = true,
            )
        }

        // ── Mon message ──
        val body = comment.body.orEmpty()
        if (body.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        comment.gifUrl?.let { gif ->
            Spacer(Modifier.height(10.dp))
            CommentGif(gifUrl = gif, previewUrl = comment.gifPreviewUrl, width = 0, height = 0)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce message ?") },
            text = { Text("Il ne sera plus visible par les autres lecteurs. C'est définitif.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            },
        )
    }
}
