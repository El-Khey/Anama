package com.novelrealm.mobile.ui.explore

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay

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

/** Cadence du défilement automatique du carrousel à la une. */
private const val AUTO_ADVANCE_MS = 5000L

/**
 * Le **carrousel à la une** de l'onglet : plusieurs romans en vedette qui défilent en
 * grand, tout en haut — le vrai réflexe « app mobile » (Webtoon, stores de streaming).
 *
 * <p><b>Auto + swipe.</b> Il avance seul toutes les quelques secondes ET se laisse
 * swiper. Dès qu'on pose le doigt (glissement), l'auto se met en pause ; il reprend
 * quand on lâche. On lit l'état de glissement du pager ([collectIsDraggedAsState]) pour
 * ne jamais lutter contre le doigt.
 *
 * <p><b>Animation.</b> Entre deux pages, le contenu (texte, cœur) se translate et
 * s'estompe légèrement selon la fraction de défilement du pager — la page qui arrive
 * « glisse » et s'allume, celle qui part s'efface. La couverture, elle, bouge un peu moins
 * vite : un mini-parallax horizontal qui donne de la profondeur au swipe.
 *
 * <p>Le [HorizontalPager] est stable depuis Compose 1.4 (plus expérimental) — conforme à
 * la règle du projet. Un seul roman ? Pas de dots, pas d'auto : un carrousel d'un élément
 * est juste un héros fixe.
 */
@Composable
internal fun NovelHeroCarousel(
    novels: List<NovelDto>,
    libraryNovelIds: Set<Long>,
    onNovelClick: (Long) -> Unit,
    onToggleLibrary: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (novels.isEmpty()) return
    val shape = RoundedCornerShape(24.dp)
    val pagerState = rememberPagerState(pageCount = { novels.size })

    // Défilement automatique, en pause tant qu'on garde le doigt dessus. `dragged` suit
    // l'interaction du pager ; l'effet se relance à chaque page atteinte et attend, puis
    // avance d'une page (en boucle). Un seul roman → pas d'auto (rien à faire tourner).
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    if (novels.size > 1) {
        LaunchedEffect(pagerState, dragged) {
            if (dragged) return@LaunchedEffect
            while (true) {
                delay(AUTO_ADVANCE_MS)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % novels.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
        ) { page ->
            // Décalage de CETTE page par rapport au centre : 0 quand centrée, ±1 aux
            // voisines. Sert au fondu du contenu et au mini-parallax de la couverture.
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val novel = novels[page]
            HeroSlide(
                novel = novel,
                inLibrary = novel.id in libraryNovelIds,
                pageOffset = offset,
                onToggleLibrary = { onToggleLibrary(novel.id) },
                onClick = { onNovelClick(novel.id) },
                shape = shape,
            )
        }

        // Les points de progression, posés en bas au centre du carrousel.
        if (novels.size > 1) {
            HeroDots(
                count = novels.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
            )
        }
    }
}

/**
 * Une page du carrousel : couverture pleine, fondu sombre, texte incrusté et CTA. Le
 * [pageOffset] (0 au centre) pilote l'animation : le contenu s'estompe et glisse quand la
 * page s'éloigne, la couverture se décale un peu moins vite (parallax horizontal).
 */
@Composable
private fun HeroSlide(
    novel: NovelDto,
    inLibrary: Boolean,
    pageOffset: Float,
    onToggleLibrary: () -> Unit,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val status = novelStatusLabel(novel.status)
    val accent = MaterialTheme.colorScheme.primary
    // Le contenu s'efface d'autant qu'on s'éloigne du centre (plein au centre, éteint à
    // une page). `absOffset` borné à 1 pour ne pas devenir négatif sur les bords.
    val absOffset = pageOffset.coerceIn(-1f, 1f)
    val contentAlpha = (1f - kotlin.math.abs(absOffset)).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        // Couverture : léger parallax horizontal (elle glisse à ~60 % de la vitesse du
        // swipe), pour la profondeur. Elle reste pleinement opaque, seule la scène défile.
        NovelCover(
            coverUrl = novel.coverImageUrl,
            contentDescription = novel.title,
            ratio = 3f / 4f,
            shape = shape,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = absOffset * size.width * 0.20f },
        )

        // Fondu du bas vers le noir : le texte incrusté reste lisible sur toute image.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.30f to Color.Transparent,
                        0.68f to Color(0xB3000000),
                        1f to Color(0xF2000005),
                    ),
                ),
        )

        HeartButton(
            inLibrary = inLibrary,
            onToggle = onToggleLibrary,
            size = 40.dp,
            iconSize = 20.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )

        // Texte incrusté en bas, animé : il glisse horizontalement à l'inverse du swipe et
        // s'estompe quand la page s'éloigne — la page entrante « s'allume ».
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 34.dp, top = 20.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationX = -absOffset * 60f
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "À LA UNE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = accent,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = novel.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                novel.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
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
            Spacer(Modifier.height(16.dp))
            HeroCta(label = "Découvrir", onClick = onClick)
        }
    }
}

/**
 * Les points de progression du carrousel. Le point actif s'étire en pilule accent ; les
 * autres restent de petits points sourds. La largeur s'anime, pour que le passage d'un
 * point à l'autre glisse au lieu de sauter.
 */
@Composable
private fun HeroDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(
                targetValue = if (active) 20.dp else 6.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "dotWidth",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (active) accent else Color.White.copy(alpha = 0.45f),
                    ),
            )
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

