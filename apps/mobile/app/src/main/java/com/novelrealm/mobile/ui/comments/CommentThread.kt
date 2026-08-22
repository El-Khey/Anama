package com.novelrealm.mobile.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.ChapterCommentDto
import com.novelrealm.mobile.data.remote.dto.MentionDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.CommentGif
import com.novelrealm.mobile.ui.components.EmojiPickerSheet
import com.novelrealm.mobile.ui.components.MentionText
import com.novelrealm.mobile.ui.components.ReactionBarPopup
import com.novelrealm.mobile.ui.util.relativeTimeLabel

/**
 * Le fil de discussion, **en un seul exemplaire pour toute l'app** (issue #45, §6).
 *
 * Il en existait deux : celui de la fin de chapitre et celui des passages. Ils ont
 * divergé, forcément — deux jeux de bulles, deux tailles d'avatar, un filet ici et
 * un simple décalage là. Résultat : d'un écran à l'autre, la même hiérarchie ne se
 * lisait pas pareil, et surtout une réponse ressemblait trait pour trait à un
 * message racine.
 *
 * ## Ce que le fil doit dire, et comment il le dit
 *
 * **Ce qui est une racine, et ce qui répond.** La racine porte une carte ; les
 * réponses n'en ont pas. C'est la différence la plus forte, et la moins coûteuse :
 * on voit la structure sans lire un mot. Avatars et graisse suivent (34 dp contre
 * 26 dp), le filet vertical fait le reste.
 *
 * **Un seul filet pour tout le groupe**, et non un segment par réponse. Un trait
 * continu dit « tout ceci appartient au message du dessus » ; des traits séparés
 * disaient « voici trois choses indépendantes qui, ça alors, sont décalées ».
 *
 * **À QUI répond une réponse.** Le serveur rattache toute réponse à la racine : il
 * n'existe pas de troisième niveau, et en simuler un serait mentir sur la donnée.
 * Ce qui existe vraiment, c'est la personne visée — portée par la mention insérée
 * à la rédaction. Quand elle diffère de l'auteur du fil, la réponse l'affiche
 * (« ↳ en réponse à Untel »). C'est exactement l'information qui manquait quand on
 * ne comprenait plus « quel degré de réponse » on lisait.
 *
 * Le composant est peint avec [foreground] plutôt qu'avec la palette Material : le
 * lecteur a ses propres fonds (sépia, OLED…) et des surfaces Material s'y verraient
 * posées par-dessus la page au lieu d'en faire partie.
 */

/** Réponses montrées d'emblée ; au-delà, elles se replient. */
private const val VISIBLE_REPLIES = 2

private val RootAvatar = 34.dp
private val ReplyAvatar = 26.dp

/**
 * Une réaction emoji agrégée sur un message : l'emoji, combien de lecteurs l'ont
 * posé, et si MOI j'en fais partie (pour surligner ma puce).
 */
data class ThreadReaction(
    val emoji: String,
    val count: Long,
    val mine: Boolean,
)

/**
 * Un message, indépendamment de la table d'où il vient. Les deux familles de
 * commentaires (fin de chapitre, passage) n'ont pas le même DTO, mais elles ont
 * le même fil — c'est ici qu'elles se rejoignent.
 */
data class ThreadComment(
    val id: Long,
    val userId: Long?,
    val pseudo: String?,
    val avatarUrl: String?,
    val body: String,
    val mentions: List<MentionDto> = emptyList(),
    val gifUrl: String? = null,
    val gifPreviewUrl: String? = null,
    val createdAt: String? = null,
    val mine: Boolean = false,
    val edited: Boolean = false,
    /** Pierre tombale : le message est supprimé mais ses réponses vivent encore. */
    val deleted: Boolean = false,
    val spoiler: Boolean = false,
    val reactions: List<ThreadReaction> = emptyList(),
    val replies: List<ThreadComment> = emptyList(),
)

/** Fusionne le décompte serveur et « mes » emojis en une liste de [ThreadReaction]. */
private fun buildReactions(
    tallies: List<com.novelrealm.mobile.data.remote.dto.EmojiTallyDto>,
    mine: List<String>,
): List<ThreadReaction> {
    val mineSet = mine.toSet()
    return tallies.map { ThreadReaction(it.emoji, it.count, it.emoji in mineSet) }
}

