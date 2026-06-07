# PrivacyDroid

> Telefonunda arka planda ne olduğunu gör.
> Kimin ne zaman kamerana, mikrofonuna,
> konumuna eriştiğini öğren.
> Tracker'ları tespit et, reklamları engelle,
> haklarını koru.

---

## PrivacyDroid nedir?

Bir fener uygulaması neden mikrofonuna erişiyor?
Banka uygulaması gece 3'te neden konumunu sorguluyor?
Hangi uygulama Çin'deki sunuculara veri gönderiyor?

PrivacyDroid bu soruların cevabını sana gösterir.
Türkiye'de geliştirilen, açık kaynaklı,
tamamen ücretsiz bir Android gizlilik monitörüdür.

---

## Özellikler

### 🔍 İzin Monitörü
- Hangi uygulama ne zaman kamerana,
  mikrofonuna, konumuna erişti?
- Gece 00:00-06:00 arası arka plan erişimleri
- "Kullanmıyorken neden açıldı?" sorusuna cevap

### 📡 Tracker Tespiti
- VPN tabanlı DNS yakalama
- Hangi uygulama hangi sunucuya bağlandı?
- graph.facebook.com, doubleclick.net gibi
  tracker sunucuları gerçek isimle gösterilir

### 🛡️ Reklam Engelleme
- DNS seviyesinde reklam engelleme
- Uygulama bazlı toggle — bir uygulama
  açılmıyorsa sadece o için engellemeyi kaldır
- Dengeli ve Agresif mod

### 📱 Uygulama Envanteri
- Cihazdaki tüm uygulamaların risk analizi
- Gömülü tracker SDK tespiti
  (Facebook SDK, AppsFlyer, Adjust...)
- Wi-Fi açma iznine sahip uygulamalar
- Güncelleme sonrası yeni izin bildirimi

### 📊 Raporlama
- Uygulama bazlı gizlilik raporu
- PDF olarak kaydet ve paylaş
- KVKK başvuru şablonu otomatik oluşturma
- "Bu uygulama neden riskli?" Türkçe açıklama

### 🔔 Bildirim Merkezi
- Tüm tespitler uygulama içinde listelenir
- Bildirim çubuğu sadece kritik durumlarda
- Özelleştirilebilir bildirim hassasiyeti

---

## Kurulum

### Gereksinimler
- Android 8.0 veya üzeri
- Root gerekmez
- İnternet bağlantısı gerekmez

### APK ile Kurulum (Önerilen)
1. [Releases](https://github.com/ataeyvaz/privacydroid/releases)
   sayfasından son APK'yı indir
2. Telefonunda Ayarlar → Güvenlik →
   Bilinmeyen kaynaklar → İzin ver
3. İndirilen APK dosyasına dokun → Yükle
4. Uygulamayı aç, kurulum sihirbazını takip et

### F-Droid (Yakında)
F-Droid mağazasında yayınlanacak.

---

## İlk Kurulum

Uygulama ilk açıldığında 4 adımlı kurulum:

1. **Hoş Geldiniz** — Ne yaptığını öğren
2. **Kullanım İzni** — Ayarlara git,
   PrivacyDroid'e "Kullanım verilerine erişim" izni ver
3. **Bildirimler** — İstersen bildirimlere izin ver
4. **Hazır!** — İlk tarama otomatik başlar

---

## VPN Modu (Gelişmiş Tracker Tespiti)

Ayarlar → Gelişmiş Tracker Tespiti → Aç

Android VPN izin sorusu çıkar → İzin ver

**Ne yapar?**
DNS sorgularını cihazında analiz eder.
Hangi uygulama hangi sunucuya bağlandığını
tam olarak gösterir.

**Güvenli mi?**
Trafik dışarıya gönderilmez.
Sadece DNS sorguları cihazında incelenir.
Diğer VPN uygulamalarıyla aynı anda çalışmaz.

---

## KVKK Başvurusu Nasıl Yapılır?

1. Uygulamalar sekmesinden şüpheli uygulamayı aç
2. "📋 KVKK Başvurusu" butonuna bas
3. Otomatik oluşturulan metni düzenle
4. Şirkete e-posta veya yazılı olarak gönder
5. 30 gün içinde yanıt gelmezse:
   kvkk.gov.tr → Başvuru Formu

---

## Gizlilik Politikası

- ❌ Analitik yok
- ❌ Reklam yok
- ❌ Bulut senkronizasyonu yok
- ❌ Veri satışı yok
- ✅ Tüm veriler sadece cihazında
- ✅ İnternet bağlantısı gerektirmez
- ✅ Açık kaynak — her satır kod incelenebilir

---

## Sık Sorulan Sorular

**Uygulama pili çok tüketiyor mu?**
WorkManager modunda günlük ek tüketim
%1-2'nin altında. Gerçek zamanlı mod
daha fazla tüketir.

**Root gerekiyor mu?**
Hayır. Tüm özellikler root olmadan çalışır.

**VPN açıkken internet kesilir mi?**
Hayır. VPN sadece DNS sorgularını izler,
tüm internet trafiği normal akar.

**Banka uygulaması çalışmıyor, ne yapmalıyım?**
Uygulamalar → Banka uygulamasına tıkla →
"Reklam Engelleme" toggle'ını kapat.

**Play Store'da neden yok?**
Google'ın politikaları bu tür izleme
uygulamalarını kısıtlıyor.
APK veya F-Droid üzerinden kurulabilir.

---

## Katkıda Bulun

Hata bildirmek veya özellik önermek için:
[Issues](https://github.com/ataeyvaz/privacydroid/issues)

Kod katkısı için:
[CONTRIBUTING.md](CONTRIBUTING.md)

---

## Lisans

GNU General Public License v3.0

Bu yazılım özgür yazılımdır.
Kopyalayabilir, değiştirebilir ve
dağıtabilirsiniz — ancak türev çalışmalar
da GPL-3.0 ile lisanslanmalıdır.

---

*PrivacyDroid — Dijital haklarını koru.*
