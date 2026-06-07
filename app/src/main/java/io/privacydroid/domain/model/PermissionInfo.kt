package io.privacydroid.domain.model

/**
 * Her Android izni için görüntü adı, emoji, risk seviyesi ve isteğe bağlı bağlam notu.
 * [contextNote] null değilse izin satırının altında küçük açıklama olarak gösterilir.
 */
data class PermissionDisplay(
    val emoji: String,
    val label: String,
    val riskLevel: AppRiskLevel,
    val contextNote: String? = null
)

/**
 * android.permission.* → kullanıcı dostu Türkçe ad + risk seviyesi.
 *
 * Listeye eklenmeyen izinler için [unknownPermission] fallback kullanılır.
 * Anahtarlar: izin adının son kısmı (substringAfterLast(".")).
 */
val PERMISSION_DISPLAY_MAP: Map<String, PermissionDisplay> = mapOf(

    // ── KONUM ──────────────────────────────────────────────────────────
    "ACCESS_FINE_LOCATION" to PermissionDisplay(
        "📍", "Hassas Konum (GPS)", AppRiskLevel.MEDIUM),
    "ACCESS_COARSE_LOCATION" to PermissionDisplay(
        "📍", "Yaklaşık Konum (Wi-Fi/Hücresel)", AppRiskLevel.MEDIUM),
    "ACCESS_BACKGROUND_LOCATION" to PermissionDisplay(
        "📍", "Arka Planda Konum (7/24 izleme)", AppRiskLevel.HIGH),

    // ── DEPOLAMA ───────────────────────────────────────────────────────
    "READ_EXTERNAL_STORAGE" to PermissionDisplay(
        "💾", "Dosya Okuma", AppRiskLevel.MEDIUM),
    "WRITE_EXTERNAL_STORAGE" to PermissionDisplay(
        "💾", "Dosya Yazma/Değiştirme", AppRiskLevel.MEDIUM),
    "READ_MEDIA_IMAGES" to PermissionDisplay(
        "🖼️", "Fotoğrafları Okuma", AppRiskLevel.MEDIUM),
    "READ_MEDIA_VIDEO" to PermissionDisplay(
        "🎬", "Videoları Okuma", AppRiskLevel.MEDIUM),
    "READ_MEDIA_AUDIO" to PermissionDisplay(
        "🎵", "Ses Dosyalarını Okuma", AppRiskLevel.MEDIUM),
    "MANAGE_EXTERNAL_STORAGE" to PermissionDisplay(
        "💾", "Tüm Dosya Sistemi Erişimi (çok tehlikeli)", AppRiskLevel.HIGH),

    // ── KAMERA VE SES ──────────────────────────────────────────────────
    "CAMERA" to PermissionDisplay(
        "📷", "Kamera", AppRiskLevel.MEDIUM),
    "RECORD_AUDIO" to PermissionDisplay(
        "🎤", "Mikrofon", AppRiskLevel.MEDIUM),

    // ── KİŞİSEL VERİ ──────────────────────────────────────────────────
    "READ_CONTACTS" to PermissionDisplay(
        "👥", "Rehberi Okuma", AppRiskLevel.MEDIUM),
    "WRITE_CONTACTS" to PermissionDisplay(
        "👥", "Rehbere Yazma", AppRiskLevel.MEDIUM),
    "GET_ACCOUNTS" to PermissionDisplay(
        "👤", "Hesap Listesi", AppRiskLevel.MEDIUM),
    "READ_CALL_LOG" to PermissionDisplay(
        "📞", "Arama Geçmişini Okuma", AppRiskLevel.HIGH),
    "WRITE_CALL_LOG" to PermissionDisplay(
        "📞", "Arama Geçmişine Yazma", AppRiskLevel.HIGH),
    "PROCESS_OUTGOING_CALLS" to PermissionDisplay(
        "📞", "Aramaları İzleme", AppRiskLevel.HIGH),
    "READ_SMS" to PermissionDisplay(
        "✉️", "SMS Okuma", AppRiskLevel.HIGH),
    "SEND_SMS" to PermissionDisplay(
        "✉️", "SMS Gönderme", AppRiskLevel.HIGH),
    "RECEIVE_SMS" to PermissionDisplay(
        "✉️", "SMS Alma", AppRiskLevel.HIGH),
    "READ_MMS" to PermissionDisplay(
        "✉️", "MMS Okuma", AppRiskLevel.HIGH),

    // ── TAKVİM ────────────────────────────────────────────────────────
    "READ_CALENDAR" to PermissionDisplay(
        "📅", "Takvimi Okuma", AppRiskLevel.MEDIUM),
    "WRITE_CALENDAR" to PermissionDisplay(
        "📅", "Takvime Yazma", AppRiskLevel.MEDIUM),

    // ── TELEFON ───────────────────────────────────────────────────────
    "READ_PHONE_STATE" to PermissionDisplay(
        "📱", "Telefon Durumu (IMEI, numara)", AppRiskLevel.HIGH),
    "CALL_PHONE" to PermissionDisplay(
        "📞", "Arama Yapma", AppRiskLevel.MEDIUM),
    "READ_PHONE_NUMBERS" to PermissionDisplay(
        "📱", "Telefon Numarasını Okuma", AppRiskLevel.HIGH),
    "USE_SIP" to PermissionDisplay(
        "📞", "SIP/VoIP Araması", AppRiskLevel.MEDIUM),
    "ANSWER_PHONE_CALLS" to PermissionDisplay(
        "📞", "Aramaları Yanıtlama", AppRiskLevel.MEDIUM),

    // ── BİYOMETRİK ───────────────────────────────────────────────────
    "USE_BIOMETRIC" to PermissionDisplay(
        "👆", "Biyometrik Kimlik Doğrulama", AppRiskLevel.LOW),
    "USE_FINGERPRINT" to PermissionDisplay(
        "👆", "Parmak İzi", AppRiskLevel.LOW),

    // ── SENSÖRLER ────────────────────────────────────────────────────
    "BODY_SENSORS" to PermissionDisplay(
        "❤️", "Sağlık Sensörleri (nabız, adım)", AppRiskLevel.MEDIUM),
    "BODY_SENSORS_BACKGROUND" to PermissionDisplay(
        "❤️", "Arka Planda Sağlık Sensörleri", AppRiskLevel.HIGH),
    "ACTIVITY_RECOGNITION" to PermissionDisplay(
        "🏃", "Fiziksel Aktivite Takibi", AppRiskLevel.MEDIUM),

    // ── BLUETOOTH ────────────────────────────────────────────────────
    "BLUETOOTH_SCAN" to PermissionDisplay(
        "📡", "Bluetooth Cihaz Tarama", AppRiskLevel.LOW),
    "BLUETOOTH_CONNECT" to PermissionDisplay(
        "📡", "Bluetooth Bağlantısı", AppRiskLevel.LOW),
    "BLUETOOTH_ADVERTISE" to PermissionDisplay(
        "📡", "Bluetooth Reklam Yayını", AppRiskLevel.LOW),
    "BLUETOOTH" to PermissionDisplay(
        "📡", "Bluetooth", AppRiskLevel.LOW),
    "BLUETOOTH_ADMIN" to PermissionDisplay(
        "📡", "Bluetooth Yönetimi", AppRiskLevel.LOW),

    // ── AĞ ───────────────────────────────────────────────────────────
    "INTERNET" to PermissionDisplay(
        "🌐", "İnternete Erişim", AppRiskLevel.MEDIUM,
        "Ağ açıkken veri gönderip alabilir, ağı kendisi açamaz"),
    "ACCESS_NETWORK_STATE" to PermissionDisplay(
        "🌐", "Ağ Durumunu Okuma", AppRiskLevel.LOW,
        "Ağ bağlantısı var mı yok mu görebilir, bağlantıyı değiştiremez"),
    "ACCESS_WIFI_STATE" to PermissionDisplay(
        "📶", "Wi-Fi Durumunu Okuma", AppRiskLevel.LOW,
        "Wi-Fi açık mı kapalı mı görebilir, açıp kapatamaz"),
    "CHANGE_WIFI_STATE" to PermissionDisplay(
        "📶", "Wi-Fi'ı Açma/Kapatma", AppRiskLevel.HIGH,
        "Siz uyurken Wi-Fi'ı kendisi açıp veri gönderebilir, sonra kapatabilir"),
    "CHANGE_NETWORK_STATE" to PermissionDisplay(
        "🌐", "Ağ Bağlantısını Değiştirme", AppRiskLevel.HIGH,
        "Mobil data ve diğer ağ bağlantılarını istediği zaman açıp kapatabilir"),
    "NEARBY_WIFI_DEVICES" to PermissionDisplay(
        "📶", "Yakın Wi-Fi Cihazları", AppRiskLevel.MEDIUM),

    // ── SİSTEM / ARKA PLAN ──────────────────────────────────────────
    "RECEIVE_BOOT_COMPLETED" to PermissionDisplay(
        "🔄", "Açılışta Otomatik Başlama", AppRiskLevel.MEDIUM),
    "WAKE_LOCK" to PermissionDisplay(
        "⚡", "Uyku Modunu Engelleme", AppRiskLevel.LOW),
    "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to PermissionDisplay(
        "🔋", "Pil Tasarrufunu Devre Dışı Bırakma", AppRiskLevel.MEDIUM),
    "USE_EXACT_ALARM" to PermissionDisplay(
        "⏰", "Tam Zamanlı Alarm", AppRiskLevel.MEDIUM),
    "SCHEDULE_EXACT_ALARM" to PermissionDisplay(
        "⏰", "Zamanlı Görev", AppRiskLevel.MEDIUM),
    "FOREGROUND_SERVICE" to PermissionDisplay(
        "▶️", "Ön Plan Servisi", AppRiskLevel.LOW),
    "FOREGROUND_SERVICE_CAMERA" to PermissionDisplay(
        "📷", "Ön Plan Kamera Servisi", AppRiskLevel.MEDIUM),
    "FOREGROUND_SERVICE_MICROPHONE" to PermissionDisplay(
        "🎤", "Ön Plan Mikrofon Servisi", AppRiskLevel.MEDIUM),
    "FOREGROUND_SERVICE_LOCATION" to PermissionDisplay(
        "📍", "Ön Plan Konum Servisi", AppRiskLevel.MEDIUM),
    "POST_NOTIFICATIONS" to PermissionDisplay(
        "🔔", "Bildirim Gönderme", AppRiskLevel.LOW),
    "VIBRATE" to PermissionDisplay(
        "📳", "Titreşim", AppRiskLevel.LOW),
    "FLASHLIGHT" to PermissionDisplay(
        "🔦", "El Feneri", AppRiskLevel.LOW),

    // ── GİZLİLİK / GÜVENLİK ─────────────────────────────────────────
    "HIDE_OVERLAY_WINDOWS" to PermissionDisplay(
        "🪟", "Ekran Üstü Pencere Gizleme", AppRiskLevel.MEDIUM),
    "SYSTEM_ALERT_WINDOW" to PermissionDisplay(
        "🪟", "Diğer Uygulamaların Üstünde Görünme", AppRiskLevel.HIGH),
    "BIND_ACCESSIBILITY_SERVICE" to PermissionDisplay(
        "♿", "Erişilebilirlik Servisi", AppRiskLevel.HIGH),
    "PACKAGE_USAGE_STATS" to PermissionDisplay(
        "📊", "Uygulama Kullanım İstatistikleri", AppRiskLevel.MEDIUM),
    "QUERY_ALL_PACKAGES" to PermissionDisplay(
        "📦", "Kurulu Uygulamaları Görme", AppRiskLevel.LOW),
    "UWB_RANGING" to PermissionDisplay(
        "📡", "UWB Mesafe Ölçümü", AppRiskLevel.MEDIUM),
)