fun ChapterCommentDto.toThreadComment(): ThreadComment = ThreadComment(
    id = id,
    userId = userId,
    pseudo = pseudo,
    avatarUrl = avatarUrl,
    body = body.orEmpty(),
    mentions = mentions,
    gifUrl = gifUrl,
    gifPreviewUrl = gifPreviewUrl,
    createdAt = createdAt,
    mine = mine,
    edited = edited,
    deleted = deleted,
    reactions = buildReactions(reactions, myReactions),
    replies = replies.map { it.toThreadComment() },
)

fun PassageCommentDto.toThreadComment(): ThreadComment = ThreadComment(
    id = id,
    userId = userId,
    pseudo = pseudo,
    avatarUrl = avatarUrl,
    body = body,
    mentions = mentions,
    gifUrl = gifUrl,
    gifPreviewUrl = gifPreviewUrl,
    createdAt = createdAt,
    mine = mine,
    spoiler = spoiler,
    reactions = buildReactions(reactions, myReactions),
    replies = replies.map { it.toThreadComment() },
)

/**
 * Un fil : une racine et ses réponses.
 *
 * @param onReply reçoit le message VISÉ et la racine du fil — répondre à une
 *   réponse reste dans le même fil côté serveur, mais la personne visée n'est pas
 *   la même, et c'est elle qu'il faut mentionner puis notifier.
 * @param onEdit `null` quand la surface ne sait pas modifier (les messages de
 *   passage ne s'éditent pas) : l'action disparaît alors au lieu d'échouer.
 * @param onToggleReaction reçoit le message visé, la racine du fil et l'emoji ;
 *   `null` désactive les réactions sur cette surface. Le même appel pose ou retire
 *   selon l'état — c'est le serveur qui tranche.
 */
