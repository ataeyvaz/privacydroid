package io.privacydroid.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.domain.repository.PermissionRepository
import io.privacydroid.util.AppInfoHelper
import io.privacydroid.util.NotificationHelper
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Her sabah 09:00'da çalışır, bir önceki günün izin özetini bildirir.
 *
 * Zamanlama: ilk çalışma bir sonraki 09:00'a denk gelecek şekilde delay hesaplanır,
 * ardından 24 saatlik periyot ile devam eder.
 */
@HiltWorker
class DailySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val permissionRepository: PermissionRepository,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: SettingsRepository,
    private val appInfoHelper: AppInfoHelper
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "daily_summary"
        private const val TARGET_HOUR = 9

        fun schedule(workManager: WorkManager) {
            val initialDelay = millisUntilNextOccurrence(TARGET_HOUR)
            Timber.d("Günlük özet planlandı — ilk çalışma ${initialDelay / 60_000} dakika sonra")

            val request = PeriodicWorkRequestBuilder<DailySummaryWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Bir sonraki [targetHour]:00'a kaç ms kaldığını hesaplar.
         * Şu an hedef saatten geçildiyse ertesi güne atlar.
         */
        fun millisUntilNextOccurrence(targetHour: Int): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            return target.timeInMillis - now.timeInMillis
        }
    }

    override suspend fun doWork(): Result {
        if (!settingsRepository.isSummaryNotificationsEnabled()) {
            Timber.d("Günlük özet devre dışı — atlandı")
            return Result.success()
        }

        val yesterdayStart = yesterdayMidnight()
        val yesterdayEnd = yesterdayStart + TimeUnit.DAYS.toMillis(1L) - 1

        return runCatching {
            val logs = permissionRepository.getLogsBetween(yesterdayStart, yesterdayEnd).first()

            val suspiciousApps = logs
                .filter { it.isBackground }
                .map { it.packageName }
                .distinct()

            val topApp = suspiciousApps.firstOrNull()?.let { pkg ->
                appInfoHelper.getAppName(pkg)
            }

            notificationHelper.sendDailySummaryNotification(
                suspiciousAppCount = suspiciousApps.size,
                topAppName = topApp
            )

            Timber.d("Günlük özet gönderildi — ${suspiciousApps.size} şüpheli uygulama")
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                Timber.e(e, "Günlük özet başarısız")
                Result.retry()
            }
        )
    }

    private fun yesterdayMidnight(): Long =
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
