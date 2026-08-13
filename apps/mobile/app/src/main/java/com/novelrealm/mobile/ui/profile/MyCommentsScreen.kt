package com.novelrealm.mobile.ui.profile

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.MyCommentDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.CommentGif
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.util.relativeTimeLabel
import kotlinx.coroutines.delay

/**
 * « Mes commentaires » (issue #45, §4) — tout ce que l'utilisateur a écrit, du
 * plus récent au plus ancien, fin de chapitre et passages confondus.
 *
 * Chaque carte situe le message (couverture · roman · chapitre · date), montre
 * l'extrait du passage commenté quand il y en a un — sans lui, un commentaire
 * inline est illisible hors contexte — puis le message. L'appui ramène au bon
 * endroit du lecteur ; la corbeille supprime, après confirmation.
 */
@Composable
fun MyCommentsScreen(
    onBack: () -> Unit,
    /** Même contrat que la cloche : passage précis, ou discussion de fin de chapitre. */
    onOpenChapter: (novelId: Long, chapterId: Long, blockIndex: Int, openComments: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MyCommentsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    // L'erreur d'une suppression s'efface seule : un bandeau qui reste devient
    // du décor, et celui-ci n'a pas d'action associée.
    LaunchedEffect(state.actionError) {
        if (state.actionError != null) {
            delay(3500)
            viewModel.actionErrorShown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Column {
                Text(
                    text = "Mes commentaires",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (state.total > 0) {
                    Text(
                        text = if (state.total == 1L) "1 message" else "${state.total} messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        state.actionError?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
            state.comments.isEmpty() -> EmptyComments()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp,
                ),
            ) {
                items(state.comments, key = { "${it.kind}-${it.id}" }) { comment ->
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
                    Spacer(Modifier.height(10.dp))
                }

                if (!state.endReached) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(
                                    text = "Voir plus",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = viewModel::loadMore)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyComments() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Chat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tu n'as encore rien écrit.\nTes commentaires te retrouveront ici.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Une carte ─────────────────────────────────────────────────────────────────

@Composable
private fun MyCommentCard(
    comment: MyCommentDto,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
                .padding(12.dp),
        ) {
            // Où : couverture minuscule + roman + chapitre + date.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val cover = resolveImageUrl(comment.novelCoverUrl)
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 26.dp, height = 36.dp)
                            .clip(RoundedCornerShape(5.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comment.novelTitle ?: "Roman supprimé",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append("Chap. ${comment.chapterNumber}")
                            val time = relativeTimeLabel(comment.createdAt)
                            if (time.isNotEmpty()) append(" · $time")
                            if (comment.reply) append(" · réponse")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (comment.reply) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Supprimer ce message",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { confirmDelete = true }
                        .padding(6.dp)
                        .size(16.dp),
                )
            }

            // L'extrait du passage commenté (inline uniquement) : en italique
            // derrière un liseré, comme une citation — c'est le contexte, pas le
            // message.
            val excerpt = comment.passageExcerpt
            if (comment.isPassage) {
                Spacer(Modifier.height(8.dp))
                Row {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(if (excerpt != null) 34.dp else 18.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = excerpt ?: "Passage introuvable (chapitre modifié)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val body = comment.body.orEmpty()
            if (body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            comment.gifUrl?.let { gif ->
                Spacer(Modifier.height(8.dp))
                CommentGif(
                    gifUrl = gif,
                    previewUrl = comment.gifPreviewUrl,
                    width = 0,
                    height = 0,
                )
            }
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
