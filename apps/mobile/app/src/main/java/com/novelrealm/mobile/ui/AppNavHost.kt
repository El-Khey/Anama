package com.novelrealm.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novelrealm.mobile.ui.detail.NovelDetailScreen
import com.novelrealm.mobile.ui.main.MainScreen
import com.novelrealm.mobile.ui.profile.AccountScreen
import com.novelrealm.mobile.ui.profile.AppearanceScreen
import com.novelrealm.mobile.ui.profile.EditProfileScreen
import com.novelrealm.mobile.ui.profile.ReaderSettingsScreen
import com.novelrealm.mobile.ui.profile.SettingsRoutes
import com.novelrealm.mobile.ui.quotes.MyQuotesScreen
import com.novelrealm.mobile.ui.reader.ReaderScreen
import com.novelrealm.mobile.ui.reviews.ReviewsScreen

// Navigation racine de l'app connectée (#35) : la coquille à onglets (main) est la base ;
// détail / avis / lecteur s'empilent PAR-DESSUS (plein écran, sans barre du bas) — le
// même schéma que Mihon où la fiche d'un manga recouvre la bottom nav.
@Composable
fun AppNavHost(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main", modifier = modifier) {
        composable("main") {
            MainScreen(
                onLogout = onLogout,
                onNovelClick = { novelId -> navController.navigate("novel/$novelId") },
                onOpenReader = { novelId, chapterId ->
                    navController.navigate("reader/$novelId/$chapterId")
                },
                onOpenSettings = { route -> navController.navigate(route) },
            )
        }

        // ── Réglages, ouverts depuis l'onglet Profil (plein écran, comme le détail) ──
        composable(SettingsRoutes.EDIT_PROFILE) {
            EditProfileScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                // Le compte n'existe plus : la session est déjà fermée, donc AppRoot
                // bascule seul vers la connexion ; on dépile simplement cet écran.
                onAccountDeleted = { navController.popBackStack() },
            )
        }
        composable(SettingsRoutes.APPEARANCE) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.READER) {
            ReaderSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(SettingsRoutes.MY_QUOTES) {
            MyQuotesScreen(
                onBack = { navController.popBackStack() },
                // Le bloc est déjà résolu par le serveur : le lecteur n'a plus qu'à
                // s'y rendre et le surligner.
                onOpenPassage = { novelId, chapterId, block ->
                    navController.navigate("reader/$novelId/$chapterId?block=$block")
                },
            )
        }

        composable(
            route = "novel/{novelId}",
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: return@composable
            NovelDetailScreen(
                novelId = novelId,
                onBack = { navController.popBackStack() },
                onOpenReader = { nId, chapterId -> navController.navigate("reader/$nId/$chapterId") },
                onOpenReviews = { nId -> navController.navigate("novel/$nId/reviews") },
            )
        }
        composable(
            route = "novel/{novelId}/reviews",
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: return@composable
            ReviewsScreen(
                novelId = novelId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            // `block` est un paramètre OPTIONNEL : les ouvertures normales du lecteur
            // (bibliothèque, fiche, historique) continuent d'appeler la route sans lui.
            route = "reader/{novelId}/{chapterId}?block={block}",
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("chapterId") { type = NavType.LongType },
                navArgument("block") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: return@composable
            val chapterId = entry.arguments?.getLong("chapterId") ?: return@composable
            ReaderScreen(
                novelId = novelId,
                chapterId = chapterId,
                highlightBlock = entry.arguments?.getInt("block") ?: -1,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
