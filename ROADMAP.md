# PrivacyDroid — Geliştirme Yol Haritası

## Vizyon
Android cihazlarda arka planda ne olduğunu görünür kılan,
kullanıcıya gücü geri veren, açık kaynak bir gizlilik monitörü.

---

## Faz Özeti

| Faz | İsim | Süre (tahmini) | Durum |
|-----|------|----------------|-------|
| 0 | Planlama & Kurulum | 1 gün | ✅ Tamamlandı |
| 1 | İzin Monitörü | 1-2 hafta | ✅ Tamamlandı |
| 2 | Ağ Trafiği İzleme | 2-3 hafta | ⏳ Bekliyor |
| 3 | Rapor & İfşa Motoru | 1-2 hafta | ⏳ Bekliyor |
| 4 | Gelişmiş Analiz | 3-4 hafta | ⏳ Bekliyor |
| 5 | Topluluk & Dağıtım | 2 hafta | ⏳ Bekliyor |

---

## FAZ 0 — Planlama & Kurulum ✅

### Tamamlanan
- [x] Proje mimarisi kararları
- [x] CLAUDE.md hazırlandı
- [x] ROADMAP.md hazırlandı
- [x] Tehdit modeli ve kapsam belirlendi

### Çıktılar
- CLAUDE.md
- ROADMAP.md

---

## FAZ 1 — İzin Monitörü 🔄

### Hedef
"Gece 3'te hangi uygulama mikrofonumu açtı?" sorusunu cevaplayabilmek.

### Kapsam
Root gerektirmez. Sadece `AppOpsManager` ve `UsageStatsManager` API'leri.

### Görevler

#### 1.1 — Proje İskeleti ✅
- [x] Android Studio projesi oluştur (Kotlin, Compose, API 26+)
- [x] `build.gradle.kts` bağımlılıkları ekle (Hilt, Room, Compose, Coroutines, WorkManager)
- [x] Temel paket yapısını oluştur
- [x] Hilt Application sınıfı (`PrivacyDroidApp`)
- [x] Temel tema ve renk paleti (koyu tema öncelikli)

#### 1.2 — Veri Katmanı ✅
- [x] `PermissionLog` entity tanımla (id, packageName, appName, permissionType, accessTime, durationMs, isBackground, createdAt)
- [x] `PermissionLogDao` (insert, queryByApp, queryByTime, queryByPermission, deleteOlderThan, existsLog)
- [x] `AppDatabase` Room tanımı
- [x] `PermissionRepository` interface ve implementasyon
- [x] `AppOpsWrapper` — 8 op kodu, Android 10+/legacy compat, ön plan/arka plan ayrımı

#### 1.3 — İzin Tarama Motoru ✅
- [x] `AppOpsWrapper` — CAMERA, RECORD_AUDIO, FINE_LOCATION, COARSE_LOCATION, READ_CONTACTS, READ_CALL_LOG, READ_SMS, PROCESS_OUTGOING_CALLS
- [x] `PermissionScanUseCase` — tüm uygulamaları tara, yeni erişimleri tespit et
- [x] Delta tespiti — SharedPreferences'ta son tarama zamanı, yalnızca yeni kayıtlar işlenir
- [x] Şüpheli aktivite kriterleri: gece 00:00-06:00, arka plan sensör, 1 saatte 10+ konum
- [x] `PermissionScanWorker` use case ile entegre edildi
- [x] Unit testler: 6 senaryo (gece tespiti, gündüz negatif, arka plan, burst, eşik altı, boş tarama)
- [x] DAO integration testleri (androidTest)

#### 1.4 — Arka Plan Servisi ✅
- [x] `WorkManagerHelper` — Constraints (pil düşükse çalışma), BackoffPolicy.EXPONENTIAL (5→10→20 dk), flex window
- [x] Doze Mode uyumluluğu: delta tespiti + ADB test komutları dokümante edildi
- [x] `BootReceiver` — BOOT_COMPLETED + QUICKBOOT_POWERON (Xiaomi/HTC), HiltAndroidEntryPoint
- [x] `PermissionMonitorService` — ForegroundService (dataSync), 2 dk poll, START_STICKY, "Durdur" aksiyonu
- [x] Pil ölçümü: scan süresi loglanır, >2000ms uyarı verir; SecurityException → retry durdurulur
- [x] `MonitoringMode` enum (PERIODIC/REALTIME) + `SettingsRepository` (Flow desteği)
- [x] `SettingsScreen` + `SettingsViewModel` — mod toggle, bildirim toggle'ları
- [x] Unit testler: WorkManagerHelper (4 test)

