package com.novelrealm.mobile.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.GifBox
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.dto.UserSearchDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.data.repository.PassageRepository
import com.novelrealm.mobile.ui.comments.CommentThread
import com.novelrealm.mobile.ui.comments.toThreadComment
import com.novelrealm.mobile.ui.components.MentionSuggestionRow

// NB : `AttachedGifPreview` et `GifButton` viennent de ChapterComments.kt — même
// paquet, pas d'import : les deux composers doivent rester identiques au pixel.

/**
 * Marque d'activité d'un passage (#41, §4) — une **note de bas de ligne** sous le
 * paragraphe.
 *
 * <p><b>Les réactions s'affichent désormais en puces emoji</b> (façon Discord), quand
 * [showReactions] est vrai — les petits rectangles `😱 1` `🔥 1`, celui où le lecteur
 * figure surligné, chacun retirable d'un tap. C'est un renversement assumé du choix
 * d'origine (aucun emoji en marge) : le lecteur a demandé à voir les réactions sur le
 * texte, et un réglage ([showReactions]) permet de les couper à qui préfère lire au
 * calme.
 *
 * <p>Le compteur de commentaires, lui, reste un glyphe monochrome discret, aligné à
 * droite sur la même ligne (comme le 💬 de Discord). Toucher la ligne ouvre la feuille
 * du passage (commentaires + citer).
 *
 * <p>Posée sous le paragraphe plutôt que dans la marge : celle-ci est réglable jusqu'à
 * zéro, et une marque qui y vivrait chevaucherait le texte dès qu'on colle aux bords.
 */
@Composable
fun BlockMark(
    activity: BlockActivityDto,
    foreground: Color,
    showComments: Boolean,
    showReactions: Boolean,
    onClick: () -> Unit,
    onToggleReaction: (emoji: String) -> Unit,
) {
    val comments = if (showComments) activity.commentCount else 0L
    val chips = if (showReactions) activity.reactions else emptyList()
    if (chips.isEmpty() && comments == 0L) return

    val ink = foreground.copy(alpha = 0.3f)
    val myReactions = activity.myReactions.toSet()

    // Les puces à GAUCHE, le compteur de commentaires TOUJOURS à droite. `SpaceBetween`
    // sépare les deux blocs sans dépendre d'un Spacer pondéré, qui décalait le 💬 quand
    // il n'y avait pas de réaction.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
    ) {
        // Bloc de gauche : les puces de réaction, sur une ligne qui défile si nombreuses
        // (`horizontalScroll` et non FlowRow, expérimental — convention du projet). Prend
        // la place restante sans écraser le 💬 (`weight(1f, fill = false)`).
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState()),
        ) {
            chips.forEach { tally ->
                BlockReactionChip(
                    emoji = tally.emoji,
                    count = tally.count,
                    mine = tally.emoji in myReactions,
                    ink = ink,
                    foreground = foreground,
                    onClick = { onToggleReaction(tally.emoji) },
                )
            }
        }
        // Bloc de droite : le compteur de commentaires, discret — toucher ouvre la feuille.
        if (comments > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                MarkItem(
                    icon = Icons.Outlined.ModeComment,
                    count = comments,
                    ink = ink,
                    description = "Commentaires sur ce passage",
                )
            }
        }
    }
}

/**
 * Une puce de réaction sous un paragraphe : l'emoji + son compteur, **à la taille du
 * compteur de commentaires** et dans le même gris neutre — pas de teinte d'accent, qui
 * jurait avec le calme de la page. Sa réaction se repère à un simple liseré, pas à une
 * bulle colorée. Un tap la bascule (pose/retire).
 */
