package io.privacydroid.domain.usecase

import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import io.privacydroid.data.local.dao.BlockedDomainDao
import io.privacydroid.data.local.dao.CorrelationResultDao
import io.privacydroid.data.local.dao.PermissionLogDao
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.model.PermissionType
import io.privacydroid.data.source.AppInventoryScanner
import io.privacydroid.data.source.NetworkUsageTracker
import io.privacydroid.domain.model.AppInfo
import io.privacydroid.domain.model.AppRiskLevel
import io.privacydroid.domain.model.TrackerCategory
import io.privacydroid.util.AppInfoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── Yapısal rapor verileri ────────────────────────────────────────────────────

data class PermissionUsageLine(
    val label: String,
    val total: Int,
    val background: Int
)

data class TrackerLine(
    val domain: String,
    val categoryLabel: String
)

/** Tek bir uygulamanın rapor verisi (Modül 1 & 3). */
data class AppReportData(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val riskScore: Int,
    val permissions30d: List<PermissionUsageLine>,
    val totalTx7d: Long,
    val trackerCount7d: Int,
    val trackers: List<TrackerLine>,
    val suspicious: List<String>,
    val sdks: List<String>
)

data class RiskyAppLine(
    val appName: String,
    val riskLabel: String
)

/** Cihaz geneli rapor verisi (Modül 4). */
data class DeviceReportData(
    val totalApps: Int,
    val highRisk: Int,
    val mediumRisk: Int,
    val lowRisk: Int,
    val topRisky: List<RiskyAppLine>,
    val totalTrackerConnections: Int,
    val topTrackerDomain: String?,
    val topTrackerCount: Int,
    val adsBlocked: Int,
    val trackersBlocked: Int,
    val nightAccessAppCount: Int,
    val recommendations: List<String>
)

/**
 * Faz 3 Rapor Motoru. Cihazdaki verilerden hem tek uygulama hem de cihaz geneli
 * metin raporları üretir. Hiçbir veri cihaz dışına çıkmaz — yalnızca yerel DB ve
 * PackageManager okunur.
 */
