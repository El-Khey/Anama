package com.novelrealm.mobile.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.novelrealm.mobile.ui.explore.ExploreScreen
import com.novelrealm.mobile.ui.history.HistoryScreen
import com.novelrealm.mobile.ui.library.LibraryScreen
import com.novelrealm.mobile.ui.notifications.NotificationBellViewModel
import com.novelrealm.mobile.ui.profile.ProfileScreen

// Coquille principale de l'app connectée (#34/#35) : barre de navigation Material 3 en
// bas + NavHost interne pour les 4 onglets. Les écrans plein-écran (détail, lecteur…)
// vivent dans le NavHost RACINE (AppNavHost) et recouvrent cette coquille.
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    onNovelClick: (Long) -> Unit,
    onOpenReader: (novelId: Long, chapterId: Long) -> Unit,
    onOpenSettings: (route: String) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Le compteur de non-lues est tenu ICI, à la racine de la coquille : il
    // alimente à la fois la cloche de la bibliothèque, la pastille de l'onglet
    // Profil et la ligne « Activité » du profil. Un seul appel pour trois
    // affichages, et surtout un seul chiffre — trois compteurs indépendants
    // finiraient par se contredire.
    val bellViewModel: NotificationBellViewModel = viewModel()
    val unreadNotifications by bellViewModel.unreadCount.collectAsState()

    // À chaque changement d'onglet, et au retour d'un écran plein cadre (la
    // coquille est alors recomposée) : c'est exactement quand le chiffre a pu
    // changer sans qu'on le sache.
    LaunchedEffect(currentRoute) {
        bellViewModel.refresh()
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    // Revenir à l'onglet de départ en mémorisant l'état,
                                    // sans empiler les onglets dans le back stack.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            TabIcon(
                                destination = destination,
                                // Les notifications se lisent depuis le profil :
                                // c'est là que la pastille doit appeler.
                                badge = if (destination == TopLevelDestination.Profile) {
                                    unreadNotifications
                                } else {
                                    0
                                },
                            )
                        },
                        label = {
                            Text(
                                text = destination.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Library.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.Library.route) {
                LibraryScreen(
                    onNovelClick = onNovelClick,
                    onOpenNotifications = onOpenNotifications,
                    unreadNotifications = unreadNotifications,
                )
            }
            composable(TopLevelDestination.Explore.route) {
                ExploreScreen(onNovelClick = onNovelClick)
            }
            composable(TopLevelDestination.History.route) {
                HistoryScreen(onOpenReader = onOpenReader)
            }
            composable(TopLevelDestination.Profile.route) {
                ProfileScreen(
                    onLogout = onLogout,
                    onOpenSettings = onOpenSettings,
                    unreadNotifications = unreadNotifications,
                )
            }
        }
    }
}

/**
 * L'icône d'un onglet, éventuellement pastillée.
 *
 * La pastille est posée en surimpression, décalée vers le haut à droite : à
 * côté de l'icône, elle décentrerait l'onglet à chaque fois que le chiffre
 * apparaît ou disparaît. Au-delà de 9, « 9+ » — le compte exact est dans
 * l'écran, pas sur une barre de navigation.
 */
@Composable
private fun TabIcon(destination: TopLevelDestination, badge: Long) {
    Box {
        Icon(destination.icon, contentDescription = destination.label)
        if (badge > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-6).dp)
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = if (badge > 9) "9+" else "$badge",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onError,
                    maxLines = 1,
                )
            }
        }
    }
}
