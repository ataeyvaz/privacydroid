package io.privacydroid.domain.model

data class DashboardStats(
    val cameraCount: Int = 0,
    val microphoneCount: Int = 0,
    val locationCount: Int = 0,
    val totalCount: Int = 0,
    val mostRiskyApp: RiskyApp? = null,
    val lastScanTimeMs: Long = 0L
)

data class RiskyApp(
    val packageName: String,
    val appName: String,
    val backgroundAccessCount: Int
)
