package io.privacydroid.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import dagger.hilt.android.AndroidEntryPoint
import io.privacydroid.data.model.MonitoringMode
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.service.PermissionMonitorService
import io.privacydroid.worker.WorkManagerHelper
import timber.log.Timber
import javax.inject.Inject

/**
 * Cihaz yeniden başladığında izleme altyapısını devreye alır.
 *
 * RECEIVE_BOOT_COMPLETED + QUICKBOOT_POWERON (bazı üreticiler):
 *   Özellikle Xiaomi, Huawei gibi üreticiler özel boot action gönderir.
 *   Her iki action da dinlenir — hiçbir cihaz atlanmaz.
 *
 * WorkManager normal şartlarda boot sonrası kendi planını yeniden kurgular,
 * ancak bazı OEM'lerde (Xiaomi MIUI, Huawei EMUI) agresif görev öldürme
 * nedeniyle bu gerçekleşmeyebilir. BootReceiver bu boşluğu kapatır.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var workManagerHelper: WorkManagerHelper

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val isBootAction = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON" || // HTC, bazı Xiaomi
                action == "com.htc.intent.action.QUICKBOOT_POWERON"    // HTC

        if (!isBootAction) return

        Timber.d("Boot tamamlandı — izleme altyapısı yeniden başlatılıyor")

        when (settingsRepository.getMonitoringMode()) {
            MonitoringMode.PERIODIC -> {
                // KEEP değil UPDATE: boot sonrası kısıtlamalar yeniden değerlendirilsin
                workManagerHelper.schedulePeriodic(ExistingPeriodicWorkPolicy.UPDATE)
                Timber.d("Periyodik tarama boot sonrası yeniden planlandı")
            }
            MonitoringMode.REALTIME -> {
                // Foreground service'i başlat
                // setAction() ile atama yapılır — dış scope'taki val action değişkenini gölgelemez
                val serviceIntent = Intent(context, PermissionMonitorService::class.java)
                    .apply { setAction(PermissionMonitorService.ACTION_START) }
                context.startForegroundService(serviceIntent)
                Timber.d("Gerçek zamanlı izleme servisi boot sonrası başlatıldı")
            }
        }
    }
}
