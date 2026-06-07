package io.privacydroid.worker

import android.content.Context
import android.net.TrafficStats
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.privacydroid.service.NetworkMonitorService
import io.privacydroid.util.NotificationHelper
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * İnternet bağlantısından 60 saniye sonra çalışır.
 *
 * [NetworkMonitorService] tarafından onAvailable() tetiklenince planlanır.
 * Bağlantı anındaki TrafficStats baseline ile karşılaştırır.
 * Arka planda >1 MB gönderen uygulamalar için bildirim gönderir.
 *
 * Root gerektirmez — TrafficStats API 8+ çalışır.
 * Her hata sessizce loglanır, diğer uygulamaların ölçümünü etkilemez.
 */
@HiltWorker
class NetworkCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    companion object {
        private const val ANOMALY_THRESHOLD_BYTES = 1L * 1024 * 1024  // 1 MB
        private const val BASELINE_MAX_AGE_MS     = 5 * 60 * 1000L    // Baseline 5 dk'dan eskiyse atla

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<NetworkCheckWorker>()
                .setInitialDelay(60, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            measureNetworkDelta()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "NetworkCheckWorker: genel hata")
            Result.failure()
        }
    }

    private fun measureNetworkDelta() {
        val prefs = context.getSharedPreferences(
            NetworkMonitorService.BASELINE_PREFS,
            Context.MODE_PRIVATE
        )

        val baselineTs = prefs.getLong(NetworkMonitorService.KEY_BASELINE_TIMESTAMP, 0L)
        if (baselineTs == 0L) {
            Timber.d("NetworkCheckWorker: baseline yok, atlanıyor")
            return
        }

        val age = System.currentTimeMillis() - baselineTs
        if (age > BASELINE_MAX_AGE_MS) {
            Timber.d("NetworkCheckWorker: baseline çok eski (${age / 1000}s), atlanıyor")
            return
        }

        context.packageManager.getInstalledApplications(0).forEach { appInfo ->
            try {
                val uid = appInfo.uid
                val baselineTx = prefs.getLong("uid_${uid}_tx", -1L)
                if (baselineTx == -1L) return@forEach

                val currentTx = TrafficStats.getUidTxBytes(uid)
                if (currentTx == TrafficStats.UNSUPPORTED.toLong()) return@forEach

                val delta = (currentTx - baselineTx).coerceAtLeast(0L)
                if (delta > ANOMALY_THRESHOLD_BYTES) {
                    val appName = runCatching {
                        context.packageManager.getApplicationLabel(
                            context.packageManager.getApplicationInfo(appInfo.packageName, 0)
                        ).toString()
                    }.getOrDefault(appInfo.packageName)

                    val mb = "%.1f".format(delta / 1_048_576.0)
                    Timber.w("NetworkCheckWorker: $appName internet açılınca $mb MB gönderdi")
                    notificationHelper.sendNetworkAnomalyNotification(appName, mb)
                }
            } catch (e: Exception) {
                // Tek UID hatası diğerlerini etkilemesin
                Timber.v("NetworkCheckWorker: UID ${appInfo.uid} atlandı")
            }
        }
    }
}
