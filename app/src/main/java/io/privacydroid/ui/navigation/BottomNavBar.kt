package io.privacydroid.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.privacydroid.ui.theme.AlertRed
import io.privacydroid.ui.theme.DarkSurfaceVariant
import io.privacydroid.ui.theme.PrimaryGreen

private data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("Ana Sayfa", Screen.Dashboard.route, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("Günlük", Screen.Logs.route, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
    BottomNavItem("Uygulamalar", Screen.Apps.route, Icons.Filled.Apps, Icons.Outlined.Apps),
    BottomNavItem("Çizelge", Screen.Timeline.route, Icons.Filled.Timeline, Icons.Outlined.Timeline),
    BottomNavItem("Ayarlar", Screen.Settings.route, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun PrivacyBottomNavBar(navController: NavController, homeUnreadCount: Int = 0) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar(containerColor = DarkSurfaceVariant) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    val icon = if (selected) item.selectedIcon else item.unselectedIcon
                    if (item.route == Screen.Dashboard.route && homeUnreadCount > 0) {
                        BadgedBox(badge = {
                            Badge(containerColor = AlertRed) {
                                Text(if (homeUnreadCount > 99) "99+" else homeUnreadCount.toString())
                            }
                        }) {
                            Icon(imageVector = icon, contentDescription = item.label)
                        }
                    } else {
                        Icon(imageVector = icon, contentDescription = item.label)
                    }
                },
                label = {
                    Text(item.label, fontSize = 9.sp, maxLines = 1)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PrimaryGreen,
                    selectedTextColor = PrimaryGreen,
                    indicatorColor = PrimaryGreen.copy(alpha = 0.15f)
                )
            )
        }
    }
}

val bottomNavRoutes = setOf(
    Screen.Dashboard.route,
    Screen.Logs.route,
    Screen.Apps.route,
    Screen.Timeline.route,
    Screen.Settings.route
)
