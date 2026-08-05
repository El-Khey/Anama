package com.novelrealm.mobile.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.BlockActivityDto
import com.novelrealm.mobile.data.remote.dto.EmojiTallyDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.data.repository.PassageRepository
import com.novelrealm.mobile.ui.util.relativeTimeLabel

/**
 * Marque d'activité d'un passage (#41, §4) — une **note de bas de ligne**, pas une
 * pastille.
 *
 * <p><b>Aucun emoji n'est affiché ici, et c'est le point central.</b> Un emoji est une
 * image en couleurs : ni la teinte ni l'opacité du texte ne s'y appliquent, il reste
 * donc toujours aussi vif que le reste de la page est calme. Semé le long d'un
 * chapitre, il attire l'œil à chaque paragraphe et hache la lecture. La marque se
 * contente donc de deux glyphes monochromes teintés dans la couleur de lecture, à
 * 30 % d'opacité : on la voit si on la cherche, on l'oublie sinon.
 *
 * <p>Le détail — quels emojis, combien de chacun — appartient à la barre d'action, où
 * l'on arrive délibérément. Une marge n'est pas faite pour informer, seulement pour
 * signaler qu'il y a quelque chose.
 *
 * <p>Posée sous le paragraphe plutôt que dans la marge : celle-ci est réglable jusqu'à
 * zéro, et une marque qui y vivrait chevaucherait le texte dès qu'on colle aux bords.
 */
@Composable
fun BlockMark(
    activity: BlockActivityDto,
    foreground: Color,
    showComments: Boolean,
    onClick: () -> Unit,
) {
    val comments = if (showComments) activity.commentCount else 0L
    val reactions = activity.reactions.sumOf { it.count }
    if (reactions == 0L && comments == 0L) return

    // Assez pâle pour disparaître dans le gris du texte, assez présent pour se voir
    // quand on le cherche.
    val ink = foreground.copy(alpha = 0.3f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 5.dp),
    ) {
        if (reactions > 0) {
            MarkItem(
                icon = Icons.Outlined.EmojiEmotions,
                count = reactions,
                ink = ink,
                description = "Réactions sur ce passage",
            )
        }
        if (reactions > 0 && comments > 0) Spacer(Modifier.width(12.dp))
        if (comments > 0) {
            MarkItem(
                icon = Icons.Outlined.ModeComment,
                count = comments,
                ink = ink,
                description = "Commentaires sur ce passage",
            )
        }
    }
}

@Composable
private fun MarkItem(icon: ImageVector, count: Long, ink: Color, description: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = ink,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = ink,
        )
    }
}

/**
 * Le panneau d'un passage : réactions, discussion et saisie **d'un seul coup**.
 *
 * <p>Il n'y a plus de menu intermédiaire. Un premier essai en posait un — emojis, puis
 * « Commenter » — et il ratait l'essentiel : on touche une marque pour LIRE ce qui a
 * été dit, pas pour choisir dans une liste. Un menu ajoutait un geste avant la seule
 * chose qu'on était venu chercher.
 *
 * <p>Le passage n'est pas rappelé en tête non plus : il est juste derrière, à l'écran,
 * là où on vient de le toucher. L'écrire une seconde fois volait trois lignes à la
 * discussion pour redire ce qu'on avait sous les yeux.
 *
 * <p>« Citer » reste accessible, relégué en petit dans l'en-tête : c'est un geste rare
 * et solitaire, il n'a pas à disputer la place à la conversation.
 */
@Composable
fun PassageThreadSheet(
    state: PassageSocialUiState,
    reactions: List<EmojiTallyDto>,
    myEmoji: String?,
    showComments: Boolean,
    onReact: (String) -> Unit,
    onQuote: () -> Unit,
    onDelete: (PassageCommentDto) -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleSpoiler: () -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(bottom = 10.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                )
            }

            // Les emojis en premier : c'est la réponse la moins coûteuse, elle doit être
            // la plus proche du pouce à l'ouverture.
            ReactionRow(reactions = reactions, myEmoji = myEmoji, onReact = onReact)

            Spacer(Modifier.height(14.dp))
            SheetRule()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
            ) {
                Text(
                    text = if (showComments) commentCountLabel(state.thread.size) else "Ce passage",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                GhostAction(icon = Icons.Filled.FormatQuote, label = "Citer", onClick = onQuote)
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(8.dp)
                        .size(18.dp),
                )
            }

            if (showComments) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    when {
                        state.threadLoading -> Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }

                        state.thread.isEmpty() -> Text(
                            text = "Personne n'a encore réagi à ce passage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 14.dp),
                        )

                        else -> state.thread.forEach { comment ->
                            PassageCommentRow(comment = comment, onDelete = { onDelete(comment) })
                        }
                    }
                }
            }

            state.error?.let { message ->
                // Contenu et borné, plutôt que du rouge brut qui s'étale sur cinq
                // lignes : un message d'erreur ne doit pas prendre plus de place que
                // la discussion qu'il interrompt.
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
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }

            if (showComments) {
                Spacer(Modifier.height(6.dp))
                PassageComposer(
                    state = state,
                    onDraftChange = onDraftChange,
                    onToggleSpoiler = onToggleSpoiler,
                    onSend = onSend,
                )
            }
        }
    }
}

