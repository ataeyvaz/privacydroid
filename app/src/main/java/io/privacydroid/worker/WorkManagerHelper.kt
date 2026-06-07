package io.privacydroid.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.privacydroid.data.model.MonitoringMode
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import io.privacydroid.data.repository.SettingsRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager periyodik tarama görevini merkezi olarak yönetir.
 *
 * --- Doze Mode davranışı ---
 * WorkManager, JobScheduler (API 23+) kullanır ve Doze Mode'a tam uyumludur.
 * Doze aktifken görevler "maintenance window"lara ertelenir.
 *
 * Senaryo: Cihaz 2 saat Doze'da kalır, 3 tarama ertelenir.
 *   → Maintenance window açılınca WorkManager bir tarama başlatır.
 *   → PermissionScanUseCase, last_scan_time_ms'yi kullanarak delta tespiti yapar.
 *   → AppOpsManager ertelenmiş 2 saatin TÜM erişimlerini döner.
 *   → Hiçbir log kaybı olmaz.
 *
 * requiresBatteryNotLow = true: Pil %15'in altındayken tarama yapılmaz.
 * Bu kritik değil — pil şarj olunca delta ile telafi edilir.
 *
 * --- Manuel test komutları ---
 *
 * Doze Mode simülasyonu:
 *   adb shell dumpsys deviceidle enable deep
 *   adb shell dumpsys deviceidle force-idle
 *   // Birkaç dakika bekle, ardından:
 *   adb shell dumpsys deviceidle unforce
 *
 * WorkManager görev durumu:
 *   adb shell dumpsys jobscheduler | grep -A5 "io.privacydroid"
 *
 * WorkManager internal durumu:
 *   adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS -p io.privacydroid.debug
 *
 * Pil optimizasyonunu devre dışı bırakma (test için):
 *   adb shell dumpsys battery unplug
 *   adb shell dumpsys deviceidle step
 *
 * Gerçek cihazda Doze testi:
 *   1. Cihazı şarjdan çıkar
 *   2. Ekranı kapat, 15+ dakika bekle
 *   3. adb shell dumpsys deviceidle get deep  → "IDLE" görmeli
 *   4. Ekranı aç, logcat'te "PermissionScanWorker başlatıldı" yaz
 *   5. Tarama zamanı logunu kontrol et — sinceMs erteleme süresini kapsamalı
 */
@Singleton
class WorkManagerHelper @Inject constructor(
    private val workManager: WorkManager,
    private val settingsRepository: SettingsRepository
) {

    private val scanConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Ağ bağlantısı gerekmez
        .setRequiresBatteryNotLow(true)                  // Pil çok düşükken çalışma
        .build()

    /**
     * Periyodik taramayı başlatır veya mevcut planı günceller.
     *
     * [policy] = KEEP: Zaten çalışan bir plan varsa dokunma.
     * [policy] = UPDATE: Kısıtlamalar değiştiyse mevcut planı güncelle.
     */
    fun schedulePeriodic(policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP) {
        val request = PeriodicWorkRequestBuilder<PermissionScanWorker>(
            PermissionScanWorker.SCAN_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            // Flex window: işi interval'in son %25'inde çalıştır (batarya/ağ durumu daha iyi olabilir)
            PermissionScanWorker.SCAN_INTERVAL_MINUTES / 4,
            TimeUnit.MINUTES
        )
            .setConstraints(scanConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                // İlk retry: 5 dakika; sonrakiler 10, 20, 40... max 1 saat
                5L,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            PermissionScanWorker.WORK_NAME,
            policy,
            request
        )
        Timber.d("Periyodik tarama planlandı (policy=$policy)")
    }

    /** Periyodik taramayı iptal eder (gerçek zamanlı moda geçişte). */
    fun cancelPeriodic() {
        workManager.cancelUniqueWork(PermissionScanWorker.WORK_NAME)
        Timber.d("Periyodik tarama iptal edildi")
    }

    /**
     * Pull-to-refresh ve uygulama açılışı için anlık tek seferlik tarama başlatır.
     * Son taramadan bu yana [SCAN_INTERVAL_MINUTES] dakika geçmemişse tarama yapılmaz —
     * UI mevcut DB verisini göstermeye devam eder.
     * Periyodik görevin zamanlamasını etkilemez.
     */
    fun triggerImmediateScan(): UUID? {
        val lastScanMs = settingsRepository.getLastPermissionScanMs()
        val sinceLastScanMs = System.currentTimeMillis() - lastScanMs
        val minIntervalMs = TimeUnit.MINUTES.toMillis(PermissionScanWorker.SCAN_INTERVAL_MINUTES)

        if (lastScanMs > 0L && sinceLastScanMs < minIntervalMs) {
            Timber.d(
                "Anlık tarama atlandı — son taramadan ${sinceLastScanMs / 1000}s geçmiş " +
                        "(min ${minIntervalMs / 1000}s gerekli)"
            )
            return null
        }

        val request = OneTimeWorkRequestBuilder<PermissionScanWorker>()
            .setConstraints(scanConstraints)
            .build()
        workManager.enqueue(request)
        Timber.d("Anlık tarama kuyruğa alındı")
        return request.id
    }

    /**
     * Manuel tracker taraması için bağımsız tek seferlik görev başlatır.
     * PermissionScanWorker'dan bağımsız — sadece TrackerDetector çalışır.
     * Kısa sürede bitmesi beklenir; zaman aşımı veya retry yok.
     */
    fun triggerTrackerScan(): UUID {
        val request = androidx.work.OneTimeWorkRequestBuilder<TrackerScanWorker>().build()
        workManager.enqueueUniqueWork(
            TrackerScanWorker.WORK_NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
        Timber.d("Manuel tracker taraması kuyruğa alındı")
        return request.id
    }

    /** Verilen iş kimliği için WorkInfo akışı döner; durum değiştikçe emit eder. */
    fun observeWork(id: UUID): Flow<WorkInfo> = workManager.getWorkInfoByIdFlow(id)

    /** Günlük özet bildirimini sabah 09:00'a planlar. */
    fun scheduleDailySummary() {
        DailySummaryWorker.schedule(workManager)
    }

    /**
     * İzleme modu değişince doğru planlamayı yapar.
     * PERIODIC → periyodik WorkManager görevi
     * REALTIME → WorkManager iptal, ForegroundService devralır
     */
    fun applyMonitoringMode(mode: MonitoringMode) {
        when (mode) {
            MonitoringMode.PERIODIC -> schedulePeriodic(ExistingPeriodicWorkPolicy.UPDATE)
            MonitoringMode.REALTIME -> cancelPeriodic()
        }
    }
}
