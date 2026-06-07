package io.privacydroid.domain.usecase

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.data.model.PermissionType
import io.privacydroid.data.source.AppOpsWrapper
import io.privacydroid.data.source.OpAccessRecord
import io.privacydroid.domain.model.ScanResult
import io.privacydroid.domain.model.SuspiciousReason
import io.privacydroid.domain.repository.PermissionRepository
import io.privacydroid.util.AppInfoHelper
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tüm kurulu uygulamaları AppOpsManager üzerinden tarar.
 * Yalnızca son taramadan bu yana gerçekleşen yeni erişimleri kaydeder (delta tespiti).
 *
 * Şüpheli aktivite kriterleri:
 *   1. Gece 00:00–06:00 arası herhangi bir sensör erişimi
 *   2. Uygulama arka plandayken kamera/mikrofon/konum erişimi
 *   3. Son 1 saatte 10+ konum sorgusu (tek uygulama)
 */
@Singleton
class PermissionScanUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appOpsWrapper: AppOpsWrapper,
    private val repository: PermissionRepository,
    private val appInfoHelper: AppInfoHelper,
    private val correlationEngine: CorrelationEngine
) {

    companion object {
        private const val PREFS_NAME = "permission_scan_prefs"
        private const val KEY_LAST_SCAN_TIME = "last_scan_time_ms"

        private const val NIGHT_START_HOUR = 0   // 00:00
        private const val NIGHT_END_HOUR = 6     // 06:00

        private const val LOCATION_BURST_THRESHOLD = 10         // 1 saatte bu kadar sorgu şüpheli
        private const val LOCATION_BURST_WINDOW_MS = 3_600_000L // 1 saat

        private val SENSOR_OPS = setOf(
            "CAMERA", "RECORD_AUDIO",
            "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"
        )
    }

    private val prefs: SharedPreferences by lazy {
        // Hassas veri değil — yalnızca zaman damgası saklanır
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Taramayı çalıştırır ve sonuçları döner.
     *
     * @return [ScanResult] — kaydedilen log sayısı ve tespit edilen şüpheli aktiviteler
     */
    suspend fun execute(): Result<ScanResult> = runCatching {
        val sinceMs = getLastScanTime()
        Timber.d("Tarama başlatıldı — sinceMs=$sinceMs (${java.util.Date(sinceMs)})")

        val rawRecords = appOpsWrapper.scanAllOpsAllPackages(sinceMs)
        Timber.d("Ham kayıt sayısı: ${rawRecords.size}")

        val newLogs = rawRecords
            .filter { it.lastAccessTimeMs > sinceMs }
            .map { record ->
                PermissionLog(
                    packageName = record.packageName,
                    appName = appInfoHelper.getAppName(record.packageName),
                    permissionType = PermissionType.fromOpStr(
                        appOpsWrapper.monitoredOps
                            .firstOrNull { it.permissionType == record.permissionType }
                            ?.opStr ?: ""
                    ),
                    accessTime = record.lastAccessTimeMs,
                    durationMs = record.durationMs,
                    isBackground = record.isBackground
                )
            }

        if (newLogs.isNotEmpty()) {
            repository.saveLogs(newLogs)
            Timber.d("${newLogs.size} yeni log kaydedildi")
            // Kamera/mikrofon erişimleri için korelasyon ölçümünü 60s sonrasına planla
            correlationEngine.scheduleForNewLogs(newLogs)
        }

        // Şüpheli aktivite tespiti sadece YENİ kayıtlar üzerinde yapılır
        val recentRecords = rawRecords.filter { it.lastAccessTimeMs > sinceMs }
        val suspiciousActivities = detectSuspiciousActivities(recentRecords, sinceMs)

        saveLastScanTime(System.currentTimeMillis())

        ScanResult(
            savedLogCount = newLogs.size,
            suspiciousActivities = suspiciousActivities,
            scanTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * Şüpheli aktiviteleri tespit eder.
     * Her kriter bağımsız olarak değerlendirilir.
     */
    private fun detectSuspiciousActivities(
        records: List<OpAccessRecord>,
        sinceMs: Long
    ): List<SuspiciousActivity> {
        val suspicious = mutableListOf<SuspiciousActivity>()

        // Kriter 1: Gece 00:00–06:00 arası sensör erişimi
        suspicious.addAll(detectNightAccesses(records))

        // Kriter 2: Arka planda sensör erişimi
        suspicious.addAll(detectBackgroundSensorAccesses(records))

        // Kriter 3: Konum burst (1 saatte 10+ sorgu)
        suspicious.addAll(detectLocationBursts(records, sinceMs))

        return suspicious
    }

    private fun detectNightAccesses(records: List<OpAccessRecord>): List<SuspiciousActivity> {
        return records
            .filter { it.permissionType in SENSOR_OPS }
            .filter { isNightTime(it.lastAccessTimeMs) }
            .map {
                SuspiciousActivity(
                    packageName = it.packageName,
                    reason = SuspiciousReason.NIGHT_ACCESS,
                    permissionType = it.permissionType,
                    accessTimeMs = it.lastAccessTimeMs,
                    isBackground = it.isBackground,
                    detail = "Gece ${formatHour(it.lastAccessTimeMs)}'da ${it.permissionDisplayName} erişimi"
                )
            }
    }

    private fun detectBackgroundSensorAccesses(records: List<OpAccessRecord>): List<SuspiciousActivity> {
        return records
            .filter { it.isBackground && it.permissionType in SENSOR_OPS }
            .map {
                SuspiciousActivity(
                    packageName = it.packageName,
                    reason = SuspiciousReason.BACKGROUND_SENSOR_ACCESS,
                    permissionType = it.permissionType,
                    accessTimeMs = it.lastAccessTimeMs,
                    isBackground = true,
                    detail = "Arka planda ${it.permissionDisplayName} erişimi"
                )
            }
    }

    private fun detectLocationBursts(
        records: List<OpAccessRecord>,
        sinceMs: Long
    ): List<SuspiciousActivity> {
        val windowStart = System.currentTimeMillis() - LOCATION_BURST_WINDOW_MS
        val effectiveSince = maxOf(sinceMs, windowStart)

        val locationRecords = records.filter {
            (it.permissionType == "ACCESS_FINE_LOCATION" ||
                    it.permissionType == "ACCESS_COARSE_LOCATION") &&
                    it.lastAccessTimeMs >= effectiveSince
        }

        return locationRecords
            .groupBy { it.packageName }
            .filter { (_, pkgRecords) -> pkgRecords.size >= LOCATION_BURST_THRESHOLD }
            .map { (pkg, pkgRecords) ->
                SuspiciousActivity(
                    packageName = pkg,
                    reason = SuspiciousReason.LOCATION_BURST,
                    permissionType = "ACCESS_FINE_LOCATION",
                    accessTimeMs = pkgRecords.maxOf { it.lastAccessTimeMs },
                    detail = "Son 1 saatte ${pkgRecords.size} konum sorgusu"
                )
            }
    }

    private fun isNightTime(timeMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in NIGHT_START_HOUR until NIGHT_END_HOUR
    }

    private fun formatHour(timeMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        return "%02d:%02d".format(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
    }

    private fun getLastScanTime(): Long =
        prefs.getLong(KEY_LAST_SCAN_TIME, 0L)

    private fun saveLastScanTime(timeMs: Long) {
        prefs.edit().putLong(KEY_LAST_SCAN_TIME, timeMs).apply()
    }
}

data class SuspiciousActivity(
    val packageName: String,
    val reason: SuspiciousReason,
    val permissionType: String,
    val accessTimeMs: Long,
    val detail: String,
    val isBackground: Boolean = false
)
