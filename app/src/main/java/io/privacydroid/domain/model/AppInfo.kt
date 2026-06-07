package io.privacydroid.domain.model

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val installTimeMs: Long,
    val lastUpdateMs: Long,
    val isSystemApp: Boolean,
    val requestedPermissions: List<String>,
    val dangerousPermissions: List<String>,
    val backgroundBehavior: BackgroundBehavior,
    val riskLevel: AppRiskLevel,
    val riskReasons: List<String>,
    val detectedSdks: List<DetectedSdk>,
    val networkStats: AppNetworkStats = AppNetworkStats()
)

data class BackgroundBehavior(
    val hasBootReceiver: Boolean,
    val hasWakeLock: Boolean,
    val hasDozeypass: Boolean,    // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    val hasExactAlarm: Boolean,   // USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM
    val hasForegroundService: Boolean
) {
    val suspiciousCount: Int get() = listOf(
        hasBootReceiver, hasWakeLock, hasDozeypass, hasExactAlarm
    ).count { it }

    val label: String get() = when {
        suspiciousCount == 0 -> "Normal arka plan davranışı"
        suspiciousCount == 1 -> "Dikkat: 1 şüpheli davranış"
        else -> "Uyarı: $suspiciousCount şüpheli davranış"
    }
}