/** « 3 commentaires », ou l'invitation quand il n'y en a pas encore. */
private fun commentCountLabel(count: Int): String = when (count) {
    0 -> "Discussion"
    1 -> "1 commentaire"
    else -> "$count commentaires"
}

/** Filet horizontal très léger, pour séparer sans cloisonner. */
@Composable
private fun SheetRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    )
}

/**
 * La rangée d'emojis. Chacun porte son compte dessous : c'est la seule vue où le
 * détail a sa place, puisqu'on y est venu exprès.
 *
 * <p>Les six sont toujours affichés, même à zéro. Ne montrer que ceux déjà posés
 * ferait bouger les positions d'un passage à l'autre, et on toucherait celui qu'on ne
 * visait pas.
 */
@Composable
private fun ReactionRow(
    reactions: List<EmojiTallyDto>,
    myEmoji: String?,
    onReact: (String) -> Unit,
) {
    val counts = reactions.associate { it.emoji to it.count }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
    ) {
        PassageRepository.EMOJIS.forEach { emoji ->
            val mine = emoji == myEmoji
            val count = counts[emoji] ?: 0L
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent,
                    )
                    .clickable { onReact(emoji) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
            ) {
                Text(text = emoji, fontSize = 21.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (count > 0) "$count" else "–",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        mine -> MaterialTheme.colorScheme.primary
                        count > 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    },
                )
            }
        }
    }
}

/** Action discrète de l'en-tête : icône + libellé, sans cadre. */
@Composable
private fun GhostAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Un message du fil, présenté comme une **bulle** et non comme une ligne de tableau.
 *
 * <p>Le corps occupe toute la largeur de la bulle au lieu d'être coincé entre l'avatar
 * et une corbeille : c'est le texte qu'on vient lire, il ne doit pas être le seul
 * élément à céder de la place. L'auteur et l'heure passent au-dessus, en petit, et la
 * suppression n'apparaît que sur ses propres messages.
 */
@Composable
private fun PassageCommentRow(comment: PassageCommentDto, onDelete: () -> Unit) {
    // Un spoiler est MASQUÉ, pas flouté : `Modifier.blur` ne fait rien avant Android 12,
    // et un flou qui ne s'applique pas révèle exactement ce qu'il devait cacher.
    var revealed by remember(comment.id) { mutableStateOf(!comment.spoiler) }

    Surface(
        // Ses propres messages sont teintés de l'accent : on se retrouve dans un fil
        // sans avoir à lire les pseudos.
        color = if (comment.mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(url = comment.avatarUrl, pseudo = comment.pseudo)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = comment.pseudo ?: "Lecteur",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = relativeTimeLabel(comment.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (comment.mine) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Supprimer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onDelete)
                            .padding(4.dp)
                            .size(15.dp),
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            if (revealed) {
                Text(
                    text = comment.body,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                        .clickable { revealed = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Spoiler — toucher pour révéler",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Petite pastille d'option de la barre de saisie. */
@Composable
private fun ComposerChip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

@Composable
private fun Avatar(url: String?, pseudo: String?) {
    val resolved = resolveImageUrl(url)
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(22.dp).clip(CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
        ) {
            Text(
                text = pseudo.orEmpty().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Saisie d'un message de passage — même bulle que le composer de fin de chapitre.
 *
 * <p><b>Le clavier ne monte pas tout seul.</b> On ouvre ce panneau pour lire la
 * discussion ; un clavier qui surgit la recouvrirait aussitôt de moitié. Il apparaît
 * quand on touche le champ, c'est-à-dire quand on a décidé d'écrire.
 */
@Composable
private fun PassageComposer(
    state: PassageSocialUiState,
    onDraftChange: (String) -> Unit,
    onToggleSpoiler: () -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // La case spoiler n'apparaît qu'une fois qu'on écrit : affichée en permanence,
        // elle meublerait une ligne entière au-dessus d'un champ vide. C'est en
        // rédigeant qu'on se rend compte qu'on en dit trop.
        if (state.draft.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                ComposerChip(
                    label = "Spoiler",
                    active = state.spoiler,
                    onClick = onToggleSpoiler,
                )
                Spacer(Modifier.weight(1f))
                if (state.draft.length > PassageRepository.MAX_BODY_LENGTH * 4 / 5) {
                    Text(
                        text = "${state.draft.length} / ${PassageRepository.MAX_BODY_LENGTH}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(23.dp),
                modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 120.dp),
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.draft.isEmpty()) {
                        Text(
                            text = "Ce que ce passage t'inspire…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    BasicTextField(
                        value = state.draft,
                        onValueChange = {
                            if (it.length <= PassageRepository.MAX_BODY_LENGTH) onDraftChange(it)
                        },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))
            val enabled = state.canSend
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled || state.isSending) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                    .clickable(enabled = enabled, onClick = onSend),
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Publier",
                        tint = if (enabled) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