@Composable
private fun BlockReactionChip(
    emoji: String,
    count: Long,
    mine: Boolean,
    ink: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(shape)
            .background(foreground.copy(alpha = 0.06f))
            .then(
                // Ma réaction : un liseré gris un peu plus marqué, pas de couleur.
                if (mine) {
                    Modifier.border(1.dp, foreground.copy(alpha = 0.28f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(text = emoji, fontSize = 10.sp)
        if (count > 0) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = if (mine) FontWeight.SemiBold else FontWeight.Normal,
                color = ink,
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
 * <p>Il n'y a pas de menu intermédiaire. Un premier essai en posait un — emojis, puis
 * « Commenter » — et il ratait l'essentiel : on touche une marque pour LIRE ce qui a
 * été dit, pas pour choisir dans une liste.
 *
 * <p>Le passage n'est pas rappelé en tête : il est juste derrière, à l'écran, là où on
 * vient de le toucher. L'écrire une seconde fois volait trois lignes à la discussion
 * pour redire ce qu'on avait sous les yeux.
 *
 * <p>Les intitulés en capitales espacées reprennent la signature des sections de
 * l'app (réglages, commentaires de fin de chapitre) : le panneau doit se lire comme
 * une partie de NovelRealm, pas comme une boîte de dialogue rapportée.
 */
@Composable
fun PassageThreadSheet(
    state: PassageSocialUiState,
    showComments: Boolean,
    onQuote: () -> Unit,
    onDelete: (PassageCommentDto) -> Unit,
    onReply: (PassageCommentDto) -> Unit,
    onReactComment: (annotationId: Long, emoji: String) -> Unit,
    onCancelReply: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleSpoiler: () -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onPickMention: (UserSearchDto) -> Unit,
    onInsertMention: () -> Unit,
    onOpenGifPicker: () -> Unit,
    onRemoveGif: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 20.dp,
        modifier = modifier
            // Feuille haute façon TikTok : ~70 % de l'écran, plutôt que de s'ajuster au
            // contenu (elle était minuscule). Le fil prend toute la place disponible.
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            // En-tête TikTok : poignée, titre « N commentaires » CENTRÉ, croix à droite.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                )
                Text(
                    text = if (showComments) commentCountLabel(state.thread.size)
                    else "Ce passage",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 12.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 10.dp, end = 6.dp),
                ) {
                    // « Citer » : geste rare, en petite icône dans l'en-tête.
                    Icon(
                        imageVector = Icons.Filled.FormatQuote,
                        contentDescription = "Citer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onQuote)
                            .padding(7.dp)
                            .size(19.dp),
                    )
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Fermer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onClose)
                            .padding(7.dp)
                            .size(19.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // Filet de séparation sous l'en-tête, comme TikTok.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )

            if (showComments) {
                Column(
                    modifier = Modifier
                        // La feuille a maintenant une hauteur FIXE (70 %) : le fil prend
                        // tout l'espace entre l'en-tête et le composeur (`weight(1f)`), et
                        // défile à l'intérieur. Le composeur, sans poids, garde sa taille —
                        // c'est ce qui l'empêchait de s'aplatir quand le clavier monte.
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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

                        else -> state.thread.forEach { root ->
                            // Même composant que la discussion de fin de chapitre :
                            // une réponse doit se lire pareil partout (issue #45, §6).
                            // La table id -> DTO rend au ViewModel l'objet qu'il
                            // attend, sans le faire transiter par l'affichage.
                            val byId = remember(root) {
                                (listOf(root) + root.replies).associateBy { it.id }
                            }
                            CommentThread(
                                root = remember(root) { root.toThreadComment() },
                                onReply = { target, _ -> byId[target.id]?.let(onReply) },
                                onDelete = { comment, _ -> byId[comment.id]?.let(onDelete) },
                                onToggleReaction = { comment, _, emoji ->
                                    onReactComment(comment.id, emoji)
                                },
                                onOpenUser = onOpenUser,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
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
                Spacer(Modifier.height(4.dp))
                PassageComposer(
                    state = state,
                    onDraftChange = onDraftChange,
                    onToggleSpoiler = onToggleSpoiler,
                    onSend = onSend,
                    onCancelReply = onCancelReply,
                    onPickMention = onPickMention,
                    onInsertMention = onInsertMention,
                    onOpenGifPicker = onOpenGifPicker,
                    onRemoveGif = onRemoveGif,
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
    onCancelReply: () -> Unit,
    onPickMention: (UserSearchDto) -> Unit,
    onInsertMention: () -> Unit,
    onOpenGifPicker: () -> Unit,
    onRemoveGif: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Le GIF joint, au-dessus du champ — là où il apparaîtra dans le message.
        state.attachedGif?.let { gif ->
            AttachedGifPreview(
                previewUrl = gif.previewUrl.ifBlank { gif.url },
                onRemove = onRemoveGif,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Suggestions du `@…` en cours de frappe (issue #45, §2).
        MentionSuggestionRow(
            suggestions = state.mentionSuggestions,
            onPick = onPickMention,
        )

        // À qui l'on répond, juste au-dessus du champ. Sans ce rappel, on tape sa
        // réponse sans plus savoir à quel message elle s'accroche — et une fois
        // publiée, il est trop tard pour s'en apercevoir.
        state.replyTo?.let { target ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
            ) {
                Text(
                    text = "Réponse à ${target.pseudo ?: "ce message"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Annuler la réponse",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onCancelReply)
                        .padding(5.dp)
                        .size(13.dp),
                )
            }
        }
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
            // Avatar du lecteur en tête, comme TikTok. L'app ne garde pas l'avatar en
            // mémoire : il est chargé une fois par le ViewModel (`getMe()`), et retombe
            // sur l'initiale du pseudo tant qu'il n'est pas là.
            ComposerAvatar(
                avatarUrl = state.myAvatarUrl,
                pseudo = state.myPseudo,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(Modifier.width(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(23.dp),
                modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 120.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                    ) {
                        if (state.draft.isEmpty()) {
                            Text(
                                text = if (state.replyTo != null) "Ta réponse…"
                                else "Ajouter un commentaire…",
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
                    // Les icônes DANS le champ, à droite (schéma TikTok) : GIF et @.
                    // Pas d'image ni de cadeau — on ne les gère pas.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp, bottom = 11.dp),
                    ) {
                        // Un GIF ne se joint qu'une fois : l'icône disparaît dès qu'un est là.
                        if (state.gifAvailable && state.attachedGif == null) {
                            ComposerIcon(
                                icon = Icons.Outlined.GifBox,
                                description = "Ajouter un GIF",
                                onClick = onOpenGifPicker,
                            )
                        }
                        ComposerIcon(
                            icon = Icons.Outlined.AlternateEmail,
                            description = "Mentionner quelqu'un",
                            onClick = onInsertMention,
                        )
                    }
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

/** Une icône d'action DANS le champ de saisie (GIF, @) — discrète, cliquable. */
@Composable
private fun ComposerIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp)
            .size(21.dp),
    )
}

/** L'avatar du lecteur en tête du composeur — vrai avatar, ou initiale du pseudo. */
@Composable
private fun ComposerAvatar(avatarUrl: String?, pseudo: String?, modifier: Modifier = Modifier) {
    val resolved = resolveImageUrl(avatarUrl)
    val size = 34.dp
    if (resolved != null) {
        AsyncImage(
            model = resolved,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
        ) {
            Text(
                text = pseudo.orEmpty().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

// ── Confirmation d'une réaction ───────────────────────────────────────────────

/** Une goutte : sa colonne, son retard au départ, sa taille. */
private data class RainDrop(val xFraction: Float, val delay: Float, val size: Int)

/**
 * Pluie brève de l'emoji qu'on vient de poser.
 *
 * <p><b>Pourquoi une animation plutôt qu'un message.</b> Le panneau se referme au
 * moment où l'on réagit : sans rien, le geste n'aurait aucun accusé de réception, et
 * le compteur qui vient de changer est déjà hors de vue. La pluie dit « c'est parti »
 * sans occuper une ligne d'écran ni demander à être fermée.
 *
 * <p><b>Volontairement sobre.</b> Neuf gouttes, une seconde et demie, une chute droite
 * et un fondu — pas de rotation, pas de rebond, pas de gerbe de confettis. C'est une
 * confirmation, pas une récompense : elle doit se remarquer une fois et ne jamais
 * lasser à la centième.
 *
 * <p>Les trajectoires sont dérivées de l'index de chaque goutte plutôt que tirées au
 * hasard : deux réactions de suite donnent la même pluie, ce qui la rend familière au
 * lieu de la faire paraître erratique. Aucun tirage aléatoire n'est nécessaire pour
 * que ce soit joli.
 *
 * <p>Le composable n'intercepte aucun geste : il n'a pas de `pointerInput`, les taps
 * traversent donc jusqu'au texte du chapitre.
 */
@Composable
fun EmojiRain(emoji: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember(emoji) { Animatable(0f) }
    val drops = remember {
        List(DROP_COUNT) { index ->
            RainDrop(
                // Réparties sur la largeur, avec un léger décalage propre à chacune
                // pour éviter l'alignement en peigne.
                xFraction = (index + 0.5f) / DROP_COUNT + ((index * 7 % 5) - 2) * 0.018f,
                delay = (index * 13 % 7) * 0.055f,
                size = 17 + (index * 5 % 3) * 5,
            )
        }
    }

    LaunchedEffect(emoji) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1500, easing = LinearEasing))
        onDone()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val height = maxHeight
        val width = maxWidth
        drops.forEach { drop ->
            // Chaque goutte parcourt sa propre fraction du temps total : celles qui
            // partent en retard tombent donc un peu plus vite, et toutes ont disparu
            // à la fin — aucune ne reste figée en bas de l'écran.
            val local = ((progress.value - drop.delay) / (1f - drop.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach

            val fade = when {
                local < 0.12f -> local / 0.12f            // apparition
                local > 0.72f -> (1f - local) / 0.28f     // effacement avant le bas
                else -> 1f
            }
            Text(
                text = emoji,
                fontSize = drop.size.sp,
                modifier = Modifier
                    .offset(
                        x = width * drop.xFraction - (drop.size / 2).dp,
                        y = height * local - 30.dp,
                    )
                    // `Modifier.alpha` agit sur la couche entière, donc aussi sur un
                    // emoji : la couleur du texte, elle, n'a aucun effet sur un glyphe
                    // en couleurs.
                    .alpha(fade),
            )
        }
    }
}

/** Assez pour que ça se voie, assez peu pour que ça reste sobre. */
private const val DROP_COUNT = 9
