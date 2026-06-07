package io.privacydroid.domain.model

data class AppNetworkStats(
    val txBytes24h: Long = 0L,   // Son 24 saatte gönderilen
    val rxBytes24h: Long = 0L,   // Son 24 saatte alınan
    val txBytes7d: Long = 0L,    // Son 7 günde gönderilen
    val rxBytes7d: Long = 0L,    // Son 7 günde alınan
    val isAnomaly: Boolean = false,
    val anomalyReason: String? = null
) {
    val totalBytes24h: Long get() = txBytes24h + rxBytes24h
    val totalBytes7d: Long get() = txBytes7d + rxBytes7d
}

fun Long.toMbString(): String = when {
    this <= 0L -> "0 B"
    this < 1_024L -> "$this B"
    this < 1_048_576L -> "${"%.1f".format(this / 1024.0)} KB"
    else -> "${"%.1f".format(this / 1_048_576.0)} MB"
}
