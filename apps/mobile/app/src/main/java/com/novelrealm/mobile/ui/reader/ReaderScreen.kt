package com.novelrealm.mobile.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.di.ServiceLocator
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.util.vmFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Lecteur de chapitre (#35), UX façon Mihon : texte plein écran, un tap fait apparaître /
// disparaître les barres (titre + signet en haut ; précédent / slider / suivant en bas).
// La position est restaurée à l'ouverture puis sauvegardée en continu (débouncée).
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
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Confort de lecture réglé dans Profil › Lecture : couleurs, typo et espacements.
    val readerPrefs by ServiceLocator.preferencesStore.state.collectAsState()
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
    DisposableEffect(readerPrefs.reader.fullscreen, chromeVisible) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (readerPrefs.reader.fullscreen && !chromeVisible) {
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

    // Restaure la position de reprise, puis suit la lecture (sauvegarde débouncée).
    LaunchedEffect(state.chapter?.id, state.isLoading) {
        if (state.isLoading || state.chapter == null) return@LaunchedEffect
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
            ) { chromeVisible = !chromeVisible },
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.statusBarsPadding())
                    Spacer(Modifier.height(48.dp))
                    Text(
                        text = chapter.title?.takeIf { it.isNotBlank() }
                            ?: "Chapitre ${chapter.chapterNumber}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = style.foreground,
                    )
                    Spacer(Modifier.height(20.dp))
                    // Le texte est découpé en paragraphes pour pouvoir appliquer l'écart
                    // réglable entre eux (un seul bloc de texte ne le permettrait pas).
                    val paragraphs = remember(chapter.content) {
                        chapter.content.split('\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .ifEmpty { listOf(chapter.content) }
                    }
                    paragraphs.forEachIndexed { index, paragraph ->
                        if (index > 0) Spacer(Modifier.height(style.paragraphSpacing))
                        Text(
                            text = paragraph,
                            style = style.textStyle,
                            color = style.foreground,
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(
                        text = "— Fin du chapitre —",
                        style = MaterialTheme.typography.bodySmall,
                        color = style.foreground.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.navigationBarsPadding())
                }
            }
        }

        // ── Barre du haut : retour + titre + signet ──
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
                            text = state.chapter?.title?.takeIf { it.isNotBlank() }
                                ?: "Chapitre ${state.chapter?.chapterNumber ?: ""}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Chapitre ${state.chapter?.chapterNumber ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Filled.Bookmark
                            else Icons.Filled.BookmarkBorder,
                            contentDescription = if (state.isFavorite) "Retirer le signet" else "Ajouter un signet",
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // ── Barre du bas : chapitre précédent / slider de progression / suivant ──
        AnimatedVisibility(
            visible = chromeVisible,
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
    }
}
