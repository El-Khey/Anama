package com.novelrealm.mobile.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.novelrealm.mobile.ui.NovelRealmWordmark
import com.novelrealm.mobile.ui.theme.NovelRealmTheme

/**
 * Écran de connexion (#33). Sans état métier propre : il reçoit l'état du
 * formulaire et remonte les actions — l'[AuthViewModel] fait le travail.
 */
@Composable
fun LoginScreen(
    form: AuthFormState,
    onSubmit: (email: String, password: String) -> Unit,
    onSwitchToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    sessionExpired: Boolean = false,
    onExpiryShown: () -> Unit = {},
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Le message d'expiration disparaît dès que l'utilisateur retape ses identifiants.
    val showExpiredNotice = sessionExpired && form.error == null
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !form.isSubmitting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NovelRealmWordmark()
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ravi de te revoir 📚",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(32.dp))

        if (showExpiredNotice) {
            InfoBanner("Ta session a expiré. Reconnecte-toi pour continuer.")
            Spacer(Modifier.height(16.dp))
        }

        AuthTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            enabled = !form.isSubmitting,
        )
        Spacer(Modifier.height(16.dp))
        PasswordField(
            value = password,
            onValueChange = { password = it },
            label = "Mot de passe",
            imeAction = ImeAction.Done,
            enabled = !form.isSubmitting,
        )

        if (form.error != null) {
            Spacer(Modifier.height(16.dp))
            ErrorBanner(form.error)
        }

        Spacer(Modifier.height(24.dp))
        SubmitButton(
            text = "Se connecter",
            onClick = { onExpiryShown(); onSubmit(email, password) },
            enabled = canSubmit,
            loading = form.isSubmitting,
        )

        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { onExpiryShown(); onSwitchToRegister() },
            enabled = !form.isSubmitting,
        ) {
            Text("Pas encore de compte ? S'inscrire")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    NovelRealmTheme {
        LoginScreen(form = AuthFormState(), onSubmit = { _, _ -> }, onSwitchToRegister = {})
    }
}
