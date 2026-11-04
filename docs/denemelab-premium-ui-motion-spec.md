# DenemeLab — Premium UI / Motion Tasarım Sistemi
Sürüm: Tasarım şartnamesi 1.0 · 5 Kasım 2026

## Marka kuralları
- **Uygulama adı:** DenemeLab. Açılış, launcher, Android ayarları, bildirim ve mağaza isminde 'Preview', 'Beta' vb. isim eki yok. Test sürümü bilgisi sürüm notunda ve Hakkında içinde olabilir.
- **Logo:** Kullanıcının onayladığı son görsel yönü: beyaz zemin üzerinde, siyah geometrik **D** formunun içinde yükselen üç çubuk. Daha sonra teknik olarak temiz SVG/Android VectorDrawable yeniden çizim, adaptive icon/monochrome icon ve launcher boyut testleri. Seçilen örneğe sadık kal, doğrudan başkasının markasını kopyalama. Grafik olmayan rastgele D simgesiyle değiştirme.
- **Görsel karakter:** Modern, minimal, Apple'ı çağrıştıran rafine bir kullanım hissi; ancak Apple'ın kendi arayüzünü/logosunu birebir kopyalamak yok.
- **Varsayılan tema:** **Sadece açık tema.** Beyaz, kırık beyaz, siyah, nötr gri. Mor/turkuaz/lacivert/gradient bir tasarım dili değil.
- Kullanıcıya sunulan gerçek deneme kartları asla 'önizleme' sayıları ile karıştırılmaz.

## Görsel tokenlar
| Token | Renk |
| --- | --- |
| background | #FAFAFA |
| surface/card | #FFFFFF |
| primary text | #111111 |
| secondary text | #666666 |
| hairline | #EAEAEA |
| subtle surface | #F4F4F4 |
| dark primary action | #111111 |
| action text | #FFFFFF |
| chart series primary | #171717 |
| chart series muted | #A0A0A0 |

Doğru/yanlış/boş ayrımı yalnızca renge emanet edilmez: ikon + Türkçe metin + desen/stroke gerekir. Veriye göre anlamlı uyarı rengi gelecekte ayrıca değerlendirilir; ekranın genel paleti renksiz kalır.

## Tipografi ve kompozisyon
- Sistem tabanlı, net sans-serif Android fontu; normal ve medium/bold ağırlıklar.
- Ana rakamlar: 34–44 sp, tabular rakamlar; güçlü net hiyerarşisi.
- Başlık: 22–28 sp. Kart başlıkları: 16–18 sp. Yardımcı metin: 12–14 sp; Android font ölçekleme desteği.
- Yatay ekran kenar boşluğu: 20–24 dp; 8dp bazlı dikey ritim; min. 16dp kart iç boşluğu.
- Kart köşeleri: yaklaşık 16–20 dp; arka planla ayrım için hafif bordür veya çok düşük yükselti; ikisini aynı anda abartma.
- Bir ekranda birincil eylem tek: **Deneme ekle** veya **İncele/Kaydet**.
- Geniş boş alanlar, hizalı metinler, düzenli grid, reklam ve renk patlaması yok.

## Bilgi mimarisi
### 1. Özet
1. Başlık: DenemeLab + küçük ayarlar simgesi.
2. 'Son TYT denemesi' gibi veri kaynağı açıkça belirtilen ana rakam.
3. Önceki denemeye göre net farkı (doğrulanmış), varsa bir çizgi grafik.
4. Tek ana buton: **Deneme ekle**.
5. 'Nerede net kaybettim?' en çok 2 ders/kısa özet.
6. 'Bir sonraki adım' en çok 2–3 öneri.
Boş durumda: tek temiz açıklama ve ekle butonu; sahte, dekoratif grafik yok.

### 2. Denemeler
- TYT / AYT / Branş filtreleri; tarihe göre sırala.
- Her deneme satırında tarih, yayın/başlık, net ve tür; gerekliyse net farkı.
- Tıklayınca detay ekranı: toplam net, ders D/Y/B, hatalı sorular, soru kaynakları ve varsa konular.
- Kayıt silme/düzenleme geri dönüşü ve açık onay; kullanıcı geçmişinin kaybolmaması.
- Ayrıntı ekranı, uygulamanın en önemli 'geri dön ve bak' noktası.

### 3. Analiz
- Ana çizgi grafik: zaman içindeki toplam net.
- Ders satırı: konuya uygun alt kırılımlar ve net artışı/azalışı.
- Bir adet sade daire/donut grafik, yalnızca D/Y/B oranlarını anlamak için; **başka 4 donut yok**.
- En çok kaçırılan / tekrar eden konular en fazla 3-5 satırla.
- Konuya dokununca kanıt: hangi deneme, hangi soru, ne kadar kayıp.
- Yalnızca dayanak mevcutsa 'Bu hafta öncelik' kartı.

