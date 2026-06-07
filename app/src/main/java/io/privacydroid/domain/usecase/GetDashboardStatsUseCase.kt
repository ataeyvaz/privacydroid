package io.privacydroid.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.domain.model.DashboardStats
import io.privacydroid.domain.model.LogFilter
import io.privacydroid.domain.model.RiskyApp
import io.privacydroid.domain.model.TimeRange
import io.privacydroid.domain.repository.PermissionRepository
import io.privacydroid.util.AppInfoHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDashboardStatsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PermissionRepository,
    private val appInfoHelper: AppInfoHelper
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("permission_scan_prefs", Context.MODE_PRIVATE)
    }

    /**
     * Bugünkü özet istatistikleri Flow olarak döner.
     * DB değiştikçe otomatik güncellenir.
     */
    fun observe(): Flow<DashboardStats> {
        val filter = LogFilter(timeRange = TimeRange.TODAY)
        return repository.getFilteredLogs(filter).map { logs ->
            val cameraCount = logs.count { it.permissionType.name == "CAMERA" }
            val micCount = logs.count { it.permissionType.name == "MICROPHONE" }
            val locCount = logs.count {
                it.permissionType.name == "LOCATION_FINE" ||
                        it.permissionType.name == "LOCATION_COARSE"
            }

            val bgByApp = logs
                .filter { it.isBackground }
                .groupBy { it.packageName }
                .mapValues { (_, v) -> v.size }

            val topPkg = bgByApp.maxByOrNull { it.value }
            val mostRiskyApp = topPkg?.let { (pkg, count) ->
                RiskyApp(
                    packageName = pkg,
                    appName = appInfoHelper.getAppName(pkg),
                    backgroundAccessCount = count
                )
            }

            DashboardStats(
                cameraCount = cameraCount,
                microphoneCount = micCount,
                locationCount = locCount,
                totalCount = logs.size,
                mostRiskyApp = mostRiskyApp,
                lastScanTimeMs = prefs.getLong("last_scan_time_ms", 0L)
            )
        }
    }
}
