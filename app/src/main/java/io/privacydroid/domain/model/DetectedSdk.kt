package io.privacydroid.domain.model

data class DetectedSdk(
    val name: String,           // "Facebook SDK"
    val description: String,    // "Davranış ve etkileşim takibi"
    val riskLevel: AppRiskLevel,
    val detectionKey: String    // eşleşme anahtarı — debug için
)

/** Bilinen SDK imzaları: paket adı içerdiği substring → SDK bilgisi */
val KNOWN_SDK_SIGNATURES: Map<String, DetectedSdk> = mapOf(
    "com.facebook"      to DetectedSdk("Facebook SDK",       "Kullanıcı davranış ve etkileşim takibi",          AppRiskLevel.HIGH,   "com.facebook"),
    "com.appsflyer"     to DetectedSdk("AppsFlyer",          "Kurulum ve kullanım attribution takibi",           AppRiskLevel.HIGH,   "com.appsflyer"),
    "com.adjust"        to DetectedSdk("Adjust",             "Mobil attribution ve reklam takibi",               AppRiskLevel.HIGH,   "com.adjust"),
    "com.braze"         to DetectedSdk("Braze",              "Kullanıcı davranışı ve pazarlama otomasyonu",      AppRiskLevel.HIGH,   "com.braze"),
    "com.appboy"        to DetectedSdk("Braze (Appboy)",     "Kullanıcı davranışı ve pazarlama otomasyonu",      AppRiskLevel.HIGH,   "com.appboy"),
    "com.amplitude"     to DetectedSdk("Amplitude Analytics","Ürün kullanım analitik takibi",                    AppRiskLevel.MEDIUM, "com.amplitude"),
    "com.mixpanel"      to DetectedSdk("Mixpanel",           "Kullanıcı etkileşimi ve funnel analitik",          AppRiskLevel.MEDIUM, "com.mixpanel"),
    "com.onesignal"     to DetectedSdk("OneSignal",          "Push bildirim gönderme servisi",                   AppRiskLevel.LOW,    "com.onesignal"),
    "com.crashlytics"   to DetectedSdk("Firebase Crashlytics","Uygulama hata raporlama",                         AppRiskLevel.LOW,    "com.crashlytics"),
    "com.google.firebase" to DetectedSdk("Firebase",         "Google analitik, veritabanı ve servisler",         AppRiskLevel.MEDIUM, "com.google.firebase"),
    "com.google.android.gms.ads" to DetectedSdk("AdMob",    "Google reklam ağı ve takip",                       AppRiskLevel.MEDIUM, "com.google.android.gms.ads"),
    "com.mopub"         to DetectedSdk("MoPub",              "Twitter/AppLovin reklam mediation",                AppRiskLevel.HIGH,   "com.mopub"),
    "com.unity3d"       to DetectedSdk("Unity Ads",          "Oyun içi reklam ağı",                             AppRiskLevel.MEDIUM, "com.unity3d"),
    "com.ironsource"    to DetectedSdk("IronSource",         "Oyun içi reklam mediation ve takip",              AppRiskLevel.MEDIUM, "com.ironsource"),
    "com.bytedance"     to DetectedSdk("TikTok/ByteDance SDK","Reklam ve kullanıcı davranış takibi",             AppRiskLevel.HIGH,   "com.bytedance"),
    "com.tiktok"        to DetectedSdk("TikTok SDK",         "TikTok reklam entegrasyonu ve takip",              AppRiskLevel.HIGH,   "com.tiktok"),
    "com.segment"       to DetectedSdk("Segment",            "Çok kanallı kullanıcı veri toplama",               AppRiskLevel.HIGH,   "com.segment"),
    "io.intercom"       to DetectedSdk("Intercom",           "Müşteri desteği ve davranış takibi",               AppRiskLevel.MEDIUM, "io.intercom"),
)
