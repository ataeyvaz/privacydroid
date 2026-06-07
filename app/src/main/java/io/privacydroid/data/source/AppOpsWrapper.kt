package io.privacydroid.data.source

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uygulama izin geçmişini UsageStatsManager üzerinden sorgular.
 *
 * Neden UsageStatsManager?
 *   AppOpsManager.getOpsForPackage() ve PackageOps/OpEntry sınıfları Android'in
 *   @hide (gizli) API'sidir — public SDK'da yer almaz ve normal uygulamalar
 *   derleyemez. PACKAGE_USAGE_STATS izniyle çalışan gerçek public API
 *   UsageStatsManager.queryUsageStats() / queryEvents()'tir.
 *
 * Yaklaşım:
 *   1. queryUsageStats() → uygulamanın son kullanım zamanı ve arka plan süresi
 *   2. PackageManager.checkPermission() → uygulama hangi hassas izinlere sahip?
 *   3. Arka plan + hassas izin var = olası arka plan erişimi kaydı oluştur
 *
 * Kısıtlama: Hangi iznin tam olarak ne zaman kullanıldığı bilinemez (platform sınırı).
 * Kesin erişim zamanı yerine "son kullanım zamanı + izin sahibi" bilgisi döner.
 */
@Singleton
class AppOpsWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val monitoredOps: List<MonitoredOp> = listOf(
        MonitoredOp(AppOpsManager.OPSTR_CAMERA,                 "CAMERA",                 "Kamera"),
        MonitoredOp(AppOpsManager.OPSTR_RECORD_AUDIO,           "RECORD_AUDIO",           "Mikrofon"),
        MonitoredOp(AppOpsManager.OPSTR_FINE_LOCATION,          "ACCESS_FINE_LOCATION",   "Konum (Hassas)"),
        MonitoredOp(AppOpsManager.OPSTR_COARSE_LOCATION,        "ACCESS_COARSE_LOCATION", "Konum (Yaklaşık)"),
        MonitoredOp(AppOpsManager.OPSTR_READ_CONTACTS,          "READ_CONTACTS",          "Rehber"),
        MonitoredOp(AppOpsManager.OPSTR_READ_CALL_LOG,          "READ_CALL_LOG",          "Arama Geçmişi"),
        MonitoredOp(AppOpsManager.OPSTR_READ_SMS,               "READ_SMS",               "SMS"),
        MonitoredOp(AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS, "PROCESS_OUTGOING_CALLS", "Giden Aramalar"),
    )

    /**
     * Tüm kurulu uygulamaları tarar; hassas izni olan ve yakın zamanda
     * arka planda aktif olanları [OpAccessRecord] olarak döner.
     */
    fun scanAllOpsAllPackages(sinceMs: Long = 0L): List<OpAccessRecord> {
        val endMs = System.currentTimeMillis()
        val stats = try {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, sinceMs, endMs)
        } catch (e: Exception) {
            Timber.w("UsageStats sorgu hatası: ${e.message}")
            return emptyList()
        }

        if (stats.isNullOrEmpty()) return emptyList()

        val pm = context.packageManager
        val results = mutableListOf<OpAccessRecord>()

        for (appStats in stats) {
            if (appStats.lastTimeUsed <= sinceMs) continue

            // totalTimeVisible > totalTimeInForeground → arka planda da çalışmış
            val wasBackground =
                appStats.totalTimeVisible > appStats.totalTimeInForeground + 2_000L

            for (op in monitoredOps) {
                val permName = opStrToPermission(op.opStr) ?: continue
                val hasPermission = try {
                    pm.checkPermission(permName, appStats.packageName) ==
                            PackageManager.PERMISSION_GRANTED
                } catch (_: Exception) { false }

                if (!hasPermission) continue

                results.add(
                    OpAccessRecord(
                        packageName = appStats.packageName,
                        opStr = op.opStr,
                        permissionType = op.permissionType,
                        permissionDisplayName = op.displayName,
                        lastAccessTimeMs = appStats.lastTimeUsed,
                        durationMs = appStats.totalTimeInForeground,
                        isBackground = wasBackground,
                        accessCount = 1
                    )
                )
            }
        }

        Timber.d("Tarama sonucu: ${results.size} kayıt (${stats.size} uygulama tarandi)")
        return results
    }

    fun getOpsForPackage(
        packageName: String,
        @Suppress("UNUSED_PARAMETER") uid: Int,
        sinceMs: Long = 0L
    ): List<OpAccessRecord> =
        scanAllOpsAllPackages(sinceMs).filter { it.packageName == packageName }

    /**
     * AppOps op string'ini Android permission adına çevirir.
     * Null dönerse bu op için permission kontrolü yapılamaz.
     */
    private fun opStrToPermission(opStr: String): String? = when (opStr) {
        AppOpsManager.OPSTR_CAMERA                 -> Manifest.permission.CAMERA
        AppOpsManager.OPSTR_RECORD_AUDIO           -> Manifest.permission.RECORD_AUDIO
        AppOpsManager.OPSTR_FINE_LOCATION          -> Manifest.permission.ACCESS_FINE_LOCATION
        AppOpsManager.OPSTR_COARSE_LOCATION        -> Manifest.permission.ACCESS_COARSE_LOCATION
        AppOpsManager.OPSTR_READ_CONTACTS          -> Manifest.permission.READ_CONTACTS
        AppOpsManager.OPSTR_READ_CALL_LOG          -> Manifest.permission.READ_CALL_LOG
        AppOpsManager.OPSTR_READ_SMS               -> Manifest.permission.READ_SMS
        AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS -> Manifest.permission.PROCESS_OUTGOING_CALLS
        else                                       -> null
    }
}

data class MonitoredOp(
    val opStr: String,
    val permissionType: String,
    val displayName: String
)

data class OpAccessRecord(
    val packageName: String,
    val opStr: String,
    val permissionType: String,
    val permissionDisplayName: String,
    val lastAccessTimeMs: Long,
    val durationMs: Long,
    val isBackground: Boolean,
    val accessCount: Int
)
