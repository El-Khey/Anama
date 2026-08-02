package com.novelrealm.mobile.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.ChapterCommentDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.util.relativeTimeLabel

/**
 * Discussion du chapitre, posée **dans le fil du chapitre** — on continue de défiler
 * après « Fin du chapitre » et on tombe dessus, comme sur le web.
 *
 * Tout est peint avec la couleur de texte du lecteur ([foreground]) plutôt qu'avec la
 * palette Material : sur fond sépia ou OLED, des cartes « surface » se verraient
 * collées par-dessus la page au lieu d'en faire partie.
 *
 * **Écrire ne se fait pas ici mais dans [CommentComposerSheet].** Le corps du chapitre
 * est enveloppé dans le `draggable` horizontal qui change de chapitre : un champ de
 * saisie posé dedans lui disputerait chaque geste (impossible de faire glisser le
 * curseur dans son propre texte), et le clavier pousserait le chapitre. En lecture
 * seule, il n'y a aucun conflit — c'est la saisie, et elle seule, qui doit sortir.
 */
@Composable
fun ChapterCommentsSection(
    state: ChapterCommentsUiState,
    foreground: Color,
    onWrite: () -> Unit,
    onReply: (rootId: Long, pseudo: String?, mention: Boolean) -> Unit,
    onEdit: (comment: ChapterCommentDto, rootId: Long?) -> Unit,
    onDelete: (comment: ChapterCommentDto, rootId: Long?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeading(total = state.total, foreground = foreground)

        Spacer(Modifier.height(16.dp))
        WriteButton(foreground = foreground, onClick = onWrite)

        val error = state.error
        when {
            state.isLoading -> {
                Spacer(Modifier.height(20.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = foreground.copy(alpha = 0.4f),
                    )
                }
            }

            error != null -> {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = foreground.copy(alpha = 0.6f),
                )
                Spacer(Modifier.height(6.dp))
                TextAction(label = "Réessayer", onClick = onRetry)
            }

            state.threads.isEmpty() -> {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Personne n'a encore réagi à ce chapitre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = foreground.copy(alpha = 0.5f),
                )
            }

            else -> {
                state.threads.forEach { thread ->
                    Spacer(Modifier.height(12.dp))
                    ThreadCard(
                        thread = thread,
                        foreground = foreground,
                        onReply = onReply,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }

                if (!state.endReached) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isLoadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = foreground.copy(alpha = 0.4f),
                            )
                        } else {
                            TextAction(label = "Voir plus de commentaires", onClick = onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

/**
 * En-tête de la section, calqué sur l'en-tête du chapitre : sur-titre en capitales et
 * filet dégradé. La discussion se lit ainsi comme une nouvelle partie de la page, pas
 * comme un bloc rapporté.
 */
@Composable
private fun SectionHeading(total: Long, foreground: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "COMMENTAIRES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        if (total > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$total",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = foreground.copy(alpha = 0.45f),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0f),
                    ),
                ),
            ),
    )
}

/**
 * Volontairement en contour, alors que « chapitre suivant » juste au-dessus est plein :
 * deux boutons pleins côte à côte se disputeraient l'attention, et l'action attendue en
 * fin de chapitre reste de continuer à lire.
 */
