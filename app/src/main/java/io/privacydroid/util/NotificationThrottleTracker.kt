package io.privacydroid.util

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class NotificationRiskLevel { CRITICAL, HIGH, LOW }

/**
 * Aynı uygulamadan gelen şüpheli aktivite bildirimlerinin spam yapmasını önler.
 *
 * Risk seviyesine göre cooldown:
 *   CRITICAL (gece + kamera/mikrofon) → 6 saat
 *   HIGH (gece erişimi, arka plan kamera/mikrofon) → 24 saat
 *   LOW (konum burst, arka plan konum) → anlık bildirim gönderilmez, günlük kuyruğa eklenir
 *
 * Key formatı: notif_${packageName}_${reason}_${yyyy-MM-dd}
 * Gün geçince key değişir ve cooldown sıfırlanmış olur.
 */
@Singleton
class NotificationThrottleTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "notification_throttle"
        private const val DAILY_QUEUE_KEY = "daily_summary_queue"
        private val COOLDOWN_CRITICAL_MS = TimeUnit.HOURS.toMillis(6L)
        private val COOLDOWN_HIGH_MS = TimeUnit.HOURS.toMillis(24L)
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun todayDate(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun throttleKey(packageName: String, reason: String): String =
        "notif_${packageName}_${reason}_${todayDate()}"

    /**
     * true döner → bildirim gönderilebilir.
     * LOW risk her zaman false döner — günlük kuyruğa eklenmesi gerekir.
     */
    fun shouldNotify(
        packageName: String,
        reason: String,
        riskLevel: NotificationRiskLevel
    ): Boolean {
        if (riskLevel == NotificationRiskLevel.LOW) return false
        val key = throttleKey(packageName, reason)
        val lastTime = prefs.getLong(key, 0L)
        val cooldownMs = when (riskLevel) {
            NotificationRiskLevel.CRITICAL -> COOLDOWN_CRITICAL_MS
            else -> COOLDOWN_HIGH_MS
        }
        return System.currentTimeMillis() - lastTime >= cooldownMs
    }

    fun markNotified(packageName: String, reason: String) {
        prefs.edit().putLong(throttleKey(packageName, reason), System.currentTimeMillis()).apply()
    }

    /** Bildirim zaten ekranda görünüyorsa tekrar gönderme. */
    fun isAlreadyVisible(notifId: Int): Boolean = try {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.activeNotifications.any { it.id == notifId }
    } catch (_: Exception) { false }

    /** LOW-risk aktiviteleri günlük özet kuyruğuna ekler (aynı uygulama tekrar eklenmez). */
    fun addToDailySummaryQueue(packageName: String, appName: String, reason: String) {
        val existing = prefs.getString(DAILY_QUEUE_KEY, "") ?: ""
        val alreadyQueued = existing.split(",").any { it.startsWith(packageName) }
        if (!alreadyQueued) {
            val entry = "$packageName|$appName|$reason"
            val updated = if (existing.isEmpty()) entry else "$existing,$entry"
            prefs.edit().putString(DAILY_QUEUE_KEY, updated).apply()
        }
    }

    fun getDailySummaryQueue(): List<Triple<String, String, String>> {
        val raw = prefs.getString(DAILY_QUEUE_KEY, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
        }
    }

    fun clearDailySummaryQueue() {
        prefs.edit().remove(DAILY_QUEUE_KEY).apply()
    }

    /** Test ve debug için tüm throttle kayıtlarını sıfırla. */
    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
