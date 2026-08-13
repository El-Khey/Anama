package com.novelrealm.mobile.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.EmojiEmotions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelrealm.mobile.data.remote.dto.BlockActivityDto
import com.novelrealm.mobile.data.remote.dto.EmojiTallyDto
import com.novelrealm.mobile.data.remote.dto.PassageCommentDto
import com.novelrealm.mobile.data.remote.dto.UserSearchDto
import com.novelrealm.mobile.data.repository.PassageRepository
import com.novelrealm.mobile.ui.comments.CommentThread
import com.novelrealm.mobile.ui.comments.toThreadComment
import com.novelrealm.mobile.ui.components.MentionSuggestionRow

// NB : `AttachedGifPreview` et `GifButton` viennent de ChapterComments.kt — même
// paquet, pas d'import : les deux composers doivent rester identiques au pixel.

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
    reactions: List<EmojiTallyDto>,
    myEmoji: String?,
    showComments: Boolean,
    onReact: (String) -> Unit,
    onQuote: () -> Unit,
    onDelete: (PassageCommentDto) -> Unit,
    onReply: (PassageCommentDto) -> Unit,
    onCancelReply: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleSpoiler: () -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onPickMention: (UserSearchDto) -> Unit,
    onOpenGifPicker: () -> Unit,
    onRemoveGif: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        tonalElevation = 6.dp,
        shadowElevation = 20.dp,
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
            // Poignée centrée, fermeture à droite : la croix ne doit pas décaler la
            // poignée du milieu, elle est donc posée PAR-DESSUS et non à côté.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(7.dp)
                        .size(17.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            PassageSectionLabel("Réactions")
            // Les emojis d'abord, et avec une vraie surface : c'est la réponse la moins
            // coûteuse, elle doit être la plus visible et la plus proche du pouce.
            ReactionRow(reactions = reactions, myEmoji = myEmoji, onReact = onReact)

            Spacer(Modifier.height(16.dp))
            PassageRule()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 2.dp),
            ) {
                Text(
                    text = if (showComments) commentCountLabel(state.thread.size).uppercase()
                    else "CE PASSAGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                // « Citer » relégué en petit : geste rare et solitaire, il n'a pas à
                // disputer la place à la conversation.
                GhostAction(icon = Icons.Filled.FormatQuote, label = "Citer", onClick = onQuote)
            }

            if (showComments) {
                Column(
                    modifier = Modifier
                        // `weight(fill = false)` : le fil prend ce qui RESTE, pas ce qu'il
                        // veut. Une Column mesure d'abord les enfants sans poids, dans
                        // l'ordre, chacun avec l'espace encore libre — le fil passait donc
                        // avant le composeur et lui laissait les miettes. Dès que le
                        // clavier montait (safeDrawing ajoute sa hauteur en marge basse),
                        // il ne restait plus rien : le champ s'aplatissait sous son
                        // `heightIn(min = 46.dp)`, et le bouton d'envoi devenait un ovale,
                        // `CircleShape` appliqué à une boîte écrasée n'étant plus un rond.
                        //
                        // Avec un poids, le fil est mesuré en DERNIER : le composeur garde
                        // sa taille, et la discussion se contente du reste — au pire elle
                        // défile, ce qu'elle sait déjà faire.
                        .weight(1f, fill = false)
                        .heightIn(max = 260.dp)
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

/** Intitulé de section : capitales espacées en accent, comme partout dans l'app. */
@Composable
private fun PassageSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
    )
}

/** Filet horizontal très léger, pour séparer sans cloisonner. */
@Composable
private fun PassageRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    )
}

/**
 * La rangée d'emojis — six **tuiles** de largeur égale.
 *
 * <p><b>Chaque emoji porte sa propre surface</b>, au lieu de flotter sur le fond. Sans
 * elle, il n'y avait rien à toucher : la cible se devinait, et la rangée se lisait
 * comme une décoration plutôt que comme la première action du panneau.
 *
 * <p><b>Réparties au poids, pas par `SpaceBetween`.</b> Cette dernière collait le
 * premier et le dernier emoji contre les bords, jusqu'à les rogner. Six poids égaux
 * dans une rangée à marges fixes tombent juste quelle que soit la largeur d'écran.
 *
 * <p>Les six sont toujours affichés, même à zéro : ne montrer que ceux déjà posés
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
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        PassageRepository.EMOJIS.forEach { emoji ->
            ReactionTile(
                emoji = emoji,
                count = counts[emoji] ?: 0L,
                mine = emoji == myEmoji,
                onClick = { onReact(emoji) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReactionTile(
    emoji: String,
    count: Long,
    mine: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        color = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
        shape = shape,
        modifier = modifier.then(
            // Un contour plutôt qu'un simple aplat plus vif : sa réaction se repère au
            // premier coup d'œil même quand l'accent est proche du fond du thème.
            if (mine) {
                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), shape)
            } else {
                Modifier
            },
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onClick)
                .padding(vertical = 9.dp),
        ) {
            Text(text = emoji, fontSize = 23.sp)
            Spacer(Modifier.height(4.dp))
            // Emplacement de hauteur FIXE, vide quand personne n'a réagi. Le tiret qui
            // s'y trouvait avant ne voulait rien dire — il ne servait qu'à empêcher la
            // tuile de rétrécir. Une boîte réservée fait le même travail sans rien
            // écrire, et les six tuiles gardent la même hauteur.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height(13.dp),
            ) {
                if (count > 0) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (mine) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }
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
            // Un GIF ne se joint qu'une fois : le bouton disparaît dès qu'un est là.
            if (state.gifAvailable && state.attachedGif == null) {
                GifButton(onClick = onOpenGifPicker)
                Spacer(Modifier.width(8.dp))
            }
            Surface(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(23.dp),
                modifier = Modifier.weight(1f).heightIn(min = 46.dp, max = 120.dp),
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (state.draft.isEmpty()) {
                        Text(
                            text = if (state.replyTo != null) "Ta réponse…"
                            else "Ce que ce passage t'inspire…",
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
