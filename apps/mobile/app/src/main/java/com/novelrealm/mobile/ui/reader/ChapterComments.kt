package com.novelrealm.mobile.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.GifBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.ChapterCommentDto
import com.novelrealm.mobile.data.remote.dto.UserSearchDto
import com.novelrealm.mobile.ui.comments.CommentThread
import com.novelrealm.mobile.ui.comments.toThreadComment
import com.novelrealm.mobile.ui.components.MentionSuggestionRow

/**
 * Longueur maximale d'un message. Doit rester alignée sur `ChapterComment.MAX_BODY_LENGTH`
 * côté back : c'est lui qui refuse, l'app ne fait qu'éviter d'aller au refus.
 */
private const val MaxCommentLength = 2_000

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
    onReply: (rootId: Long, toUserId: Long?, toPseudo: String?, mention: Boolean) -> Unit,
    onEdit: (comment: ChapterCommentDto, rootId: Long?) -> Unit,
    onDelete: (comment: ChapterCommentDto, rootId: Long?) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenUser: (Long) -> Unit,
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
                    // Le fil est rendu par le composant PARTAGE avec les passages :
                    // une seule mise en forme pour les deux surfaces (issue #45, §6).
                    // La correspondance id -> DTO permet de rendre au ViewModel
                    // l'objet qu'il attend, sans le faire transiter par l'affichage.
                    val byId = remember(thread) {
                        (listOf(thread) + thread.replies).associateBy { it.id }
                    }
                    CommentThread(
                        root = remember(thread) { thread.toThreadComment() },
                        foreground = foreground,
                        onReply = { target, root ->
                            // Repondre a une reponse : meme fil cote serveur, mais
                            // c'est l'auteur VISE qu'il faut mentionner et notifier.
                            onReply(root.id, target.userId, target.pseudo, target.id != root.id)
                        },
                        onEdit = { comment, root ->
                            byId[comment.id]?.let {
                                onEdit(it, if (comment.id == root.id) null else root.id)
                            }
                        },
                        onDelete = { comment, root ->
                            byId[comment.id]?.let {
                                onDelete(it, if (comment.id == root.id) null else root.id)
                            }
                        },
                        onOpenUser = onOpenUser,
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


// ── Rédaction ─────────────────────────────────────────────────────────────────

/**
 * Panneau de saisie, posé par-dessus la page. C'est la seule partie de la discussion
 * qui sort du fil du chapitre, et pour une raison précise : voir [ChapterCommentsSection].
 *
 * Il reprend l'habillage Material des autres panneaux du lecteur (réglages) plutôt que
 * les couleurs de lecture : c'est une surface flottante, pas un morceau de la page.
 *
 * Depuis l'issue #45 : suggestions de mention au-dessus du champ (§2) et GIF joint
 * (§5) — bouton à gauche de la bulle, aperçu au-dessus, retirable d'une croix.
 *
 * <p><b>Sur le placement vertical.</b> Le panneau ne pose qu'un seul retrait bas, égal
 * à `max(clavier, barre de navigation)`. Deux pièges ont été écartés ici :
 * <ul>
 *   <li>`imePadding()` suivi de `navigationBarsPadding()` ADDITIONNE les deux marges —
 *       clavier ouvert, une bande vide de la hauteur de la barre système s'ouvrait sous
 *       le champ ;</li>
 *   <li>et surtout, ce retrait ne vaut que si la fenêtre est REDIMENSIONNÉE par le
 *       clavier. Sans `windowSoftInputMode="adjustResize"` au manifeste, Android
 *       déplaçait la fenêtre entière et notre marge s'ajoutait à ce déplacement : le
 *       panneau sortait par le haut de l'écran. Les deux vont ensemble.</li>
 * </ul>
 */
@Composable
fun CommentComposerSheet(
    state: ChapterCommentsUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
    onPickMention: (UserSearchDto) -> Unit,
    onOpenGifPicker: () -> Unit,
    onRemoveGif: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    // Le clavier monte tout de suite : on a ouvert ce panneau pour écrire, pas pour le
    // regarder.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 6.dp,
        shadowElevation = 16.dp,
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 14.dp)
                .padding(top = 10.dp, bottom = 12.dp),
        ) {
            // Poignée, comme sur le panneau de réglages : les deux panneaux du lecteur
            // s'ouvrent pareil, ils doivent se ressembler.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
                )
            }

            Spacer(Modifier.height(12.dp))
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp),
                )
                // Une croix plutôt que le mot « Annuler » : elle occupe le coin où on la
                // cherche et ne concurrence pas le bouton d'envoi du regard.
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

            val actionError = state.actionError
            if (actionError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            // Le GIF joint, au-dessus du champ — là où il apparaîtra dans le message.
            state.attachedGif?.let { gif ->
                AttachedGifPreview(
                    previewUrl = gif.previewUrl.ifBlank { gif.url },
                    onRemove = onRemoveGif,
                )
                Spacer(Modifier.height(8.dp))
            }

            MentionSuggestionRow(
                suggestions = state.mentionSuggestions,
                onPick = onPickMention,
            )

            ComposerInputRow(
                draft = state.draft,
                canSend = state.canSend,
                isSending = state.isSending,
                focusRequester = focusRequester,
                onDraftChange = onDraftChange,
                onSend = onSend,
                // Un GIF ne se joint qu'à un message NEUF : la modification ne touche
                // que le texte, et un seul GIF par message.
                onOpenGifPicker = if (
                    state.gifAvailable &&
                    state.attachedGif == null &&
                    state.target !is ComposerTarget.Edit
                ) {
                    onOpenGifPicker
                } else {
                    null
                },
            )

            // Le compteur n'apparaît qu'à l'approche de la limite : affiché en
            // permanence, il ne ferait que meubler.
            if (state.draft.length > MaxCommentLength * 4 / 5) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${state.draft.length} / $MaxCommentLength",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.draft.length >= MaxCommentLength) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    // 46 dp de bouton + 10 dp d'écart : le compteur s'aligne ainsi sur
                    // le bord droit de la bulle, et non sur celui du bouton d'envoi.
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 56.dp),
                )
            }
        }
    }
}

