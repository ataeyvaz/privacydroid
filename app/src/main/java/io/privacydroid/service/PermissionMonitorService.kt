package io.privacydroid.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.privacydroid.MainActivity
import io.privacydroid.domain.usecase.PermissionScanUseCase
import io.privacydroid.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Gerçek zamanlı izleme servisi.
 *
 * Kullanıcı "Gerçek Zamanlı" modu seçtiğinde WorkManager'ın yerini alır.
 * Her [POLL_INTERVAL_MS] ms'de bir tarama yapar — Doze Mode'dan etkilenmez
 * çünkü Foreground Service olarak çalışır.
 *
 * Fayda  : Değişiklikler ~2 dakikada tespit edilir (WorkManager 15 dk'ya karşı).
 * Maliyet: Kalıcı bildirim ikonu + hafif süregelen CPU/pil kullanımı.
 *
 * Kullanıcı "Pil Dostu" moduna geçince servis durdurulur,
 * WorkManager periyodik görevi devreye girer.
 *
 * --- Manuel test ---
 *   Servisi başlat:
 *     adb shell am startservice -n io.privacydroid.debug/.service.PermissionMonitorService \
 *       --es action start
 *
 *   Servis durumunu kontrol et:
 *     adb shell dumpsys activity services io.privacydroid
 *
 *   Servisi durdur:
 *     adb shell am stopservice -n io.privacydroid.debug/.service.PermissionMonitorService
 */
@AndroidEntryPoint
class PermissionMonitorService : Service() {

    companion object {
        const val ACTION_START = "io.privacydroid.action.START_MONITORING"
        const val ACTION_STOP = "io.privacydroid.action.STOP_MONITORING"

        private const val FOREGROUND_NOTIFICATION_ID = 9001
        private val POLL_INTERVAL_MS = TimeUnit.MINUTES.toMillis(2L)
    }

    @Inject
    lateinit var permissionScanUseCase: PermissionScanUseCase

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanJob: Job? = null

    /** Kullanıcı veya Settings ekranı kasıtlı durduruyorsa true. */
    private var stoppedIntentionally = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                stoppedIntentionally = false
                startMonitoring()
            }
            ACTION_STOP -> {
                stoppedIntentionally = true
                stopSelf()
            }
            else -> {
                // START_STICKY ile sistem yeniden başlattı
                stoppedIntentionally = false
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startForeground(FOREGROUND_NOTIFICATION_ID, buildForegroundNotification())
        Timber.d("PermissionMonitorService başlatıldı — ${POLL_INTERVAL_MS / 1000}s aralıkla tarama")

        scanJob?.cancel()
        scanJob = serviceScope.launch {
            while (true) {
                runScan()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun runScan() {
        runCatching {
            permissionScanUseCase.execute().fold(
                onSuccess = { result ->
                    Timber.d("Servis taraması — ${result.savedLogCount} yeni log")
                    if (result.hasSuspiciousActivity) {
                        notificationHelper.sendSuspiciousActivityNotification(result.suspiciousActivities)
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Servis taraması başarısız: ${error.message}")
                }
            )
        }.onFailure { error ->
            // Use case'in dışından gelen beklenmedik hata (örn. DB crash)
            Timber.e(error, "Servis taramasında beklenmedik hata")
        }
    }

    private fun buildForegroundNotification() = NotificationCompat.Builder(
        this, NotificationHelper.CHANNEL_SERVICE
    )
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("PrivacyDroid İzliyor")
        .setContentText("Arka plan izin erişimleri takip ediliyor")
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setOngoing(true)
        .setShowWhen(false)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            android.R.drawable.ic_delete,
            "Durdur",
            PendingIntent.getService(
                this, 1,
                Intent(this, PermissionMonitorService::class.java).apply { action = ACTION_STOP },
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    override fun onDestroy() {
        super.onDestroy()
        scanJob?.cancel()

        if (stoppedIntentionally) {
            Timber.d("PermissionMonitorService kasıtlı olarak durduruldu")
        } else {
            // Sistem baskısı, OOM, beklenmedik crash — kullanıcıyı bilgilendir
            Timber.e("PermissionMonitorService beklenmedik şekilde durdu — restart bildirimi gönderiliyor")
            notificationHelper.sendServiceStoppedNotification()
        }
    }
}
