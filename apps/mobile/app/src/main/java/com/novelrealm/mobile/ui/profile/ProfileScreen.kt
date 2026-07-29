package com.novelrealm.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.dto.UserStatsDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.NovelRealmWordmark
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen

// Onglet Profil (#35) : bannière + avatar, infos du compte, stats de lecture
// (dont les séries de jours consécutifs) et actions (modifier / se déconnecter).
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingScreen()
            state.error != null && state.user == null -> EmptyScreen(
                message = state.error ?: "",
                actionLabel = "Réessayer",
                onAction = viewModel::refresh,
            )
            state.user != null -> {
                val user = state.user ?: return@Box
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    ProfileHeader(user)

                    state.stats?.let { StatsBlock(it) }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Actions, façon liste de réglages Mihon.
                    SettingRow(
                        icon = Icons.Filled.Edit,
                        label = "Modifier le profil",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { showEditDialog = true },
                    )
                    SettingRow(
                        icon = Icons.AutoMirrored.Filled.Logout,
                        label = "Se déconnecter",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onLogout,
                    )

                    Spacer(Modifier.height(24.dp))
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
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showEditDialog && state.user != null) {
        EditProfileDialog(
            user = state.user ?: return,
            isSaving = state.isSaving,
            onSave = { pseudo, bio ->
                viewModel.saveProfile(pseudo, bio)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }
}

// Bannière (image ou dégradé) + avatar rond + pseudo + email + type de compte.
@Composable
private fun ProfileHeader(user: UserDto) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val bannerUrl = resolveImageUrl(user.bannerUrl)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
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
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                ),
                            ),
                        ),
                )
            }
        }

        // Avatar chevauchant la bannière.
        Box(modifier = Modifier.padding(top = 0.dp)) {
            val avatarUrl = resolveImageUrl(user.avatarUrl)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
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

        Spacer(Modifier.height(12.dp))
        Text(
            text = user.pseudo,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!user.bio.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = user.bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (user.provider == "GOOGLE") "Compte Google" else "Compte NovelRealm",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// Grille de statistiques (2 par ligne), icônes Material façon Mihon Stats.
@Composable
private fun StatsBlock(stats: UserStatsDto) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Statistiques",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        val items = listOf(
            Triple(Icons.Filled.AutoStories, stats.chaptersRead, "Chapitres lus"),
            Triple(Icons.Filled.CollectionsBookmark, stats.novelsFollowed, "Romans suivis"),
            Triple(Icons.Filled.TaskAlt, stats.novelsCompleted, "Romans terminés"),
            Triple(Icons.Filled.Bookmark, stats.chaptersFavorited, "Chapitres en signet"),
            Triple(Icons.Filled.LocalFireDepartment, stats.currentStreak, "Série en cours"),
            Triple(Icons.Filled.EmojiEvents, stats.longestStreak, "Meilleure série"),
            Triple(Icons.Filled.CalendarMonth, stats.readingDays, "Jours de lecture"),
        )
        items.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowItems.forEach { (icon, value, label) ->
                    StatCard(
                        icon = icon,
                        value = value,
                        label = label,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: Long,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Ligne d'action façon liste de réglages Mihon (icône + libellé).
@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tint == MaterialTheme.colorScheme.error) tint
            else MaterialTheme.colorScheme.onBackground,
        )
    }
}

// Dialogue d'édition : pseudo (3-30) + bio (max 280, vide = effacée).
@Composable
private fun EditProfileDialog(
    user: UserDto,
    isSaving: Boolean,
    onSave: (pseudo: String, bio: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var pseudo by remember { mutableStateOf(user.pseudo) }
    var bio by remember { mutableStateOf(user.bio ?: "") }
    val pseudoValid = pseudo.trim().length in 3..30

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifier le profil") },
        text = {
            Column {
                OutlinedTextField(
                    value = pseudo,
                    onValueChange = { pseudo = it },
                    label = { Text("Pseudo (3 à 30 caractères)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 280) bio = it },
                    label = { Text("Bio (${bio.length}/280)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(pseudo, bio) },
                enabled = pseudoValid && !isSaving,
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