#### 1.5 — Bildirim Sistemi ✅
- [x] Bildirim kanalları gözden geçirildi: SUSPICIOUS=HIGH, SUMMARY=LOW, SERVICE=MIN — doğru
- [x] Detaylı başlık/içerik: "[App] gece erişti" + "Gece 03:24'te mikrofonunuza arka planda erişti"
- [x] Aksiyon butonları: "Detayları Gör" (AppDetailScreen deep link) + "İzni Kaldır" (sistem ayarları)
- [x] Spam önleme: `NotificationThrottleTracker` — (app, reason) başına 1 saat cooldown
- [x] Bildirim gruplama: GROUP_KEY_SUSPICIOUS + 3+ app → özet bildirim (setGroupSummary)
- [x] Günlük özet: `DailySummaryWorker` — sabah 09:00, PeriodicWork + initial delay hesabı
- [x] Settings toggle — özet bildirimi açıp kapatılabilir
- [x] Deep link: bildirim tıklaması MainActivity'ye EXTRA_NAVIGATE_TO_PACKAGE iletir
- [x] Debug menüsü (yalnızca DEBUG build): 5 bildirim testi, scan tetikleme, throttle sıfırlama, WorkManager araçları
- [x] Testler: NotificationThrottleTrackerTest (6), DailySummaryWorkerTest (4)

#### 1.6 — Dashboard UI ✅
- [x] Dashboard gerçek veriye bağlandı: GetDashboardStatsUseCase → kamera/mikrofon/konum sayıları, en riskli uygulama, son tarama zamanı ("X dk önce")
- [x] Pull-to-refresh → WorkManagerHelper.triggerImmediateScan() (OneTimeWorkRequest)
- [x] Boş durum: "Henüz tarama yapılmadı" + "Şimdi Tara" butonu / filtre boşsa "Bu kriterde erişim bulunamadı"
- [x] TimelineCanvas (Canvas tabanlı): 24 saatlik grid, gece bandı (kırmızımsı 00-06), dolu daire=arka plan, boş daire=ön plan, her izin türü renk kodlu
- [x] Pinch-to-zoom (detectTransformGestures, X ekseni 0.5x–6x) + yatay kaydırma
- [x] FilterBar: zaman (bugün/hafta/ay) + izin türü MultiSelect + arka plan toggle + gece toggle
- [x] Filtreler DB seviyesinde: FilterQueryBuilder → SimpleSQLiteQuery + @RawQuery
- [x] TimelineViewModel: flatMapLatest ile filter değişince DB sorgusu otomatik tetiklenir
- [x] Unit testler: FilterQueryBuilderTest (8 test)

#### 1.7 — Uygulama Detay Ekranı ✅
- [x] Uygulama ikonu (PackageManager → Drawable → ImageBitmap, async) + ad + paket adı
- [x] Risk skoru halkası (Canvas drawArc, 0-100, yeşil/sarı/kırmızı eşikler)
- [x] Risk bileşen çubukları: arka plan ×40, gece ×30, çeşitlilik ×20, sıklık ×10
- [x] AccessFrequencyChart: stacked Canvas bar chart, son 7 gün, izin türüne göre renk, üstte sayı etiketi
- [x] İzin geçmişi Paging 3: 30 kayıt/sayfa, arka plan satırları kırmızı vurgu, gece satırları NightsStay ikonu
- [x] Aksiyon butonları: Sistem Ayarları (ACTION_APPLICATION_DETAILS_SETTINGS) + Kaldır (ACTION_DELETE) + Rapor (Faz 3 placeholder Toast)
- [x] Paylaş: buildShareText() metin raporu + Intent.ACTION_SEND → Android Share Sheet
- [x] Paging 3: room-paging, paging-runtime-ktx, paging-compose bağımlılıkları eklendi
- [x] Unit testler: RiskScoreCalculatorTest (9 test)

