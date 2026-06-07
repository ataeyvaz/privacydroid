package io.privacydroid.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.domain.usecase.PermissionChangeDetector
import io.privacydroid.domain.usecase.PermissionScanUseCase
import io.privacydroid.domain.usecase.TrackerDetector
import io.privacydroid.util.NotificationHelper
import timber.log.Timber

/**
 * WorkManager üzerinden 15 dakikada bir çalışan izin tarama işçisi.
 *
 * Backoff: EXPONENTIAL, ilk retry 5 dk → maks 1 saat
 * Retry sayısı: [MAX_RETRIES]'ı geçince kalıcı başarısızlık döner.
 * SecurityException → izin iptal edilmiş, retry döngüsüne girilmez.
 * Pil uyarı eşiği: [SCAN_DURATION_WARN_MS] — sistem baskısına işaret eder.
 */
@HiltWorker
class PermissionScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val permissionScanUseCase: PermissionScanUseCase,
    private val notificationHelper: NotificationHelper,
    private val permissionChangeDetector: PermissionChangeDetector,
    private val trackerDetector: TrackerDetector,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "permission_scan_periodic"
        const val SCAN_INTERVAL_MINUTES = 15L
        const val LOG_RETENTION_DAYS = 30L
        private const val SCAN_DURATION_WARN_MS = 2_000L
        private const val MAX_RETRIES = 5
    }

    override suspend fun doWork(): Result {
        val startMs = System.currentTimeMillis()

        if (runAttemptCount > 0) {
            Timber.w(
                "PermissionScanWorker yeniden deneniyor " +
                        "(deneme ${runAttemptCount + 1}/$MAX_RETRIES)"
            )
        } else {
            Timber.d("PermissionScanWorker başlatıldı")
        }

        // Maksimum retry sayısını aştıysa kalıcı başarısızlık
        if (runAttemptCount >= MAX_RETRIES) {
            Timber.e(
                "PermissionScanWorker ${MAX_RETRIES} denemeden sonra başarısız oldu — " +
                        "kalıcı iptal edildi"
            )
            return Result.failure()
        }

        return permissionScanUseCase.execute().fold(
            onSuccess = { scanResult ->
                val durationMs = System.currentTimeMillis() - startMs
                logDuration(durationMs)

                if (runAttemptCount > 0) {
                    Timber.i(
                        "PermissionScanWorker ${runAttemptCount + 1}. denemede başarılı " +
                                "(${durationMs}ms)"
                    )
                } else {
                    Timber.d(
                        "Tarama tamamlandı — ${scanResult.savedLogCount} yeni log, " +
                                "${scanResult.suspiciousActivities.size} şüpheli, ${durationMs}ms"
                    )
                }

                if (scanResult.hasSuspiciousActivity) {
                    notificationHelper.sendSuspiciousActivityNotification(
                        scanResult.suspiciousActivities
                    )
                }
                // İzin değişikliklerini tespit et ve bildir
                permissionChangeDetector.detectAndNotify()
                // /proc/net üzerinden tracker bağlantısı tespiti
                trackerDetector.scan()
                settingsRepository.saveLastTrackerScanMs()
                settingsRepository.saveLastPermissionScanMs()
                Result.success()
            },
            onFailure = { error ->
                val durationMs = System.currentTimeMillis() - startMs

                if (error is SecurityException) {
                    // İzin iptal — retry yapmaya gerek yok
                    Timber.w(
                        "PACKAGE_USAGE_STATS izni iptal edilmiş — tarama durduruldu (${durationMs}ms)"
                    )
                    Result.failure()
                } else {
                    Timber.e(
                        error,
                        "Tarama başarısız (deneme ${runAttemptCount + 1}, ${durationMs}ms): " +
                                "${error::class.simpleName} — ${error.message}"
                    )
                    Result.retry()
                }
            }
        )
    }

    private fun logDuration(durationMs: Long) {
        if (durationMs > SCAN_DURATION_WARN_MS) {
            Timber.w(
                "Tarama süresi eşiği aştı: ${durationMs}ms > ${SCAN_DURATION_WARN_MS}ms — " +
                        "sistem baskısı veya fazla kurulu uygulama olabilir"
            )
        } else {
            Timber.d("Tarama süresi: ${durationMs}ms")
        }
    }
}