## Animasyon yönergeleri
Hareket kalite hissine hizmet etmeli; kullanıcıyı yavaşlatmamalı.
| Etkileşim | Hedef süre / davranış |
| --- | --- |
| Sekme geçişi | ~160–220 ms sade fade/slide, hissedilir ama gösterişsiz |
| Kart genişleme/daralma | ~180–260 ms, animateContentSize |
| Özet rakam güncelleme | ~200–350 ms, tek seferlik değer geçişi |
| Donut grafik ilk açılış | ~350–500 ms, yalnızca 1 kez çizim |
| Grafik veri değişimi | ~200–300 ms, eksen stabil kalır |
| Buton basımı | Anlık dokunma/pressed durumu, ~80–120 ms |
| Dosya işleniyor | Gerçek aşama veya açık belirsiz durum; sahte %99 ilerleme yok |
| Kaydetme onayı | Kısa, sakin onay; confetti/flash/sekme sarsma yok |

**Hareketin kullanılmayacağı yerler:** sürekli dönen grafikler, sayısal değerlerin her kaydırmada tekrar animasyonu, kontrol dışı titreyen kartlar, sürekli pulse, yükselip alçalan sahte istatistikler.

- Android'in **Remove animations / azaltılmış hareket** tercihine uy ve hareketleri azalt veya kapat.
- Animasyon olmadan da her durum açıkça anlaşılmalı.
- 60 Hz ve 120 Hz cihazlarda stabil hareket; performans profilinde jank tespit edilirse hareket basitleştirilecek.
- Düşük güçlü cihazda veri çizimleri için Compose Canvas / draw phase tabanlı işler tercih edilir.
- Kullanıcının hareket isteğine bağlı olmayan haptic feedback zorunlu değil; abartılmayacak.

Android resmi animasyon başvuru:
https://developer.android.com/develop/ui/compose/animation/quick-guide

## Bileşen kütüphanesi (Compose'a geçildiğinde)
- AppTopBar / DenemeLab wordmark / SettingsAction
- NetHeroCard (label, validated net, delta, evidence link)
- MiniTrendChart / DetailedTrendChart
- ExamListRow / ExamDetailHeader
- SubjectBreakdownRow (doğru, yanlış, boş, net)
- DonutSummary (D/Y/B oranları; merkezde metin)
- WeakTopicRow (kanıt sayısı ve son görülme)
- NextBestActionCard (1–3 öneri; kanıta gider)
- ImportMethodSheet (elle, cevap ızgarası, fotoğraf/PDF uygun olduğunda)
- VerificationReviewSheet (AI'nin çözemediği alanlar)
- EmptyState, ErrorState, LoadingState, OfflineState

## Doğru grafik kullanımı
- Net ilerlemesi = çizgi grafiği; x ekseni gerçek tarih/deneme, y ekseni tutarlı ve etiketli.
- Ders karşılaştırma = düz yatay bar.
- D/Y/B dağılımı = tek donut + üç sayısal etiket.
- Dört grafik göstermek yerine 1 grafikten detay ekranına ilerle.
- Grafiğe dokunulduğunda ham değerler açılmalı; ekran okuyucu için tablo/etiket olmalı.
- Eksenleri dramatik görünüm için kırpma ya da yapay veriyle doldurma yok.
- Net azaltma/artırma için renk yerine ok + işaret + rakam + metin.

## Uygulama teknolojisi
- Uzun dönem hedef: Kotlin + Jetpack Compose + Material 3. Mevcut ilk APK Java tabanlı önizleme; Compose göçü henüz yapılmış değil.
- Grafikler için öncelikle Compose Canvas ile küçük, sağlam grafikler; karmaşık çizgi/çoklu seri ihtiyacında bakımı devam eden grafik kütüphanesi seçimi, lisans ve performans testlerinden sonra.
- Animasyon: Compose animation APIs, basit grafik interpolasyonu; Lottie yalnızca gerçekten ihtiyaç olursa.
- Tasarım varlıkları Figma'da bileşen/token olarak tutulabilir, uygulama içinde ise gerçek Android kaynakları olmalı.
- Figma/prototip ve cihazdaki gerçek ekran **ayrı** kabul kapılarıdır.

## Kabul kriterleri
- Kullanıcı ilk 5 saniyede deneme ekleme eylemini bulur (kullanıcı araştırması hedefi, garanti değil).
- 360dp küçük telefon ve tablet görünümünde taşan metin, clipping, çakışma yok.
- Sistem font ölçekleme ve erişilebilirlik seçeneklerinde işlev kaybı yok.
- Açık tema her sayfada tutarlı; renkli dekorasyon yok.
- Geçişler takılmadan, uygulamayı bekletmeden işlevi destekler.
- Yalnızca 3 ana sekme; profil ve ayarlar sade üst menüden.
- Bilgi/AI belirsizliği saklanmaz; gerçek data yoksa örnek istatistik gösterilmez.
