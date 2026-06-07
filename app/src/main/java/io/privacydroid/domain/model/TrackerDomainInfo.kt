package io.privacydroid.domain.model

/**
 * Bilinen tracker/reklam domainleri için kullanıcıya gösterilecek insan-okur
 * açıklamalar. Detay popup'larında "Bu domain hakkında" metni olarak kullanılır.
 *
 * Eşleşme bulunamazsa kategoriye göre genel bir açıklama üretilir.
 */
object TrackerDomainInfo {

    // En sık karşılaşılan domainler için özel açıklamalar.
    private val descriptions: Map<String, String> = mapOf(
        "graph.facebook.com" to
            "Facebook'un kullanıcı davranışı takip sunucusu. Sosyal medya " +
            "aktivitenizi profillemek için kullanılır.",
        "facebook.com" to
            "Facebook'un takip altyapısı. Uygulama içi davranışınızı reklam " +
            "profilinize ekler.",
        "doubleclick.net" to
            "Google'ın reklam takip ağı. Kullandığınız uygulamalar üzerinden " +
            "reklam profili oluşturmak için kullanılır.",
        "googlesyndication.com" to
            "Google'ın reklam yayın ağı. Gösterilen reklamları ve " +
            "etkileşimlerinizi takip eder.",
        "google-analytics.com" to
            "Google Analytics — uygulama kullanım istatistiklerinizi toplar.",
        "app-measurement.com" to
            "Google/Firebase Analytics olay toplama sunucusu. Uygulama içi " +
            "davranışınızı ölçer.",
        "graph.instagram.com" to
            "Instagram (Meta) takip sunucusu. Etkinliğinizi reklam için profiller.",
        "ads.tiktok.com" to
            "TikTok reklam ve hedefleme sunucusu.",
        "analytics.tiktok.com" to
            "TikTok analitik sunucusu — uygulama içi davranışınızı izler.",
        "appsflyer.com" to
            "AppsFlyer — kurulum ve kullanıcı edinim attribution takibi yapar.",
        "adjust.com" to
            "Adjust — reklam attribution ve kullanıcı takibi servisi.",
        "crashlytics.com" to
            "Firebase Crashlytics — çökme raporları toplar (cihaz/kullanım verisi içerebilir).",
        "sentry.io" to
            "Sentry — hata raporlama servisi.",
        "branch.io" to
            "Branch — deep link ve attribution takibi.",
        "taboola.com" to
            "Taboola — içerik önerisi adıyla çalışan reklam takip ağı.",
        "criteo.com" to
            "Criteo — yeniden hedefleme (retargeting) reklam ağı.",
        "onesignal.com" to
            "OneSignal — push bildirim ve kullanıcı segmentasyon servisi.",
        "unity3d.com" to
            "Unity Ads — oyun içi reklam ve analitik ağı.",
        "applovin.com" to
            "AppLovin — mobil reklam ve hedefleme ağı."
    )

    /** Domain için açıklama döndürür; özel kayıt yoksa kategoriye göre üretir. */
    fun describe(domain: String, category: TrackerCategory): String {
        val d = domain.lowercase().removePrefix("~").substringBefore(" ")
        // Tam eşleşme veya alt-domain eşleşmesi ara.
        descriptions[d]?.let { return it }
        descriptions.entries.firstOrNull { (key, _) -> d.endsWith(key) }?.let { return it.value }
        return when (category) {
            TrackerCategory.SOCIAL ->
                "Sosyal medya takip sunucusu. Uygulama dışı davranışınızı sosyal " +
                "profilinizle ilişkilendirir."
            TrackerCategory.ANALYTICS ->
                "Analitik sunucusu. Uygulamayı nasıl kullandığınızı ölçer ve raporlar."
            TrackerCategory.ADVERTISING ->
                "Reklam ve hedefleme sunucusu. Size kişiselleştirilmiş reklam " +
                "göstermek için davranışınızı izler."
            TrackerCategory.ATTRIBUTION ->
                "Attribution sunucusu. Hangi reklamdan geldiğinizi ve kurulum " +
                "kaynağınızı takip eder."
            TrackerCategory.CRASH_REPORTING ->
                "Hata raporlama sunucusu. Çökme verileri cihaz bilgisi içerebilir."
            TrackerCategory.PUSH ->
                "Push bildirim servisi. Kullanıcı segmentasyonu için de kullanılabilir."
            TrackerCategory.CDN ->
                "İçerik dağıtım ağı (CDN). Genelde içerik sunar; takip amaçlı " +
                "olmayabilir ama trafik buradan geçer."
            TrackerCategory.UNKNOWN ->
                "Bu domain bilinen tracker listesinde, ancak kategorisi " +
                "belirlenemedi."
        }
    }
}
