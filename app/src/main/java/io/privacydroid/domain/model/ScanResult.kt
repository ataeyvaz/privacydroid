package io.privacydroid.domain.model

data class ScanResult(
    val savedLogCount: Int,
    val suspiciousActivities: List<io.privacydroid.domain.usecase.SuspiciousActivity>,
    val scanTimeMs: Long
) {
    val hasSuspiciousActivity: Boolean get() = suspiciousActivities.isNotEmpty()
}

enum class SuspiciousReason(val displayName: String) {
    NIGHT_ACCESS("Gece Erişimi"),
    BACKGROUND_SENSOR_ACCESS("Arka Plan Sensör Erişimi"),
    LOCATION_BURST("Yoğun Konum Sorgusu")
}
