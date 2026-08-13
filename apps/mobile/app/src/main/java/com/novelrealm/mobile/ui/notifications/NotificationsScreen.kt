package com.novelrealm.mobile.ui.notifications

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
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.NotificationDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.util.relativeTimeLabel

/**
 * La cloche (issue #45, §3) — réponses à mes commentaires et mentions, les plus
 * récentes en tête.
 *
 * Chaque ligne dit trois choses d'un coup d'œil : QUI (avatar + pseudo), QUOI
 * (l'icône typée et la phrase), OÙ (roman · chapitre). L'appui marque comme lue
 * ET emmène directement à la discussion — fin de chapitre ou passage précis, le
 * lecteur sait déjà faire les deux.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    /**
     * Ouvre le lecteur au bon endroit : `blockIndex` ≥ 0 vise un passage précis,
     * `openComments` fait défiler jusqu'à la discussion de fin de chapitre.
     */
    onOpenChapter: (novelId: Long, chapterId: Long, blockIndex: Int, openComments: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Header(
            unreadCount = state.unreadCount,
            onBack = onBack,
            onMarkAllRead = viewModel::markAllRead,
        )

        FilterRow(
            unreadOnly = state.unreadOnly,
            onChange = viewModel::setUnreadOnly,
        )
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )

        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> EmptyScreen(
                message = state.error ?: "",
                actionLabel = "Réessayer",
                onAction = viewModel::load,
            )
            state.notifications.isEmpty() -> EmptyBell(unreadOnly = state.unreadOnly)
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.notifications, key = { it.id }) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = {
                            viewModel.markRead(notification)
                            val novelId = notification.novelId
                            val chapterId = notification.chapterId
                            if (novelId != null && chapterId != null) {
                                val passage = notification.commentKind == "PASSAGE_COMMENT"
                                onOpenChapter(
                                    novelId,
                                    chapterId,
                                    if (passage) notification.blockIndex ?: -1 else -1,
                                    !passage,
                                )
                            }
                        },
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(start = 74.dp),
                    )
                }

                if (!state.endReached) {
                    item {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
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
private fun Header(unreadCount: Long, onBack: () -> Unit, onMarkAllRead: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (unreadCount > 0) {
                Text(
                    text = if (unreadCount == 1L) "1 non lue" else "$unreadCount non lues",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (unreadCount > 0) {
            Text(
                text = "Tout lire",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMarkAllRead)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Deux pastilles : Toutes / Non lues — même langage que les filtres d'Explorer. */
@Composable
private fun FilterRow(unreadOnly: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        FilterPill(label = "Toutes", selected = !unreadOnly, onClick = { onChange(false) })
        Spacer(Modifier.width(8.dp))
        FilterPill(label = "Non lues", selected = unreadOnly, onClick = { onChange(true) })
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun EmptyBell(unreadOnly: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.NotificationsNone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (unreadOnly) "Aucune notification non lue."
            else "Rien pour l'instant.\nRéponses et mentions arriveront ici.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ── Une ligne ─────────────────────────────────────────────────────────────────

@Composable
private fun NotificationRow(notification: NotificationDto, onClick: () -> Unit) {
    // Une non-lue se voit DEUX fois : fond légèrement teinté + point d'accent.
    // Le fond seul est trop subtil sur certains écrans, le point seul se rate.
    val background = if (notification.read) Color.Transparent
    else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ActorBadge(notification)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline(notification),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (notification.read) FontWeight.Normal else FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // L'extrait, en retrait citation : on reconnaît son propre fil avant
            // même d'ouvrir.
            val excerpt = notification.excerpt
            if (!excerpt.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Row {
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp, bottom = 2.dp)
                            .width(2.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    notification.novelTitle?.let { append(it) }
                    notification.chapterNumber?.let {
                        if (isNotEmpty()) append(" · ")
                        append("Chap. $it")
                    }
                    val time = relativeTimeLabel(notification.createdAt)
                    if (time.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(time)
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (!notification.read) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** « Qui » + « quoi » en une image : l'avatar, timbré de l'icône de l'événement. */
@Composable
private fun ActorBadge(notification: NotificationDto) {
    Box {
        val avatar = resolveImageUrl(notification.actorAvatarUrl)
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(42.dp).clip(CircleShape),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    text = notification.actorPseudo.orEmpty().take(1).uppercase()
                        .ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            Icon(
                imageVector = typeIcon(notification.type),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

private fun typeIcon(type: String): ImageVector = when (type) {
    "MENTION" -> Icons.Filled.AlternateEmail
    "NEW_CHAPTER" -> Icons.Filled.NewReleases
    else -> Icons.AutoMirrored.Filled.Reply
}

private fun headline(notification: NotificationDto): String {
    val actor = notification.actorPseudo ?: "Quelqu'un"
    return when (notification.type) {
        // Présent et non passé composé : « te mentionne » s'accorde avec tout le
        // monde, là où « t'a mentionné(e) » obligerait à choisir.
        "MENTION" -> "$actor te mentionne"
        "NEW_CHAPTER" -> "Nouveau chapitre disponible"
        else -> "$actor a répondu à ton commentaire"
    }
}
