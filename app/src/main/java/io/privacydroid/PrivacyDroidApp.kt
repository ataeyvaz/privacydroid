package io.privacydroid

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.privacydroid.data.model.MonitoringMode
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.util.CrashLogger
import io.privacydroid.util.NotificationHelper
import io.privacydroid.worker.WorkManagerHelper
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PrivacyDroidApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var workManagerHelper: WorkManagerHelper
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var crashLogger: CrashLogger

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // CrashLogger hem debug hem release'de çalışır — cihazda log tutmak için
        crashLogger.install()

        notificationHelper.createNotificationChannels()
        startMonitoringForCurrentMode()
        workManagerHelper.scheduleDailySummary()
    }

    private fun startMonitoringForCurrentMode() {
        when (settingsRepository.getMonitoringMode()) {
            MonitoringMode.PERIODIC -> workManagerHelper.schedulePeriodic()
            MonitoringMode.REALTIME -> workManagerHelper.cancelPeriodic()
        }
        // Modül 2: Ağ bağlantı izlemeyi başlat
        try {
            io.privacydroid.service.NetworkMonitorService.start(this)
        } catch (e: Exception) {
            Timber.e(e, "NetworkMonitorService başlatılamadı")
        }
    }
}
