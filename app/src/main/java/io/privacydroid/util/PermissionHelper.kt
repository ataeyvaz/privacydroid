package io.privacydroid.util

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppOpsManager wrapper — kullanıcının bu uygulamaya PACKAGE_USAGE_STATS
 * iznini verip vermediğini kontrol eder.
 *
 * Root gerektirmez — Android 5.0+ (API 21+).
 */
@Singleton
class PermissionHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * PACKAGE_USAGE_STATS izninin verilip verilmediğini kontrol eder.
     * Bu izin system-level olduğundan normal checkPermission ile test edilemez.
     */
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Timber.e(e, "UsageStats izin kontrolü başarısız")
            false
        }
    }

    /** POST_NOTIFICATIONS iznini kontrol eder (Android 13+) */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 altında otomatik olarak verilir
        }
    }
}