/** L'aperçu du GIF joint : vignette figée + croix pour le retirer. */
@Composable
fun AttachedGifPreview(previewUrl: String, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        ) {
            AsyncImage(
                model = previewUrl,
                contentDescription = "GIF joint",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "GIF joint",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Retirer le GIF",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .padding(6.dp)
                .size(16.dp),
        )
    }
}

/**
 * La ligne de saisie : le bouton GIF (s'il a lieu d'être), une bulle qui grandit, et
 * le bouton d'envoi à côté.
 *
 * <p><b>Pourquoi une bulle maison et pas un `OutlinedTextField`.</b> Ce dernier impose
 * une hauteur minimale de 56 dp et des marges internes prévues pour un libellé
 * flottant ; sur une seule ligne de texte, le curseur se retrouvait haut dans un cadre
 * trop grand, et le bouton d'envoi — aligné sur le bas — semblait décalé. Un
 * [BasicTextField] dans une surface arrondie qu'on dimensionne soi-même donne
 * exactement la hauteur voulue.
 *
 * <p><b>Pourquoi les deux partagent le même bas.</b> La bulle a la hauteur minimale du
 * bouton (46 dp) et la ligne est alignée sur `Bottom` : à une ligne, les deux sont
 * parfaitement côte à côte ; quand le texte s'allonge, la bulle grandit vers le haut et
 * leurs bases restent alignées. Il n'existe donc aucun état où l'un flotte à côté de
 * l'autre.
 */
@Composable
private fun ComposerInputRow(
    draft: String,
    canSend: Boolean,
    isSending: Boolean,
    focusRequester: FocusRequester,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenGifPicker: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        if (onOpenGifPicker != null) {
            GifButton(onClick = onOpenGifPicker)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(23.dp),
            modifier = Modifier
                .weight(1f)
                // Le champ grandit avec le texte puis défile sur lui-même : sans
                // plafond, un long message pousserait le panneau sur tout l'écran.
                .heightIn(min = 46.dp, max = 148.dp),
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = "Écris ton message…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
                BasicTextField(
                    value = draft,
                    // La limite est tenue à la saisie, et pas seulement par le serveur :
                    // découvrir au moment d'envoyer qu'on a écrit 200 caractères de trop
                    // est la pire façon de l'apprendre.
                    onValueChange = { if (it.length <= MaxCommentLength) onDraftChange(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        }

        Spacer(Modifier.width(10.dp))
        SendButton(enabled = canSend, sending = isSending, onClick = onSend)
    }
}

/** Le bouton GIF — même gabarit que le bouton d'envoi, en version sourde. */
@Composable
fun GifButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = Icons.Outlined.GifBox,
            contentDescription = "Joindre un GIF",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Bouton d'envoi : un **disque plein** de 46 dp, et non une icône nue.
 *
 * Une icône seule n'a pas de bord : rien ne la cale sur celui de la bulle, et elle
 * paraissait flotter. Un disque de la hauteur exacte du champ à une ligne se lit
 * comme sa continuation.
 */
@Composable
private fun SendButton(enabled: Boolean, sending: Boolean, onClick: () -> Unit) {
    val container = if (enabled || sending) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    }
    val content = if (enabled || sending) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = content,
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Publier",
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