@Singleton
class ReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionLogDao: PermissionLogDao,
    private val trackerConnectionDao: TrackerConnectionDao,
    private val blockedDomainDao: BlockedDomainDao,
    private val correlationDao: CorrelationResultDao,
    private val networkUsageTracker: NetworkUsageTracker,
    private val appInfoHelper: AppInfoHelper,
    private val scanner: AppInventoryScanner
) {

    companion object {
        private const val APP_NAME = "PrivacyDroid"
        private const val APP_VERSION = "0.1.0-alpha"
    }

    // ── Veri toplama: tek uygulama ───────────────────────────────────────────

    suspend fun gatherAppReport(
        packageName: String,
        appName: String,
        riskScore: Int
    ): AppReportData = withContext(Dispatchers.IO) {
        val since30d = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        val since7d = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)

        val breakdown = permissionLogDao.getPermissionBreakdownForApp(packageName, since30d)
            .map { row ->
                PermissionUsageLine(
                    label = PermissionType.fromOpStr(row.permission_type).displayName,
                    total = row.total,
                    background = row.background
                )
            }

        val uid = appInfoHelper.getUid(packageName)
        val net = if (uid >= 0) networkUsageTracker.getStats(uid) else null

        val trackerSummary = trackerConnectionDao.getDomainSummaryForApp(packageName, since7d)
        val trackers = trackerSummary.map {
            TrackerLine(
                domain = it.domain,
                categoryLabel = TrackerCategory.fromString(it.category).displayName
            )
        }

        val suspicious = buildList {
            val nightCount = permissionLogDao.getNightBackgroundCount(packageName, since30d)
            if (nightCount > 0) {
                add("Gece saatlerinde (00:00–06:00) arka planda $nightCount kez sensöre erişti")
            }
            correlationDao.getHighSuspicionForAppOnce(packageName, since30d).forEach { c ->
                val time = SimpleDateFormat("dd MMM HH:mm", Locale("tr")).format(Date(c.accessStartMs))
                val type = if (c.accessType == "CAMERA") "kamera" else "mikrofon"
                if (c.newMediaCreated) {
                    add("$time — $type erişimi sırasında yeni medya oluşturuldu")
                } else if (c.networkBytesSent > 0) {
                    add("$time — $type erişiminden sonra ${formatBytes(c.networkBytesSent)} veri gönderildi")
                }
            }
            net?.anomalyReason?.let { add("Ağ anomalisi: $it") }
        }

        val sdks = scanner.scanDeep(packageName)?.detectedSdks?.map { it.name } ?: emptyList()

        AppReportData(
            packageName = packageName,
            appName = appName,
            versionName = appInfoHelper.getVersionName(packageName),
            riskScore = riskScore,
            permissions30d = breakdown,
            totalTx7d = net?.txBytes7d ?: 0L,
            trackerCount7d = trackerSummary.size,
            trackers = trackers,
            suspicious = suspicious,
            sdks = sdks
        )
    }

    // ── Veri toplama: cihaz geneli ───────────────────────────────────────────

    suspend fun gatherDeviceReport(): DeviceReportData = withContext(Dispatchers.IO) {
        val since7d = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val todayStart = todayStartMs()

        val apps = scanner.scanAllApps(includeSystem = false)
        val high = apps.count { it.riskLevel == AppRiskLevel.HIGH }
        val medium = apps.count { it.riskLevel == AppRiskLevel.MEDIUM }
        val low = apps.count { it.riskLevel == AppRiskLevel.LOW }

        val topRisky = apps
            .sortedByDescending { it.riskLevel.ordinal }
            .take(5)
            .map { RiskyAppLine(it.appName, it.riskLevel.label) }

        val totalTrackers = trackerConnectionDao.countSince(since7d)
        val topDomain = trackerConnectionDao.topDomainsSince(since7d, limit = 1).firstOrNull()

        val adsBlocked = blockedDomainDao.countByTypeSinceOnce("AD", todayStart)
        val trackersBlocked = blockedDomainDao.countByTypeSinceOnce("TRACKER", todayStart)
        val nightApps = permissionLogDao.getDistinctNightBackgroundAppCount(since7d)

        DeviceReportData(
            totalApps = apps.size,
            highRisk = high,
            mediumRisk = medium,
            lowRisk = low,
            topRisky = topRisky,
            totalTrackerConnections = totalTrackers,
            topTrackerDomain = topDomain?.domain,
            topTrackerCount = topDomain?.connectionCount ?: 0,
            adsBlocked = adsBlocked,
            trackersBlocked = trackersBlocked,
            nightAccessAppCount = nightApps,
            recommendations = buildRecommendations(apps)
        )
    }

    // ── Metin üretimi (Modül 1) ──────────────────────────────────────────────

    fun buildAppReportText(data: AppReportData): String = buildString {
        appendLine("=== $APP_NAME Gizlilik Raporu ===")
        appendLine("Tarih: ${todayStr()}")
        appendLine("Uygulama: ${data.appName} (${data.packageName})")
        appendLine("Versiyon: ${data.versionName}")
        appendLine("Risk Skoru: ${data.riskScore}/100 ${riskEmoji(data.riskScore)} ${riskLabel(data.riskScore)}")
        appendLine()
        appendLine("📊 İZİN ERİŞİMİ (Son 30 gün):")
        if (data.permissions30d.isEmpty()) {
            appendLine("- Kayıt yok")
        } else {
            data.permissions30d.forEach { p ->
                appendLine("- ${p.label}: ${p.total} kez (${p.background}'i arka planda)")
            }
        }
        appendLine()
        appendLine("🌐 AĞ AKTİVİTESİ (Son 7 gün):")
        appendLine("- Toplam gönderim: ${formatBytes(data.totalTx7d)}")
        appendLine("- Bağlandığı tracker'lar: ${data.trackerCount7d}")
        appendLine()
        appendLine("📡 TESPİT EDİLEN TRACKER'LAR:")
        if (data.trackers.isEmpty()) {
            appendLine("- Tespit edilmedi")
        } else {
            data.trackers.forEach { t -> appendLine("- ${t.domain} (${t.categoryLabel})") }
        }
        appendLine()
        appendLine("⚠️ ŞÜPHELİ AKTİVİTELER:")
        if (data.suspicious.isEmpty()) {
            appendLine("- Şüpheli aktivite tespit edilmedi")
        } else {
            data.suspicious.forEach { s -> appendLine("- $s") }
        }
        appendLine()
        appendLine("🔧 GÖMÜLÜ BİLEŞENLER:")
        if (data.sdks.isEmpty()) {
            appendLine("- Bilinen SDK tespit edilmedi")
        } else {
            appendLine("- ${data.sdks.joinToString(", ")}")
        }
        appendLine()
        appendLine("---")
        appendLine("$APP_NAME v$APP_VERSION ile oluşturuldu")
        appendLine("io.privacydroid | GPL-3.0")
    }

    // ── KVKK başvuru şablonu (Modül 3) ───────────────────────────────────────

    fun buildKvkkTemplate(data: AppReportData): String = buildString {
        appendLine("Kişisel Verilerin Korunması Kanunu Kapsamında Başvuru")
        appendLine()
        appendLine("${todayStr()}")
        appendLine()
        appendLine("${data.appName} Genel Müdürlüğü'ne,")
        appendLine()
        appendLine(
            "${data.appName} mobil uygulamanız, sunduğunuz hizmet için zorunlu " +
            "olmayan aşağıdaki kişisel veri işlemlerini gerçekleştirmektedir:"
        )
        appendLine()

        var idx = 1
        val notable = data.permissions30d.filter { it.total > 0 }
        if (notable.isEmpty()) {
            appendLine("(Bu uygulama için izlenen izin erişimi kaydı bulunmamaktadır.)")
            appendLine()
        } else {
            notable.forEach { p ->
                val pct = if (p.total > 0) (p.background * 100 / p.total) else 0
                appendLine("${idx}. ${p.label.uppercase(Locale("tr"))} ERİŞİMİ:")
                appendLine(
                    "Uygulamanız son 30 gün içinde ${p.label.lowercase(Locale("tr"))} iznime " +
                    "${p.total} kez erişmiştir."
                )
                if (p.background > 0) {
                    appendLine(
                        "Bu erişimlerin ${p.background}'i (%$pct) uygulama arka plandayken " +
                        "gerçekleşmiştir."
                    )
                }
                appendLine("[Uygulama işlevi için bu erişim zorunlu değildir.]")
                appendLine()
                idx++
            }
        }

        appendLine("Bu kapsamda KVKK m.11 çerçevesinde:")
        appendLine("a) Hangi kişisel verilerimin işlendiğini,")
        appendLine("b) İşleme amacını ve hukuki dayanağını,")
        appendLine("c) Aktarılan üçüncü tarafları")
        appendLine("öğrenmek istiyorum.")
        appendLine()
        appendLine("Ek: $APP_NAME uygulama raporu (kanıt niteliğinde)")
        appendLine()
        appendLine("Ad Soyad: ___________")
        appendLine("TC Kimlik No: ___________")
        appendLine("İletişim: ___________")
    }

    // ── Cihaz geneli rapor metni (Modül 4) ───────────────────────────────────

    fun buildDeviceReportText(data: DeviceReportData): String = buildString {
        appendLine("=== CİHAZ GİZLİLİK RAPORU ===")
        appendLine("Tarih: ${todayStr()}")
        appendLine("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
        appendLine()
        appendLine("📱 UYGULAMA ÖZETİ:")
        appendLine("Toplam uygulama: ${data.totalApps}")
        appendLine("🔴 Yüksek riskli: ${data.highRisk}")
        appendLine("🟡 Orta riskli: ${data.mediumRisk}")
        appendLine("🟢 Düşük riskli: ${data.lowRisk}")
        appendLine()
        appendLine("🏆 EN RİSKLİ UYGULAMALAR:")
        if (data.topRisky.isEmpty()) {
            appendLine("- Veri yok")
        } else {
            data.topRisky.forEachIndexed { i, app ->
                appendLine("${i + 1}. ${app.appName} — ${app.riskLabel}")
            }
        }
        appendLine()
        appendLine("📡 TRACKER İSTATİSTİKLERİ:")
        appendLine("Toplam tracker bağlantısı: ${data.totalTrackerConnections}")
        if (data.topTrackerDomain != null) {
            appendLine("En çok bağlanan: ${data.topTrackerDomain} (${data.topTrackerCount} kez)")
        }
        appendLine("Engellenen reklam: ${data.adsBlocked}")
        appendLine("Engellenen tracker: ${data.trackersBlocked}")
        appendLine()
        appendLine("⚠️ DİKKAT GEREKTİREN DURUMLAR:")
        if (data.nightAccessAppCount > 0) {
            appendLine(
                "- ${data.nightAccessAppCount} uygulama gece saatlerinde arka planda sensöre erişti"
            )
        } else {
            appendLine("- Belirgin bir durum tespit edilmedi")
        }
        appendLine()
        appendLine("🛡️ ÖNERİLER:")
        if (data.recommendations.isEmpty()) {
            appendLine("- Şu an için ek öneri yok")
        } else {
            data.recommendations.forEach { r -> appendLine("- $r") }
        }
        appendLine()
        appendLine("---")
        appendLine("$APP_NAME v$APP_VERSION ile oluşturuldu")
        appendLine("io.privacydroid | GPL-3.0")
    }

    // ── Yardımcılar ──────────────────────────────────────────────────────────

    private fun buildRecommendations(apps: List<AppInfo>): List<String> =
        apps.filter { it.riskLevel == AppRiskLevel.HIGH }
            .take(5)
            .map { "${it.appName} için tehlikeli izinleri gözden geçirin veya uygulamayı kaldırın" }

    private fun todayStartMs(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun todayStr(): String =
        SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Date())

    private fun riskLabel(score: Int) = when {
        score <= 30 -> "Düşük Risk"
        score <= 60 -> "Orta Risk"
        else -> "Yüksek Risk"
    }

    private fun riskEmoji(score: Int) = when {
        score <= 30 -> "🟢"
        score <= 60 -> "🟡"
        else -> "🔴"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_048_576 -> "${"%.1f".format(bytes / 1024.0)} KB"
        else -> "${"%.1f".format(bytes / 1_048_576.0)} MB"
    }
}
