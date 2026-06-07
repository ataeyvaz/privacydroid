package io.privacydroid.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.privacydroid.BuildConfig
import io.privacydroid.ui.appdetail.AppDetailScreen
import io.privacydroid.ui.apps.AppsScreen
import io.privacydroid.ui.blocking.BlockingDetailsScreen
import io.privacydroid.ui.dashboard.DashboardScreen
import io.privacydroid.ui.debug.DebugScreen
import io.privacydroid.ui.logs.LogsScreen
import io.privacydroid.ui.notifications.NotificationCenterScreen
import io.privacydroid.ui.onboarding.OnboardingScreen
import io.privacydroid.ui.settings.SettingsScreen
import io.privacydroid.ui.timeline.TimelineScreen
import io.privacydroid.ui.trackers.TrackerConnectionsScreen

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Logs : Screen("logs")
    data object Apps : Screen("apps")
    data object Timeline : Screen("timeline")
    data object AppDetail : Screen("app_detail/{packageName}") {
        fun createRoute(packageName: String) = "app_detail/$packageName"
    }
    data object Settings : Screen("settings")
    data object Debug : Screen("debug")
    data object NotificationCenter : Screen("notification_center")
    data object TrackerConnections : Screen("tracker_connections")
    data object BlockingDetails : Screen("blocking_details")
}

@Composable
fun PrivacyDroidNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Onboarding.route,
    pendingPackageNavigation: String? = null,
    onPackageNavigationHandled: () -> Unit = {}
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val showBottomNav = currentRoute in bottomNavRoutes

    // Activity seviyesinde badge count — bottom nav badge için
    val badgeViewModel: NotificationBadgeViewModel = hiltViewModel()
    val unreadCount by badgeViewModel.unreadCount.collectAsStateWithLifecycle()

    // Bildirim deep link — uygulama açıkken de çalışır
    LaunchedEffect(pendingPackageNavigation) {
        if (pendingPackageNavigation != null) {
            navController.navigate(Screen.AppDetail.createRoute(pendingPackageNavigation)) {
                launchSingleTop = true
            }
            onPackageNavigationHandled()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                PrivacyBottomNavBar(
                    navController = navController,
                    homeUnreadCount = unreadCount
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    },
                    onTimelineClick = { navController.navigate(Screen.Timeline.route) },
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onNotificationCenterClick = {
                        navController.navigate(Screen.NotificationCenter.route)
                    },
                    onTrackerConnectionsClick = {
                        navController.navigate(Screen.TrackerConnections.route)
                    },
                    onBlockingDetailsClick = {
                        navController.navigate(Screen.BlockingDetails.route)
                    },
                    onDebugClick = if (BuildConfig.DEBUG) {
                        { navController.navigate(Screen.Debug.route) }
                    } else null
                )
            }

            composable(Screen.Logs.route) {
                LogsScreen()
            }

            composable(Screen.Apps.route) {
                AppsScreen()
            }

            composable(Screen.Timeline.route) {
                TimelineScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.AppDetail.route) { backStackEntry ->
                val packageName = backStackEntry.arguments?.getString("packageName")
                    ?: return@composable
                AppDetailScreen(
                    packageName = packageName,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.NotificationCenter.route) {
                NotificationCenterScreen(
                    onBack = { navController.popBackStack() },
                    onAppClick = { packageName ->
                        navController.navigate(Screen.AppDetail.createRoute(packageName))
                    }
                )
            }

            composable(Screen.TrackerConnections.route) {
                TrackerConnectionsScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.BlockingDetails.route) {
                BlockingDetailsScreen(onBack = { navController.popBackStack() })
            }

            if (BuildConfig.DEBUG) {
                composable(Screen.Debug.route) {
                    DebugScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
