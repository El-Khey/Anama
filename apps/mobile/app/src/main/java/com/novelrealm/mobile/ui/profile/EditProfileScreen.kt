package com.novelrealm.mobile.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.novelrealm.mobile.data.remote.dto.UserDto
import com.novelrealm.mobile.data.remote.resolveImageUrl
import com.novelrealm.mobile.data.repository.ImageKind
import com.novelrealm.mobile.ui.auth.ErrorBanner
import com.novelrealm.mobile.ui.auth.InfoBanner
import com.novelrealm.mobile.ui.components.LoadingScreen
import com.novelrealm.mobile.ui.components.SettingsScaffold

/**
 * Édition du profil : images (bannière + photo), pseudo et bio.
 *
 * Les images sont envoyées **immédiatement** après sélection (une action = un effet,
 * pas de « brouillon » d'image à gérer), tandis que pseudo/bio sont validés par le
 * bouton Enregistrer.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }
    // Fermeture automatique après un enregistrement réussi.
    LaunchedEffect(state.savedSuccessfully) {
        if (state.savedSuccessfully) onBack()
    }

    SettingsScaffold(title = "Modifier le profil", onBack = onBack, modifier = modifier) {
        val user = state.user
        if (user == null) {
            LoadingScreen()
            return@SettingsScaffold
        }

        var pseudo by remember(user.id) { mutableStateOf(user.pseudo) }
        var bio by remember(user.id) { mutableStateOf(user.bio ?: "") }

        val pseudoTrimmed = pseudo.trim()
        val pseudoError = when {
            pseudoTrimmed.length < 3 -> "Au moins 3 caractères"
            pseudoTrimmed.length > 30 -> "30 caractères maximum"
            else -> null
        }
        val hasChanges = pseudoTrimmed != user.pseudo || bio.trim() != (user.bio ?: "").trim()

        // Sélecteurs d'image : PickVisualMedia n'exige AUCUNE permission (l'utilisateur
        // choisit lui-même le média dans le sélecteur système).
        val bannerPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> uri?.let { sendImage(context, viewModel, ImageKind.BANNER, it) } }

        val avatarPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> uri?.let { sendImage(context, viewModel, ImageKind.AVATAR, it) } }

        val pickImageRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            ImageEditor(
                user = user,
                uploading = state.uploading,
                onPickBanner = { bannerPicker.launch(pickImageRequest) },
                onPickAvatar = { avatarPicker.launch(pickImageRequest) },
                onRemoveBanner = { viewModel.deleteImage(ImageKind.BANNER) },
                onRemoveAvatar = { viewModel.deleteImage(ImageKind.AVATAR) },
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                state.error?.let {
                    ErrorBanner(it)
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = "Identité",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = pseudo,
                    onValueChange = { if (it.length <= 40) pseudo = it },
                    label = { Text("Pseudo") },
                    supportingText = {
                        Text(pseudoError ?: "Visible par les autres lecteurs · ${pseudoTrimmed.length}/30")
                    },
                    isError = pseudoError != null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 280) bio = it },
                    label = { Text("Bio") },
                    supportingText = { Text("${bio.length}/280 · laisse vide pour l'effacer") },
                    minLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.saveProfile(pseudo, bio) },
                    enabled = pseudoError == null && hasChanges && !state.isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Enregistrer", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(12.dp))
                InfoBanner("Formats acceptés : JPG, PNG ou WebP. Photo 2 Mo max, bannière 4 Mo max.")
                Spacer(Modifier.height(32.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

/** Bannière + avatar avec leurs boutons d'action (changer / retirer). */
@Composable
private fun ImageEditor(
    user: UserDto,
    uploading: ImageKind?,
    onPickBanner: () -> Unit,
    onPickAvatar: () -> Unit,
    onRemoveBanner: () -> Unit,
    onRemoveAvatar: () -> Unit,
) {
    val bannerUrl = resolveImageUrl(user.bannerUrl)
    val avatarUrl = resolveImageUrl(user.avatarUrl)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clickable(onClick = onPickBanner),
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = "Bannière",
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
            // Voile sombre : garantit la lisibilité des boutons sur toute image.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
            )

            if (uploading == ImageKind.BANNER) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    CircleAction(Icons.Filled.PhotoCamera, "Changer la bannière", onPickBanner)
                    if (user.bannerUrl != null) {
                        CircleAction(Icons.Filled.Delete, "Retirer la bannière", onRemoveBanner)
                    }
                }
            }

            Text(
                text = "Touche la bannière pour la changer",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 12.dp),
            )
        }

        // Avatar chevauchant, avec son propre bouton photo.
        Box(modifier = Modifier.offset(y = (-48).dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(104.dp)
                    .border(4.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clip(CircleShape)
                    .clickable(onClick = onPickAvatar),
            ) {
                when {
                    uploading == ImageKind.AVATAR -> Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) { CircularProgressIndicator(strokeWidth = 2.dp) }

                    avatarUrl != null -> AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Photo de profil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = user.pseudo.take(1).uppercase(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clickable(onClick = onPickAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = "Changer la photo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }

        if (user.avatarUrl != null) {
            TextButton(
                onClick = onRemoveAvatar,
                modifier = Modifier.offset(y = (-44).dp),
            ) {
                Text("Retirer la photo", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(0.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
    // Compense le décalage négatif de l'avatar pour que le contenu suivant respire.
    Spacer(Modifier.height(if (user.avatarUrl != null) 0.dp else 8.dp))
}

/** Petit bouton rond translucide posé sur une image. */
@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/**
 * Lit l'image choisie et confie les octets au ViewModel.
 *
 * La lecture se fait ici parce qu'elle réclame un `ContentResolver` (donc un Context) :
 * le ViewModel, lui, n'en détient jamais. Les images de profil restent petites (2-4 Mo
 * max, imposés par le back), un chargement en mémoire est donc sans risque.
 */
private fun sendImage(
    context: Context,
    viewModel: ProfileViewModel,
    kind: ImageKind,
    uri: Uri,
) {
    val resolver = context.contentResolver
    val bytes = runCatching {
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()

    if (bytes == null) {
        viewModel.uploadImage(kind, ByteArray(0), null)   // déclenche le message d'erreur
        return
    }
    viewModel.uploadImage(kind, bytes, resolver.getType(uri))
}
