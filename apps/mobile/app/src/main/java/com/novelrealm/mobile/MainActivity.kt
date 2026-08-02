package com.novelrealm.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
                // Ce Surface peint le fond de TOUTE l'app avec la couleur du thème choisi.
                // Sans lui, seuls les écrans munis d'un Scaffold (les onglets) avaient un
                // fond : les écrans plein écran du NavHost racine (fiche d'un roman, avis…)
                // laissaient apparaître le fond BLANC de la fenêtre Android — illisible en
                // thème sombre, où le texte est clair. Ce n'est pas un Scaffold : il ne
                // gère aucun inset, donc pas de double Scaffold imbriqué.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // AppRoot aiguille vers le flux d'auth ou la coquille à onglets.
                    AppRoot()
                }
            }
        }
    }
}
