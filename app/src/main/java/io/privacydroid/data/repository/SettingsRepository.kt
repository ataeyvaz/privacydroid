package io.privacydroid.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.privacydroid.data.model.BlockingMode
import io.privacydroid.data.model.MonitoringMode
import io.privacydroid.data.model.NotificationSensitivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kullanıcı ayarlarını yönetir.
 * Hassas veri içermediğinden standart SharedPreferences kullanılır.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val KEY_MONITORING_MODE = "monitoring_mode"
        private const val KEY_SUSPICIOUS_NOTIFICATIONS = "suspicious_notifications_enabled"
        private const val KEY_SUMMARY_NOTIFICATIONS = "summary_notifications_enabled"
        private const val KEY_TRACKER_MONITORING = "tracker_monitoring_enabled"
        private const val KEY_LAST_TRACKER_SCAN = "last_tracker_scan_ms"
        private const val KEY_VPN_MODE = "vpn_mode_enabled"
        private const val KEY_NOTIFICATION_SENSITIVITY = "notification_sensitivity"
        private const val KEY_LAST_PERMISSION_SCAN = "last_permission_scan_ms"
        private const val KEY_BLOCKING_MODE = "blocking_mode"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getMonitoringMode(): MonitoringMode =
        MonitoringMode.fromString(prefs.getString(KEY_MONITORING_MODE, MonitoringMode.PERIODIC.name)!!)

    fun setMonitoringMode(mode: MonitoringMode) {
        prefs.edit().putString(KEY_MONITORING_MODE, mode.name).apply()
    }

    /** Flow — UI, mod değişince otomatik yenilenir. */
    fun observeMonitoringMode(): Flow<MonitoringMode> = callbackFlow {
        trySend(getMonitoringMode())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_MONITORING_MODE) trySend(getMonitoringMode())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isSuspiciousNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_SUSPICIOUS_NOTIFICATIONS, true)

    fun setSuspiciousNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUSPICIOUS_NOTIFICATIONS, enabled).apply()
    }

    fun isSummaryNotificationsEnabled(): Boolean =
        prefs.getBoolean(KEY_SUMMARY_NOTIFICATIONS, false)

    fun setSummaryNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUMMARY_NOTIFICATIONS, enabled).apply()
    }

    fun isTrackerMonitoringEnabled(): Boolean =
        prefs.getBoolean(KEY_TRACKER_MONITORING, true)

    fun setTrackerMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACKER_MONITORING, enabled).apply()
    }

    fun getLastTrackerScanMs(): Long =
        prefs.getLong(KEY_LAST_TRACKER_SCAN, 0L)

    fun saveLastTrackerScanMs() {
        prefs.edit().putLong(KEY_LAST_TRACKER_SCAN, System.currentTimeMillis()).apply()
    }

    fun observeLastTrackerScanMs(): Flow<Long> = callbackFlow {
        trySend(getLastTrackerScanMs())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LAST_TRACKER_SCAN) trySend(getLastTrackerScanMs())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun isVpnModeEnabled(): Boolean = prefs.getBoolean(KEY_VPN_MODE, false)

    fun setVpnModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VPN_MODE, enabled).apply()
    }

    fun observeVpnModeEnabled(): Flow<Boolean> = callbackFlow {
        trySend(isVpnModeEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_VPN_MODE) trySend(isVpnModeEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun getNotificationSensitivity(): NotificationSensitivity =
        NotificationSensitivity.fromString(
            prefs.getString(KEY_NOTIFICATION_SENSITIVITY, NotificationSensitivity.MEDIUM.name)!!
        )

    fun setNotificationSensitivity(sensitivity: NotificationSensitivity) {
        prefs.edit().putString(KEY_NOTIFICATION_SENSITIVITY, sensitivity.name).apply()
    }

    fun observeNotificationSensitivity(): Flow<NotificationSensitivity> = callbackFlow {
        trySend(getNotificationSensitivity())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_NOTIFICATION_SENSITIVITY) trySend(getNotificationSensitivity())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun getLastPermissionScanMs(): Long = prefs.getLong(KEY_LAST_PERMISSION_SCAN, 0L)

    fun saveLastPermissionScanMs() {
        prefs.edit().putLong(KEY_LAST_PERMISSION_SCAN, System.currentTimeMillis()).apply()
    }

    fun observeLastPermissionScanMs(): Flow<Long> = callbackFlow {
        trySend(getLastPermissionScanMs())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_LAST_PERMISSION_SCAN) trySend(getLastPermissionScanMs())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // -------------------------------------------------------------------------
    // DNS engelleme modu (reklam/tracker)
    // -------------------------------------------------------------------------

    /** Varsayılan OFF: kullanıcı bilinçli olarak açana kadar hiçbir şey engellenmez
     *  (uygulamaları sessizce bozmamak için — şeffaflık ilkesi). */
    fun getBlockingMode(): BlockingMode =
        BlockingMode.fromString(prefs.getString(KEY_BLOCKING_MODE, BlockingMode.OFF.name))

    fun setBlockingMode(mode: BlockingMode) {
        prefs.edit().putString(KEY_BLOCKING_MODE, mode.name).apply()
    }

    fun observeBlockingMode(): Flow<BlockingMode> = callbackFlow {
        trySend(getBlockingMode())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_BLOCKING_MODE) trySend(getBlockingMode())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun observeTrackerMonitoringEnabled(): Flow<Boolean> = callbackFlow {
        trySend(isTrackerMonitoringEnabled())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_TRACKER_MONITORING) trySend(isTrackerMonitoringEnabled())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
}
