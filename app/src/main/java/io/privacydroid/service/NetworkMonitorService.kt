package io.privacydroid.service

import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import io.privacydroid.util.NotificationHelper
import io.privacydroid.worker.NetworkCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Ağ bağlantı durumunu izler.
 *
 * İnternet bağlandığında (onAvailable):
 *   1. Tüm uygulamaların anlık TrafficStats baseline'ını kaydet.
 *   2. CHANGE_WIFI_STATE iznine sahip ve son 10 dakikada aktif uygulamaları şüpheli işaretle.
 *   3. 60 saniye sonra NetworkCheckWorker ile delta ölçümü yap.
 *
 * İnternet kesildiğinde (onLost):
 *   Anlık snapshot'ı kaydet.
 *
 * Her hata sessizce loglanır, servis durmamalı.
 * Root gerektirmez — ConnectivityManager.NetworkCallback API 21+ çalışır.
 */
@AndroidEntryPoint
class NetworkMonitorService : Service() {

    companion object {
        const val ACTION_START = "io.privacydroid.action.START_NET_MONITOR"
        const val ACTION_STOP  = "io.privacydroid.action.STOP_NET_MONITOR"

        const val BASELINE_PREFS         = "net_baseline"
        const val KEY_BASELINE_TIMESTAMP = "baseline_ts"

        private const val WIFI_CHANGE_PERM      = "android.permission.CHANGE_WIFI_STATE"
        private const val RECENT_ACTIVE_WINDOW_MS = 10L * 60 * 1000  // 10 dakika

        fun start(context: Context) {
            context.startService(
                Intent(context, NetworkMonitorService::class.java)
                    .apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NetworkMonitorService::class.java)
                    .apply { action = ACTION_STOP }
            )
        }
    }

    @Inject lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val baselinePrefs: SharedPreferences by lazy {
        getSharedPreferences(BASELINE_PREFS, Context.MODE_PRIVATE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> if (networkCallback == null) registerNetworkCallback()
        }
        return START_STICKY
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    serviceScope.launch {
                        try { handleInternetAvailable() }
                        catch (e: Exception) { Timber.e(e, "onAvailable işlenemedi") }
                    }
                }

                override fun onLost(network: Network) {
                    serviceScope.launch {
                        try { saveDisconnectSnapshot() }
                        catch (e: Exception) { Timber.e(e, "onLost işlenemedi") }
                    }
                }
            }

            connectivityManager?.registerNetworkCallback(request, callback)
            networkCallback = callback
            Timber.d("NetworkMonitorService: ağ callback'i kaydedildi")

        } catch (e: Exception) {
            Timber.e(e, "NetworkMonitorService: callback kaydedilemedi")
        }
    }

    private fun handleInternetAvailable() {
        saveTrafficBaseline()
        checkSuspiciousWifiApps()
        try { NetworkCheckWorker.schedule(this) }
        catch (e: Exception) { Timber.e(e, "NetworkCheckWorker planlanamadı") }
        Timber.d("NetworkMonitorService: internet bağlandı, baseline kaydedildi")
    }

    /** Tüm uygulamaların anlık TX/RX byte değerlerini SharedPreferences'a yazar. */
    private fun saveTrafficBaseline() {
        try {
            val editor = baselinePrefs.edit()
            editor.putLong(KEY_BASELINE_TIMESTAMP, System.currentTimeMillis())
            packageManager.getInstalledApplications(0).forEach { appInfo ->
                try {
                    val uid = appInfo.uid
                    val tx = TrafficStats.getUidTxBytes(uid)
                    val rx = TrafficStats.getUidRxBytes(uid)
                    if (tx != TrafficStats.UNSUPPORTED.toLong()) {
                        editor.putLong("uid_${uid}_tx", tx)
                        editor.putLong("uid_${uid}_rx", rx)
                    }
                } catch (e: Exception) {
                    Timber.v("Baseline UID ${appInfo.uid} atlandı")
                }
            }
            editor.apply()
        } catch (e: Exception) {
            Timber.e(e, "TrafficStats baseline kaydedilemedi")
        }
    }

    /**
     * CHANGE_WIFI_STATE iznine sahip ve son 10 dakikada aktif olan
     * uygulamaları tespit eder, şüpheli bildirim gönderir.
     *
     * Filtreler (önce uygulanır):
     *   1. Bilinen sistem/donanım paket önekleri → tamamen atla
     *   2. ApplicationInfo.FLAG_SYSTEM → NotificationLog'a yaz, sistem bildirimi gönderme
     *   3. Kullanıcı uygulaması → sistem bildirimi gönder (24 saatte 1 kez)
     */
    private fun checkSuspiciousWifiApps() {
        try {
            val wifiApps = packageManager.getPackagesHoldingPermissions(
                arrayOf(WIFI_CHANGE_PERM), 0
            ) ?: return

            val tenMinAgo = System.currentTimeMillis() - RECENT_ACTIVE_WINDOW_MS
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val recentlyUsed = try {
                usm?.queryUsageStats(UsageStatsManager.INTERVAL_BEST, tenMinAgo, System.currentTimeMillis())
                    ?.map { it.packageName }?.toSet() ?: emptySet()
            } catch (e: Exception) { emptySet() }

            wifiApps.forEach { pkgInfo ->
                try {
                    val pkg = pkgInfo.packageName

                    // Bilinen sistem/donanım paketlerini tamamen atla
                    if (isSystemOrTrusted(pkg)) {
                        Timber.v("Güvenilir paket atlandı: $pkg")
                        return@forEach
                    }

                    val wasActive = pkg in recentlyUsed
                    if (!wasActive) return@forEach

                    val appInfo = runCatching { packageManager.getApplicationInfo(pkg, 0) }
                        .getOrNull() ?: return@forEach
                    val appName = packageManager.getApplicationLabel(appInfo).toString()

                    // FLAG_SYSTEM: sistem uygulaması ise sadece NotificationLog'a yaz
                    val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

                    Timber.w("Şüpheli Wi-Fi uygulaması: $appName (sistem=$isSystemApp)")
                    notificationHelper.sendSuspiciousWifiAppNotification(appName, pkg, isSystemApp)

                } catch (e: Exception) {
                    Timber.v("Uygulama kontrolü atlandı: ${pkgInfo.packageName}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Şüpheli Wi-Fi uygulama kontrolü başarısız")
        }
    }

    /**
     * Bilinen sistem, donanım sürücüsü veya güvenilir çerçeve paketlerini döner.
     * Bu paketler için hiçbir işlem yapılmaz.
     */
    private fun isSystemOrTrusted(packageName: String): Boolean {
        val trustedPrefixes = listOf(
            "android",
            "com.android.",
            "com.google.android.gms",
            "com.miui.",
            "com.xiaomi.",
            "com.qualcomm.",
            "vendor.qti.",
            "com.sec.",        // Samsung
            "com.samsung.",
            "com.huawei.",
            "com.oppo.",
            "com.realme."
        )
        return trustedPrefixes.any { packageName == it || packageName.startsWith(it) }
    }

    private fun saveDisconnectSnapshot() {
        try {
            baselinePrefs.edit()
                .putLong("disconnect_ts", System.currentTimeMillis())
                .apply()
            Timber.d("NetworkMonitorService: internet kesildi, snapshot kaydedildi")
        } catch (e: Exception) {
            Timber.e(e, "Disconnect snapshot kaydedilemedi")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Timber.e(e, "NetworkCallback kaldırılamadı")
        }
    }
}
