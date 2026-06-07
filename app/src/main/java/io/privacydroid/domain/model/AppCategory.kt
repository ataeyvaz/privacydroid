package io.privacydroid.domain.model

/**
 * Uygulama kategorileri — paket adı ve uygulama adından tahmin edilir.
 * İzin risk hesabında ve risk kural motorunda kullanılır.
 */
enum class AppCategory {
    SOCIAL_MEDIA,  // Facebook, Instagram, TikTok, Snapchat…
    GAME,          // Oyunlar
    NEWS,          // Haber uygulamaları
    SHOPPING,      // E-ticaret
    HEADPHONE,     // Kulaklık/hoparlör kontrolü
    SMART_HOME,    // Akıllı ev / IoT
    FITNESS,       // Sağlık / spor
    NAVIGATION,    // Harita / navigasyon
    WEATHER,       // Hava durumu
    FLASHLIGHT,    // El feneri
    CALCULATOR,    // Hesap makinesi
    CLOCK,         // Saat / alarm
    UNKNOWN
}

/**
 * Paket adı ve uygulama adından kategori tahmin eder.
 * Küçük harf karşılaştırması yapılır — büyük/küçük harf duyarlı değil.
 */
fun detectAppCategory(packageName: String, appName: String): AppCategory {
    val pkg  = packageName.lowercase()
    val name = appName.lowercase()

    return when {
        // ── Sosyal medya ─────────────────────────────────────────────
        "facebook" in pkg || "instagram" in pkg || "twitter" in pkg ||
        "tiktok"   in pkg || "snapchat"  in pkg || "whatsapp" in pkg ||
        "telegram" in pkg || "discord"   in pkg || "reddit"   in pkg ||
        "linkedin" in pkg || "pinterest" in pkg || "tumblr"   in pkg ||
        "x.com"    in pkg || "threads"   in pkg -> AppCategory.SOCIAL_MEDIA

        // ── Oyunlar ───────────────────────────────────────────────────
        "game"   in pkg  || "games"   in pkg  || "gaming"  in pkg  ||
        "oyun"   in pkg  || "puzzle"  in pkg  || "arcade"  in pkg  ||
        "game"   in name || "oyun"    in name || "puzzle"  in name ||
        "racing" in name || "shooter" in name || "quest"   in name -> AppCategory.GAME

        // ── Haber ─────────────────────────────────────────────────────
        "news"   in pkg  || "haber"   in pkg  || "gazete"  in pkg  ||
        "news"   in name || "haber"   in name || "gazete"  in name -> AppCategory.NEWS

        // ── Alışveriş ─────────────────────────────────────────────────
        "shop"        in pkg  || "market"    in pkg  || "store"  in pkg  ||
        "trendyol"    in pkg  || "hepsiburada" in pkg || "amazon" in pkg  ||
        "n11"         in pkg  || "ciceksepeti" in pkg || "gittigidiyor" in pkg ||
        "aliexpress"  in pkg  || "ebay"      in pkg  ||
        "shopping"    in name || "alışveriş" in name -> AppCategory.SHOPPING

        // ── Kulaklık / ses cihazları ──────────────────────────────────
        "headphone" in name || "kulaklık"  in name || "earphone" in name ||
        "headset"   in name || "speaker"   in name || "earbuds"  in name ||
        "bose"      in pkg  || "sony.headphones" in pkg -> AppCategory.HEADPHONE

        // ── Akıllı ev / IoT ──────────────────────────────────────────
        "smarthome" in pkg  || "smartthings" in pkg || "tuya"  in pkg  ||
        "philips.hue" in pkg || "nest"       in pkg || "ring"  in pkg  ||
        "smart"    in name  || "iot"         in name || "bulb" in name  ||
        "thermostat" in name || "sensor"     in name -> AppCategory.SMART_HOME

        // ── Sağlık / fitness ──────────────────────────────────────────
        "fitness" in pkg  || "health"  in pkg  || "garmin"  in pkg  ||
        "fitbit"  in pkg  || "strava"  in pkg  || "samsung.shealth" in pkg ||
        "fitness" in name || "health"  in name || "workout" in name ||
        "sağlık"  in name || "egzersiz" in name -> AppCategory.FITNESS

        // ── Navigasyon / harita ───────────────────────────────────────
        "maps"       in pkg  || "navigation" in pkg  || "waze" in pkg   ||
        "yandex.navi" in pkg || "google.maps" in pkg ||
        "maps"       in name || "navigation" in name || "yol tarifi" in name -> AppCategory.NAVIGATION

        // ── Hava durumu ───────────────────────────────────────────────
        "weather"  in pkg  || "hava"     in pkg  ||
        "weather"  in name || "hava durumu" in name -> AppCategory.WEATHER

        // ── El feneri ─────────────────────────────────────────────────
        "flashlight" in pkg  || "torch" in pkg  || "fener" in pkg  ||
        "flashlight" in name || "torch" in name || "fener" in name -> AppCategory.FLASHLIGHT

        // ── Hesap makinesi ────────────────────────────────────────────
        "calculator" in pkg  || "hesap" in pkg  ||
        "calculator" in name || "hesap makinesi" in name -> AppCategory.CALCULATOR

        // ── Saat / alarm ──────────────────────────────────────────────
        "clock" in pkg  || "alarm" in pkg  ||
        "clock" in name || "alarm" in name || "saat" in name -> AppCategory.CLOCK

        else -> AppCategory.UNKNOWN
    }
}
