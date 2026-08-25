package com.novelrealm.mobile.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.novelrealm.mobile.ui.theme.NovelCommentSheetDark
import kotlinx.coroutines.launch

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

    // TOUT à DROITE, côte à côte : les emojis de réaction PUIS l'icône commentaire,
    // collés l'un à l'autre au coin bas-droit du paragraphe. `Arrangement.End` pousse
    // le groupe entier à droite ; les emojis défilent (`horizontalScroll`, pas FlowRow
    // expérimental) et sont bornés par `weight(1f, fill = false)` pour ne jamais chasser
    // le 💬 hors de l'écran.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 5.dp),
    ) {
        // Les emojis, juste à gauche du 💬 — tout contre lui, pas à l'autre bout.
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
        // L'icône commentaire, tout de suite après les emojis — toucher ouvre la feuille.
        if (comments > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 6.dp)
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
 * Une puce de réaction sous un paragraphe : l'emoji + son compteur dans une petite
 * pastille arrondie **sans liseré** — le fond suffit à détacher la puce du texte, le
 * contour l'alourdissait. Ma réaction à moi se repère au compteur en gras. Un tap la
 * bascule (pose/retire).
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // La puce : un fond arrondi, mais PAS de bordure. La zone cliquable garde un
        // padding confortable au doigt.
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(foreground.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    ) {
        Text(text = emoji, fontSize = 11.sp)
        if (count > 0) {
            Spacer(Modifier.width(3.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal,
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
    onVoteComment: (annotationId: Long, value: Int) -> Unit,
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
    // ── Glisser-pour-fermer, façon TikTok ────────────────────────────────────────
    // La feuille suit le doigt vers le BAS, puis se ferme si on a tiré assez loin (ou
    // vite), sinon revient en place avec un ressort. On branche le geste sur le
    // `nestedScroll` : ainsi il cohabite avec le défilement du fil au lieu de le
    // voler. Le fil défile normalement ; ce n'est QUE lorsqu'il est déjà tout en
    // haut et qu'on continue à tirer vers le bas que le rab de scroll pousse la
    // feuille. Inversement, un scroll vers le haut ramène d'abord la feuille en
    // place avant de reprendre le défilement.
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    // Seuil de fermeture : ~18 % de la hauteur de la feuille, mesurée au vol.
    var sheetHeightPx by remember { mutableStateOf(0f) }
    val dismissThreshold = { if (sheetHeightPx > 0f) sheetHeightPx * 0.18f else 220f }
    // Fermeture EN COURS : une fois qu'on a décidé de fermer, on ignore tout nouveau
    // rab de geste (le fling résiduel du scroll, un second `onPreFling`…), sinon deux
    // sources se disputent `dragOffset` et il peut se figer hors écran — d'où la feuille
    // « ouverte mais invisible » qui refusait de se rouvrir.
    var closing by remember { mutableStateOf(false) }
    // À CHAQUE ouverture d'un bloc, on repart d'une feuille bien amarrée. On se cale sur
    // `state.threadBlock` — l'identité du panneau ouvert — plutôt que sur le montage :
    // `AnimatedVisibility` peut RECYCLER l'enfant sans le détruire (rouvrir juste après
    // avoir fermé), et un `LaunchedEffect(Unit)` ne se rejouerait alors pas — la feuille
    // resterait figée hors écran avec un `dragOffset` résiduel, « ouverte mais invisible ».
    //
    // MAIS on ne remet à zéro QU'À L'OUVERTURE (threadBlock non nul). Le faire aussi à la
    // FERMETURE (threadBlock -> null) ramènerait la feuille d'un coup en position 0 en
    // plein glissement du doigt : c'est le « rollback » qu'on voyait avant le slide-out.
    // À la fermeture, on ne touche donc plus à `dragOffset` — `AnimatedVisibility` glisse
    // la feuille (déjà décalée par le doigt) proprement vers le bas.
    LaunchedEffect(state.threadBlock) {
        if (state.threadBlock != null) {
            dragOffset.snapTo(0f)
            closing = false
        }
    }

    // `onClose` en clé : si la surface change de rappel, la connection le suit au lieu
    // de retenir l'ancien (le reste — scope, Animatable, mesure — est stable).
    val nestedScroll = remember(onClose) {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            // Convention (VÉRIFIÉE sur l'appareil, pas en théorie — c'est le symptôme du
            // bug qui l'a tranchée) : dans `available`, y > 0 = geste vers le BAS (le doigt
            // descend), y < 0 = geste vers le HAUT. On s'en sert pour deux choses.
            //
            // AVANT que le fil ne défile : si la feuille est DÉJÀ décalée et qu'on remonte
            // le doigt (y < 0), on ré-amarre la feuille d'ABORD — on la ramène vers 0 avant
            // de rendre la main au défilement. Sinon le fil se remettrait à défiler alors
            // que la feuille est encore pendante.
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (closing) return available
                val dy = available.y
                return if (dy < 0f && dragOffset.value > 0f) {
                    // Geste vers le haut (dy < 0) : on réduit le décalage, sans dépasser 0.
                    // On consomme exactement ce qu'on a absorbé (borné par le décalage restant).
                    val consume = -minOf(-dy, dragOffset.value)
                    scope.launch { dragOffset.snapTo((dragOffset.value + consume).coerceAtLeast(0f)) }
                    androidx.compose.ui.geometry.Offset(0f, consume)
                } else {
                    androidx.compose.ui.geometry.Offset.Zero
                }
            }

            // APRÈS le fil : le rab que le fil n'a pas pu consommer parce qu'il est en
            // butée. On ne réagit QU'au geste vers le BAS en butée HAUTE (y > 0 non
            // consommé alors que le fil est déjà tout en haut) : c'est le vrai début d'un
            // glissement de fermeture. Un geste vers le HAUT (y < 0, ex. on remonte le
            // contenu déjà en haut) ne doit RIEN faire — c'était lui qui faisait « flotter »
            // la feuille vers le bas au lieu de la laisser ancrée en haut. C'ÉTAIT LE BUG.
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (closing) return available
                val dy = available.y
                // Seul un geste par le doigt (drag) peut ouvrir la fermeture — on ignore
                // le rab d'un fling, qui ferait sauter la feuille de façon incontrôlée.
                // `UserInput` est le nom 1.7 de l'ancien `Drag` (déprécié).
                val fromDrag = source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput
                return if (dy > 0f && fromDrag) {
                    // dy > 0 = vers le bas : on décale la feuille de dy, freiné (×0.5).
                    scope.launch { dragOffset.snapTo(dragOffset.value + dy * 0.5f) }
                    androidx.compose.ui.geometry.Offset(0f, dy)
                } else {
                    androidx.compose.ui.geometry.Offset.Zero
                }
            }

            // Au lâcher : passé le seuil (ou lancé vite vers le bas), on ferme ;
            // sinon la feuille revient se caler en haut.
            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (closing) return available
                if (dragOffset.value > 0f) {
                    val shouldDismiss =
                        dragOffset.value > dismissThreshold() || available.y > 1800f
                    if (shouldDismiss) {
                        // On ferme : `AnimatedVisibility` (slideOutVertically) joue déjà la
                        // sortie vers le bas — inutile d'animer `dragOffset` nous-mêmes, ça
                        // ferait doublon ET entrerait en conflit avec le reset d'ouverture.
                        closing = true
                        onClose()
                    } else {
                        dragOffset.animateTo(0f, tween(durationMillis = 220))
                    }
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    // Fond de la feuille : en thème sombre, un gris TikTok neutre et profond
    // (`NovelCommentSheetDark`) plutôt que le `surface` par défaut, un peu chaud. En
    // thème clair on garde `surface` : la même couleur en dur y jurerait. On tranche sur
    // la luminance du `surface` courant — robuste aux thèmes de lecteur (sépia, OLED…)
    // qui ne passent pas par `isSystemInDarkTheme`.
    val surfaceColor = MaterialTheme.colorScheme.surface
    val sheetColor = if (surfaceColor.luminance() < 0.5f) NovelCommentSheetDark else surfaceColor

    Surface(
        color = sheetColor,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        // `tonalElevation = 0` : avec `surfaceTint = White`, une élévation tonale
        // reteinterait `sheetColor` vers un gris plus clair — on veut EXACTEMENT la
        // couleur choisie. Le relief vient de l'ombre (`shadowElevation`), pas du ton.
        tonalElevation = 0.dp,
        shadowElevation = 20.dp,
        modifier = modifier
            // Clavier ouvert : la feuille REMONTE au-dessus de lui (`imePadding` en TÊTE
            // de chaîne) au lieu de rétrécir son intérieur. C'était LE bug : la feuille
            // faisait 70 % de l'écran ENTIER et absorbait l'inset clavier à l'intérieur —
            // le champ de saisie passait donc sous le clavier. Ici `imePadding` inset la
            // feuille par le bas ; ancrée en bas, elle se cale juste au-dessus du clavier,
            // et le `fillMaxHeight(0.7f)` porte sur la hauteur RESTANTE (écran − clavier).
            .imePadding()
            // Feuille haute façon TikTok : ~70 % de la hauteur visible (hors clavier),
            // plutôt que de s'ajuster au contenu. Le fil prend toute la place disponible.
            .fillMaxWidth()
            .fillMaxHeight(0.7f)
            .onGloballyPositioned { sheetHeightPx = it.size.height.toFloat() }
            .graphicsLayer { translationY = dragOffset.value }
            .nestedScroll(nestedScroll)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            // L'inset clavier est désormais porté par la feuille elle-même (`imePadding`
            // ci-dessus) : ici on ne garde que la barre de navigation, pour que le contenu
            // ne colle pas aux boutons système quand le clavier est fermé. (Clavier ouvert,
            // cet inset vaut 0 — la barre passe derrière le clavier —, pas de double écart.)
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
        ) {
            // En-tête TikTok : poignée, titre « N commentaires » CENTRÉ, croix à droite.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    // L'en-tête n'est pas défilable : le `nestedScroll` ne peut donc pas
                    // y capter le glissement. On y pose un geste vertical direct, qui
                    // pilote le MÊME `dragOffset` — le handle et le titre deviennent une
                    // vraie prise pour tirer la feuille vers le bas.
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dy ->
                                if (!closing) {
                                    scope.launch {
                                        dragOffset.snapTo((dragOffset.value + dy).coerceAtLeast(0f))
                                    }
                                }
                            },
                            onDragEnd = {
                                if (closing) return@detectVerticalDragGestures
                                if (dragOffset.value > dismissThreshold()) {
                                    // Fermeture : on laisse `AnimatedVisibility` glisser la
                                    // feuille dehors (pas d'animation manuelle en doublon).
                                    closing = true
                                    onClose()
                                } else {
                                    scope.launch { dragOffset.animateTo(0f, tween(durationMillis = 220)) }
                                }
                            },
                            onDragCancel = {
                                if (!closing) {
                                    scope.launch { dragOffset.animateTo(0f, tween(durationMillis = 220)) }
                                }
                            },
                        )
                    },
            ) {
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
                        // PAS de padding horizontal ici : le survol des commentaires doit
                        // aller bord à bord — c'est `CommentThread(contentInset = …)` qui
                        // rentre le contenu. Les autres états (vide, chargement) reçoivent
                        // donc leur propre marge latérale.
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                                onVote = { comment, _, value ->
                                    onVoteComment(comment.id, value)
                                },
                                onOpenUser = onOpenUser,
                                modifier = Modifier.padding(bottom = 16.dp),
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