#### 1.8 — Kurulum Akışı ✅
- [x] Başlangıç yönlendirme: hasPermission → Dashboard; !perm AND done → Dashboard+banner; !perm AND !done → Onboarding
- [x] 4 sayfalı HorizontalPager (userScrollEnabled=false): Hoş Geldin → UsageStats → Bildirim → Hazır
- [x] İlerleme noktaları: animasyonlu genişleyen dot indicator (spring animasyonu)
- [x] Sayfa 1: ACTION_USAGE_ACCESS_SETTINGS launcher + ON_RESUME recheck + izin verilince otomatik ilerleme
- [x] Sayfa 2: POST_NOTIFICATIONS (Android 13+), Android <13 otomatik granted, "Atla" opsiyonu
- [x] Sayfa 3: ilk tarama OneTimeWork tetiklenir → Dashboard'a geçiş
- [x] Edge cases: onboarding_completed flag, izin sonradan iptal → Banner (uygulama çökmez)
- [x] PermissionBanner: AnimatedVisibility, "Ayarlara Git" butonu, kırmızı arka plan
- [x] DashboardViewModel: hasPermission StateFlow, ON_RESUME'da checkPermissionOnResume()
- [x] Unit testler: OnboardingViewModelTest (7 test)

### Faz 1 Tamamlanma Kriterleri
- [ ] Gerçek cihazda test: Facebook uygulaması açık değilken konum erişimi loglanıyor mu?
- [ ] Gece 00:00-06:00 arası arka plan erişimi doğru tespit ediliyor mu?
- [ ] 7 gün veri tutulduktan sonra performans kabul edilebilir mi?
- [ ] Pil tüketimi < %2/gün ek yük

---

## FAZ 2 — Ağ Trafiği İzleme ⏳

### Hedef
"Bu uygulama nereye veri gönderiyor?" sorusunu cevaplayabilmek.

### Kapsam
Root gerektirmez. `NetworkStatsManager` + isteğe bağlı `VpnService`.

### Görevler

#### 2.1 — Temel Ağ İstatistikleri
- [ ] `NetworkStatsManager` wrapper
  - Uygulama bazlı gönderilen/alınan byte
  - Wi-Fi vs mobil veri ayrımı
  - Arka plan vs ön plan ayrımı
- [ ] `NetworkLog` entity ve DAO
- [ ] Anomali tespiti: arka planda aşırı veri gönderimi

#### 2.2 — DNS İzleme (VPN Tabanlı)
- [ ] Yerel VPN servisi kur (`VpnService` API)
- [ ] DNS sorgularını yakala ve logla
- [ ] Domain → uygulama eşleştirmesi
- [ ] Bilinen tracker domain veritabanı (EasyPrivacy listesi)
- [ ] "Bu uygulama facebook.com'a bağlanmaya çalıştı" bildirimi

#### 2.3 — Bağlantı Analizi
- [ ] Aktif TCP/UDP bağlantıları (`/proc/net/tcp` — root gerekebilir, fallback: NetworkStats)
- [ ] Coğrafi konum: IP → ülke eşleştirmesi (yerel GeoIP DB)
- [ ] "Çin'deki sunucuya bağlantı" tespiti

#### 2.4 — UI Güncellemeleri
- [ ] Ağ aktivitesi timeline'a eklenir
- [ ] Uygulama detayına "bağlandığı domainler" bölümü
- [ ] Dünya haritasında bağlantı görselleştirmesi
- [ ] Tracker engelleme seçeneği (VPN DNS üzerinden)

### Faz 2 Tamamlanma Kriterleri
- [ ] Fener uygulaması Çin sunucusuna veri gönderirken tespit ediliyor mu?
- [ ] Tracker domain'leri doğru kategorize ediliyor mu?
- [ ] VPN servisi diğer VPN'lerle çakışmıyor mu?

---

## FAZ 3 — Rapor & İfşa Motoru ⏳

