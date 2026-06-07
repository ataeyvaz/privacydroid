package io.privacydroid.domain.model

/**
 * Korelasyon motoru şüphe seviyesi.
 *
 * HIGH  → arka plan + >1MB veri + medya dosyası yok (dosyayı kaydetmeden gönderiyor)
 * HIGH  → arka plan + gece saati + herhangi ağ trafiği
 * MEDIUM→ arka plan + >100KB veri
 * LOW   → ön plan erişimi veya veri yok
 */
enum class SuspicionLevel(
    val displayName: String,
    val shortLabel: String
) {
    LOW("Düşük", "DÜŞÜK"),
    MEDIUM("Orta", "ORTA"),
    HIGH("Yüksek", "YÜKSEK");

    companion object {
        fun calculate(
            isBackground: Boolean,
            isNightTime: Boolean,
            networkBytesSent: Long,
            newMediaCreated: Boolean,
            accessType: String
        ): SuspicionLevel {
            val isSensorAccess = accessType == "CAMERA" || accessType == "MICROPHONE"

            return when {
                // Dosyayı kaydetmeden büyük veri gönderimi — en riskli
                isBackground && isSensorAccess &&
                        networkBytesSent > 1_000_000L && !newMediaCreated -> HIGH

                // Gece saati arka plan trafiği
                isBackground && isSensorAccess &&
                        isNightTime && networkBytesSent > 0L -> HIGH

                // Arka planda kayda değer trafik
                isBackground && networkBytesSent > 100_000L -> MEDIUM

                else -> LOW
            }
        }

        fun fromString(value: String): SuspicionLevel =
            entries.firstOrNull { it.name == value } ?: LOW
    }
}
