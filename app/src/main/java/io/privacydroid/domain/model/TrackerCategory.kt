package io.privacydroid.domain.model

enum class TrackerCategory(val displayName: String, val emoji: String) {
    SOCIAL("Sosyal medya takibi", "👥"),
    ANALYTICS("Analitik ve kullanım takibi", "📊"),
    ADVERTISING("Reklam ve hedefleme", "📢"),
    CDN("İçerik dağıtım ağı", "🌐"),
    CRASH_REPORTING("Hata raporlama", "🐛"),
    ATTRIBUTION("Kurulum attribution takibi", "📱"),
    PUSH("Push bildirim servisi", "🔔"),
    UNKNOWN("Bilinmeyen tracker", "❓");

    companion object {
        fun fromString(value: String): TrackerCategory =
            entries.firstOrNull { it.name == value } ?: UNKNOWN
    }
}

/** Domain adından kategori tahmin eder. */
fun classifyTrackerDomain(domain: String): TrackerCategory {
    val d = domain.lowercase()
    return when {
        "facebook" in d || "instagram" in d || "twitter" in d ||
        "tiktok" in d || "snapchat" in d || "reddit" in d ||
        "linkedin" in d || "bytedance" in d || "snssdk" in d -> TrackerCategory.SOCIAL

        "analytics" in d || "amplitude" in d || "mixpanel" in d ||
        "segment" in d || "heap" in d || "hotjar" in d ||
        "app-measurement" in d || "firebase" in d || "flurry" in d -> TrackerCategory.ANALYTICS

        "doubleclick" in d || "googlesyndication" in d || "adservice" in d ||
        "mopub" in d || "admob" in d || "applovin" in d || "ironsource" in d ||
        "mintegral" in d || "inmobi" in d || "moat" in d ||
        "criteo" in d || "adnxs" in d -> TrackerCategory.ADVERTISING

        "appsflyer" in d || "adjust" in d || "branch" in d ||
        "kochava" in d || "singular" in d || "attribution" in d -> TrackerCategory.ATTRIBUTION

        "crashlytics" in d || "sentry" in d || "bugsnag" in d ||
        "rollbar" in d || "instabug" in d -> TrackerCategory.CRASH_REPORTING

        "onesignal" in d || "braze" in d || "appboy" in d ||
        "leanplum" in d || "clevertap" in d -> TrackerCategory.PUSH

        "cloudfront" in d || "akamai" in d || "fastly" in d ||
        "cloudflare" in d || "cdn" in d -> TrackerCategory.CDN

        else -> TrackerCategory.UNKNOWN
    }
}