### Hedef
Toplanan veriyi anlamlı, paylaşılabilir, hukuki başvuruya uygun raporlara dönüştür.

### Görevler

#### 3.1 — Rapor Motoru
- [ ] Uygulama bazlı gizlilik raporu
  ```
  [Uygulama adı] Gizlilik Raporu
  Tarih aralığı: XX - YY
  
  İzin Erişimleri:
  - Mikrofon: 47 kez (43'ü arka planda)
  - Konum: 312 kez (298'i arka planda)
  
  Ağ Aktivitesi:
  - Toplam gönderim: 84 MB
  - Bağlandığı sunucular: graph.facebook.com, ...
  - Tespit edilen tracker'lar: 6
  ```
- [ ] PDF export (iText7 veya Android PrintManager)
- [ ] Metin olarak kopyalama (KVKK başvurusu için)

#### 3.2 — Şüpheli Aktivite Skoru
- [ ] Her uygulama için 0-100 "gizlilik riski" skoru
- [ ] Skor bileşenleri:
  - Arka plan sensör erişimi ağırlığı
  - Gece saatleri erişimi ağırlığı
  - Veri gönderim miktarı
  - Tracker domain bağlantı sayısı
  - İstenen vs kullanılan izin farkı

#### 3.3 — Karşılaştırma
- [ ] "Bu uygulama ne kadar veri gönderiyor vs benzer uygulamalar"
- [ ] Kategori bazlı beklenti profili (banka, sosyal medya, oyun...)

#### 3.4 — KVKK Başvuru Şablonu
- [ ] Otomatik KVKK başvuru metni oluştur
- [ ] Kanıt olarak log excerptleri ekle
- [ ] Başvuru rehberi (kvkk.gov.tr linki)

---

## FAZ 4 — Gelişmiş Analiz ⏳

### Hedef
Derin analiz, davranış kalıpları, anomali tespiti.

### Görevler

#### 4.1 — Davranış Profili Analizi
- [ ] Uygulama kullanım kalıbı öğrenme (kullandığın saatler)
- [ ] "Kullanmadığın saatlerde erişim" anomalisi
- [ ] Haftalık trend analizi

#### 4.2 — Çapraz Uygulama Analizi
- [ ] Aynı tracker SDK'yı kullanan uygulamaların tespiti
- [ ] "Bu 5 uygulama aynı şirkete veri gönderiyor" uyarısı
- [ ] Veri broker ağı görselleştirmesi

#### 4.3 — Root Özellikleri (Opsiyonel Modül)
- [ ] Magisk modülü olarak paketleme
- [ ] `/proc/net` üzerinden derin bağlantı analizi
- [ ] Tcpdump entegrasyonu
- [ ] XPrivacyLua benzeri sahte veri döndürme

#### 4.4 — Faraday Modu
- [ ] "Telefon kapalıyken trafik var mı?" tespiti
- [ ] Açılma/kapanma anları arasındaki ağ aktivitesini logla
- [ ] Uçak modu bypass tespiti

#### 4.5 — Olay Zinciri Görünümü
- [ ] Milisaniye hassasiyetli timestamp zinciri
- [ ] Sensör + Ağ + Dosya olayları tek ekranda
- [ ] "Kamera kapandıktan 691ms sonra 2.3 MB veri gönderildi" formatı
- [ ] Korelasyon açıklaması Türkçe
- [ ] PDF export + KVKK başvurusuna hazır

#### 4.6 — Sistem Uygulamaları Korelasyonu
- [ ] "Ayarlar", "Telefon", "Galeri" gibi sistem uygulamalarının sensör erişimini aynı anda ağ trafiğiyle korelasyon
- [ ] "Ayarlar kameraya erişirken samsung-analytics.com'a bağlandı" tespiti
- [ ] Sistem uygulamaları için ayrı risk profili

#### 4.7 — Davranış Değişikliği Tespiti
- [ ] Dünkü vs bugünkü erişim profili karşılaştırması
- [ ] "Facebook dün 47 kez sensöre erişiyordu, bugün 0 kez" anomali tespiti
- [ ] Remote Config sorgusu + davranış değişikliği korelasyonu: "Uzaktan komut aldı mı?"