/** Mapping'de bulunamayan izinler için fallback. */
fun unknownPermission(permShortName: String) = PermissionDisplay(
    "⚠️",
    permShortName.replace("_", " ").lowercase()
        .replaceFirstChar { it.uppercase() },
    AppRiskLevel.MEDIUM
)

/** Tam izin adından statik display bilgisini döner (bağlam yok). */
fun resolvePermissionDisplay(fullPermissionName: String): PermissionDisplay {
    val key = fullPermissionName.substringAfterLast(".")
    return PERMISSION_DISPLAY_MAP[key] ?: unknownPermission(key)
}

/**
 * Bağlam duyarlı izin risk hesabı.
 *
 * Aynı Bluetooth iznini sosyal medya uygulaması istiyorsa 🔴, navigasyon istiyorsa 🟢 döner.
 * Bağlam notu (contextNote), kullanıcıya neden riskli olduğunu açıklar.
 */
fun resolvePermissionDisplayContextual(
    fullPermissionName: String,
    packageName: String,
    appName: String
): PermissionDisplay {
    val base = resolvePermissionDisplay(fullPermissionName)
    val key  = fullPermissionName.substringAfterLast(".")
    val cat  = detectAppCategory(packageName, appName)

    val isSocialOrGame = cat == AppCategory.SOCIAL_MEDIA || cat == AppCategory.GAME
    val isNewsOrShop   = cat == AppCategory.NEWS || cat == AppCategory.SHOPPING

    return when (key) {

        // ── Bluetooth ─────────────────────────────────────────────────
        "BLUETOOTH_SCAN", "BLUETOOTH_CONNECT",
        "BLUETOOTH_ADVERTISE", "BLUETOOTH", "BLUETOOTH_ADMIN" -> when (cat) {
            AppCategory.SOCIAL_MEDIA, AppCategory.GAME,
            AppCategory.NEWS, AppCategory.SHOPPING ->
                base.copy(
                    riskLevel = AppRiskLevel.HIGH,
                    contextNote = "Sosyal medya/oyun uygulamalarının Bluetooth iznine " +
                            "ihtiyacı yoktur. Etrafınızdaki cihazları tarayarak konumunuzu tespit edebilir."
                )
            AppCategory.HEADPHONE, AppCategory.SMART_HOME,
            AppCategory.FITNESS, AppCategory.NAVIGATION ->
                base.copy(riskLevel = AppRiskLevel.LOW, contextNote = null)
            else ->
                base.copy(
                    riskLevel = AppRiskLevel.MEDIUM,
                    contextNote = "Bu uygulama için Bluetooth izninin gerekliliği belirsiz."
                )
        }

        // ── Hassas/Yaklaşık Konum ─────────────────────────────────────
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION" -> when {
            isSocialOrGame || isNewsOrShop ->
                base.copy(
                    riskLevel = AppRiskLevel.HIGH,
                    contextNote = "Sosyal medya/oyun/alışveriş uygulamalarında konum izni " +
                            "genellikle reklam hedefleme amacıyla kullanılır."
                )
            cat == AppCategory.NAVIGATION || cat == AppCategory.WEATHER ->
                base.copy(riskLevel = AppRiskLevel.MEDIUM, contextNote = null)
            else -> base
        }

        // ── Arka Planda Konum ─────────────────────────────────────────
        "ACCESS_BACKGROUND_LOCATION" -> when {
            isSocialOrGame || isNewsOrShop ->
                base.copy(
                    riskLevel = AppRiskLevel.HIGH,
                    contextNote = "Sürekli arka plan konum takibi bu tür uygulamalar için anormal."
                )
            else -> base
        }

        // ── Hesap Listesi ─────────────────────────────────────────────
        "GET_ACCOUNTS" -> when (cat) {
            AppCategory.SOCIAL_MEDIA ->
                base.copy(
                    riskLevel = AppRiskLevel.HIGH,
                    contextNote = "Cihazdaki tüm hesap listesine erişiyor — kimlik profili çıkarma riski."
                )
            else -> base
        }

        // ── Telefon Durumu (IMEI) ─────────────────────────────────────
        "READ_PHONE_STATE" -> when (cat) {
            AppCategory.SOCIAL_MEDIA, AppCategory.GAME ->
                base.copy(
                    riskLevel = AppRiskLevel.HIGH,
                    contextNote = "IMEI ve telefon numaranıza erişiyor — cihaz parmak izi oluşturabilir."
                )
            else -> base
        }

        // ── SMS (sosyal medyada normalden de tehlikeli) ────────────────
        "READ_SMS", "SEND_SMS", "RECEIVE_SMS" -> when (cat) {
            AppCategory.SOCIAL_MEDIA, AppCategory.GAME ->
                base.copy(
                    riskLevel = AppRiskLevel.HIGH,
                    contextNote = "Sosyal medya uygulamaları SMS'lerinize erişmemelidir."
                )
            else -> base
        }

        else -> base
    }
}
