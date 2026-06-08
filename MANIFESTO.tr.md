# PrivacyDroid — Neden Var?

## Bir Gerçekle Yüzleşmek

Telefonun cebinde. Her an yanında. Seni dinliyor, seni izliyor, seni analiz ediyor.

Bu bir komplo teorisi değil. Bu, milyarlarca dolarlık bir endüstrinin iş modeli.

Fener uygulaması neden mikrofonuna erişmek istiyor?  
Hesap makinesi neden konumunu sorguluyor?  
Banka uygulaması gece 3'te neden arka planda aktif?  
Sistem uygulaması kameraya erişirken aynı anda nereye veri gönderiyor?

Bu soruları hiç sordun mu? Çoğu insan sormadı bile.

---

## Mahremiyet Bir Lüks Değil, Bir Hak

Mahremiyet, kapını kilitleyebilmek demek.  
Mahremiyet, düşüncelerinin sadece sende kalması demek.  
Mahremiyet, sevdiklerinle paylaştıklarının sadece onlarda kalması demek.  
Mahremiyet, hatalarının, korkularının, zayıflıklarının seni tanımlamaması demek.

Bu hak çalınıyor. Sessizce. Sistemli olarak. Ve çoğunlukla senin "izninle."

"İzin ver" butonuna bastığında ne verdiğini biliyor musun?

---

## Nasıl Çalışıyor Bu Sistem?

Bir uygulama indiriyorsun. Hava durumu, fener, oyun — fark etmez.

O uygulamanın içinde 5, 10, bazen 15 farklı "tracker SDK" var. Her biri farklı bir şirkete ait. Her biri senin hakkında farklı veriler topluyor. Her biri kendi sunucusuna gönderiyor.

```
Sen hava durumuna bakıyorsun
    ↓
Facebook SDK: "Bu kişi şu saatte uyandı"
Google Analytics: "Telefonu 3 dakika kullandı"
AppsFlyer: "Şu bölgede, şu cihazda"
Adjust: "Şu ağa bağlı"
    ↓
Dört farklı şirket, senin hakkında
veri topladı. Sen sadece hava durumuna baktın.
```

Bu veri satılıyor. Birleştiriliyor. Profiliniyor.

Ve o profil — senin dijital kimliğin — senden çok daha değerli.

---

## Sistem Uygulamaları Da Dahil

Sadece Facebook veya Instagram değil.

Telefonunun fabrikadan gelen uygulamaları da dahil:

```
"Ayarlar" uygulaması kameraya erişti
    ↓
Meşru gerekçe: "Kamera çalışıyor mu?" testi
    ↓
Ama aynı anda ağ trafiği var mı?
    ↓
Samsung Analytics sunucusuna 
2.3 MB gitti mi?
    ↓
Sen bunu biliyor muydun?
```

Kapalı kaynak kod = içinde ne olduğunu bilemezsin.
"Güven" değil — "doğrulama" gerekiyor.

---

## Kanıt Zinciri — Neden Yeterli?

PrivacyDroid kesin kanıt ürettiğini iddia etmez.
Ama şunu üretir: **makul şüphe zinciri.**

Hukuk sistemleri üç farklı eşik tanır:

**Cezai davalar:** "Makul şüphenin ötesinde kesinlik"
→ PrivacyDroid bu eşiği hedeflemez

**KVKK/İdari davalar:** "Makul şüphe yeterli"
→ PrivacyDroid bu eşiği karşılar

**Kamuoyu/Medya:** "Görsel kanıt yeterli"
→ PrivacyDroid bu eşiği fazlasıyla karşılar

Örnek bir kanıt zinciri:

```
03:24:00 — Facebook kameraya erişti (arka plan)
03:24:08 — Kamera kapandı (8 saniye)
03:24:09 — 2.3 MB veri gönderildi
           → graph.facebook.com
03:24:11 — Trafik tamamlandı
           → Yerel dosya: Oluşturulmadı
```

Facebook bu zincire itiraz edemez:
- "Kamera açılmadı" diyemez → Log var
- "Veri gönderilmedi" diyemez → Log var
- "Aynı zamanda olmadı" diyemez → Timestamp var
- "Yerel kaydettik" diyemez → Dosya yok

691 milisaniyeyi inkâr edebilir.
Ama zincirin tamamını inkâr edemez.

