package io.privacydroid.data.source

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.domain.model.AppCategory
import io.privacydroid.domain.model.AppInfo
import io.privacydroid.domain.model.AppRiskLevel
import io.privacydroid.domain.model.BackgroundBehavior
import io.privacydroid.domain.model.KNOWN_SDK_SIGNATURES
import io.privacydroid.domain.model.detectAppCategory
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PackageManager üzerinden cihazda kurulu uygulamaları tarar.
 *
 * Root gerektirmez. Tüm bilgiler public PackageManager API'si üzerinden alınır.
 *
 * Risk kural motoru:
 *   - Uygulama adından kategori tahmin edilir (fener, hesap makinesi, oyun…)
 *   - İstenen tehlikeli izinler kategori beklentisiyle karşılaştırılır
 *   - Uyumsuzluk → risk seviyesi artar
 */
@Singleton
class AppInventoryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sdkDetector: SdkDetector,
    private val networkUsageTracker: NetworkUsageTracker
) {

    private val pm = context.packageManager

    companion object {
        private val DANGEROUS_PERMISSIONS = setOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
        )

        // Kategori tespiti için uygulama adı alt dizeleri
        private val CATEGORY_FLASHLIGHT = listOf("fener", "flashlight", "torch", "lamp", "el feneri")
        private val CATEGORY_CALCULATOR  = listOf("hesap", "calculator", "calc", "hesap makinesi")
        private val CATEGORY_GAME        = listOf("game", "oyun", "puzzle", "quiz", "run", "race", "shooter")
        private val CATEGORY_WEATHER     = listOf("hava", "weather", "forecast", "meteo")
        private val CATEGORY_CLOCK       = listOf("saat", "clock", "alarm", "timer", "stopwatch")

        // İzinler bu kategoriler için şüpheli
        private val FLASHLIGHT_RISKY_PERMS = setOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_SMS, Manifest.permission.ACCESS_FINE_LOCATION
        )
        private val CALCULATOR_RISKY_PERMS = setOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
        )
        private val GAME_RISKY_PERMS = setOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.SEND_SMS
        )
        private val WEATHER_RISKY_PERMS = setOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.READ_SMS,
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO
        )
    }

    /**
     * Cihazdaki tüm uygulamaları tarar.
     *
     * Android 11+ (API 30): QUERY_ALL_PACKAGES olmadan bu metot yalnızca ~5 uygulama döner.
     * Manifest'te bildirilen izinle tam liste alınır.
     *
     * Kullanıcı uygulama tespiti:
     *   - FLAG_SYSTEM == 0           → kesinlikle kullanıcı uygulaması
     *   - FLAG_UPDATED_SYSTEM_APP != 0 → OEM pre-install ama kullanıcı güncelledi
     *     (Samsung/Xiaomi cihazlarda Facebook, WhatsApp bu kategoride olabilir)
     */
    fun scanAllApps(includeSystem: Boolean = false): List<AppInfo> {
        // getInstalledPackages: tek çağrıda izinler + metaData — getInstalledApplications'dan daha verimli
        val packages = pm.getInstalledPackages(
            PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
        )

        val filtered = if (includeSystem) {
            packages
        } else {
            packages.filter { pkg ->
                val flags = pkg.applicationInfo?.flags ?: 0
                val isBaseSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                // Hiç system flag yoksa → kullanıcı yüklemesi
                // System flag var ama güncellenmiş → göster (pre-install ama kullanıcı güncelledi)
                !isBaseSystemApp || isUpdatedSystemApp
            }
        }

        Timber.d("Taranacak uygulama sayısı: ${filtered.size} (toplam: ${packages.size}, sistem dahil: $includeSystem)")

        return filtered.mapNotNull { pkgInfo ->
            try {
                buildAppInfoFromPackage(pkgInfo)
            } catch (e: Exception) {
                Timber.w("Uygulama tarama hatası [${pkgInfo.packageName}]: ${e.message}")
                null
            }
        }.sortedWith(
            compareByDescending<AppInfo> { it.riskLevel.ordinal }
                .thenByDescending { it.lastUpdateMs }
        )
    }

    /** Tek uygulama için derin tarama (detay görünümü). */
    fun scanDeep(packageName: String): AppInfo? {
        return try {
            val pkgInfo = pm.getPackageInfo(
                packageName,
                PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
            )
            val basic = buildAppInfoFromPackage(pkgInfo)
            val deepSdks = sdkDetector.detectDeep(packageName)
            basic?.copy(detectedSdks = deepSdks)
        } catch (e: Exception) {
            Timber.w("Derin tarama hatası [$packageName]: ${e.message}")
            null
        }
    }

    /**
     * getInstalledPackages()'tan gelen PackageInfo üzerinden AppInfo inşa eder.
     * Artık ek getPackageInfo() çağrısı gerekmez — izinler zaten pakette mevcut.
     */
    private fun buildAppInfoFromPackage(pkgInfo: android.content.pm.PackageInfo): AppInfo? {
        val app = pkgInfo.applicationInfo ?: return null

        val allPerms = pkgInfo.requestedPermissions?.toList() ?: emptyList()
        val dangerousPerms = allPerms.filter { it in DANGEROUS_PERMISSIONS }

        val bgBehavior = BackgroundBehavior(
            hasBootReceiver  = Manifest.permission.RECEIVE_BOOT_COMPLETED in allPerms,
            hasWakeLock      = Manifest.permission.WAKE_LOCK in allPerms,
            hasDozeypass     = "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in allPerms,
            hasExactAlarm    = "android.permission.USE_EXACT_ALARM" in allPerms ||
                    "android.permission.SCHEDULE_EXACT_ALARM" in allPerms,
            hasForegroundService = Manifest.permission.FOREGROUND_SERVICE in allPerms
        )

        val sdks = sdkDetector.detectFromMetaData(app.packageName)
        val netStats = networkUsageTracker.getStats(app.uid)

        val appNameLower = pm.getApplicationLabel(app).toString().lowercase()
        val (riskLevel, riskReasons) = calculateRisk(
            packageName = app.packageName,
            appName = appNameLower,
            dangerousPerms = dangerousPerms,
            bgBehavior = bgBehavior,
            sdks = sdks,
            netStats = netStats
        )

        return AppInfo(
            packageName        = app.packageName,
            appName            = pm.getApplicationLabel(app).toString(),
            versionName        = pkgInfo.versionName ?: "—",
            installTimeMs      = pkgInfo.firstInstallTime,
            lastUpdateMs       = pkgInfo.lastUpdateTime,
            isSystemApp        = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            requestedPermissions = allPerms,
            dangerousPermissions = dangerousPerms,
            backgroundBehavior = bgBehavior,
            riskLevel          = riskLevel,
            riskReasons        = riskReasons,
            detectedSdks       = sdks,
            networkStats       = netStats
        )
    }

    private fun calculateRisk(
        packageName: String,
        appName: String,
        dangerousPerms: List<String>,
        bgBehavior: BackgroundBehavior,
        sdks: List<io.privacydroid.domain.model.DetectedSdk>,
        netStats: io.privacydroid.domain.model.AppNetworkStats
    ): Pair<AppRiskLevel, List<String>> {
        val reasons = mutableListOf<String>()
        val category = detectAppCategory(packageName, appName)

        // Kategori + izin uyumsuzluğu — ortak detectAppCategory kullanılıyor
        val riskyPerms = mutableSetOf<String>()
        when (category) {
            AppCategory.FLASHLIGHT ->
                riskyPerms += dangerousPerms.filter { it in FLASHLIGHT_RISKY_PERMS }
            AppCategory.CALCULATOR ->
                riskyPerms += dangerousPerms.filter { it in CALCULATOR_RISKY_PERMS }
            AppCategory.GAME ->
                riskyPerms += dangerousPerms.filter { it in GAME_RISKY_PERMS }
            AppCategory.WEATHER ->
                riskyPerms += dangerousPerms.filter { it in WEATHER_RISKY_PERMS }
            AppCategory.SOCIAL_MEDIA ->
                riskyPerms += dangerousPerms.filter { perm ->
                    val key = perm.substringAfterLast(".")
                    key in setOf("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT", "READ_SMS",
                        "SEND_SMS", "READ_PHONE_STATE", "GET_ACCOUNTS")
                }
            else -> Unit
        }
        if (riskyPerms.isNotEmpty()) {
            reasons += "Kategoriyle uyumsuz izinler: ${riskyPerms.size} adet"
        }

        // Arka plan davranışı
        if (bgBehavior.suspiciousCount >= 2) {
            reasons += bgBehavior.label
        } else if (bgBehavior.hasBootReceiver) {
            reasons += "Cihaz açılışında otomatik başlıyor"
        }

        // Yüksek riskli SDK
        val highRiskSdks = sdks.filter { it.riskLevel == AppRiskLevel.HIGH }
        if (highRiskSdks.isNotEmpty()) {
            reasons += "${highRiskSdks.size} yüksek riskli takip SDK'sı"
        }

        // Ağ anomalisi
        if (netStats.isAnomaly) {
            reasons += netStats.anomalyReason ?: "Anormal ağ trafiği"
        }

        // 5+ tehlikeli izin
        if (dangerousPerms.size >= 5) {
            reasons += "${dangerousPerms.size} tehlikeli izin isteniyor"
        }

        val level = when {
            reasons.size >= 2 || riskyPerms.isNotEmpty() -> AppRiskLevel.HIGH
            reasons.size == 1 || dangerousPerms.size >= 3 -> AppRiskLevel.MEDIUM
            else -> AppRiskLevel.LOW
        }
        return Pair(level, reasons)
    }
}
