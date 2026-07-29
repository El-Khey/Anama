package com.novelrealm.mobile.ui

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novelrealm.mobile.ui.auth.AuthFormState
import com.novelrealm.mobile.ui.auth.AuthViewModel
import com.novelrealm.mobile.ui.auth.LoginScreen
import com.novelrealm.mobile.ui.auth.RegisterScreen

/**
 * Porte d'entrée de l'app : aiguillage selon l'état de session. Connecté →
 * [AppNavHost] (navigation racine : onglets + détail + lecteur, #35) ; sinon →
 * le flux d'auth (login ⇄ register, #33).
 *
 * L'[AuthViewModel] est résolu ici et **partagé** avec les écrans d'auth (même
 * portée que l'Activity), ce qui garde un seul état de session cohérent.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val authViewModel: AuthViewModel = viewModel()
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val form by authViewModel.form.collectAsState()

    if (isAuthenticated) {
        AppNavHost(
            onLogout = authViewModel::logout,
            modifier = modifier,
        )
    } else {
        AuthFlow(
            form = form,
            onLogin = authViewModel::login,
            onRegister = authViewModel::register,
            onClearError = authViewModel::clearError,
            modifier = modifier.systemBarsPadding(),
        )
    }
}

/** Bascule login ⇄ register en gardant l'état du formulaire au même endroit. */
@Composable
private fun AuthFlow(
    form: AuthFormState,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (pseudo: String, email: String, password: String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRegister by rememberSaveable { mutableStateOf(false) }

    if (showRegister) {
        RegisterScreen(
            form = form,
            onSubmit = onRegister,
            onSwitchToLogin = { onClearError(); showRegister = false },
            modifier = modifier,
        )
    } else {
        LoginScreen(
            form = form,
            onSubmit = onLogin,
            onSwitchToRegister = { onClearError(); showRegister = true },
            modifier = modifier,
        )
    }
}