Bu yeterli. Çünkü mahremiyet davalarında
ispat yükü şirkettedir:
**"Neden kameraya eriştin?"** sorusuna
şirket cevap vermek zorundadır.
PrivacyDroid sadece soruyu sorar.

---

## Neden Kimse Durdurmadı?

Çünkü sistem bu şekilde tasarlandı.

Büyük teknoloji şirketleri aynı zamanda büyük lobiciler. Yasal düzenlemeler onlar için yazılıyor — ya da onlar tarafından engelleniyor.

GDPR geldi — Meta 1.2 milyar Euro ceza ödedi. Meta'nın yıllık geliri 116 milyar dolar. Ceza, günlük gelirinin birkaç saatiydi. Kimse titremiyor.

KVKK var Türkiye'de — ama denetim kapasitesi sınırlı, siyasi bağlar güçlü, şeffaflık zayıf.

Snowden her şeyi anlattı. Dünya öğrendi. NSA hâlâ çalışıyor.

Bu bir teslimiyet çağrısı değil. Bu gerçeği görmek.

---

## Peki Ne Yapılabilir?

Sistemi tek başına değiştiremezsin. Bu gerçek.

Ama şunu yapabilirsin:

**Görebilirsin.** Kendi cihazında ne olduğunu. Hangi uygulamanın ne zaman neye eriştiğini. Hangi sunuculara veri gittiğini.

**Karar verebilirsin.** Neyi kabul edip neyi reddedeceğini. Hangi uygulamayı silip hangisini tutacağını. Hangi izni verip hangisini vermeyeceğini.

**Paylaşabilirsin.** Sevdiklerini uyarabilirsin. Onların cihazlarını koruyabilirsin. Farkındalığı yayabilirsin.

**Belgeleyebilirsin.** Artık bu bir iddia değil — kanıt zinciri.

---

## Shadow Profile — En Karanlık Gerçek

Sosyal medyada hiç hesap açmadın diyelim. Facebook'u hiç kullanmadın.

Önemli değil.

Seni rehberine kaydeden birinin telefonu var. O telefonda Facebook kurulu. Facebook o rehberi topladı. Senin adın, numaran, ilişkilerin — Facebook'ta.

Sen hiç izin vermeden.

Buna "gölge profil" deniyor. Ve milyarlarca insan için mevcut.

Sevdiklerinin telefonu senin gizliliğini etkiliyor. Senin telefonu onlarınkini.

---

## PrivacyDroid Ne Yapıyor?

PrivacyDroid sana X-ray gözlüğü veriyor.

Görünmezi görünür kılıyor.

```
Gece 03:24
Facebook
Mikrofon — 8 saniye — Arka plan
2.3 MB veri → graph.facebook.com
Yerel dosya oluşturulmadı
Korelasyon: YÜKSEK ŞÜPHELİ
```

Sen uyuyordun. Telefonu kullanmıyordun. Ama bir şeyler oldu.

Artık biliyorsun.

---

## Kolektif Kanıt Gücü

Bir kişi "Facebook gece 3'te mikrofonuma erişti" diyorsa — kim dinler?

Yüz bin kişi aynı anda aynı şeyi belgeliyorsa:

```
→ Bu haber olur
→ Bu KVKK davası olur  
→ Bu uluslararası baskı olur
→ Bu değişim yaratır
```

PrivacyDroid bireysel koruma aracı olduğu kadar
kolektif kanıt makinesidir.

---

## Neden Açık Kaynak?

Çünkü güven, şeffaflık gerektirir.

"Bize güven" diyen her şirket, senden bir şeyler saklıyor olabilir.

PrivacyDroid'in her satır kodu GitHub'da. Herkes okuyabilir, denetleyebilir, doğrulayabilir.

Verin cihazında kalıyor. Hiçbir sunucuya gitmiyor. İnternet bağlantısı bile gerektirmiyor.

Çünkü bir gizlilik uygulamasının kendisi gizli çalışmamalı.

---

## Son Söz

Bu uygulama sistemi değiştirmeyebilir.

Ama şunu yapabilir:

Bir kişi "dur, bu uygulama neden gece 3'te mikrofonumu açtı?" diyebilir.

Sonra iki kişi.

Sonra bin.

Farkındalık, değişimin tek gerçek başlangıcı.

Ve sen artık biliyorsun.

---

*PrivacyDroid — Açık kaynak, özgür yazılım, GPL-3.0*
*Verin sende kalır. Her zaman.*

*"Gözetlemenin gücü, hedefin farkında olmamasından gelir."*
