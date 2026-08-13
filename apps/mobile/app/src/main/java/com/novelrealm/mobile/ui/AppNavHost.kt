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
import com.novelrealm.mobile.ui.notifications.NotificationsScreen
import com.novelrealm.mobile.ui.profile.AccountScreen
import com.novelrealm.mobile.ui.profile.AppearanceScreen
import com.novelrealm.mobile.ui.profile.EditProfileScreen
import com.novelrealm.mobile.ui.profile.MyCommentsScreen
import com.novelrealm.mobile.ui.profile.ReaderSettingsScreen
import com.novelrealm.mobile.ui.profile.SettingsRoutes
import com.novelrealm.mobile.ui.quotes.MyQuotesScreen
import com.novelrealm.mobile.ui.reader.ReaderScreen
import com.novelrealm.mobile.ui.reviews.ReviewsScreen
import com.novelrealm.mobile.ui.social.PublicProfileScreen

// Navigation racine de l'app connectée (#35) : la coquille à onglets (main) est la base ;
// détail / avis / lecteur s'empilent PAR-DESSUS (plein écran, sans barre du bas) — le
// même schéma que Mihon où la fiche d'un manga recouvre la bottom nav.
@Composable
fun AppNavHost(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    // Le lien profond des notifications et de « Mes commentaires » (issue #45) : un
    // passage précis (`block` ≥ 0) ou la discussion de fin de chapitre (`comments`).
    // Une seule fabrique d'URL — deux écrans l'utilisent, ils doivent rester d'accord.
    val openChapterAt = { novelId: Long, chapterId: Long, block: Int, comments: Boolean ->
        navController.navigate("reader/$novelId/$chapterId?block=$block&comments=$comments")
    }

    NavHost(navController = navController, startDestination = "main", modifier = modifier) {
        composable("main") {
            MainScreen(
                onLogout = onLogout,
                onNovelClick = { novelId -> navController.navigate("novel/$novelId") },
                onOpenReader = { novelId, chapterId ->
                    navController.navigate("reader/$novelId/$chapterId")
                },
                onOpenSettings = { route -> navController.navigate(route) },
                onOpenNotifications = { navController.navigate("notifications") },
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
        composable(SettingsRoutes.MY_COMMENTS) {
            MyCommentsScreen(
                onBack = { navController.popBackStack() },
                onOpenChapter = openChapterAt,
            )
        }

        // ── La cloche (issue #45, §3) ──
        composable("notifications") {
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenChapter = openChapterAt,
            )
        }

        // ── Profil public d'un autre lecteur (issue #45, §2) ──
        composable(
            route = "user/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) { entry ->
            val userId = entry.arguments?.getLong("userId") ?: return@composable
            PublicProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
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
            // `block` et `comments` sont OPTIONNELS : les ouvertures normales du
            // lecteur (bibliothèque, fiche, historique) appellent la route sans eux.
            // `block` ≥ 0 rejoint un passage ; `comments` fait défiler jusqu'à la
            // discussion de fin de chapitre (liens profonds de l'issue #45, §3).
            route = "reader/{novelId}/{chapterId}?block={block}&comments={comments}",
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("chapterId") { type = NavType.LongType },
                navArgument("block") {
                    type = NavType.IntType
                    defaultValue = -1
                },
                navArgument("comments") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val novelId = entry.arguments?.getLong("novelId") ?: return@composable
            val chapterId = entry.arguments?.getLong("chapterId") ?: return@composable
            ReaderScreen(
                novelId = novelId,
                chapterId = chapterId,
                highlightBlock = entry.arguments?.getInt("block") ?: -1,
                openComments = entry.arguments?.getBoolean("comments") ?: false,
                onOpenUser = { userId -> navController.navigate("user/$userId") },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
