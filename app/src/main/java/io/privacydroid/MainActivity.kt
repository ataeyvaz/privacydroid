package io.privacydroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.privacydroid.data.repository.OnboardingRepository
import io.privacydroid.ui.navigation.PrivacyDroidNavGraph
import io.privacydroid.ui.navigation.Screen
import io.privacydroid.ui.theme.PrivacyDroidTheme
import io.privacydroid.util.NotificationHelper
import io.privacydroid.util.PermissionHelper
import javax.inject.Inject

/**
 * Tek aktivite mimarisi.
 *
 * startDestination kararı:
 *   hasPermission → Dashboard (izin varsa her zaman dashboard)
 *   !hasPermission AND onboarding tamamlandı → Dashboard (banner gösterilir, çökmez)
 *   !hasPermission AND onboarding tamamlanmadı → Onboarding
 *
 * Bu karar `onCreate` içinde senkron olarak alınır;
 * AppOps kontrolü ve SharedPreferences okuma ana thread'de hızlıdır.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionHelper: PermissionHelper
    @Inject lateinit var onboardingRepository: OnboardingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = resolveStartDestination()
        val initialTarget = intent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE_TO_PACKAGE)

        setContent {
            PrivacyDroidTheme {
                var navigateToPackage by remember { mutableStateOf(initialTarget) }

                Surface(modifier = Modifier.fillMaxSize()) {
                    PrivacyDroidNavGraph(
                        startDestination = startDestination,
                        pendingPackageNavigation = navigateToPackage,
                        onPackageNavigationHandled = { navigateToPackage = null }
                    )
                }
            }
        }
    }

    /** Bildirim tıklaması uygulama zaten açıkken gelirse (FLAG_ACTIVITY_SINGLE_TOP). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun resolveStartDestination(): String {
        val hasPermission = permissionHelper.hasUsageStatsPermission()
        val onboardingDone = onboardingRepository.isCompleted

        return when {
            hasPermission -> Screen.Dashboard.route
            onboardingDone -> Screen.Dashboard.route  // Banner ile çalışmaya devam et
            else -> Screen.Onboarding.route
        }
    }
}
