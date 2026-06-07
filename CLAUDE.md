# PrivacyDroid — Claude Code Bağlamı

## Proje Özeti
PrivacyDroid, Android cihazlarda arka planda çalışan uygulamaların sensör erişimlerini,
ağ trafiğini ve izin kullanımlarını gerçek zamanlı olarak izleyen, loglayan ve
kullanıcıya raporlayan açık kaynaklı bir gizlilik monitörü uygulamasıdır.

**Temel felsefe:** Gözetlemeyi engelleyemiyorsan, görünür kıl. Kullanıcı neyin
ne yaptığını bilirse bilinçli kararlar alabilir.

---

## Teknik Kısıtlar ve Kararlar

### Platform
- **Hedef:** Android 8.0+ (API 26+), öncelikli test cihazı Android 12+
- **Dil:** Kotlin (Java interop gerekirse açıkça belirt)
- **iOS:** Şimdilik kapsam dışı — Apple sandbox kısıtları nedeniyle
- **Dağıtım:** F-Droid öncelikli, Play Store ikincil, APK doğrudan indirme üçüncül

### Mimari
- **Pattern:** MVVM + Clean Architecture
- **DI:** Hilt
- **DB:** Room (tüm loglar cihazda, hiçbir şey buluta gitmez)
- **Async:** Coroutines + Flow
- **UI:** Jetpack Compose
- **Navigation:** Navigation Component

### Kritik Prensipler
1. **Veri yerelliği:** Hiçbir kullanıcı verisi cihaz dışına çıkmaz. Analitik yok,
   crash reporting yok, bulut senkronizasyon yok.
2. **Root gerektirmez:** Tüm Faz 1 ve Faz 2 özellikleri root olmadan çalışmalı.
   Root gerektiren özellikler varsa açıkça etiketlenmeli.
3. **Pil dostu:** Arka plan servisi aggressive olmamalı. WorkManager ve
   minimum polling kullan.
4. **Şeffaf kod:** Açık kaynak. Her fonksiyon ne yaptığını açıklayan yorum içermeli.
5. **Engellenemezlik:** Play Store'a bağımlı olma. APK ve F-Droid ile dağıtılabilir ol.

### Kullanılan API'ler (Root Gerektirmez)
- `AppOpsManager` — izin kullanım geçmişi
- `UsageStatsManager` — uygulama kullanım istatistikleri
- `NetworkStatsManager` — uygulama bazlı veri kullanımı
- `ConnectivityManager` — ağ durumu
- `PackageManager` — kurulu uygulama bilgileri

### Kullanılan API'ler (Root Gerektirir — Faz 3+)
- `VpnService` — ağ trafiği yakalama (root gerektirmez ama VPN çakışması var)
- `/proc/net/` — aktif bağlantılar (root gerekebilir)
- `tcpdump` — paket analizi (root gerekir)

---

## Proje Yapısı

```
privacydroid/
├── app/
│   ├── src/main/
│   │   ├── java/io/privacydroid/
│   │   │   ├── data/
│   │   │   │   ├── local/          # Room DB, DAO'lar, Entity'ler
│   │   │   │   ├── repository/     # Repository implementasyonları
│   │   │   │   └── model/          # Domain modeller
│   │   │   ├── domain/
│   │   │   │   ├── usecase/        # Business logic
│   │   │   │   └── repository/     # Repository interface'leri
│   │   │   ├── service/
│   │   │   │   ├── PermissionMonitorService.kt
│   │   │   │   ├── NetworkMonitorService.kt
│   │   │   │   └── SensorWatchService.kt
│   │   │   ├── ui/
│   │   │   │   ├── dashboard/      # Ana ekran
│   │   │   │   ├── appdetail/      # Uygulama detay ekranı
│   │   │   │   ├── timeline/       # Zaman çizelgesi
│   │   │   │   ├── report/         # Rapor oluşturma
│   │   │   │   └── settings/       # Ayarlar
│   │   │   ├── worker/
│   │   │   │   └── PermissionScanWorker.kt
│   │   │   └── util/
│   │   │       ├── AppInfoHelper.kt
│   │   │       ├── PermissionHelper.kt
│   │   │       └── NotificationHelper.kt
│   │   └── res/
│   └── build.gradle.kts
├── CLAUDE.md                       # Bu dosya
├── ROADMAP.md                      # Faz planı
└── README.md                       # Kullanıcı dokümantasyonu
```

---

## Kod Standartları

### Kotlin Conventions
```kotlin
// Fonksiyon isimleri: ne yaptığını açıkça belirt
fun getPermissionAccessLogsForApp(packageName: String): Flow<List<PermissionLog>>

// Magic number yok
companion object {
    private const val SCAN_INTERVAL_MINUTES = 15L
    private const val LOG_RETENTION_DAYS = 30
}

// Suspend fonksiyonlar için her zaman hata yönetimi
suspend fun scanPermissions(): Result<List<PermissionLog>> = runCatching {
    // implementation
}
```

### Yorum Standardı
```kotlin
/**
 * AppOpsManager üzerinden son [intervalMinutes] dakika içinde
 * hangi uygulamaların hangi izinlere eriştiğini sorgular.
 *
 * Root gerektirmez — Android 5.0+ (API 21+) çalışır.
 * Android 6.0 altında bazı op kodları eksik olabilir.
 */
```

### Güvenlik Kuralları
- Kullanıcı verisini log'a yazdırma (Timber.d ile bile)
- SharedPreferences'a hassas veri yazma — EncryptedSharedPreferences kullan
- Ağ isteği yapma (intentional olsa bile PR'da açıkla)
- Üçüncü parti analytics/crash SDK ekleme

---

## Test Stratejisi

```
Unit Tests     → Domain & Repository katmanı, %80+ coverage hedef
Integration    → Room DB, WorkManager
UI Tests       → Compose UI, kritik akışlar
Manual Tests   → İzin monitoring doğruluğu (gerçek cihazda)
```

---

## Sık Kullanılan Komutlar

```bash
# Build
./gradlew assembleDebug

# Test
./gradlew test
./gradlew connectedAndroidTest

# Lint
./gradlew lint

# APK çıkar
./gradlew assembleRelease
```

---

## Önemli Notlar Claude Code İçin

1. Her faz tamamlanmadan bir sonrakine geçme
2. Yeni bir API kullanacaksan önce "root gerektirir mi?" kontrol et
3. Herhangi bir ağ isteği eklemeden önce sor
4. Room migration'larını her zaman yaz, otomatik migration kullanma
5. Compose UI'da state hoisting prensibine uy
6. WorkManager kullanırken Doze Mode'u test et

---

*Son güncelleme: FAZ 1 TAMAMLANDI — Tüm 1.1–1.8 görevleri bitti*
*Bir sonraki adım: Faz 2.1 — NetworkStatsManager wrapper (ağ trafiği izleme altyapısı)*
