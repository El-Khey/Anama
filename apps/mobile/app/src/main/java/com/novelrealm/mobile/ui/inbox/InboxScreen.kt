package com.novelrealm.mobile.ui.inbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.ui.notifications.NotificationsViewModel
import com.novelrealm.mobile.ui.util.relativeDayLabel

/**
 * La boîte de réception (issue #45, §3 et §4) — **une seule page** pour tout ce
 * qui concerne mes échanges : ce qu'on m'adresse, et ce que j'ai écrit.
 *
 * Les deux vivaient dans deux écrans séparés, atteints par deux chemins
 * différents (la cloche d'un côté, le profil de l'autre). C'était deux fois le
 * même geste mental — « qu'est-ce qui s'est passé sur mes commentaires ? » — pour
 * deux destinations. Un seul écran, deux onglets : la cloche ouvre le premier, le
 * profil ouvre le second, et passer de l'un à l'autre ne coûte plus une
 * navigation.
 *
 * Le style commun est défini ici (carte, séparateur de jour, citation, méta) et
 * partagé par les deux listes : rien n'est plus visible qu'une page dont les
 * moitiés ne se ressemblent pas.
 */
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    /**
     * Ouvre le lecteur au bon endroit : `blockIndex` ≥ 0 vise un passage précis,
     * `openComments` fait défiler jusqu'à la discussion de fin de chapitre.
     */
    onOpenChapter: (novelId: Long, chapterId: Long, blockIndex: Int, openComments: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    notificationsViewModel: NotificationsViewModel = viewModel(),
) {
    // Les alertes sont chargées d'emblée (leur compteur alimente la pastille de
    // l'onglet). « Mes commentaires » n'est demandé qu'à la première ouverture de son
    // onglet — inutile d'appeler le serveur pour une liste que personne ne regarde.
    val notificationsState by notificationsViewModel.state.collectAsState()
    // L'onglet est mémorisé par son RANG, pas par sa valeur : un Int se range dans
    // le Bundle de sauvegarde d'état sans supposer quoi que ce soit du type.
    var tabIndex by rememberSaveable { mutableIntStateOf(InboxTab.Alerts.ordinal) }
    val tab = InboxTab.entries[tabIndex]

    // Remontés ici pour survivre au changement d'onglet : revenir sur une liste
    // et la retrouver en haut est le genre de détail qui donne l'impression que
    // l'app oublie ce qu'on faisait.
    val alertsListState = rememberLazyListState()
    val commentsListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
            }
            Text(
                text = "Activité",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        InboxTabs(
            selected = tab,
            unreadCount = notificationsState.unreadCount,
            onSelect = { tabIndex = it.ordinal },
        )

        when (tab) {
            InboxTab.Alerts -> AlertsList(
                viewModel = notificationsViewModel,
                listState = alertsListState,
                onOpenChapter = onOpenChapter,
            )
            InboxTab.Mine -> MyCommentsList(
                listState = commentsListState,
                onOpenChapter = onOpenChapter,
            )
        }
    }
}

/** Les deux moitiés de la page. */
enum class InboxTab { Alerts, Mine }

// ── Le sélecteur d'onglets ────────────────────────────────────────────────────

/**
 * Un vrai interrupteur à deux positions plutôt que des onglets soulignés : sur une
 * page qui n'en aura jamais que deux, la pastille pleine dit mieux « tu es ici »
 * qu'un trait de 2 dp, et le doigt vise une surface, pas une ligne.
 */
