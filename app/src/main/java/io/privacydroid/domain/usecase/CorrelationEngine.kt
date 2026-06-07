package io.privacydroid.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.data.model.PermissionLog
import io.privacydroid.data.model.PermissionType
import io.privacydroid.data.repository.CrashLogRepository
import io.privacydroid.worker.CorrelationMeasureWorker
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kamera veya mikrofon erişimi tespit edildiğinde:
 *   1. O anki TrafficStats baseline'ı alır (gönderilen byte sayısı).
 *   2. [CorrelationMeasureWorker]'ı 60 saniye sonra çalışacak şekilde planlar.
 *   3. Worker, delta + MediaStore sorgusu + şüphe seviyesi hesabı yapar ve Room'a kaydeder.
 *
 * Hata izolasyonu: herhangi bir hata sessizce loglanır, ana tarama akışını engellemez.
 */
@Singleton
class CorrelationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val crashLogRepository: CrashLogRepository
) {

    companion object {
        private val SENSOR_TYPES = setOf(PermissionType.CAMERA, PermissionType.MICROPHONE)
        private const val MEASURE_DELAY_SECONDS = 60L
    }

    /**
     * Yeni tespit edilen log listesini alır; kamera/mikrofon erişimleri için
     * ölçüm worker'ını planlar.
     */
    fun scheduleForNewLogs(logs: List<PermissionLog>) {
        val sensorLogs = logs.filter { it.permissionType in SENSOR_TYPES }
        if (sensorLogs.isEmpty()) return

        for (log in sensorLogs) {
            try {
                scheduleOne(log)
            } catch (e: Exception) {
                crashLogRepository.writeCrashLog(Thread.currentThread(), e)
                Timber.e(e, "CorrelationEngine.scheduleOne hata: ${log.packageName}")
            }
        }
    }

    private fun scheduleOne(log: PermissionLog) {
        val uid = try {
            context.packageManager.getApplicationInfo(log.packageName, PackageManager.GET_META_DATA).uid
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.w("UID bulunamadı: ${log.packageName}")
            return
        }

        val baselineBytes = TrafficStats.getUidTxBytes(uid)
        if (baselineBytes == TrafficStats.UNSUPPORTED.toLong()) {
            Timber.d("TrafficStats desteklenmiyor: ${log.packageName}")
            return
        }

        val inputData = workDataOf(
            CorrelationMeasureWorker.KEY_PACKAGE    to log.packageName,
            CorrelationMeasureWorker.KEY_APP_NAME   to log.appName,
            CorrelationMeasureWorker.KEY_UID        to uid,
            CorrelationMeasureWorker.KEY_BASELINE   to baselineBytes,
            CorrelationMeasureWorker.KEY_ACCESS_TYPE to log.permissionType.name,
            CorrelationMeasureWorker.KEY_ACCESS_MS  to log.accessTime,
            CorrelationMeasureWorker.KEY_IS_BG      to log.isBackground
        )

        val request = OneTimeWorkRequestBuilder<CorrelationMeasureWorker>()
            .setInitialDelay(MEASURE_DELAY_SECONDS, TimeUnit.SECONDS)
            .setInputData(inputData)
            .build()

        workManager.enqueue(request)
        Timber.d(
            "Korelasyon ölçümü planlandı: ${log.packageName} / " +
                    "${log.permissionType.name}, ${MEASURE_DELAY_SECONDS}s sonra"
        )
    }
}
