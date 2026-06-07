package io.privacydroid.domain.model

enum class AppRiskLevel(val emoji: String, val label: String, val shortLabel: String) {
    LOW("🟢", "Temiz", "Temiz"),
    MEDIUM("🟡", "Dikkat", "Dikkat"),
    HIGH("🔴", "Riskli", "Riskli")
}
