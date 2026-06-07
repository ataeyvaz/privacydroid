package io.privacydroid.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.MainActivity
import io.privacydroid.R
import io.privacydroid.data.local.dao.NotificationLogDao
import io.privacydroid.data.local.dao.PermissionLogDao
import io.privacydroid.data.local.entity.NotificationLogEntity
import io.privacydroid.data.model.NotificationSensitivity
import io.privacydroid.data.repository.SettingsRepository
import io.privacydroid.domain.model.SuspiciousReason
import io.privacydroid.domain.usecase.SuspiciousActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tüm bildirim oluşturma ve gönderme mantığını yönetir.
 *
 * Kanal mimarisi:
 *   CHANNEL_SUSPICIOUS  → IMPORTANCE_HIGH  : şüpheli aktivite, titreşim açık
 *   CHANNEL_SUMMARY     → IMPORTANCE_LOW   : günlük özet, sessiz
 *   CHANNEL_SERVICE     → IMPORTANCE_MIN   : foreground servis ikonu, badge yok
 *
 * Sistem çubuğu kuralı (sıkı):
 *   Sadece CRITICAL aktiviteler sistem bildirimi alır:
 *   - Gece 00-06 + arka plan + kamera/mikrofon (aynı anda üçü)
 *   Diğer tüm tespitler sadece uygulama içi NotificationLog'a kaydedilir.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val throttle: NotificationThrottleTracker,
    private val appInfoHelper: AppInfoHelper,
    private val settingsRepository: SettingsRepository,
    private val notificationLogDao: NotificationLogDao,
    private val permissionLogDao: PermissionLogDao
) {

    companion object {
        const val CHANNEL_SUSPICIOUS = "suspicious_activity"
        const val CHANNEL_SUMMARY = "daily_summary"
        const val CHANNEL_SERVICE = "monitoring_service"

        private const val GROUP_KEY_SUSPICIOUS = "group_suspicious"
        private const val NOTIFICATION_ID_GROUP_SUMMARY = 8001
        const val NOTIFICATION_ID_DAILY_SUMMARY = 8000
        const val NOTIFICATION_ID_SERVICE = 9001
        private const val NOTIFICATION_ID_SERVICE_STOPPED = 9002
        const val CHANNEL_SERVICE_ALERT = "service_alert"
        const val CHANNEL_PERMISSION_CHANGE = "permission_change"
        const val CHANNEL_TRACKER  = "tracker_connection"
        const val CHANNEL_NETWORK  = "network_anomaly"
        const val CHANNEL_VPN      = "vpn_service"

        const val NOTIFICATION_ID_VPN_FAILED = 9200
        private const val NOTIFICATION_ID_WIFI_SUSPECT_BASE   = 6500
        private const val NOTIFICATION_ID_NET_ANOMALY_BASE    = 6700

        private const val NOTIFICATION_ID_PERM_CHANGE_BASE = 7000
        private const val NOTIFICATION_ID_TRACKER_BASE = 7500

        private const val APP_NOTIFICATION_ID_BASE = 1000
        private const val APP_NOTIFICATION_ID_MAX = 6999

        const val EXTRA_NAVIGATE_TO_PACKAGE = "navigate_to_package"

        private const val MULTI_APP_SUMMARY_THRESHOLD = 3
    }

    /** Fire-and-forget DB yazma operasyonları için yaşam süresi Singleton ile eşleşen scope. */
    private val dbScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // -------------------------------------------------------------------------
    // Kanal kurulumu
    // -------------------------------------------------------------------------

    fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_SUSPICIOUS,
                    context.getString(R.string.notification_channel_suspicious_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_suspicious_desc)
                    enableVibration(true)
                    setShowBadge(true)
                },
                NotificationChannel(
                    CHANNEL_SUMMARY,
                    context.getString(R.string.notification_channel_summary_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_summary_desc)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_SERVICE,
                    context.getString(R.string.notification_channel_service_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = context.getString(R.string.notification_channel_service_desc)
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_SERVICE_ALERT,
                    "Servis Uyarısı",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "İzleme servisi beklenmedik şekilde durduğunda bildirir"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_PERMISSION_CHANGE,
                    "İzin Değişikliği",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Uygulama güncellemesi yeni izin eklediğinde uyarır"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_TRACKER,
                    "Tracker Bağlantısı",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Bilinen takip sunucularına bağlantı tespit edildiğinde bildirir"
                },
                NotificationChannel(
                    CHANNEL_NETWORK,
                    "Ağ Anomalisi",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "İnternet açıldığında arka planda veri gönderimini bildirir"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_VPN,
                    "VPN Servisi",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "PrivacyDroid VPN aktifken gösterilir"
                    setShowBadge(false)
                }
            )
        )
        Timber.d("Bildirim kanalları oluşturuldu")
    }

    // -------------------------------------------------------------------------
    // İzin değişim bildirimleri
    // -------------------------------------------------------------------------

    fun sendPermissionAddedNotification(
        appName: String,
        versionName: String,
        addedPerms: List<String>,
        removedPerms: List<String>,
        packageName: String = ""
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = NOTIFICATION_ID_PERM_CHANGE_BASE + (appName.hashCode() and 0x1FF)

        // Uygulama içi bildirim merkezi için NotificationLog'a kaydet
        dbScope.launch {
            try {
                notificationLogDao.insert(
                    NotificationLogEntity(
                        packageName = packageName,
                        appName = appName,
                        message = "Güncelleme ${addedPerms.size} yeni izin ekledi (v$versionName)",
                        riskLevel = "HIGH",
                        permissionType = "PERMISSION_CHANGE"
                    )
                )
            } catch (e: Exception) {
                Timber.w("İzin değişikliği NotificationLog kaydedilemedi: ${e.message}")
            }
        }

        val addedLines = addedPerms.joinToString("\n") { perm ->
            val d = io.privacydroid.domain.model.resolvePermissionDisplay(perm)
            "+ ${d.emoji} ${d.label} (YENİ)"
        }
        val removedLines = if (removedPerms.isNotEmpty())
            removedPerms.joinToString("\n") { perm ->
                val d = io.privacydroid.domain.model.resolvePermissionDisplay(perm)
                "- ${d.emoji} ${d.label} (kaldırıldı)"
            }
        else ""

        val body = buildString {
            append(addedLines)
            if (removedLines.isNotEmpty()) { append("\n").append(removedLines) }
            append("\n\nBu izinleri vermeden önce neden gerekli olduğunu sorgulayın.")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSION_CHANGE)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ $appName güncellendi — YENİ izinler ekledi")
            .setContentText("${addedPerms.size} yeni izin tespit edildi")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityIntent(notifId))
            .build()

        manager.notify(notifId, notification)
        Timber.i("İzin değişikliği bildirimi: $appName v$versionName +${addedPerms.size} izin")
    }

    fun sendPermissionRemovedNotification(appName: String, removedPerms: List<String>) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = NOTIFICATION_ID_PERM_CHANGE_BASE + (appName.hashCode() and 0x1FF) + 256

        val removedLines = removedPerms.joinToString("\n") { perm ->
            val d = io.privacydroid.domain.model.resolvePermissionDisplay(perm)
            "- ${d.emoji} ${d.label}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSION_CHANGE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("✅ $appName bir izni kaldırdı")
            .setStyle(NotificationCompat.BigTextStyle().bigText(removedLines))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityIntent(notifId))
            .build()

        manager.notify(notifId, notification)
    }

    // -------------------------------------------------------------------------
    // Tracker bağlantısı bildirimi
    // -------------------------------------------------------------------------

    fun sendTrackerConnectionNotification(
        appName: String,
        trackerDomain: String,
        estimatedBytes: Long,
        packageName: String = ""
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = NOTIFICATION_ID_TRACKER_BASE + (appName.hashCode() and 0xFF)

        val byteLabel = when {
            estimatedBytes < 1_024L -> "${estimatedBytes}B"
            estimatedBytes < 1_048_576L -> "${"%.1f".format(estimatedBytes / 1024.0)}KB"
            else -> "${"%.1f".format(estimatedBytes / 1_048_576.0)}MB"
        }

        // Uygulama içi bildirim merkezi için her zaman NotificationLog'a kaydet
        dbScope.launch {
            try {
                notificationLogDao.insert(
                    NotificationLogEntity(
                        packageName = packageName,
                        appName = appName,
                        message = "Tracker sunucusuna bağlandı: $trackerDomain (≈$byteLabel)",
                        riskLevel = "HIGH",
                        permissionType = "TRACKER"
                    )
                )
            } catch (e: Exception) {
                Timber.w("Tracker NotificationLog kaydedilemedi: ${e.message}")
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TRACKER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("📡 $appName tracker sunucusuna bağlandı")
            .setContentText("Hedef: $trackerDomain — Tahmini veri: $byteLabel")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityIntent(notifId))
            .build()

        manager.notify(notifId, notification)
    }

    // -------------------------------------------------------------------------
    // Ağ anomali bildirimleri (Modül 2)
    // -------------------------------------------------------------------------

    /**
     * Wi-Fi açma iznine sahip ve internet bağlanmadan önce aktif olan uygulama bildirimi.
     *
     * [isSystemApp] = true  → sadece NotificationLog'a yaz, sistem bildirimi gönderme
     * [isSystemApp] = false → 24 saatte en fazla 1 sistem bildirimi gönder
     */
    fun sendSuspiciousWifiAppNotification(
        appName: String,
        packageName: String,
        isSystemApp: Boolean = false
    ) {
        try {
            val message = "$appName, Wi-Fi açma iznine sahip ve internet bağlanmadan önce aktifti."
            val riskLevel = if (isSystemApp) "LOW" else "HIGH"

            // Her durumda NotificationLog'a kaydet
            dbScope.launch {
                try {
                    notificationLogDao.insert(
                        NotificationLogEntity(
                            packageName = packageName,
                            appName = appName,
                            message = message,
                            riskLevel = riskLevel,
                            permissionType = "CHANGE_WIFI_STATE"
                        )
                    )
                } catch (e: Exception) {
                    Timber.w("Wi-Fi NotificationLog kaydedilemedi: ${e.message}")
                }
            }

            // Sistem uygulamaları → sadece log, bildirim yok
            if (isSystemApp) {
                Timber.d("Sistem uygulaması Wi-Fi erişimi — sadece log: $appName")
                return
            }

            // Kullanıcı uygulamaları → 24 saatte 1 kez sistem bildirimi
            if (!throttle.shouldNotify(packageName, "WIFI_CHANGE", NotificationRiskLevel.HIGH)) {
                Timber.d("Wi-Fi bildirim throttle (24s) — atlandı: $appName")
                return
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifId = NOTIFICATION_ID_WIFI_SUSPECT_BASE + (packageName.hashCode() and 0xFF)
            val notification = NotificationCompat.Builder(context, CHANNEL_NETWORK)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠️ $appName Wi-Fi açma iznine sahip")
                .setContentText("İnternet bağlanmadan önce aktifti — veri göndermiş olabilir")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("⚠️ $appName, Wi-Fi'ı Açma/Kapatma iznine sahip ve internet açılmadan " +
                            "önce aktifti.\n\nSiz uyurken Wi-Fi'ı açıp veri gönderme riski vardır."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(buildDetailIntent(packageName, notifId))
                .build()

            manager.notify(notifId, notification)
            throttle.markNotified(packageName, "WIFI_CHANGE")
            Timber.i("Şüpheli Wi-Fi bildirimi: $appName")
        } catch (e: Exception) {
            Timber.e(e, "Şüpheli Wi-Fi bildirimi gönderilemedi")
        }
    }

    /**
     * İnternet bağlanır bağlanmaz arka planda >5 MB veri gönderen uygulama için bildirim.
     * [mbSent] metin olarak formatlanmış MB değeri (ör. "7.2").
     */
    fun sendNetworkAnomalyNotification(appName: String, mbSent: String) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notifId = NOTIFICATION_ID_NET_ANOMALY_BASE + (appName.hashCode() and 0xFF)

            // NotificationLog'a her zaman kaydet
            val mbFloat = mbSent.toFloatOrNull() ?: 0f
            val riskLevel = if (mbFloat >= 5f) "CRITICAL" else "HIGH"
            dbScope.launch {
                try {
                    notificationLogDao.insert(
                        NotificationLogEntity(
                            packageName = "",
                            appName = appName,
                            message = "İnternet açılır açılmaz arka planda $mbSent MB gönderildi",
                            riskLevel = riskLevel,
                            permissionType = "NETWORK"
                        )
                    )
                } catch (e: Exception) {
                    Timber.w("Ağ anomali NotificationLog kaydedilemedi: ${e.message}")
                }
            }

            // Sistem bildirimi sadece ≥5 MB için
            if (mbFloat < 5f) {
                Timber.d("Ağ anomali: $mbSent MB < 5 MB, sistem bildirimi gönderilmedi")
                return
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_NETWORK)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("⚠️ İnternet açılır açılmaz $appName veri gönderdi")
                .setContentText("Arka planda $mbSent MB gönderildi")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("$appName, internet bağlantısı kurulur kurulmaz arka planda " +
                            "$mbSent MB veri gönderdi. Bu şüpheli bir davranış olabilir."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(buildMainActivityIntent(requestCode = notifId))
                .build()
            manager.notify(notifId, notification)
            Timber.i("Ağ anomali bildirimi: $appName $mbSent MB")
        } catch (e: Exception) {
            Timber.e(e, "Ağ anomali bildirimi gönderilemedi")
        }
    }

    // -------------------------------------------------------------------------
    // VPN bildirimleri
    // -------------------------------------------------------------------------

    fun sendVpnFailedNotification() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE_ALERT)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🔬 VPN bağlantısı kurulamadı")
                .setContentText("Gelişmiş tracker tespiti devre dışı. Ayarlardan yeniden deneyin.")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("PrivacyDroid VPN servisi ${"3"} deneme sonrasında bağlantı kuramadı. " +
                            "Başka bir VPN uygulaması açık olabilir veya sistem kısıtlaması var."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(buildMainActivityIntent(requestCode = 50))
                .build()
            manager.notify(NOTIFICATION_ID_VPN_FAILED, notification)
        } catch (e: Exception) {
            Timber.e(e, "VPN başarısız bildirimi gönderilemedi")
        }
    }

    // -------------------------------------------------------------------------
    // Tracker tarama durum kayıtları
    // -------------------------------------------------------------------------

    /**
     * Tracker tespitinin bu cihazda yapılamadığını uygulama içi bildirim merkezine
     * yazar. Sistem bildirimi GÖNDERMEZ — sadece NotificationLog'a kaydedilir.
     * proc/net ve TrafficStats yöntemlerinin ikisi de başarısız olduğunda çağrılır.
     */
    fun logTrackerDetectionRestricted() {
        dbScope.launch {
            try {
                notificationLogDao.insert(
                    NotificationLogEntity(
                        packageName = "",
                        appName = "PrivacyDroid",
                        message = "Tracker tespiti bu cihazda kısıtlı. Gelişmiş tespit için " +
                                "Ayarlar'dan VPN modunu etkinleştirebilirsiniz.",
                        riskLevel = "LOW",
                        permissionType = "TRACKER"
                    )
                )
                Timber.d("Tracker kısıtlı mesajı NotificationLog'a yazıldı")
            } catch (e: Exception) {
                Timber.w("Tracker kısıtlı NotificationLog kaydedilemedi: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Servis durma bildirimi
    // -------------------------------------------------------------------------

    fun sendServiceStoppedNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val restartIntent = android.app.PendingIntent.getService(
            context, 0,
            android.content.Intent(context, io.privacydroid.service.PermissionMonitorService::class.java)
                .apply { setAction(io.privacydroid.service.PermissionMonitorService.ACTION_START) },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("İzleme Servisi Durdu")
            .setContentText("PrivacyDroid izleme servisi beklenmedik şekilde durdu — yeniden başlatmak için dokunun")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("PrivacyDroid izleme servisi beklenmedik şekilde durdu. Gerçek zamanlı arka plan izleme devre dışı. Yeniden başlatmak için bu bildirimi açın."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(restartIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID_SERVICE_STOPPED, notification)
        Timber.e("Servis durma bildirimi gönderildi")
    }

    // -------------------------------------------------------------------------
    // Şüpheli aktivite bildirimleri
    // -------------------------------------------------------------------------

    /**
     * Şüpheli aktivite listesini işler:
     *   1. Her aktivite için NotificationLog'a kaydet (her zaman, uygulama içi bildirim merkezi için).
     *   2. Sadece CRITICAL aktiviteler sistem bildirimi alır:
     *      - Gece 00-06 + arka plan + kamera/mikrofon (üçü birden)
     *   3. CRITICAL için cooldown ve tekrar gösterim kontrolü uygulanır.
     *   4. Sistem bildirimi gönderilen loglar notified=true olarak işaretlenir.
     */
    fun sendSuspiciousActivityNotification(activities: List<SuspiciousActivity>) {
        if (activities.isEmpty()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var systemNotifSent = 0

        for (activity in activities) {
            val riskLevel = classifyRiskLevel(activity)
            val appName = appInfoHelper.getAppName(activity.packageName)
            val timeLabel = formatTime(activity.accessTimeMs)
            val message = buildDetailedBody(appName, activity, timeLabel)

            // Her aktiviteyi NotificationLog'a kaydet (uygulama içi bildirim merkezi)
            dbScope.launch {
                try {
                    notificationLogDao.insert(
                        NotificationLogEntity(
                            packageName = activity.packageName,
                            appName = appName,
                            message = message,
                            riskLevel = riskLevel.name,
                            permissionType = activity.permissionType
                        )
                    )
                } catch (e: Exception) {
                    Timber.w("NotificationLog kaydedilemedi: ${e.message}")
                }
            }

            // Sistem bildirimi sadece CRITICAL için
            if (riskLevel != NotificationRiskLevel.CRITICAL) {
                Timber.d("NotificationLog'a eklendi (sistem bildirimi yok): ${activity.packageName} / $riskLevel")
                continue
            }

            if (!throttle.shouldNotify(activity.packageName, activity.reason.name, riskLevel)) {
                Timber.d("Throttle — sistem bildirimi atlandı: ${activity.packageName}")
                continue
            }

            val notifId = notificationIdForApp(activity.packageName)
            if (throttle.isAlreadyVisible(notifId)) {
                Timber.d("Zaten görünür — atlandı: ${activity.packageName}")
                continue
            }

            postSuspiciousNotification(manager, activity, appName)
            throttle.markNotified(activity.packageName, activity.reason.name)

            // Bildirimi gönderilen PermissionLog'u işaretle
            val pkg = activity.packageName
            val perm = activity.permissionType
            val time = activity.accessTimeMs
            dbScope.launch {
                try {
                    permissionLogDao.markAsNotified(pkg, perm, time)
                } catch (e: Exception) {
                    Timber.w("markAsNotified başarısız: ${e.message}")
                }
            }

            systemNotifSent++
        }

        if (systemNotifSent > 0) maybePostGroupSummary(manager)
        Timber.d("$systemNotifSent sistem bildirimi + ${activities.size} NotificationLog kaydı")
    }

    /**
     * Risk seviyesi belirleme:
     *   CRITICAL → Gece 00-06 + arka plan + kamera VEYA mikrofon (üçü birden)
     *   HIGH     → Gece veya arka plan kamera/mikrofon (ama üçü birden değil)
     *   LOW      → Konum burst veya arka plan konum
     */
    private fun classifyRiskLevel(activity: SuspiciousActivity): NotificationRiskLevel {
        val isCameraOrMic = activity.permissionType in setOf("CAMERA", "RECORD_AUDIO")
        return when (activity.reason) {
            SuspiciousReason.NIGHT_ACCESS ->
                if (isCameraOrMic && activity.isBackground) NotificationRiskLevel.CRITICAL
                else NotificationRiskLevel.HIGH
            SuspiciousReason.BACKGROUND_SENSOR_ACCESS ->
                if (isCameraOrMic) NotificationRiskLevel.HIGH else NotificationRiskLevel.LOW
            SuspiciousReason.LOCATION_BURST -> NotificationRiskLevel.LOW
        }
    }

    private fun postSuspiciousNotification(
        manager: NotificationManager,
        activity: SuspiciousActivity,
        appName: String
    ) {
        val notifId = notificationIdForApp(activity.packageName)
        val timeLabel = formatTime(activity.accessTimeMs)

        val title = buildDetailedTitle(appName, activity.reason)
        val body = buildDetailedBody(appName, activity, timeLabel)

        val notification = NotificationCompat.Builder(context, CHANNEL_SUSPICIOUS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY_SUSPICIOUS)
            .setAutoCancel(true)
            .setContentIntent(buildDetailIntent(activity.packageName, notifId))
            .addAction(buildDetailAction(activity.packageName, notifId))
            .addAction(buildRevokePermissionAction(activity.packageName, notifId))
            .build()

        manager.notify(notifId, notification)
    }

    private fun maybePostGroupSummary(manager: NotificationManager) {
        val activeNotifs = NotificationManagerCompat.from(context).activeNotifications
        val suspiciousCount = activeNotifs.count {
            it.notification.group == GROUP_KEY_SUSPICIOUS &&
                    it.id != NOTIFICATION_ID_GROUP_SUMMARY
        }

        if (suspiciousCount >= MULTI_APP_SUMMARY_THRESHOLD) {
            val summary = NotificationCompat.Builder(context, CHANNEL_SUSPICIOUS)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("$suspiciousCount uygulamadan şüpheli aktivite")
                .setContentText("Detaylar için dokun")
                .setGroup(GROUP_KEY_SUSPICIOUS)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(buildMainActivityIntent(requestCode = 0))
                .build()
            manager.notify(NOTIFICATION_ID_GROUP_SUMMARY, summary)
        }
    }

    // -------------------------------------------------------------------------
    // Günlük özet bildirimi
    // -------------------------------------------------------------------------

    fun sendDailySummaryNotification(suspiciousAppCount: Int, topAppName: String?) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title: String
        val body: String

        when {
            suspiciousAppCount == 0 -> {
                title = "Dün Temiz Görünüyor"
                body = "Şüpheli arka plan aktivitesi tespit edilmedi."
            }
            suspiciousAppCount == 1 && topAppName != null -> {
                title = "1 Uygulama Dikkat Çekiyor"
                body = "$topAppName dün şüpheli davranış gösterdi."
            }
            else -> {
                title = "$suspiciousAppCount Uygulama Dikkat Çekiyor"
                body = if (topAppName != null)
                    "En riskli: $topAppName. Detaylar için dokun."
                else
                    "Dünkü gizlilik raporunu görüntüle."
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(buildMainActivityIntent(requestCode = 1))
            .build()

        manager.notify(NOTIFICATION_ID_DAILY_SUMMARY, notification)
        Timber.d("Günlük özet bildirimi gönderildi: $title")
    }

    // -------------------------------------------------------------------------
    // Intent inşaatçıları
    // -------------------------------------------------------------------------

    private fun buildDetailIntent(packageName: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NAVIGATE_TO_PACKAGE, packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildDetailAction(packageName: String, requestCode: Int): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            context.getString(R.string.notification_action_details),
            buildDetailIntent(packageName, requestCode + 100)
        ).build()

    private fun buildRevokePermissionAction(
        packageName: String,
        requestCode: Int
    ): NotificationCompat.Action {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pi = PendingIntent.getActivity(
            context,
            requestCode + 200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_delete,
            context.getString(R.string.notification_action_revoke),
            pi
        ).build()
    }

    private fun buildMainActivityIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // -------------------------------------------------------------------------
    // Metin inşaatçıları
    // -------------------------------------------------------------------------

    private fun buildDetailedTitle(appName: String, reason: SuspiciousReason): String =
        when (reason) {
            SuspiciousReason.NIGHT_ACCESS -> "$appName gece erişti"
            SuspiciousReason.BACKGROUND_SENSOR_ACCESS -> "$appName arka planda erişti"
            SuspiciousReason.LOCATION_BURST -> "$appName yoğun konum sorgusu"
        }

    private fun buildDetailedBody(
        appName: String,
        activity: SuspiciousActivity,
        timeLabel: String
    ): String = when (activity.reason) {
        SuspiciousReason.NIGHT_ACCESS ->
            "$timeLabel'de ${permissionDisplayName(activity.permissionType)}'a arka planda erişti."
        SuspiciousReason.BACKGROUND_SENSOR_ACCESS ->
            "Siz kullanmıyorken $timeLabel'de ${permissionDisplayName(activity.permissionType)}'a erişti."
        SuspiciousReason.LOCATION_BURST ->
            activity.detail
    }

    private fun permissionDisplayName(permissionType: String): String = when (permissionType) {
        "CAMERA" -> "kameranıza"
        "RECORD_AUDIO" -> "mikrofonunuza"
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> "konumunuza"
        "READ_CONTACTS" -> "rehberinize"
        "READ_CALL_LOG" -> "arama geçmişinize"
        "READ_SMS" -> "SMS'lerinize"
        else -> "verilerinize"
    }

    // -------------------------------------------------------------------------
    // Yardımcılar
    // -------------------------------------------------------------------------

    private fun notificationIdForApp(packageName: String): Int =
        (packageName.hashCode() and 0x7FFFFFFF) %
                (APP_NOTIFICATION_ID_MAX - APP_NOTIFICATION_ID_BASE) +
                APP_NOTIFICATION_ID_BASE

    private fun formatTime(timeMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.getDefault())
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(timeMs))
}
