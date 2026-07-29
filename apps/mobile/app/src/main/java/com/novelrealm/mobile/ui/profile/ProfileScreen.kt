package com.novelrealm.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.NovelRealmWordmark
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.IconTile
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.SettingsDivider
import com.novelrealm.mobile.ui.components.SettingsRow
import com.novelrealm.mobile.ui.components.SettingsSection
import com.novelrealm.mobile.ui.theme.palette

/**
 * Onglet Profil — un **hub** : une en-tête vitrine (bannière + avatar + identité), un
 * résumé de lecture mis en avant, puis les réglages regroupés par thème, chaque groupe
 * ouvrant son propre écran dédié (compte, apparence, lecture, sécurité).
 */
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenSettings: (route: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val prefs by com.novelrealm.mobile.di.ServiceLocator.preferencesStore.state.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        val user = state.user
        when {
            state.isLoading -> LoadingScreen()
            user == null -> EmptyScreen(
                message = state.error ?: "Profil indisponible.",
                actionLabel = "Réessayer",
                onAction = viewModel::refresh,
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ProfileHero(user)

                    Spacer(Modifier.height(24.dp))
                    state.stats?.let { stats ->
                        ReadingSummary(stats)
                        Spacer(Modifier.height(20.dp))
                        StatsGrid(stats)
                        Spacer(Modifier.height(28.dp))
                    }

                    SettingsSection(title = "Compte") {
                        SettingsRow(
                            icon = Icons.Filled.Person,
                            title = "Modifier le profil",
                            subtitle = "Pseudo, bio, photo et bannière",
                            onClick = { onOpenSettings(SettingsRoutes.EDIT_PROFILE) },
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Filled.Lock,
                            title = "Sécurité",
                            subtitle = "Mot de passe et suppression du compte",
                            onClick = { onOpenSettings(SettingsRoutes.ACCOUNT) },
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    SettingsSection(title = "Personnalisation") {
                        SettingsRow(
                            icon = Icons.Filled.Palette,
                            title = "Apparence",
                            subtitle = "${prefs.themeMode.label} · ${prefs.accent.label}",
                            tint = prefs.accent.palette.base,
                            onClick = { onOpenSettings(SettingsRoutes.APPEARANCE) },
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.Filled.MenuBook,
                            title = "Préférences de lecture",
                            subtitle = "${prefs.reader.fontSize} sp · ${prefs.reader.font.label} · ${prefs.reader.theme.label}",
                            onClick = { onOpenSettings(SettingsRoutes.READER) },
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    SettingsSection(title = "Session") {
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            title = "Se déconnecter",
                            subtitle = user.email,
                            destructive = true,
                            showChevron = false,
                            onClick = { confirmLogout = true },
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                    NovelRealmWordmark(
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Text(
                        text = "Application mobile",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            title = { Text("Se déconnecter ?") },
            text = { Text("Tu devras saisir à nouveau tes identifiants pour revenir.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) {
                    Text("Se déconnecter", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("Annuler") }
            },
        )
    }
}

/**
 * En-tête vitrine : bannière assombrie par un dégradé (pour que le texte reste lisible
 * quelle que soit l'image), avatar cerclé qui chevauche, puis identité et bio centrées.
 */
@Composable
private fun ProfileHero(user: UserDto) {
    val bannerUrl = resolveImageUrl(user.bannerUrl)
    val avatarUrl = resolveImageUrl(user.avatarUrl)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                            ),
                        ),
                )
            }
            // Dégradé vers le fond : fond la bannière dans la page, sans coupure nette.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            1f to MaterialTheme.colorScheme.background,
                        ),
                    ),
            )
        }

        // Avatar remontant sur la bannière.
        Box(modifier = Modifier.offset(y = (-56).dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(112.dp)
                    .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clip(CircleShape),
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Photo de profil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = user.pseudo.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        // Le décalage de l'avatar laisse un vide sous lui : on le rattrape.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-44).dp),
        ) {
            Text(
                text = user.pseudo,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!user.bio.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = user.bio,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 36.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            ProviderBadge(user.provider)
        }
    }
}

/** Petite puce indiquant l'origine du compte (NovelRealm ou Google). */
@Composable
private fun ProviderBadge(provider: String?) {
    val label = if (provider == "GOOGLE") "Compte Google" else "Compte NovelRealm"
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * Bandeau de tête des statistiques : la série de lecture, mise en scène sur un dégradé
 * d'accent — c'est l'information qui donne envie de revenir chaque jour.
 */
@Composable
private fun ReadingSummary(stats: UserStatsDto) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                    ),
                ),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (stats.currentStreak) {
                            0L -> "Aucune série en cours"
                            1L -> "1 jour d'affilée"
                            else -> "${stats.currentStreak} jours d'affilée"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = if (stats.currentStreak == 0L) "Lis un chapitre pour lancer ta série"
                        else "Record : ${stats.longestStreak} j · ${stats.readingDays} jours de lecture",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Grille de statistiques 2 colonnes : pastille colorée, valeur en gras, libellé court. */
@Composable
private fun StatsGrid(stats: UserStatsDto) {
    val items = listOf(
        StatItem(Icons.Filled.AutoStories, stats.chaptersRead, "Chapitres lus", MaterialTheme.colorScheme.primary),
        StatItem(Icons.Filled.CollectionsBookmark, stats.novelsFollowed, "Romans suivis", Color(0xFF3B82F6)),
        StatItem(Icons.Filled.TaskAlt, stats.novelsCompleted, "Romans terminés", Color(0xFF10B981)),
        StatItem(Icons.Filled.Bookmark, stats.chaptersFavorited, "Chapitres en signet", Color(0xFF8B5CF6)),
        StatItem(Icons.Filled.EmojiEvents, stats.longestStreak, "Meilleure série", Color(0xFFF59E0B)),
        StatItem(Icons.Filled.CalendarMonth, stats.readingDays, "Jours de lecture", Color(0xFFEC4899)),
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Statistiques",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
        )
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowItems.forEach { item ->
                    StatCard(item = item, modifier = Modifier.weight(1f))
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private data class StatItem(
    val icon: ImageVector,
    val value: Long,
    val label: String,
    val color: Color,
)

@Composable
private fun StatCard(item: StatItem, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            IconTile(icon = item.icon, tint = item.color, size = 38)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "${item.value}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