@Composable
private fun WriteButton(foreground: Color, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Comment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Écrire un commentaire",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Un fil ────────────────────────────────────────────────────────────────────

@Composable
private fun ThreadCard(
    thread: ChapterCommentDto,
    foreground: Color,
    onReply: (Long, String?, Boolean) -> Unit,
    onEdit: (ChapterCommentDto, Long?) -> Unit,
    onDelete: (ChapterCommentDto, Long?) -> Unit,
) {
    Surface(
        // Teinte dérivée de la couleur du texte : la carte s'éclaircit sur fond sombre
        // et s'assombrit sur fond clair, sans qu'on ait à décliner une palette par thème.
        color = foreground.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            CommentBlock(
                comment = thread,
                foreground = foreground,
                onReply = { onReply(thread.id, thread.pseudo, false) },
                onEdit = { onEdit(thread, null) },
                onDelete = { onDelete(thread, null) },
            )

            thread.replies.forEach { reply ->
                // `IntrinsicSize.Min` donne au filet la hauteur exacte de la réponse ;
                // un `fillMaxHeight` seul n'aurait rien à quoi se mesurer ici.
                Row(modifier = Modifier.padding(top = 14.dp).height(IntrinsicSize.Min)) {
                    // Un seul filet pour toutes les réponses : le fil se lit d'un coup
                    // d'œil, sans l'escalier qu'un second niveau d'indentation créerait.
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp, end = 12.dp)
                            .width(2.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(foreground.copy(alpha = 0.15f)),
                    )
                    CommentBlock(
                        comment = reply,
                        foreground = foreground,
                        // Répondre à une réponse reste dans ce fil : le pseudo visé est
                        // simplement préfixé au message.
                        onReply = { onReply(thread.id, reply.pseudo, true) },
                        onEdit = { onEdit(reply, thread.id) },
                        onDelete = { onDelete(reply, thread.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentBlock(
    comment: ChapterCommentDto,
    foreground: Color,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // `remember` avant toute branche : un message peut devenir une pierre tombale sous
    // nos yeux, et un `return` anticipé ferait varier le nombre de blocs mémorisés.
    var confirmDelete by remember { mutableStateOf(false) }

    if (comment.deleted) {
        Text(
            text = "Message supprimé",
            style = MaterialTheme.typography.bodySmall,
            color = foreground.copy(alpha = 0.4f),
        )
        return
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Avatar(url = comment.avatarUrl, pseudo = comment.pseudo, foreground = foreground)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.pseudo.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = foreground.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTimeLabel(comment.createdAt) +
                        if (comment.edited) " · modifié" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.4f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = comment.body.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = foreground.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TextAction(label = "Répondre", onClick = onReply)
                if (comment.mine) {
                    TextAction(label = "Modifier", onClick = onEdit)
                    TextAction(
                        label = "Supprimer",
                        onClick = { confirmDelete = true },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce message ?") },
            text = { Text("Il ne sera plus visible par les autres lecteurs. C'est définitif.") },
            confirmButton = {
                Text(
                    text = "Supprimer",
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

@Composable
private fun TextAction(
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    )
}

@Composable
private fun Avatar(url: String?, pseudo: String?, foreground: Color) {
    val resolved = resolveImageUrl(url)
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(32.dp).clip(CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(foreground.copy(alpha = 0.12f)),
        ) {
            Text(
                text = pseudo.orEmpty().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = foreground.copy(alpha = 0.7f),
            )
        }
    }
}

// ── Rédaction ─────────────────────────────────────────────────────────────────

/**
 * Panneau de saisie, posé par-dessus la page. C'est la seule partie de la discussion
 * qui sort du fil du chapitre, et pour une raison précise : voir [ChapterCommentsSection].
 *
 * Il reprend l'habillage Material des autres panneaux du lecteur (réglages) plutôt que
 * les couleurs de lecture : c'est une surface flottante, pas un morceau de la page.
 */
@Composable
fun CommentComposerSheet(
    state: ChapterCommentsUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    // Le clavier monte tout de suite : on a ouvert ce panneau pour écrire, pas pour le
    // regarder.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            // Absorbe les taps : sans ça, toucher le panneau basculerait les barres du
            // lecteur, qui écoute le moindre tap sur le fond.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (val target = state.target) {
                        ComposerTarget.NewThread -> "Ton avis sur ce chapitre"
                        is ComposerTarget.Reply ->
                            target.toPseudo?.let { "Réponse à $it" } ?: "Réponse au fil"
                        is ComposerTarget.Edit -> "Modifier ton message"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextAction(
                    label = "Annuler",
                    onClick = onClose,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val actionError = state.actionError
            if (actionError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    placeholder = { Text("Écris ton message…") },
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 6,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onSend, enabled = state.canSend) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Publier",
                            tint = if (state.canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        }
    }
}
