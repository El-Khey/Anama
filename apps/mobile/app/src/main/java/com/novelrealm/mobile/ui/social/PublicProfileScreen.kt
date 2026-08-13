package com.novelrealm.mobile.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.PublicUserDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.ui.components.EmptyScreen
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.util.dateLabel
import com.novelrealm.mobile.ui.util.vmFactory

/**
 * Profil PUBLIC d'un autre lecteur (issue #45, §2) — ce qu'ouvre l'appui sur une
 * mention ou sur le pseudo d'un commentaire.
 *
 * Même vitrine que son propre profil (bannière, avatar cerclé, identité), mais
 * réduite à ce qui est public : pseudo, bio, ancienneté. Ni email, ni réglages,
 * ni statistiques — le serveur ne les envoie d'ailleurs pas.
 */
@Composable
fun PublicProfileScreen(
    userId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublicProfileViewModel = viewModel(
        key = "public-profile-$userId",
        factory = vmFactory { PublicProfileViewModel(userId) },
    ),
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val user = state.user
        when {
            state.isLoading -> LoadingScreen()
            user == null -> EmptyScreen(
                message = state.error ?: "Profil indisponible.",
                actionLabel = "Réessayer",
                onAction = viewModel::load,
            )
            else -> ProfileContent(user)
        }

        // Bouton retour posé PAR-DESSUS la bannière : un vrai TopAppBar la couperait.
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ProfileContent(user: PublicUserDto) {
    val bannerUrl = resolveImageUrl(user.bannerUrl)
    val avatarUrl = resolveImageUrl(user.avatarUrl)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(190.dp)) {
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
                            text = user.pseudo.take(1).uppercase().ifBlank { "?" },
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-44).dp),
        ) {
            Text(
                text = user.pseudo,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
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
            if (!user.createdAt.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = "Lecteur depuis le ${dateLabel(user.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