@Composable
private fun InboxTabs(selected: InboxTab, unreadCount: Long, onSelect: (InboxTab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            TabSegment(
                label = "Reçues",
                badge = unreadCount,
                selected = selected == InboxTab.Alerts,
                onClick = { onSelect(InboxTab.Alerts) },
                modifier = Modifier.weight(1f),
            )
            TabSegment(
                label = "Mes commentaires",
                badge = 0,
                selected = selected == InboxTab.Mine,
                onClick = { onSelect(InboxTab.Mine) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TabSegment(
    label: String,
    badge: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La couleur glisse d'une position à l'autre : sans transition, le changement
    // d'onglet donne l'impression que l'écran a sauté.
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        label = "segment-bg",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "segment-fg",
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1,
        )
        if (badge > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 5.dp),
            ) {
                Text(
                    text = if (badge > 9) "9+" else badge.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

// ── Vocabulaire visuel partagé par les deux listes ────────────────────────────

internal val ScreenPadding = 16.dp
internal val CardShape = RoundedCornerShape(18.dp)
private val CardPadding = 14.dp

/**
 * La carte, seule et même brique des deux onglets.
 *
 * Un fond légèrement plus clair que l'écran **et** un liseré d'un pixel : sur le
 * quasi-noir du thème sombre, le fond seul ne suffit pas à détacher la carte, et
 * le liseré seul la rend creuse. Non lue, elle prend la teinte d'accent — sur le
 * pourtour, pas au centre, pour ne pas assombrir le texte qu'elle contient.
 */
@Composable
internal fun InboxCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = if (highlighted) accent.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
        shape = CardShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (highlighted) accent.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .clip(CardShape)
                .clickable(onClick = onClick)
                .padding(CardPadding),
            content = content,
        )
    }
}

/**
 * Le titre de section « Aujourd'hui / Hier / 3 mars 2026 ».
 *
 * C'est ce qui manquait le plus : sans lui, cinquante cartes identiques forment
 * un mur qu'on parcourt sans repère. Avec, l'œil sait tout de suite ce qui est
 * frais et ce qui date.
 */
@Composable
internal fun DayHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

/** Une citation : filet d'accent + texte en retrait. Sert au passage commenté et à l'extrait cité. */
@Composable
internal fun QuoteRail(
    text: String,
    modifier: Modifier = Modifier,
    italic: Boolean = false,
    maxLines: Int = 2,
) {
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .padding(vertical = 1.dp)
                .width(3.dp)
                .height(if (maxLines > 1) 30.dp else 16.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** La ligne discrète du bas : roman · chapitre · date. Jamais en gras, jamais pleine teinte. */
@Composable
internal fun MetaLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Une étiquette minuscule (« Réponse », « Passage ») — une nuance, pas un titre. */
@Composable
internal fun TinyPill(label: String, icon: ImageVector? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** L'état vide d'un onglet : une icône fantôme, une phrase, et rien d'autre. */
@Composable
internal fun InboxEmpty(icon: ImageVector, title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 90.dp, start = 32.dp, end = 32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Découpe la liste en sections par jour et pose les cartes.
 *
 * Le regroupement suppose une liste DÉJÀ triée du plus récent au plus ancien —
 * c'est ce que servent les deux endpoints. Il conserve donc simplement l'ordre
 * reçu au lieu de retrier.
 */
internal fun <T> LazyListScope.dayGroupedItems(
    items: List<T>,
    key: (T) -> Any,
    dateOf: (T) -> String?,
    itemContent: @Composable (T) -> Unit,
) {
    var currentDay: String? = null
    items.forEach { entry ->
        val day = relativeDayLabel(dateOf(entry))
        if (day != currentDay) {
            currentDay = day
            item(key = "day-$day", contentType = "day") { DayHeader(day) }
        }
        item(key = key(entry), contentType = "card") {
            itemContent(entry)
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Le pied de liste : « Voir plus », ou le rond de chargement à sa place. */
internal fun LazyListScope.loadMoreItem(isLoading: Boolean, onClick: () -> Unit) {
    item(contentType = "more") {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = "Voir plus",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** La barre d'outils d'un onglet : filtres à gauche, action à droite. Même hauteur partout. */
@Composable
internal fun InboxToolbar(
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenPadding)
            .height(36.dp),
    ) {
        left()
        Spacer(Modifier.weight(1f))
        right()
    }
}

/** Une pastille de filtre (« Toutes », « Non lues »). */
@Composable
internal fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) accent.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

/** Une action textuelle discrète en bout de barre (« Tout lire »). */
@Composable
internal fun ToolbarAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Le rappel du nombre, à gauche de la barre — même graisse que les pastilles. */
@Composable
internal fun ToolbarCount(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Marge basse commune aux deux listes : la dernière carte ne colle pas au bord. */
internal val ListContentPadding = PaddingValues(
    start = ScreenPadding,
    end = ScreenPadding,
    bottom = 28.dp,
)
