package io.privacydroid.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.privacydroid.data.repository.CrashLogRepository
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.domain.usecase.TrackerDetector
import io.privacydroid.domain.usecase.TrackerScanResult
import io.privacydroid.util.NotificationHelper
import timber.log.Timber

/**
 * Manuel tracker taraması için bağımsız tek seferlik worker.
 * PermissionScanWorker'dan bağımsız çalışır — sadece TrackerDetector.scan() çağırır.
 *
 * Hata stratejisi:
 *   - TrackerDetector.scan() yöntem zinciri (proc/net → TrafficStats) dener.
 *   - scan() exception fırlatırsa: tam stack trace crashLogRepository'ye yazılır
 *     ve Result.failure() döner (retry yapılmaz).
 *   - scan() [TrackerScanResult.Restricted] dönerse: kullanıcıya "kısıtlı" mesajı
 *     NotificationLog'a yazılır ve Result.success() döner — bu bir hata değil,
 *     cihaz kısıtlamasıdır.
 */
@HiltWorker
class TrackerScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val trackerDetector: TrackerDetector,
    private val crashLogRepository: CrashLogRepository,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "tracker_scan_manual"
    }

    override suspend fun doWork(): Result {
        Timber.d("TrackerScanWorker başlatıldı")
        return try {
            when (val result = trackerDetector.scan()) {
                is TrackerScanResult.Completed -> {
                    // Başarılı tarama — son tracker tarama zamanını kaydet ki
                    // Ayarlar'daki "Son tarama" etiketi güncellensin.
                    settingsRepository.saveLastTrackerScanMs()
                    Timber.d(
                        "TrackerScanWorker tamamlandı: ${result.found} tracker " +
                                "(yöntem=${result.source})"
                    )
                    Result.success()
                }
                is TrackerScanResult.Restricted -> {
                    // Hata değil — cihaz kısıtlaması. Kullanıcıyı bilgilendir, başarı dön.
                    Timber.w("TrackerScanWorker: tracker tespiti bu cihazda kısıtlı")
                    notificationHelper.logTrackerDetectionRestricted()
                    Result.success()
                }
            }
        } catch (e: Exception) {
            // Tam stack trace'i crash log dosyasına yaz — teşhis için
            crashLogRepository.writeNonFatal("tracker_scan", e)
            Timber.e(e, "TrackerScanWorker başarısız: ${e.message}")
            Result.failure()
        }
    }
}
