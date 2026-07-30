package com.novelrealm.mobile.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.data.remote.dto.ChapterDetailDto
import com.novelrealm.mobile.data.remote.dto.ChapterDto
import com.novelrealm.mobile.data.remote.dto.displayTitle
import com.novelrealm.mobile.di.ServiceLocator
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.util.vmFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** Distance à parcourir au doigt pour valider un changement de chapitre. */
private val SwipeThreshold = 96.dp

/** Déplacement maximal du texte pendant le balayage — au-delà, ça ne bouge plus. */
private val SwipeMaxTravel = 140.dp

/** Vitesse de lecture retenue pour l'estimation de durée (mots par minute). */
private const val WordsPerMinute = 220

// Lecteur de chapitre (#35), UX façon Mihon : texte plein écran, un tap fait apparaître /
// disparaître les barres (titre + réglages + signet en haut ; précédent / slider / suivant
// en bas). La position est restaurée à l'ouverture puis sauvegardée en continu (débouncée).
@Composable
fun ReaderScreen(
    novelId: Long,
    chapterId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = viewModel(factory = vmFactory { ReaderViewModel(novelId, chapterId) }),
) {
    val state by viewModel.state.collectAsState()
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Confort de lecture réglé ici même (engrenage) ou dans Profil › Lecture : c'est le
    // même stockage, donc les deux écrans restent d'accord sans effort.
    val store = ServiceLocator.preferencesStore
    val readerPrefs by store.state.collectAsState()
    val style = rememberReaderStyle(readerPrefs.reader)

    // Empêche la mise en veille pendant la lecture, et restaure le comportement normal
    // en quittant l'écran (sinon le réglage « fuiterait » sur le reste de l'app).
    val view = LocalView.current
    DisposableEffect(readerPrefs.reader.keepScreenOn) {
        view.keepScreenOn = readerPrefs.reader.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Mode plein écran : masque les barres système, rendues à nouveau visibles quand
    // les commandes du lecteur sont affichées (un tap) ou à la sortie de l'écran.
    val chromeOrSettings = chromeVisible || settingsVisible
    DisposableEffect(readerPrefs.reader.fullscreen, chromeOrSettings) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (readerPrefs.reader.fullscreen && !chromeOrSettings) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Pourcentage de lecture courant (0-100). -1 tant que la mise en page n'est pas mesurée.
    val percent by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            when {
                max == Int.MAX_VALUE -> -1
                max <= 0 -> 100                    // chapitre entier visible à l'écran
                else -> (scrollState.value * 100f / max).toInt().coerceIn(0, 100)
            }
        }
    }

    // ── Balayage latéral : chapitre précédent / suivant ──
    //
    // Le décalage est une simple valeur d'état, écrite **synchronement** par le geste.
    // Une `Animatable` pilotée par `scope.launch` (une coroutine par événement de
    // déplacement) laissait passer des écritures en retard : une seule arrivant après la
    // remise à zéro suffisait à figer le texte de travers, décalé pour de bon.
    // `animateFloatAsState` avec `snap()` pendant le geste et `spring()` au relâchement
    // donne le même rendu sans aucune course.
    val density = LocalDensity.current
    val thresholdPx = with(density) { SwipeThreshold.toPx() }
    val maxTravelPx = with(density) { SwipeMaxTravel.toPx() }
    var swipeTarget by remember { mutableStateOf(0f) }
    var swiping by remember { mutableStateOf(false) }
    val swipeOffset by animateFloatAsState(
        targetValue = swipeTarget,
        animationSpec = if (swiping) snap() else spring(stiffness = Spring.StiffnessMediumLow),
        label = "swipeOffset",
    )

    // Restaure la position de reprise, puis suit la lecture (sauvegarde débouncée).
    LaunchedEffect(state.chapter?.id, state.isLoading) {
        if (state.isLoading || state.chapter == null) return@LaunchedEffect
        swiping = false
        swipeTarget = 0f
        scrollState.scrollTo(0)
        val target = state.initialPercent
        if (target in 1..99) {
            val max = withTimeoutOrNull(1500) {
                snapshotFlow { scrollState.maxValue }.first { it != Int.MAX_VALUE && it > 0 }
            }
            if (max != null) scrollState.scrollTo((target / 100f * max).toInt())
        }
        snapshotFlow { percent }.collectLatest { p ->
            if (p >= 0) {
                delay(600)
                viewModel.onScrollPercent(p)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(style.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // Un tap referme d'abord les réglages : sinon il faudrait viser le voile.
                if (settingsVisible) settingsVisible = false else chromeVisible = !chromeVisible
            },
    ) {
        // Capturé une fois : évite un `return` depuis la lambda, qui sauterait aussi les
        // barres de commandes déclarées plus bas dans ce même Box.
        val chapter = state.chapter
        when {
            state.isLoading -> LoadingScreen()
            state.error != null -> EmptyScreen(
                message = state.error ?: "",
                actionLabel = "Retour",
                onAction = onBack,
            )
            chapter != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .draggable(
                            orientation = Orientation.Horizontal,
                            // Le panneau de réglages a ses propres curseurs : un balayage
                            // dessus ne doit pas changer de chapitre.
                            enabled = !settingsVisible,
                            state = rememberDraggableState { delta ->
                                // Le texte suit le doigt au millimètre du côté où il y a
                                // un chapitre ; de l'autre il « bute » (élastique très
                                // amorti), ce qui se comprend sans message d'erreur.
                                val towardsExisting =
                                    if (delta < 0) state.hasNext else state.hasPrevious
                                val applied = if (towardsExisting) delta else delta * 0.12f
                                swiping = true
                                swipeTarget =
                                    (swipeTarget + applied).coerceIn(-maxTravelPx, maxTravelPx)
                            },
                            onDragStopped = {
                                when {
                                    swipeTarget <= -thresholdPx && state.hasNext ->
                                        viewModel.openNext()
                                    swipeTarget >= thresholdPx && state.hasPrevious ->
                                        viewModel.openPrevious()
                                }
                                // Remise à zéro inconditionnelle : même quand on change de
                                // chapitre, c'est la seule garantie que le texte revienne
                                // droit (le nouveau chapitre passe par l'écran de
                                // chargement, le retour en ressort ne se voit pas).
                                swiping = false
                                swipeTarget = 0f
                            },
                        ),
                ) {
                    ChapterBody(
                        chapter = chapter,
                        style = style,
                        previous = state.previousChapter,
                        next = state.nextChapter,
                        onOpenPrevious = viewModel::openPrevious,
                        onOpenNext = viewModel::openNext,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                            .verticalScroll(scrollState),
                    )

                    // Indice de balayage : montre vers quel chapitre on se dirige.
                    SwipeHint(
                        offset = swipeOffset,
                        thresholdPx = thresholdPx,
                        previous = state.previousChapter,
                        next = state.nextChapter,
                        foreground = style.foreground,
                    )
                }
            }
        }

        // ── Barre du haut : retour + titre + réglages + signet, souligné d'une fine
        //    ligne de progression (on sait où on en est sans ouvrir la barre du bas).
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .statusBarsPadding()
                            .height(56.dp)
                            .fillMaxWidth(),
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.chapter?.displayTitle ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = buildString {
                                    append("Chapitre ${state.chapter?.chapterNumber ?: ""}")
                                    if (state.position > 0 && state.chapters.isNotEmpty()) {
                                        append(" · ${state.position}/${state.chapters.size}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = { settingsVisible = !settingsVisible }) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = "Réglages de lecture",
                                tint = if (settingsVisible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Filled.Bookmark
                                else Icons.Filled.BookmarkBorder,
                                contentDescription = if (state.isFavorite) "Retirer le signet"
                                else "Ajouter un signet",
                                tint = if (state.isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    ProgressHairline(percent = percent)
                }
            }
        }

        // ── Barre du bas : chapitre précédent / slider de progression / suivant ──
        AnimatedVisibility(
            visible = chromeVisible && !settingsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::openPrevious, enabled = state.hasPrevious) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Chapitre précédent",
                            )
                        }
                        Slider(
                            value = (if (percent < 0) 0 else percent) / 100f,
                            onValueChange = { fraction ->
                                scope.launch {
                                    val max = scrollState.maxValue
                                    if (max in 1 until Int.MAX_VALUE) {
                                        scrollState.scrollTo((fraction * max).toInt())
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::openNext, enabled = state.hasNext) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Chapitre suivant",
                            )
                        }
                    }
                    Text(
                        text = "${if (percent < 0) 0 else percent} %",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }

        // ── Réglages de lecture, sans quitter le chapitre ──
        AnimatedVisibility(
            visible = settingsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ReaderSheetScrim(
                onDismiss = { settingsVisible = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
        AnimatedVisibility(
            visible = settingsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderSettingsSheet(
                prefs = readerPrefs.reader,
                onUpdate = { transform -> store.updateReader(transform) },
                onReset = store::resetReader,
            )
        }
    }
}

// ── Contenu du chapitre ────────────────────────────────────────────────────────

@Composable
private fun ChapterBody(
    chapter: ChapterDetailDto,
    style: ReaderStyle,
    previous: ChapterDto?,
    next: ChapterDto?,
    onOpenPrevious: () -> Unit,
    onOpenNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Le texte est découpé en paragraphes pour pouvoir appliquer l'écart réglable entre
    // eux (un seul bloc de texte ne le permettrait pas).
    val paragraphs = remember(chapter.content) {
        chapter.content.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(chapter.content) }
    }
    val wordCount = remember(chapter.content) {
        chapter.content.split(' ', '\n', '\t').count { it.isNotBlank() }
    }

    Column(modifier = modifier.padding(horizontal = style.horizontalPadding)) {
        Spacer(Modifier.statusBarsPadding())
        Spacer(Modifier.height(56.dp))

        ChapterHeading(
            chapter = chapter,
            wordCount = wordCount,
            foreground = style.foreground,
        )

        Spacer(Modifier.height(26.dp))
        paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) Spacer(Modifier.height(style.paragraphSpacing))
            Text(text = paragraph, style = style.textStyle, color = style.foreground)
        }

        Spacer(Modifier.height(44.dp))
        EndOfChapterLabel(foreground = style.foreground)

        if (previous != null || next != null) {
            Spacer(Modifier.height(22.dp))
            // Le texte peut aller jusqu'au bord (marge réglée à 0), pas les boutons : on
            // leur rend le retrait qui manque pour qu'ils restent des boutons, et non des
            // bandeaux collés aux tranches de l'écran.
            Column(
                modifier = Modifier.padding(
                    horizontal = (14.dp - style.horizontalPadding).coerceAtLeast(0.dp),
                ),
            ) {
                if (previous != null) {
                    ChapterNavCard(
                        chapter = previous,
                        isNext = false,
                        foreground = style.foreground,
                        onClick = onOpenPrevious,
                    )
                }
                if (previous != null && next != null) Spacer(Modifier.height(10.dp))
                if (next != null) {
                    ChapterNavCard(
                        chapter = next,
                        isNext = true,
                        foreground = style.foreground,
                        onClick = onOpenNext,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/**
 * En-tête de chapitre : sur-titre discret, titre, puis un **filet dégradé** court qui
 * sépare le titre du corps de texte sans le trait rigide d'un séparateur pleine largeur.
 */
@Composable
private fun ChapterHeading(
    chapter: ChapterDetailDto,
    wordCount: Int,
    foreground: Color,
) {
    Text(
        text = "CHAPITRE ${chapter.chapterNumber}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = chapter.displayTitle,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = foreground,
    )
    Spacer(Modifier.height(14.dp))
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
    if (wordCount > 0) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "$wordCount mots · ${(wordCount / WordsPerMinute).coerceAtLeast(1)} min de lecture",
            style = MaterialTheme.typography.labelMedium,
            color = foreground.copy(alpha = 0.5f),
        )
    }
}

/** « Fin du chapitre » encadré de deux filets — plus net qu'un simple texte centré. */
@Composable
private fun EndOfChapterLabel(foreground: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Rule(color = foreground.copy(alpha = 0.18f), modifier = Modifier.weight(1f))
        Text(
            text = "FIN DU CHAPITRE",
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = foreground.copy(alpha = 0.45f),
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Rule(color = foreground.copy(alpha = 0.18f), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Rule(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(1.dp).background(color))
}

/**
 * Bouton de fin de chapitre. Le **suivant** est plein (c'est l'action attendue), le
 * précédent reste en contour : on distingue les deux d'un coup d'œil, sans lire.
 */
@Composable
private fun ChapterNavCard(
    chapter: ChapterDto,
    isNext: Boolean,
    foreground: Color,
    onClick: () -> Unit,
) {
    val filled = isNext
    val container = if (filled) MaterialTheme.colorScheme.primary else Color.Transparent
    val content = if (filled) MaterialTheme.colorScheme.onPrimary else foreground

    Surface(
        color = container,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .then(
                    if (filled) {
                        Modifier
                    } else {
                        // Contour dessiné avec la couleur du TEXTE du lecteur : sur fond
                        // sépia ou OLED, une bordure Material jurerait avec la page.
                        Modifier.border(
                            width = 1.dp,
                            color = foreground.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(16.dp),
                        )
                    },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            if (!isNext) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = if (isNext) Alignment.End else Alignment.Start,
            ) {
                Text(
                    text = if (isNext) "CHAPITRE SUIVANT" else "CHAPITRE PRÉCÉDENT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = content.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = chapter.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isNext) TextAlign.End else TextAlign.Start,
                )
            }
            if (isNext) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Repère de balayage : une **pastille ronde cerclée d'un anneau qui se remplit** au fil du
 * geste. L'anneau boucle pile au seuil, puis la pastille se remplit d'un coup — on sait
 * donc **avant de relâcher** si le chapitre va changer, sans avoir à deviner.
 *
 * Elle grandit et s'opacifie avec le geste : à peine ébauchée elle est presque invisible,
 * elle ne pollue donc pas la lecture quand on effleure l'écran.
 */
@Composable
private fun SwipeHint(
    offset: Float,
    thresholdPx: Float,
    previous: ChapterDto?,
    next: ChapterDto?,
    foreground: Color,
) {
    val goingNext = offset < 0f
    val target = (if (goingNext) next else previous) ?: return
    val progress = (abs(offset) / thresholdPx).coerceIn(0f, 1f)
    if (progress < 0.06f) return

    val armed = progress >= 1f
    val accent = MaterialTheme.colorScheme.primary
    val fill = if (armed) accent else foreground.copy(alpha = 0.10f)
    val content = if (armed) MaterialTheme.colorScheme.onPrimary else foreground.copy(alpha = 0.8f)
    val track = foreground.copy(alpha = 0.14f)

    Box(
        contentAlignment = if (goingNext) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                // Elle éclot avec le geste plutôt que d'apparaître d'un bloc.
                .scale(0.82f + 0.18f * progress)
                .alpha(0.35f + 0.65f * progress),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2f
                drawCircle(color = fill, radius = size.minDimension / 2f - stroke)
                drawArc(
                    color = track,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    // Le sens de remplissage suit celui du geste : vers l'avant pour le
                    // chapitre suivant, à rebours pour le précédent.
                    sweepAngle = if (goingNext) 360f * progress else -360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (goingNext) Icons.AutoMirrored.Filled.KeyboardArrowRight
                    else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "${target.chapterNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
            }
        }
    }
}

/** Filet de progression de 2 dp sous la barre du haut. */
@Composable
private fun ProgressHairline(percent: Int) {
    val fraction = (if (percent < 0) 0 else percent) / 100f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}