#### 4.8 — ContentProvider Tarayıcı
- [ ] Her uygulamanın export edilmiş ContentProvider'larını tara
- [ ] İzinsiz erişilebilir olanları işaretle
- [ ] "Bu uygulama verisini dışarıya açık bırakmış" uyarısı

#### 4.9 — Upload Döngüsü Tespiti
- [ ] FileObserver ile shared storage izle
- [ ] Geçici dosya oluştu + silindi + aynı anda ağ trafiği = upload döngüsü
- [ ] Magic bytes ile dosya türü tespiti (ses, video, fotoğraf)

#### 4.10 — Trafik Parmak İzi
- [ ] TLS fingerprint + paket boyutu analizi
- [ ] "Bu trafik ses kaydına benziyor"
- [ ] "Bu trafik fotoğraf transferine benziyor"
- [ ] Boyut/zaman oranından içerik tahmini

---

## FAZ 5 — Topluluk & Dağıtım ⏳

### Hedef
Uygulamayı sürdürülebilir, ulaşılabilir, engellenemez hale getir.

### Görevler

#### 5.1 — F-Droid Yayını
- [ ] F-Droid metadata hazırla
- [ ] Reproducible build konfigürasyonu
- [ ] F-Droid submit

#### 5.2 — Topluluk Veri Paylaşımı (Opsiyonel, Anonim)
- [ ] Opt-in anonim istatistik paylaşımı
- [ ] "X bankası uygulaması kaç kullanıcıda arka planda mikrofon açtı"
- [ ] Merkezi olmayan, Tor üzerinden

#### 5.3 — Dokümantasyon
- [ ] Kullanıcı kılavuzu (Türkçe + İngilizce)
- [ ] KVKK başvuru rehberi
- [ ] Geliştirici dokümantasyonu
- [ ] Video tutorial

#### 5.4 — Güvenlik Denetimi
- [ ] Kod güvenlik taraması
- [ ] Bağımlılık güvenlik kontrolü
- [ ] Topluluk code review

#### 5.5 — Steganografi / Kimlik Gizleme
- [ ] Build flavor sistemi ile çoklu paket adı
- [ ] Masum sistem adlarıyla APK dağıtımı
- [ ] İmza rotasyonu stratejisi
- [ ] "PrivacyDroid tespitine karşı" koruma

#### 5.6 — Kolektif Kanıt Platformu
- [ ] Opsiyonel anonim veri paylaşımı
- [ ] "Bu ay Türkiye'den 47.832 gece erişimi"
- [ ] Merkezi olmayan, gizlilik odaklı
- [ ] Medya ve KVKK için hazır raporlar

---

## Teknik Borç & Riskler

| Risk | Olasılık | Etki | Azaltma |
|------|----------|------|---------|
| Google Play Store yasağı | Yüksek | Orta | F-Droid + APK dağıtım |
| AppOpsManager API kısıtlaması | Orta | Yüksek | ADB fallback, her sürüm test |
| VPN çakışması (Faz 2) | Yüksek | Orta | Kullanıcıyı bilgilendir, graceful degrade |
| Donanım üretici engeli | Düşük | Yüksek | Açık kaynak, fork edilebilir |
| Pil tüketimi şikayeti | Orta | Orta | Agresif optimizasyon, kullanıcı kontrolü |

---

## Versiyon Planı

```
v0.1 — Alpha    : Faz 1 tamamlandı, internal test
v0.2 — Beta     : Faz 2 tamamlandı, topluluk testi
v0.5 — RC       : Faz 3 tamamlandı, F-Droid submit
v1.0 — Stable   : Faz 1-3 stabil, dokümantasyon hazır
v1.5            : Faz 4 özellikleri
v2.0            : Faz 5, topluluk özellikleri
```

---

## İletişim & Katkı

- **Lisans:** GPL-3.0 (copyleft — fork edebilirsin, kapamazsın)
- **Dil:** Türkçe öncelikli, İngilizce ikincil
- **Hedef kitle:** Gizlilik bilincine sahip Android kullanıcıları

---

*Oluşturulma: Faz 0*
*Güncelleme: Her faz tamamlandığında*
