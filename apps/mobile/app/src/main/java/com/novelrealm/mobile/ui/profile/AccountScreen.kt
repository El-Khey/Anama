package com.novelrealm.mobile.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.ui.auth.ErrorBanner
import com.novelrealm.mobile.ui.auth.InfoBanner
import com.novelrealm.mobile.ui.components.IconTile
import com.novelrealm.mobile.ui.components.SettingsScaffold
import com.novelrealm.mobile.ui.components.SettingsSection

/**
 * Sécurité du compte : changement de mot de passe et suppression définitive.
 *
 * Note : la **réinitialisation par email** (« mot de passe oublié ») n'existe pas encore
 * côté serveur — plutôt qu'un bouton qui ne mènerait nulle part, on l'explique
 * franchement à l'utilisateur.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.accountDeleted) {
        if (state.accountDeleted) onAccountDeleted()
    }

    SettingsScaffold(title = "Sécurité", onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            if (state.isGoogleAccount) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    InfoBanner(
                        "Ton compte utilise la connexion Google : le mot de passe se gère directement chez Google.",
                    )
                }
            } else {
                PasswordSection(
                    isSubmitting = state.isChangingPassword,
                    error = state.passwordError,
                    success = state.passwordChanged,
                    onSubmit = viewModel::changePassword,
                    onClearFeedback = viewModel::clearPasswordFeedback,
                )

                Spacer(Modifier.height(20.dp))
                SettingsSection(title = "Mot de passe oublié") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        IconTile(icon = Icons.Filled.HelpOutline, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(16.dp))
                        Text(
                            text = "La réinitialisation par email n'est pas encore disponible. " +
                                "Si tu as perdu ton mot de passe, contacte l'équipe NovelRealm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            DangerZone(
                isDeleting = state.isDeleting,
                error = state.deleteError,
                onDeleteRequest = { showDeleteDialog = true },
            )

            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            pseudo = state.pseudo,
            isDeleting = state.isDeleting,
            onConfirm = { showDeleteDialog = false; viewModel.deleteAccount() },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/** Formulaire de changement de mot de passe (actuel + nouveau + confirmation). */
@Composable
private fun PasswordSection(
    isSubmitting: Boolean,
    error: String?,
    success: Boolean,
    onSubmit: (current: String, new: String) -> Unit,
    onClearFeedback: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    // Vide les champs après un changement réussi.
    LaunchedEffect(success) {
        if (success) {
            current = ""; newPassword = ""; confirm = ""
        }
    }

    val tooShort = newPassword.isNotEmpty() && newPassword.length < 8
    val mismatch = confirm.isNotEmpty() && confirm != newPassword
    val canSubmit = current.isNotBlank() && newPassword.length >= 8 &&
        confirm == newPassword && !isSubmitting

    SettingsSection(title = "Mot de passe") {
        Column(modifier = Modifier.padding(16.dp)) {
            if (success) {
                InfoBanner("Mot de passe modifié avec succès.")
                Spacer(Modifier.height(16.dp))
            }
            if (error != null) {
                ErrorBanner(error)
                Spacer(Modifier.height(16.dp))
            }

            OutlinedTextField(
                value = current,
                onValueChange = { current = it; onClearFeedback() },
                label = { Text("Mot de passe actuel") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it; onClearFeedback() },
                label = { Text("Nouveau mot de passe") },
                singleLine = true,
                isError = tooShort,
                supportingText = { Text(if (tooShort) "8 caractères minimum" else "8 caractères minimum") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirm,
                onValueChange = { confirm = it; onClearFeedback() },
                label = { Text("Confirmer le nouveau mot de passe") },
                singleLine = true,
                isError = mismatch,
                supportingText = { if (mismatch) Text("Les mots de passe ne correspondent pas") },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSubmit(current, newPassword) },
                enabled = canSubmit,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Changer le mot de passe")
                }
            }
        }
    }
}

/** Zone rouge : action irréversible, visuellement séparée du reste. */
@Composable
private fun DangerZone(
    isDeleting: Boolean,
    error: String?,
    onDeleteRequest: () -> Unit,
) {
    Column {
        Text(
            text = "ZONE DE DANGER",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(icon = Icons.Filled.DeleteForever, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.size(16.dp))
                    Column {
                        Text(
                            text = "Supprimer mon compte",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Définitif : profil, bibliothèque, historique et avis seront effacés.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    ErrorBanner(error)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDeleteRequest,
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        Text("Supprimer définitivement")
                    }
                }
            }
        }
    }
}

/**
 * Confirmation par saisie du pseudo : un simple « Oui » est trop facile à cliquer pour
 * une action irréversible (même garde-fou que sur le site web).
 */
@Composable
private fun DeleteAccountDialog(
    pseudo: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val matches = typed.trim() == pseudo && pseudo.isNotBlank()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                Icons.Filled.DeleteForever,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text("Supprimer le compte ?") },
        text = {
            Column {
                Text(
                    text = "Cette action est irréversible. Toutes tes données seront effacées : " +
                        "bibliothèque, progression, signets, avis et historique.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Pour confirmer, saisis ton pseudo : $pseudo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    isError = typed.isNotEmpty() && !matches,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches && !isDeleting) {
                Text("Supprimer", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Annuler") }
        },
    )
}
