package com.novelrealm.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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

                    Spacer(Modifier.height(8.dp))
                    state.stats?.let { stats ->
                        ReadingStats(stats)
                        Spacer(Modifier.height(20.dp))
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
                    SettingsSection(title = "Ma collection") {
                        SettingsRow(
                            icon = Icons.Filled.FormatQuote,
                            title = "Mes citations",
                            subtitle = "Les passages que tu as gardés",
                            onClick = { onOpenSettings(SettingsRoutes.MY_QUOTES) },
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            title = "Mes commentaires",
                            subtitle = "Tout ce que tu as écrit, au même endroit",
                            onClick = { onOpenSettings(SettingsRoutes.MY_COMMENTS) },
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
 * Statistiques de lecture, réunies dans **une seule carte** au lieu d'une mosaïque de
 * vignettes multicolores : trois chiffres de tête, puis les valeurs secondaires en
 * lignes.
 *
 * Chaque nombre n'apparaît qu'une fois — l'ancienne version affichait le record de série
 * à la fois dans le bandeau et dans une vignette. Et la couleur d'accent ne sert plus
 * qu'à **un** repère (la série en cours) : une teinte différente par vignette ne
 * hiérarchisait rien, elle ajoutait juste du bruit.
 */
@Composable
private fun ReadingStats(stats: UserStatsDto) {
    SettingsSection(title = "Statistiques") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
        ) {
            Figure(stats.chaptersRead, "Chapitres lus", Modifier.weight(1f))
            FigureSeparator()
            Figure(stats.novelsFollowed, "Romans suivis", Modifier.weight(1f))
            FigureSeparator()
            Figure(stats.novelsCompleted, "Terminés", Modifier.weight(1f))
        }

        StatsDivider()
        StreakRow(stats)
        StatsDivider()
        StatRow("Meilleure série", dayCount(stats.longestStreak))
        StatsDivider()
        StatRow("Jours de lecture", dayCount(stats.readingDays))
        StatsDivider()
        StatRow("Chapitres en signet", "${stats.chaptersFavorited}")
    }
}

/** Un chiffre de tête et son libellé, centrés. */
@Composable
private fun Figure(value: Long, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Filet vertical entre deux chiffres de tête. */
@Composable
private fun FigureSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/**
 * Filet pleine largeur. `SettingsDivider` ne convient pas ici : son retrait de 68 dp
 * l'aligne sous les pastilles d'icônes des lignes de réglage, or ces lignes-ci n'en
 * ont pas.
 */
@Composable
private fun StatsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

/** La série en cours : le seul repère coloré de la carte, et seulement s'il est vivant. */
@Composable
private fun StreakRow(stats: UserStatsDto) {
    val alive = stats.currentStreak > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = if (alive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Série en cours",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (alive) dayCount(stats.currentStreak) else "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (alive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ligne « libellé à gauche, valeur à droite ». */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun dayCount(days: Long): String = if (days <= 1) "$days jour" else "$days jours"
