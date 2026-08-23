package com.novelrealm.mobile.ui.explore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelrealm.mobile.data.remote.dto.NovelDto
import com.novelrealm.mobile.ui.components.COVER_RATIO_BOOK
import com.novelrealm.mobile.ui.components.NovelCover

// La vitrine éditoriale d'Explorer : le héros à la une et les rangées horizontales.
// Tout est peint « à la main » (dégradés, badges) sur la couverture partagée
// [NovelCover] — aucune API Compose expérimentale, conformément aux conventions du
// projet. Les données sont strictement celles du catalogue (titre, auteur, statut,
// couverture) : on n'invente ni note ni compteur qui n'existent pas côté serveur.

/** « En cours » / « Terminé » pour l'affichage — l'API renvoie ONGOING | COMPLETED. */
internal fun novelStatusLabel(status: String?): String? = when (status) {
    "ONGOING" -> "En cours"
    "COMPLETED" -> "Terminé"
    else -> null
}

/**
 * Le héros de l'onglet : le roman mis en avant, en grand, tout en haut.
 *
 * <p>Une couverture large (format livre légèrement recadré par le cadre), noyée sous un
 * dégradé qui garantit la lisibilité du texte quelle que soit l'image, puis le titre,
 * l'auteur, une pastille de statut et deux actions : suivre (le cœur) et ouvrir la fiche.
 *
 * <p><b>Parallax.</b> [scrollOffsetProvider] rend le décalage de défilement de la liste
 * (en pixels, ≥ 0). On translate la couverture à vitesse réduite : le fond « traîne »
 * derrière le contenu quand on descend, ce qui donne la profondeur cinématographique
 * demandée sans jamais bouger le texte, qui doit rester net et stable.
 */
@Composable
internal fun NovelHero(
    novel: NovelDto,
    inLibrary: Boolean,
    onToggleLibrary: () -> Unit,
    onClick: () -> Unit,
    scrollOffsetProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val status = novelStatusLabel(novel.status)
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        // Couverture, décalée en parallax (à mi-vitesse du scroll). Un facteur négatif la
        // fait remonter légèrement quand on descend : elle « résiste » au défilement.
        NovelCover(
            coverUrl = novel.coverImageUrl,
            contentDescription = novel.title,
            ratio = 4f / 5f,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -scrollOffsetProvider() * 0.18f },
        )

        // Dégradé bas → haut : le texte se pose sur du sombre, l'image respire en haut.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        0.72f to Color(0xB3000000),
                        1f to Color(0xF200000A),
                    ),
                ),
        )

        // Le cœur, en haut à droite : suivre sans ouvrir la fiche, comme sur les cartes.
        HeartButton(
            inLibrary = inLibrary,
            onToggle = onToggleLibrary,
            size = 40.dp,
            iconSize = 20.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            // Petit surtitre pour poser le ton « à la une », en accent.
            Text(
                text = "À LA UNE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = accent,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = novel.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                novel.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (status != null) {
                    if (novel.author?.isNotBlank() == true) {
                        Spacer(Modifier.width(8.dp))
                        Dot()
                        Spacer(Modifier.width(8.dp))
                    }
                    StatusBadge(label = status)
                }
            }
            Spacer(Modifier.height(14.dp))
            // Une seule action forte, pleine largeur : « Découvrir » ouvre la fiche. Le
            // suivi passe par le cœur ci-dessus — deux boutons côte à côte alourdiraient.
            HeroCta(label = "Découvrir", onClick = onClick)
        }
    }
}

/** Le bouton d'action pleine largeur du héros — accent plein, façon CTA d'app store. */
@Composable
private fun HeroCta(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** Un point de séparation « auteur · statut ». */
@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.5f)),
    )
}

/**
 * L'en-tête d'une rangée éditoriale : le titre de section à gauche, « Voir tout → » à
 * droite quand une action est fournie. Capitales espacées, comme les autres sections.
 */
@Composable
internal fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // 4 dp seulement : la grille hôte inset déjà de 16 dp, le total retombe sur les
        // 20 dp d'indentation des titres de section.
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Voir tout",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Une rangée horizontale de posters. `LazyRow` : seuls les visibles sont composés, la
 * rangée défile toute seule. Chaque poster ouvre la fiche ; le cœur suit/arrête de suivre.
 */
@Composable
internal fun NovelRow(
    novels: List<NovelDto>,
    libraryNovelIds: Set<Long>,
    onNovelClick: (Long) -> Unit,
    onToggleLibrary: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        // 4 dp de retrait : la grille hôte inset déjà cette ligne de 16 dp (total 20).
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(items = novels, key = { it.id }) { novel ->
            NovelRowCard(
                novel = novel,
                inLibrary = novel.id in libraryNovelIds,
                onClick = { onNovelClick(novel.id) },
                onToggleLibrary = { onToggleLibrary(novel.id) },
            )
        }
    }
}

/**
 * Un poster de rangée : couverture 2:3, cœur en surimpression, titre + auteur dessous.
 * Largeur fixe pour un rythme régulier ; le titre sur deux lignes garde la ligne d'auteur
 * alignée d'un poster à l'autre.
 */
@Composable
private fun NovelRowCard(
    novel: NovelDto,
    inLibrary: Boolean,
    onClick: () -> Unit,
    onToggleLibrary: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
    ) {
        Box(modifier = Modifier.clip(shape)) {
            NovelCover(
                coverUrl = novel.coverImageUrl,
                contentDescription = novel.title,
                ratio = COVER_RATIO_BOOK,
                shape = shape,
                modifier = Modifier.fillMaxWidth(),
            )
            // Léger voile bas pour asseoir le cœur et unifier des couvertures inégales.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.6f to Color.Transparent,
                            1f to Color(0x66000000),
                        ),
                    ),
            )
            HeartButton(
                inLibrary = inLibrary,
                onToggle = onToggleLibrary,
                size = 28.dp,
                iconSize = 15.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = novel.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        novel.author?.takeIf { it.isNotBlank() }?.let { author ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/**
 * La pastille de statut (« En cours » / « Terminé »), en accent translucide. Sobre :
 * une info d'appoint, pas un bouton.
 */
@Composable
internal fun StatusBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

/**
 * Le cœur suivre/ne-plus-suivre, en surimpression sur une couverture — pastille sombre
 * pour tenir sur n'importe quelle image, cœur plein en accent quand le roman est suivi.
 *
 * <p><b>Pop.</b> Au basculement, le cœur rebondit brièvement (ressort) : la réaction se
 * sent au doigt, façon « like » d'Instagram. Le `key(inLibrary)` du scale relance le
 * ressort à chaque changement d'état, sans qu'on ait à gérer une animation manuelle.
 */
@Composable
internal fun HeartButton(
    inLibrary: Boolean,
    onToggle: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val pop by animateFloatAsState(
        targetValue = if (inLibrary) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "heartPop",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xB3000000))
            .clickable(onClick = onToggle),
    ) {
        Icon(
            imageVector = if (inLibrary) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (inLibrary) "Retirer de ta bibliothèque"
            else "Ajouter à ta bibliothèque",
            tint = if (inLibrary) MaterialTheme.colorScheme.primary else Color.White,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                },
        )
    }
}

