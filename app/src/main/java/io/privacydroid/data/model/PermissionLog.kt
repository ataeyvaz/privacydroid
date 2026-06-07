package io.privacydroid.data.model

/**
 * Domain modeli — UI ve UseCase katmanı bu sınıfı kullanır.
 * Entity'den ayrı tutulur; DB şeması değişse domain kodu etkilenmez.
 */
data class PermissionLog(
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val permissionType: PermissionType,
    val accessTime: Long,
    val durationMs: Long = 0L,
    val isBackground: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

enum class PermissionType(val opStr: String, val displayName: String) {
    CAMERA("android:camera", "Kamera"),
    MICROPHONE("android:record_audio", "Mikrofon"),
    LOCATION_FINE("android:fine_location", "Konum (Hassas)"),
    LOCATION_COARSE("android:coarse_location", "Konum (Yaklaşık)"),
    CONTACTS("android:read_contacts", "Rehber"),
    CALL_LOG("android:read_call_log", "Arama Geçmişi"),
    CALENDAR("android:read_calendar", "Takvim"),
    SMS("android:read_sms", "SMS"),
    STORAGE("android:read_external_storage", "Depolama"),
    PHONE("android:read_phone_state", "Telefon"),
    UNKNOWN("", "Diğer");

    companion object {
        fun fromOpStr(opStr: String): PermissionType =
            entries.firstOrNull { it.opStr == opStr } ?: UNKNOWN
    }
}
