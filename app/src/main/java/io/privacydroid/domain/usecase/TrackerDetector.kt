package io.privacydroid.domain.usecase

import android.content.Context
import android.net.TrafficStats
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.data.local.dao.TrackerConnectionDao
import io.privacydroid.data.local.entity.TrackerConnectionEntity
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.data.source.ProcNetReader
import io.privacydroid.data.source.SdkDetector
import io.privacydroid.domain.model.classifyTrackerDomain
import io.privacydroid.util.AppInfoHelper
import io.privacydroid.util.NotificationHelper
import timber.log.Timber
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracker tarama sonucu — worker'ın doğru aksiyonu alması için.
 */
sealed interface TrackerScanResult {
    /**
     * Tarama tamamlandı. [found] kaydedilen tracker bağlantısı sayısı,
     * [source] kullanılan yöntem ("proc_net", "traffic_stats", "disabled").
     */
    data class Completed(val found: Int, val source: String) : TrackerScanResult

    /** Hiçbir yöntem çalışmadı — tracker tespiti bu cihazda kısıtlı. */
    data object Restricted : TrackerScanResult
}

@Singleton
class TrackerDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val procNetReader: ProcNetReader,
    private val appInfoHelper: AppInfoHelper,
    private val notificationHelper: NotificationHelper,
    private val trackerConnectionDao: TrackerConnectionDao,
    private val settingsRepository: SettingsRepository,
    private val sdkDetector: SdkDetector
) {

    companion object {
        // TrafficStats fallback: bir uygulamanın "veri gönderdi" sayılması için
        // önceki snapshot'tan bu yana göndermesi gereken minimum bayt.
        private const val TX_DELTA_THRESHOLD_BYTES = 50L * 1024  // 50 KB
        // Önceki snapshot bu süreden eskiyse delta "güncel aktivite" sayılmaz.
        private val MAX_SNAPSHOT_AGE_MS = TimeUnit.MINUTES.toMillis(60)
        private const val TX_SNAPSHOT_PREFS = "tracker_tx_snapshots"
    }

    private val trackerDomains: Set<String> by lazy { loadTrackerDomains() }
    private val ipDomainCache = mutableMapOf<String, String?>()

    private val txPrefs by lazy {
        context.getSharedPreferences(TX_SNAPSHOT_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * Aktif bağlantıları tarar ve bilinen tracker'larla karşılaştırır.
     *
     * Yöntem zinciri (sırayla):
     *   1. /proc/net/tcp + /proc/net/tcp6 — UID + uzak IP, reverse DNS ile domain.
     *   2. TrafficStats fallback — proc/net erişilemezse uygulamaların gönderdiği
     *      veriyi ölçüp gömülü tracker SDK'larıyla korele eder.
     *   3. Her ikisi de başarısızsa [TrackerScanResult.Restricted] döner.
     *
     * Tüm exception'lar yakalanır; tarama hatası uygulamayı çökertmez.
     */
    suspend fun scan(): TrackerScanResult {
        if (!settingsRepository.isTrackerMonitoringEnabled()) {
            Timber.d("TrackerDetector: izleme devre dışı, atlandı")
            return TrackerScanResult.Completed(found = 0, source = "disabled")
        }

        // ── Yöntem 1: /proc/net/tcp + tcp6 ──────────────────────────────────
        val connections = try {
            procNetReader.readActiveConnections()
        } catch (e: SecurityException) {
            Timber.w("TrackerDetector: /proc/net okuma izni yok (Android 10+): ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Timber.w("TrackerDetector: /proc/net okunamadı: ${e.message}")
            emptyList()
        }

        if (connections.isNotEmpty()) {
            Timber.d("TrackerDetector: ${connections.size} aktif bağlantı taranıyor (proc/net)")
            val saved = processConnections(connections)
            cleanupOldConnections()
            return TrackerScanResult.Completed(found = saved, source = "proc_net")
        }

        // ── Yöntem 2: TrafficStats fallback ─────────────────────────────────
        Timber.d("TrackerDetector: proc/net boş/erişilemez — TrafficStats fallback deneniyor")
        val fallbackCount = scanViaTrafficStats()
        if (fallbackCount != null) {
            cleanupOldConnections()
            return TrackerScanResult.Completed(found = fallbackCount, source = "traffic_stats")
        }

        // ── Yöntem 3: Her ikisi de başarısız ────────────────────────────────
        Timber.w("TrackerDetector: hiçbir tarama yöntemi çalışmadı — kısıtlı")
        return TrackerScanResult.Restricted
    }

    /**
     * proc/net bağlantılarını işler: reverse DNS ile tracker domain'e çözer,
     * eşleşenleri Room'a kaydeder ve bildirim gönderir. Kaydedilen sayıyı döner.
     */
    private suspend fun processConnections(connections: List<io.privacydroid.data.source.ProcNetEntry>): Int {
        val toSave = mutableListOf<TrackerConnectionEntity>()

        for (entry in connections) {
            try {
                val packages = procNetReader.packagesForUid(entry.uid)
                if (packages.isEmpty()) continue

                val domain = resolveToTrackerDomain(entry.remoteIp) ?: continue
                val mainPackage = packages.first()
                val appName = appInfoHelper.getAppName(mainPackage)
                val category = classifyTrackerDomain(domain)

                Timber.i("Tracker bağlantısı: $mainPackage → $domain (${category.displayName})")

                toSave.add(
                    TrackerConnectionEntity(
                        packageName = mainPackage,
                        appName = appName,
                        domain = domain,
                        category = category.name,
                        bytesSent = 0L
                    )
                )

                notificationHelper.sendTrackerConnectionNotification(
                    appName = appName,
                    trackerDomain = domain,
                    estimatedBytes = 0L,
                    packageName = mainPackage
                )
            } catch (e: Exception) {
                Timber.w("TrackerDetector: bağlantı işlenemedi ${entry.remoteIp}: ${e.message}")
            }
        }

        if (toSave.isNotEmpty()) {
            trackerConnectionDao.insertAll(toSave)
        }
        return toSave.size
    }

    /**
     * TrafficStats tabanlı fallback.
     *
     * proc/net okunamadığında uzak IP/domain bilgisi alınamaz. Bunun yerine her
     * kurulu uygulamanın gönderdiği toplam baytı (TrafficStats.getUidTxBytes) okur,
     * önceki snapshot'a göre delta hesaplar ve son tarama aralığında (yaklaşık 15 dk)
     * gözle görülür veri gönderen uygulamaları belirler. Bu uygulamalar içinde gömülü
     * tracker SDK'sı (Facebook, AppsFlyer, AdMob vb.) bulunanları "tracker bağlantısı"
     * olarak kaydeder — SDK'nın bilinen kategorisiyle korele eder.
     *
     * Domain seviyesinde kesinlik VPN modunu gerektirir; bu yalnızca bir tahmindir.
     *
     * @return kaydedilen tracker sayısı, ya da TrafficStats hiç desteklenmiyorsa null
     *         (bu durumda çağıran [TrackerScanResult.Restricted] döndürmeli).
     */
    private suspend fun scanViaTrafficStats(): Int? {
        val installedApps = try {
            context.packageManager.getInstalledApplications(0)
        } catch (e: Exception) {
            Timber.w("TrackerDetector: kurulu uygulamalar alınamadı: ${e.message}")
            return null
        }

        val now = System.currentTimeMillis()
        var anyTrafficStatsSupported = false
        val toSave = mutableListOf<TrackerConnectionEntity>()
        val snapshotEditor = txPrefs.edit()

        for (appInfo in installedApps) {
            val uid = appInfo.uid
            val pkg = appInfo.packageName

            val txBytes = TrafficStats.getUidTxBytes(uid)
            if (txBytes == TrafficStats.UNSUPPORTED.toLong()) continue
            anyTrafficStatsSupported = true

            // Önceki snapshot ile delta hesapla
            val prev = txPrefs.getString("uid_$uid", null)
            snapshotEditor.putString("uid_$uid", "$txBytes,$now")

            val parts = prev?.split(",")
            val prevTx = parts?.getOrNull(0)?.toLongOrNull()
            val prevTs = parts?.getOrNull(1)?.toLongOrNull()
            if (prevTx == null || prevTs == null) continue  // ilk snapshot — bir sonraki taramada karşılaştırılır

            val ageMs = now - prevTs
            if (ageMs > MAX_SNAPSHOT_AGE_MS) continue  // çok eski — güncel aktivite sayılmaz
            val delta = txBytes - prevTx
            if (delta < TX_DELTA_THRESHOLD_BYTES) continue  // anlamlı veri gönderimi yok

            // Bu uygulama son aralıkta veri gönderdi — gömülü tracker SDK'sı var mı?
            val sdks = sdkDetector.detectFromMetaData(pkg)
            if (sdks.isEmpty()) continue

            val appName = appInfoHelper.getAppName(pkg)
            for (sdk in sdks) {
                val category = classifyTrackerDomain(sdk.name)
                toSave.add(
                    TrackerConnectionEntity(
                        packageName = pkg,
                        appName = appName,
                        // Domain yerine SDK adı — TrafficStats domain veremez
                        domain = "${sdk.name} (trafik analizi)",
                        category = category.name,
                        bytesSent = delta
                    )
                )
                Timber.i("TrafficStats tracker tahmini: $pkg → ${sdk.name} (${delta / 1024} KB)")
            }

            notificationHelper.sendTrackerConnectionNotification(
                appName = appName,
                trackerDomain = sdks.first().name,
                estimatedBytes = delta,
                packageName = pkg
            )
        }

        snapshotEditor.apply()

        if (!anyTrafficStatsSupported) {
            Timber.w("TrackerDetector: TrafficStats bu cihazda desteklenmiyor")
            return null
        }

        if (toSave.isNotEmpty()) {
            trackerConnectionDao.insertAll(toSave)
        }
        Timber.d("TrackerDetector: TrafficStats fallback ${toSave.size} tracker tahmini kaydetti")
        return toSave.size
    }

    private suspend fun cleanupOldConnections() {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        trackerConnectionDao.deleteOlderThan(cutoff)
    }

    private fun resolveToTrackerDomain(ip: String): String? {
        return ipDomainCache.getOrPut(ip) {
            try {
                val hostname = InetAddress.getByName(ip).canonicalHostName
                trackerDomains.firstOrNull { domain -> hostname.endsWith(domain) }
            } catch (_: Exception) {
                trackerDomains.firstOrNull { domain ->
                    try {
                        InetAddress.getAllByName(domain).any { it.hostAddress == ip }
                    } catch (_: Exception) { false }
                }
            }
        }
    }

    private fun loadTrackerDomains(): Set<String> {
        return try {
            context.assets.open("tracker_domains.txt").bufferedReader()
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.trim().lowercase() }
                .toSet()
        } catch (e: Exception) {
            Timber.w("tracker_domains.txt okunamadı: ${e.message}")
            emptySet()
        }
    }
}
