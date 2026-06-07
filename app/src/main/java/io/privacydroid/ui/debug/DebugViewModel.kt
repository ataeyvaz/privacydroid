package io.privacydroid.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import io.privacydroid.domain.model.SuspiciousReason
import io.privacydroid.domain.usecase.PermissionScanUseCase
import io.privacydroid.domain.usecase.SuspiciousActivity
import io.privacydroid.util.NotificationHelper
import io.privacydroid.util.NotificationThrottleTracker
import io.privacydroid.worker.WorkManagerHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DebugUiState(val lastActionResult: String = "")

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val notificationHelper: NotificationHelper,
    private val throttleTracker: NotificationThrottleTracker,
    private val permissionScanUseCase: PermissionScanUseCase,
    private val workManagerHelper: WorkManagerHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    // ---- Bildirim testleri ----

    fun sendNightAccessNotification() {
        throttleTracker.resetAll() // Test için throttle'ı atla
        notificationHelper.sendSuspiciousActivityNotification(
            listOf(
                SuspiciousActivity(
                    packageName = "com.test.nightapp",
                    reason = SuspiciousReason.NIGHT_ACCESS,
                    permissionType = "RECORD_AUDIO",
                    accessTimeMs = nightTimeMs(hour = 3, minute = 24),
                    detail = "Gece 03:24'te mikrofonunuza arka planda erişti"
                )
            )
        )
        setResult("Gece erişimi bildirimi gönderildi")
    }

    fun sendBackgroundSensorNotification() {
        throttleTracker.resetAll()
        notificationHelper.sendSuspiciousActivityNotification(
            listOf(
                SuspiciousActivity(
                    packageName = "com.test.bgapp",
                    reason = SuspiciousReason.BACKGROUND_SENSOR_ACCESS,
                    permissionType = "CAMERA",
                    accessTimeMs = System.currentTimeMillis(),
                    detail = "Siz kullanmıyorken kameranıza erişti"
                )
            )
        )
        setResult("Arka plan kamera bildirimi gönderildi")
    }

    fun sendLocationBurstNotification() {
        throttleTracker.resetAll()
        notificationHelper.sendSuspiciousActivityNotification(
            listOf(
                SuspiciousActivity(
                    packageName = "com.test.tracker",
                    reason = SuspiciousReason.LOCATION_BURST,
                    permissionType = "ACCESS_FINE_LOCATION",
                    accessTimeMs = System.currentTimeMillis(),
                    detail = "Son 1 saatte 15 konum sorgusu"
                )
            )
        )
        setResult("Konum burst bildirimi gönderildi")
    }

    fun sendDailySummaryNotification() {
        notificationHelper.sendDailySummaryNotification(
            suspiciousAppCount = 3,
            topAppName = "Örnek Uygulama"
        )
        setResult("Günlük özet bildirimi gönderildi")
    }

    fun sendMultipleNotifications() {
        throttleTracker.resetAll()
        val activities = listOf(
            SuspiciousActivity("com.app.one", SuspiciousReason.NIGHT_ACCESS,
                "CAMERA", nightTimeMs(2, 15), "Gece kamera erişimi"),
            SuspiciousActivity("com.app.two", SuspiciousReason.BACKGROUND_SENSOR_ACCESS,
                "RECORD_AUDIO", System.currentTimeMillis(), "Arka plan mikrofon"),
            SuspiciousActivity("com.app.three", SuspiciousReason.LOCATION_BURST,
                "ACCESS_FINE_LOCATION", System.currentTimeMillis(), "15 konum sorgusu"),
        )
        notificationHelper.sendSuspiciousActivityNotification(activities)
        setResult("3 uygulamadan bildirim gönderildi — özet bildirim görünmeli")
    }

    // ---- Scan motoru ----

    fun triggerManualScan() {
        viewModelScope.launch {
            setResult("Tarama başlatıldı…")
            permissionScanUseCase.execute().fold(
                onSuccess = { result ->
                    setResult(
                        "Tarama tamamlandı:\n" +
                                "  Yeni log: ${result.savedLogCount}\n" +
                                "  Şüpheli: ${result.suspiciousActivities.size}\n" +
                                "  Süre: hesaplanıyor"
                    )
                },
                onFailure = { e ->
                    setResult("Tarama başarısız: ${e.message}")
                }
            )
        }
    }

    fun resetNotificationThrottle() {
        throttleTracker.resetAll()
        setResult("Throttle sıfırlandı — tüm bildirimler yeniden gönderilebilir")
    }

    // ---- WorkManager ----

    fun reschedulePeriodic() {
        workManagerHelper.schedulePeriodic(ExistingPeriodicWorkPolicy.UPDATE)
        setResult("Periyodik tarama yeniden planlandı")
    }

    fun cancelAllWork() {
        workManagerHelper.cancelPeriodic()
        setResult("Tüm WorkManager işleri iptal edildi")
    }

    private fun setResult(message: String) {
        _uiState.value = DebugUiState(lastActionResult = message)
    }

    private fun nightTimeMs(hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
