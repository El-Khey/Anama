package com.novelrealm.mobile.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.NotificationDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.notifications.NotificationsViewModel
import com.novelrealm.mobile.ui.util.relativeTimeLabel

/**
 * Onglet « Reçues » (issue #45, §3) — réponses à mes commentaires et mentions de
 * mon pseudo, les plus récentes en tête.
 *
 * Chaque carte répond à trois questions dans cet ordre : **qui** (l'avatar,
 * timbré de l'icône de l'événement), **quoi** (la phrase, puis l'extrait cité
 * pour reconnaître son propre fil), **où** (roman · chapitre · quand). L'appui
 * marque comme lue et emmène à l'endroit exact — passage précis ou discussion de
 * fin de chapitre.
 */
@Composable
internal fun AlertsList(
    viewModel: NotificationsViewModel,
    listState: LazyListState,
    onOpenChapter: (novelId: Long, chapterId: Long, blockIndex: Int, openComments: Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    InboxToolbar(
        left = {
            FilterPill(
                label = "Toutes",
                selected = !state.unreadOnly,
                onClick = { viewModel.setUnreadOnly(false) },
            )
            Spacer(Modifier.width(8.dp))
            FilterPill(
                label = "Non lues",
                selected = state.unreadOnly,
                onClick = { viewModel.setUnreadOnly(true) },
            )
        },
        right = {
            if (state.unreadCount > 0) {
                ToolbarAction(label = "Tout lire", onClick = viewModel::markAllRead)
            }
        },
    )

    when {
        state.isLoading -> LoadingScreen()
        state.error != null -> EmptyScreen(
            message = state.error ?: "",
            actionLabel = "Réessayer",
            onAction = viewModel::load,
        )
        state.notifications.isEmpty() -> InboxEmpty(
            icon = Icons.Outlined.NotificationsNone,
            title = if (state.unreadOnly) "Tout est lu" else "Rien pour l'instant",
            subtitle = if (state.unreadOnly) "Aucune notification non lue."
            else "Les réponses à tes commentaires et les mentions de ton pseudo arriveront ici.",
        )
        else -> LazyColumn(
            state = listState,
            contentPadding = ListContentPadding,
            modifier = Modifier.fillMaxSize(),
        ) {
            dayGroupedItems(
                items = state.notifications,
                key = { it.id },
                dateOf = { it.createdAt },
            ) { notification ->
                AlertCard(
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
            }

            if (!state.endReached) {
                loadMoreItem(isLoading = state.isLoadingMore, onClick = viewModel::loadMore)
            }
        }
    }
}

@Composable
private fun AlertCard(notification: NotificationDto, onClick: () -> Unit) {
    InboxCard(onClick = onClick, highlighted = !notification.read) {
        Row {
            ActorBadge(notification)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Headline(notification)

                val excerpt = notification.excerpt
                if (!excerpt.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    QuoteRail(text = excerpt)
                }

                Spacer(Modifier.height(8.dp))
                MetaLine(
                    text = buildString {
                        notification.novelTitle?.let { append(it) }
                        notification.chapterNumber?.let {
                            if (isNotEmpty()) append("  ·  ")
                            append("Chap. $it")
                        }
                        val time = relativeTimeLabel(notification.createdAt)
                        if (time.isNotEmpty()) {
                            if (isNotEmpty()) append("  ·  ")
                            append(time)
                        }
                    },
                )
            }

            // Le point de non-lu : dernier élément de la ligne, aligné sur la
            // phrase. Le fond teinté le double déjà — mais lui seul reste visible
            // en plein soleil.
            if (!notification.read) {
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * La phrase, avec le pseudo en gras : c'est le nom qu'on cherche en balayant la
 * liste, pas le verbe.
 */
@Composable
private fun Headline(notification: NotificationDto) {
    val actor = notification.actorPseudo?.takeIf { it.isNotBlank() } ?: "Quelqu'un"
    // Présent et non passé composé : « te mentionne » s'accorde avec tout le
    // monde, là où « t'a mentionné(e) » obligerait à choisir.
    val verb = when (notification.type) {
        "MENTION" -> " te mentionne"
        "NEW_CHAPTER" -> " a publié un chapitre"
        else -> " a répondu à ton message"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = actor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = verb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

/** « Qui » et « quoi » en une seule image : l'avatar, timbré de l'icône de l'événement. */
@Composable
private fun ActorBadge(notification: NotificationDto) {
    Box {
        val avatar = resolveImageUrl(notification.actorAvatarUrl)
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Text(
                    text = notification.actorPseudo.orEmpty().take(1).uppercase().ifBlank { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Le timbre déborde légèrement de l'avatar et porte un liseré de la
        // couleur de la carte : sans ce détour, il se confond avec l'avatar dès
        // que celui-ci est sombre.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(19.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(1.5.dp)
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
