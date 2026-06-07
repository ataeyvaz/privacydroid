package io.privacydroid.data.model

enum class NotificationSensitivity(
    val displayName: String,
    val descriptionText: String
) {
    CRITICAL_ONLY(
        "Sadece Kritik",
        "Yalnızca gece kamera/mikrofon erişimleri için anlık bildirim"
    ),
    MEDIUM(
        "Orta",
        "Gece ve arka plan sensör erişimleri için anlık bildirim"
    ),
    ALL(
        "Tümü",
        "Tüm şüpheli aktiviteler için anlık bildirim"
    );

    companion object {
        fun fromString(s: String): NotificationSensitivity =
            entries.firstOrNull { it.name == s } ?: MEDIUM
    }
}
