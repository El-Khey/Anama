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
            route = "reader/{novelId}/{chapterId}",
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("chapterId") { type = NavType.LongType },
            ),
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: return@composable
            val chapterId = entry.arguments?.getLong("chapterId") ?: return@composable
            ReaderScreen(
                novelId = novelId,
                chapterId = chapterId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
