package com.novelrealm.mobile.ui.comments

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
 * on voit la structure sans lire un mot. Avatars et graisse suivent (38 dp contre
 * 30 dp), le filet vertical fait le reste.
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

private val RootAvatar = 38.dp
private val ReplyAvatar = 30.dp

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
    /** Pouces verts / rouges, et le sens de MON vote (+1, -1, 0). */
    val likeCount: Long = 0,
    val dislikeCount: Long = 0,
    val myVote: Int = 0,
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
    likeCount = likes,
    dislikeCount = dislikes,
    myVote = myVote,
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
    likeCount = likes,
    dislikeCount = dislikes,
    myVote = myVote,
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
    // Vote (pouce vert / rouge). Reçoit le message visé, la racine du fil, et le sens
    // (+1 / -1). `null` masque les pouces. Le même appel pose, bascule ou retire selon
    // l'état — c'est le serveur qui tranche.
    onVote: ((comment: ThreadComment, root: ThreadComment, value: Int) -> Unit)? = null,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
    // Marge latérale du CONTENU. Le survol, lui, va bord à bord : c'est l'inset qui
    // rentre le contenu, pas la ligne surlignée. Les surfaces qui appelaient avec un
    // `padding(horizontal = 16)` autour passent désormais cette valeur ici et retirent
    // ce padding, sinon le survol s'arrêterait avant les bords (au lieu de les toucher).
    contentInset: Dp = 16.dp,
) {
    // Le repli est propre à chaque fil : déplier l'un ne déplie pas les autres.
    var expanded by remember(root.id) { mutableStateOf(false) }
    val replies = root.replies

    // Plus de carte : les commentaires sont À PLAT façon TikTok, séparés par de
    // l'espace, pas par des rectangles teintés. Seules les réponses gardent un filet
    // vertical pour montrer leur rattachement. Pas de padding horizontal ICI : le
    // survol doit pouvoir aller bord à bord, l'inset est posé plus bas sur le contenu.
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        CommentBody(
            comment = root,
            foreground = foreground,
            avatarSize = RootAvatar,
            contentInset = contentInset,
            isRoot = true,
            replyTo = null,
            onReply = { onReply(root, root) },
            onEdit = onEdit?.let { edit -> { edit(root, root) } },
            onDelete = { onDelete(root, root) },
            onOpenUser = onOpenUser,
            onToggleReaction = onToggleReaction?.let { toggle ->
                { emoji -> toggle(root, root, emoji) }
            },
            onVote = onVote?.let { vote ->
                { value -> vote(root, root, value) }
            },
        )

        if (replies.isNotEmpty()) {
            // Réponses REPLIÉES par défaut, façon TikTok. Replié : « Afficher N
            // réponses » sous la racine. Déplié : les réponses, PUIS « Masquer » tout
            // en bas — sous la DERNIÈRE réponse, jamais coincé sous le parent (ça
            // faisait bizarre : le bouton semblait appartenir à la racine, pas au
            // groupe qu'il ferme).
            if (!expanded) {
                Spacer(Modifier.height(10.dp))
                RepliesToggle(
                    count = replies.size,
                    expanded = false,
                    foreground = foreground,
                    startInset = contentInset + 32.dp,
                    onClick = { expanded = true },
                )
            } else {
                Spacer(Modifier.height(14.dp))
                // `IntrinsicSize.Min` donne au filet la hauteur exacte du groupe :
                // un `fillMaxHeight` seul n'aurait rien à quoi se mesurer ici.
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .padding(start = contentInset + 32.dp, end = contentInset),
                ) {
                    ThreadRail(foreground)
                    Column {
                        replies.forEachIndexed { index, reply ->
                            if (index > 0) Spacer(Modifier.height(16.dp))
                            CommentBody(
                                comment = reply,
                                foreground = foreground,
                                avatarSize = ReplyAvatar,
                                // Les réponses sont déjà en retrait (rail + décalage du
                                // groupe) : leur survol part de là et file jusqu'au bord.
                                contentInset = 0.dp,
                                isRoot = false,
                                replyTo = replyTargetOf(reply, root),
                                onReply = { onReply(reply, root) },
                                onEdit = onEdit?.let { edit -> { edit(reply, root) } },
                                onDelete = { onDelete(reply, root) },
                                onOpenUser = onOpenUser,
                                onToggleReaction = onToggleReaction?.let { toggle ->
                                    { emoji -> toggle(reply, root, emoji) }
                                },
                                onVote = onVote?.let { vote ->
                                    { value -> vote(reply, root, value) }
                                },
                            )
                        }
                    }
                }
                // « Masquer » aligné sous les réponses (même retrait que le rail).
                Spacer(Modifier.height(12.dp))
                RepliesToggle(
                    count = replies.size,
                    expanded = true,
                    foreground = foreground,
                    startInset = contentInset + 32.dp,
                    onClick = { expanded = false },
                )
            }
        }
    }
}

