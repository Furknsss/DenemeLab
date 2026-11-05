# DenemeLab — Onaylı beş sayfa ve ürün mimarisi
5 Kasım 2026 · Kullanıcı vizyonuna göre

## Premium UI sabitleri
- Uygulama görünür adı: DenemeLab. Ek sürüm isimleri yok.
- Onaylanan görsel işareti: beyaz kare üzerinde siyah geometrik D ve yükselen 3 sütun.
- Beyaz zemin, beyaz kart, siyah tipografi, çok açık gri yüzey/ayraç.
- 5 yüzen yuvarlak simge alt menüde. Aktif simge siyah zeminli ve diğerlerinden büyüktür.
- Alt bar simgelerinde erişilebilir ad vardır; hareketler 160–260 ms aralığında sakin biçimde tamamlanır.
- Göstergeler yalnızca gerçek kayda bağlı; boş durumda örnek istatistik gerçek sanılmaz.
- Animasyonlara Android'in hareketi azaltma ayarı ve ekran okuyucu desteği eklenecek.

## Beş ana sayfa
1. **Anasayfa:** Son gerçek deneme neti, son karşılaştırılabilir denemeye göre değişim, tek grafik, tek deneme ekleme eylemi, çözüm bekleyen yanlışların özeti.
2. **Denemeler:** Genel ve Branş olarak birbirinden ayrı kayıtları gösterme; TYT/AYT ayrıntıları; sağ üstten ekleme, sonuç kartı dokununca ayrıntı ve ileri sürümde optik/PDF aktarım.
3. **Analiz:** Genel / Branş filtreleri farklı analiz kümeleri; D/Y/B donut, net zaman çizgisi, ders değişimi, hata konu/kanıt kırılımları. Sonraki sürümde soru bazlı analiz.
4. **İlerlemem:** Aya göre gelişim, hedef üniversite-bölüm, resmî yıl etiketli taban başarı sırası ve son yerleşenin doğrulanabilen OBP/net kırılımı, öğrencinin kişisel hedef kıyaslaması.
5. **Soru Kumbaram:** Yanlış/boş soru görselleri, kaynak ve konu, otomatik/manuel ekleme, 0 Hata Kitapçığı, tekrar oturumları, sonuçlara göre sıradaki çalışma.

Ayarlar/profil ana alt menüde altıncı sekme değildir; üst menüye yerleştirilir.

## Bilinen veriye sadakat
- Sadece deneme D/Y/B toplamıyla hangi soruların yanlış olduğu belirlenemez. Soru Kumbarasına otomatik aktarım ancak soru düzeyinde yanıt + doğrulanmış anahtar varsa yapılır.
- Net hesabı deterministik: doğru − yanlış/4. TYT genel toplam 120, AYT genel toplam 80; branş sınav sorusu özgündür.
- Genel ve Branş farklı tür/ölçeklerdir. 40 soruluk matematik neti ile 120 soruluk TYT toplam neti aynı trendde kıyaslanmaz.
- 0 Hata sisteminde hedef: yanlış/boş soru -> çözüm inceleme -> 1. doğru tekrar -> ayrı 2. doğru tekrar -> tamamlanma. Her yeni yanlışta ilerleme sıfırlanır. Yalnızca 'doğru çözdüm' kullanıcının beyanıdır, otomatik doğrulanmış sayılmaz.
- Her soru, kaynak deneme, sayfa/soru numarası, kırpım görüntüsü, ders, konu, cevap güveni ve tekrar durumuna bağlanır.
- Yanıltıcı AI: bir öğrencinin hatasının nedeninin 'dikkatsizlik' olduğu yalnızca yanlış şıktan kesin çıkarılamaz.

## Resmî YÖK Atlas ve ÖSYM verileri
Kaynak: https://yokatlas.yok.gov.tr/
2026 ÖSYM yerleştirme sayısal verileri: https://www.osym.gov.tr/2026-yks-yerlestirme-sonuclarina-iliskin-sayisal-bilgiler

- YÖK Atlas Net Sihirbazı programlara son yerleşenin ders bazlı netlerini yıl bazında sağlayabilir, ancak erişim ve güncelliği alan bazında doğrulanır.
- 2026 program taban sıralaması ile 2025 son yerleşen netini tek 2026 gözlemi gibi birleştirme.
- Kurumsal veride ÖSYM program kodu, lisans/önlisans, program türü, yıl, veri kaynağı, son kontrol tarihi tutulur.
- 2026 verisinin netleri doğrulanamıyorsa 2026 için veri yok yaz; tahmin üretilirse açıkça model tahmini olarak işaretle.
- Her öğrenci OBP tahmini için diploma notu vermeyi isteğe bağlı seçer; netlerden kesin YKS sırası vaat edilmez.
- Resmî canlı veriyi mobil uygulamaya bağlamadan önce izin, erişim, sürümleme ve veri doğruluğu değerlendirilecek.

## Sürüm aşamaları
- V0.2: Gerçek Compose 5 sekme; yüzen büyüyen icon dock; yerel genel/branş denemesi; net hesaplama; gerçek kayıt grafikleri; aylık özet; elle hedef; manuel soru kumbarası; iki aşamalı tekrar.
- V0.3: Ders bazında doğru/yanlış/boş, soru bazlı cevaba göre yanlış tespiti, gerçek soru görseli ekleme, Android/Tablet QA.
- V0.4: Üretim düzeyi OCR ve cevap eşleştirme, kaynak-deneme-soru bütünlüğü, manuel onay ve gerçek yayınevi karşılaştırmaları.
- V0.5: Resmî katalog ve güncel verilere dayalı otomatik üniversite hedef modülü.
- V1: Yük testi, ekran okuyucu, senkronizasyon/gizlilik, gerçek cihaz kalite değerlendirmesi.

## Kabul kontrolleri
- Beş ikonun tamamı doğru ekrana gider, seçili buton büyür; 360dp küçük telefonda alt menü taşmaz.
- Genel deneme Denemeler/Analiz içinde Branşta görünmez ve tersi de geçerli.
- TYT 80 doğru, 20 yanlış, 20 boş -> 75 net.
- Geçmiş kayıtlar uygulama kapanıp yeniden açılınca korunur; hedef ve kumbara kayıtları da korunur.
- Gelişim grafiğinde en az iki gerçek sonuç olmadan uydurma trend gösterilmez.
- Ay bazlı gelişimler sınav türü bazında kıyaslanır; derslerin soru toplamı ve net ölçeği korunur.
- 0 Hata: yanlış -> doğru -> doğru tamam; arada yanlış olursa sıfırlanır.
- Üniversite resmî hedef veri kaynağı/yılı yoksa hayalî OBP veya son net yazılmaz.