@Composable
fun CommentThread(
    root: ThreadComment,
    onReply: (target: ThreadComment, root: ThreadComment) -> Unit,
    onDelete: (comment: ThreadComment, root: ThreadComment) -> Unit,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onEdit: ((comment: ThreadComment, root: ThreadComment) -> Unit)? = null,
    onToggleReaction: ((comment: ThreadComment, root: ThreadComment, emoji: String) -> Unit)? = null,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
) {
    // Le repli est propre à chaque fil : déplier l'un ne déplie pas les autres.
    var expanded by remember(root.id) { mutableStateOf(false) }
    val replies = root.replies
    // Jamais de repli pour une seule réponse cachée : le bouton coûterait la place
    // qu'il fait gagner.
    val collapsed = replies.size > VISIBLE_REPLIES + 1 && !expanded
    val visible = if (collapsed) replies.take(VISIBLE_REPLIES) else replies

    Surface(
        // Teinte dérivée de la couleur du texte : la carte s'éclaircit sur fond
        // sombre et s'assombrit sur fond clair, sans décliner une palette par thème.
        color = foreground.copy(alpha = 0.05f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            CommentBody(
                comment = root,
                foreground = foreground,
                avatarSize = RootAvatar,
                isRoot = true,
                replyTo = null,
                onReply = { onReply(root, root) },
                onEdit = onEdit?.let { edit -> { edit(root, root) } },
                onDelete = { onDelete(root, root) },
                onOpenUser = onOpenUser,
                onToggleReaction = onToggleReaction?.let { toggle ->
                    { emoji -> toggle(root, root, emoji) }
                },
            )

            if (visible.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                // `IntrinsicSize.Min` donne au filet la hauteur exacte du groupe :
                // un `fillMaxHeight` seul n'aurait rien à quoi se mesurer ici.
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    ThreadRail(foreground)
                    Column {
                        visible.forEachIndexed { index, reply ->
                            if (index > 0) Spacer(Modifier.height(16.dp))
                            CommentBody(
                                comment = reply,
                                foreground = foreground,
                                avatarSize = ReplyAvatar,
                                isRoot = false,
                                replyTo = replyTargetOf(reply, root),
                                onReply = { onReply(reply, root) },
                                onEdit = onEdit?.let { edit -> { edit(reply, root) } },
                                onDelete = { onDelete(reply, root) },
                                onOpenUser = onOpenUser,
                                onToggleReaction = onToggleReaction?.let { toggle ->
                                    { emoji -> toggle(reply, root, emoji) }
                                },
                            )
                        }
                        if (collapsed) {
                            Spacer(Modifier.height(12.dp))
                            ThreadAction(
                                label = "Voir les ${replies.size - VISIBLE_REPLIES} autres réponses",
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { expanded = true },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * À qui cette réponse s'adresse-t-elle, si ce n'est pas à l'auteur du fil ?
 *
 * La mention insérée au moment de répondre est la seule trace fiable de la cible —
 * et elle survit à un renommage, puisqu'elle porte l'identifiant. Répondre à la
 * racine ne mérite aucune étiquette : c'est le cas par défaut, l'afficher
 * n'apprendrait rien et alourdirait chaque réponse.
 */
private fun replyTargetOf(reply: ThreadComment, root: ThreadComment): String? {
    val mention = reply.mentions.firstOrNull() ?: return null
    if (mention.userId == root.userId) return null
    if (mention.userId == reply.userId) return null
    // Le pseudo ACTUEL d'abord ; à défaut (compte supprimé depuis, champ absent)
    // le `handle`, c'est-à-dire le pseudo tel qu'il était écrit dans le message.
    // Moins à jour, mais toujours plus parlant qu'une étiquette vide.
    return mention.pseudo?.takeIf { it.isNotBlank() }
        ?: mention.handle.takeIf { it.isNotBlank() }
}

/** Le filet vertical qui rattache TOUT le groupe de réponses à sa racine. */
@Composable
private fun ThreadRail(foreground: Color) {
    Box(
        modifier = Modifier
            .padding(start = 5.dp, end = 13.dp)
            .width(2.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(foreground.copy(alpha = 0.16f)),
    )
}

// ── Un message ────────────────────────────────────────────────────────────────

@Composable
private fun CommentBody(
    comment: ThreadComment,
    foreground: Color,
    avatarSize: Dp,
    isRoot: Boolean,
    replyTo: String?,
    onReply: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleReaction: ((emoji: String) -> Unit)?,
) {
    // `remember` avant toute branche : un message peut devenir une pierre tombale
    // sous nos yeux, et un `return` anticipé ferait varier le nombre de blocs
    // mémorisés d'une recomposition à l'autre.
    var confirmDelete by remember { mutableStateOf(false) }
    // Un spoiler est MASQUÉ, pas flouté : `Modifier.blur` ne fait rien avant
    // Android 12, et un flou qui ne s'applique pas révèle ce qu'il devait cacher.
    var revealed by remember(comment.id) { mutableStateOf(!comment.spoiler) }
    // Barre de réaction rapide (appui long) et sélecteur complet (bouton « + »).
    var showReactionBar by remember(comment.id) { mutableStateOf(false) }
    var showEmojiPicker by remember(comment.id) { mutableStateOf(false) }

    if (comment.deleted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .clip(CircleShape)
                    .background(foreground.copy(alpha = 0.07f)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Message supprimé",
                style = MaterialTheme.typography.bodySmall,
                color = foreground.copy(alpha = 0.4f),
            )
        }
        return
    }

    val userId = comment.userId
    // L'appui long ouvre la barre de réaction — seulement si la surface les gère et
    // que le message est révélé (long-presser un spoiler ouvrirait une barre sur un
    // contenu qu'on n'a pas encore accepté de voir).
    val reactionGesture = if (onToggleReaction != null && revealed) {
        Modifier.pointerInput(comment.id) {
            detectTapGestures(onLongPress = { showReactionBar = true })
        }
    } else {
        Modifier
    }
    Row(modifier = Modifier.fillMaxWidth().then(reactionGesture)) {
        Avatar(
            url = comment.avatarUrl,
            pseudo = comment.pseudo,
            foreground = foreground,
            size = avatarSize,
            onClick = userId?.let { { onOpenUser(it) } },
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.pseudo.orEmpty().ifBlank { "Lecteur" },
                    style = if (isRoot) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    // Son propre message se repère à la couleur du pseudo — pas à un
                    // fond teinté, qui salit la carte dès que l'accent est chaud.
                    color = if (comment.mine) MaterialTheme.colorScheme.primary
                    else foreground.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (userId != null) {
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onOpenUser(userId) }
                            } else {
                                Modifier
                            },
                        ),
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

            if (replyTo != null) {
                Spacer(Modifier.height(3.dp))
                ReplyTargetTag(pseudo = replyTo, foreground = foreground)
            }

            if (revealed) {
                if (comment.body.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    MentionText(
                        body = comment.body,
                        mentions = comment.mentions,
                        color = foreground.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                        onMentionClick = onOpenUser,
                    )
                }
                comment.gifUrl?.let { gif ->
                    Spacer(Modifier.height(7.dp))
                    CommentGif(
                        gifUrl = gif,
                        previewUrl = comment.gifPreviewUrl,
                        width = 0,
                        height = 0,
                    )
                }
            } else {
                Spacer(Modifier.height(6.dp))
                SpoilerVeil(foreground = foreground, onReveal = { revealed = true })
            }

            // Les puces de réaction, sous le message. Un re-tap sur une puce déjà à
            // moi la retire (même appel que l'ajout — le serveur tranche). Le bouton
            // « + » d'ajout n'apparaît que si la surface gère les réactions.
            if (revealed && (comment.reactions.isNotEmpty() || onToggleReaction != null)) {
                Spacer(Modifier.height(8.dp))
                ReactionChips(
                    reactions = comment.reactions,
                    foreground = foreground,
                    onToggle = onToggleReaction,
                    onAdd = if (onToggleReaction != null) {
                        { showReactionBar = true }
                    } else {
                        null
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            // « Répondre » seul en accent ; modifier et supprimer restent gris.
            // Trois libellés colorés côte à côte pesaient plus lourd que le message
            // qu'ils accompagnent.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThreadAction(
                    label = "Répondre",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onReply,
                )
                if (comment.mine && onEdit != null) {
                    ThreadAction(
                        label = "Modifier",
                        color = foreground.copy(alpha = 0.5f),
                        onClick = onEdit,
                    )
                }
                if (comment.mine) {
                    ThreadAction(
                        label = "Supprimer",
                        color = foreground.copy(alpha = 0.5f),
                        onClick = { confirmDelete = true },
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

    // La barre de réaction rapide, en surimpression (appui long). Un tap dessus pose
    // l'emoji et referme ; le bouton « + » bascule vers le sélecteur complet.
    if (showReactionBar && onToggleReaction != null) {
        ReactionBarPopup(
            onPick = { emoji ->
                showReactionBar = false
                onToggleReaction(emoji)
            },
            onMore = {
                showReactionBar = false
                showEmojiPicker = true
            },
            onDismiss = { showReactionBar = false },
        )
    }

    if (showEmojiPicker && onToggleReaction != null) {
        Dialog(onDismissRequest = { showEmojiPicker = false }) {
            EmojiPickerSheet(
                onPick = { emoji ->
                    showEmojiPicker = false
                    onToggleReaction(emoji)
                },
                onClose = { showEmojiPicker = false },
            )
        }
    }
}

/**
 * Les puces de réaction sous un message. Chaque puce montre l'emoji et son compteur ;
 * celle où le lecteur figure est surlignée. Le « + » ouvre la barre de réaction.
 */
@Composable
private fun ReactionChips(
    reactions: List<ThreadReaction>,
    foreground: Color,
    onToggle: ((emoji: String) -> Unit)?,
    onAdd: (() -> Unit)?,
) {
    // Une seule ligne qui défile plutôt que de passer à la ligne : `horizontalScroll`
    // et non `FlowRow`, qui reste une API expérimentale (convention du projet, cf.
    // LibraryScreen). Les réactions restent alignées sous le message, comme Discord.
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        reactions.forEach { reaction ->
            val accent = MaterialTheme.colorScheme.primary
            val bg = if (reaction.mine) accent.copy(alpha = 0.16f) else foreground.copy(alpha = 0.07f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .then(
                        if (reaction.mine) {
                            Modifier.border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (onToggle != null) {
                            Modifier.clickable { onToggle(reaction.emoji) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(text = reaction.emoji, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = reaction.count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reaction.mine) accent else foreground.copy(alpha = 0.7f),
                )
            }
        }
        if (onAdd != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(foreground.copy(alpha = 0.07f))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddReaction,
                    contentDescription = "Ajouter une réaction",
                    tint = foreground.copy(alpha = 0.55f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** « ↳ en réponse à Untel » — l'étiquette qui dit à qui l'on parle dans un fil plat. */
@Composable
private fun ReplyTargetTag(pseudo: String, foreground: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.45f),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "en réponse à $pseudo",
            style = MaterialTheme.typography.labelSmall,
            color = foreground.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpoilerVeil(foreground: Color, onReveal: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(foreground.copy(alpha = 0.07f))
            .clickable(onClick = onReveal)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Visibility,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Spoiler — toucher pour révéler",
            style = MaterialTheme.typography.labelSmall,
            color = foreground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ThreadAction(label: String, color: Color, onClick: () -> Unit) {
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
private fun Avatar(
    url: String?,
    pseudo: String?,
    foreground: Color,
    size: Dp,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clip(CircleShape).clickable(onClick = onClick)
    } else {
        Modifier
    }
    val resolved = resolveImageUrl(url)
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .then(clickModifier),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(foreground.copy(alpha = 0.1f))
                .then(clickModifier),
        ) {
            Text(
                text = pseudo.orEmpty().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = foreground.copy(alpha = 0.7f),
            )
        }
    }
}