/**
 * « Afficher N réponses » / « Masquer les réponses », avec un chevron qui pivote.
 * Le trait horizontal à gauche reprend le rail des réponses TikTok.
 */
@Composable
private fun RepliesToggle(
    count: Int,
    expanded: Boolean,
    foreground: Color,
    startInset: Dp,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = startInset)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
    ) {
        // Le petit trait TikTok qui « raccroche » le bouton au fil des réponses.
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(1.dp)
                .clip(RoundedCornerShape(50))
                .background(foreground.copy(alpha = 0.22f)),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (expanded) {
                "Masquer les réponses"
            } else {
                "Afficher $count ${if (count > 1) "réponses" else "réponse"}"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = foreground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(3.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.5f),
            modifier = Modifier
                .size(17.dp)
                .rotate(if (expanded) -90f else 90f),
        )
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
    contentInset: Dp,
    isRoot: Boolean,
    replyTo: String?,
    onReply: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleReaction: ((emoji: String) -> Unit)?,
    onVote: ((value: Int) -> Unit)?,
) {
    // `remember` avant toute branche : un message peut devenir une pierre tombale
    // sous nos yeux, et un `return` anticipé ferait varier le nombre de blocs
    // mémorisés d'une recomposition à l'autre.
    var confirmDelete by remember { mutableStateOf(false) }
    // Un spoiler est MASQUÉ, pas flouté : `Modifier.blur` ne fait rien avant
    // Android 12, et un flou qui ne s'applique pas révèle ce qu'il devait cacher.
    var revealed by remember(comment.id) { mutableStateOf(!comment.spoiler) }
    // Menu contextuel (appui long) : Répondre / Modifier / Supprimer. L'appui long
    // n'ouvre PLUS la barre d'emojis — celle-ci passe par le « + » des puces.
    var showMenu by remember(comment.id) { mutableStateOf(false) }
    // Sélecteur d'emoji complet, ouvert depuis le « + » des puces de réaction.
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
    // Retour tactile façon TikTok/iOS : pendant qu'on garde le doigt sur un message,
    // il « s'enfonce » très légèrement (scale) et se teinte (fond). `onPress` reste
    // suspendu jusqu'au relâchement (`tryAwaitRelease`) — on tient donc l'état pressé
    // exactement le temps du contact, tap comme appui long compris.
    var pressed by remember(comment.id) { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "commentPressScale",
    )
    val pressBg by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "commentPressBg",
    )
    // Le geste sur un message : TAP = répondre, APPUI LONG = menu (Répondre /
    // Modifier / Supprimer). On ne l'attache que si le message est révélé — agir
    // sur un spoiler qu'on n'a pas accepté de voir n'aurait pas de sens.
    val messageGesture = if (revealed) {
        Modifier.pointerInput(comment.id) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    // Suspend jusqu'au relâchement OU à l'annulation (le doigt part
                    // en scroll) : dans les deux cas on rend son état normal au message.
                    tryAwaitRelease()
                    pressed = false
                },
                onTap = { onReply() },
                onLongPress = { showMenu = true },
            )
        }
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            // Survol BORD À BORD : le fond prend toute la largeur (pas d'arrondi latéral,
            // pas de marge — c'est la ligne entière qui s'allume, façon TikTok). L'inset
            // n'est appliqué qu'ENSUITE, au contenu, pour que le texte garde sa marge
            // pendant que le surlignage, lui, touche les deux bords.
            .background(foreground.copy(alpha = pressBg))
            .then(messageGesture)
            .padding(horizontal = contentInset, vertical = 6.dp),
    ) {
        Avatar(
            url = comment.avatarUrl,
            pseudo = comment.pseudo,
            foreground = foreground,
            size = avatarSize,
            onClick = userId?.let { { onOpenUser(it) } },
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Ligne du pseudo, façon TikTok : « auteur ▸ cible » quand la réponse
            // vise quelqu'un d'autre que la racine. Pseudo en GRIS (l'auteur est en
            // retrait, c'est le message qui compte) ; le mien reste en accent.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.pseudo.orEmpty().ifBlank { "Lecteur" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (comment.mine) MaterialTheme.colorScheme.primary
                    else foreground.copy(alpha = 0.62f),
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
                if (replyTo != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = foreground.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = replyTo,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = foreground.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }

            if (revealed) {
                if (comment.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    // Corps plus présent que le pseudo gris — c'est lui qu'on lit.
                    MentionText(
                        body = comment.body,
                        mentions = comment.mentions,
                        color = foreground.copy(alpha = 0.95f),
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
            // Ligne d'actions façon TikTok : date à GAUCHE, pouces vert/rouge à DROITE.
            // Le TAP sur le message répond, l'APPUI LONG ouvre le menu. Les pouces sont
            // maintenant de VRAIS votes : un tap pose (+1/-1), un re-tap du même sens
            // retire, l'autre sens bascule — c'est le serveur qui tranche, l'UI reflète
            // `myVote` renvoyé. Pouce PLEIN quand c'est mon vote, contour sinon.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = relativeTimeLabel(comment.createdAt) +
                        if (comment.edited) " · modifié" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = foreground.copy(alpha = 0.4f),
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                VoteThumb(
                    up = true,
                    count = comment.likeCount,
                    active = comment.myVote > 0,
                    foreground = foreground,
                    onClick = onVote?.let { { it(1) } },
                )
                Spacer(Modifier.width(14.dp))
                VoteThumb(
                    up = false,
                    count = comment.dislikeCount,
                    active = comment.myVote < 0,
                    foreground = foreground,
                    onClick = onVote?.let { { it(-1) } },
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

    // Le menu contextuel (appui long) : Répondre pour tous, Modifier / Supprimer si
    // le message est le mien. Un petit popup posé au centre, façon iOS/Telegram.
    if (showMenu) {
        CommentActionMenu(
            canEdit = comment.mine && onEdit != null,
            canDelete = comment.mine,
            foreground = foreground,
            onReply = { showMenu = false; onReply() },
            onEdit = { showMenu = false; onEdit?.invoke() },
            onDelete = { showMenu = false; confirmDelete = true },
            onDismiss = { showMenu = false },
        )
    }

    // La barre de réaction rapide, en surimpression (via le « + » des puces). Un tap
    // dessus pose l'emoji et referme ; le bouton « + » bascule vers le sélecteur complet.
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

/** Le vert du pouce levé quand c'est mon vote, le rouge du pouce baissé. */
private val VoteUpColor = Color(0xFF22C55E)
private val VoteDownColor = Color(0xFFEF4444)

/**
 * Un pouce de vote (vert levé / rouge baissé), avec son compteur. PLEIN et coloré quand
 * c'est MON vote, en contour gris sinon. `onClick` null = pouces inertes (surface qui ne
 * gère pas le vote) — ils s'affichent alors juste comme compteurs.
 */
@Composable
private fun VoteThumb(
    up: Boolean,
    count: Long,
    active: Boolean,
    foreground: Color,
    onClick: (() -> Unit)?,
) {
    val accent = if (up) VoteUpColor else VoteDownColor
    // Mon vote se lit à la COULEUR (vert/rouge plein) plutôt qu'à un pouce plein vs
    // contour : ça évite d'importer les deux variantes d'icône (même nom, collision) et
    // reste tout aussi clair — la couleur saute plus aux yeux que le remplissage.
    val tint = if (active) accent else foreground.copy(alpha = 0.4f)
    val icon = if (up) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbDown
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = if (up) "J'aime" else "Je n'aime pas",
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = tint,
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
                    // Plus de liseré autour de MES réactions : le fond teinté accent et le
                    // compteur en accent suffisent à les distinguer, sans le contour qui
                    // alourdissait la pastille.
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

/**
 * Le menu contextuel d'un message (appui long), façon Telegram/iOS : un petit
 * panneau flottant centré, fond assombri pour isoler le message visé. Répondre est
 * toujours là ; Modifier / Supprimer n'apparaissent que sur mes messages.
 */
@Composable
private fun CommentActionMenu(
    canEdit: Boolean,
    canDelete: Boolean,
    foreground: Color,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.width(220.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                MenuRow(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    label = "Répondre",
                    tint = MaterialTheme.colorScheme.onSurface,
                    onClick = onReply,
                )
                if (canEdit) {
                    MenuRow(
                        icon = Icons.Outlined.Edit,
                        label = "Modifier",
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onEdit,
                    )
                }
                if (canDelete) {
                    MenuRow(
                        icon = Icons.Outlined.Delete,
                        label = "Supprimer",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
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
