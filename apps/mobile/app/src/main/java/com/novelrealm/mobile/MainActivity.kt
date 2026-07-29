package com.novelrealm.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.novelrealm.mobile.di.ServiceLocator
import com.novelrealm.mobile.ui.AppRoot
import com.novelrealm.mobile.ui.theme.NovelRealmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Le thème suit les préférences de l'utilisateur (mode + accent) : tout
            // changement dans Profil › Apparence se répercute instantanément sur l'app.
            val prefs by ServiceLocator.preferencesStore.state.collectAsState()

            NovelRealmTheme(themeMode = prefs.themeMode, accent = prefs.accent) {
                // AppRoot aiguille vers le flux d'auth ou la coquille à onglets (MainScreen),
                // chacun gérant ses propres insets système → pas de Scaffold ici (éviter le
                // double Scaffold imbriqué).
                AppRoot()
            }
        }
    }
}
