# DenemeLab — Ürün Stratejisi ve Rakip Analizi
Tarih: 5 Kasım 2026  
Durum: Tasarım/geliştirme hedefi. Mevcut v0.1.0 uygulama henüz örnek verili Android iskeletidir.

## Tek cümlelik ürün vaadi
**Denemeyi en az zahmetle kaydet; net değişiminin nedenlerini kanıta dayalı gör; bugün hangi konudan başlaman gerektiğini öğren.**

DenemeLab'ın markası da uygulamada görünen adı da her zaman **DenemeLab**. 'Preview', 'Pro', 'Lite' veya deneysel sürüm etiketleri kullanıcıya gösterilen uygulama adının parçası değildir. Sürümün test niteliği uygulama ayarlarından ve sürüm notlarından açıkça anlaşılır.

## Rakip özeti — kamuya açık beyanlar, birebir kullanım testi değil
| Rakip | Kamuya açık özellikleri | Çıkarımımız |
| --- | --- | --- |
| Foncat | Fotoğrafla işaretli kitapçık + cevap anahtarı okuma, elle işaretleme, belirsiz soruları inceleme, konu bazlı analiz | Rakibin en önemli güçlü yönü giriş kolaylığı; yanlış otomatik eşleştirme yapmamak hayati. |
| Pandorina — YKS Deneme Takip | TYT/AYT ve branş denemeleri, net/gelişim grafikleri, ders kırılımı | Grafikler tek başına ayırt edici değil; değişimin nedenini açıklamalıyız. |
| YKS Deneme Takip — Ahmet Furkan Bıçak | Deneme kayıtları, hedef netler, AI içgörüler, konu bazlı giriş | Her deneme kartından kalıcı, ayrıntılı sonuç ekranına ulaşım gerekli. |
| YKS Analiz | TYT/AYT/YDT, soru çözme, AI quiz, koç, yarışma/lig, Pomodoro, defter | Çok fazla bağımsız modül yerine deneme analizindeki temel akışı mükemmelleştireceğiz. |
| Sinavify | Deneme, net, hedef/yerleşme, yanlış defteri, tekrar, görev, Pomodoro/orman, AI koç | Yanlışların tekrarı önemli, gamification, ağaç, coin, sosyal modül önceliksiz. |
| Gradius | Yanlışları konu bazında gruplama ve tekrarlayan hatalara odaklanma | 'Bir sonraki neye çalışmalıyım?' sorusunu net cevaplayacağız. |

Kaynaklar:
- Foncat: https://foncat.com/
- Pandorina: https://play.google.com/store/apps/details?id=com.pandorina.yks_deneme_takip
- YKS Deneme Takip: https://play.google.com/store/apps/details?id=com.yks.tracker
- YKS Deneme Takip App Store kullanıcı yorumu: https://apps.apple.com/tr/app/yks-deneme-takip/id6760952124
- YKS Analiz: https://play.google.com/store/apps/details?id=com.ihsanarslan.yksanaliz
- Sinavify: https://play.google.com/store/apps/details?id=com.utkuuygun.sinavify
- Gradius: https://mygradius.com/

**Not:** Bunlar siteler ve uygulama mağazası açıklamalarına dayalı araştırma bulgularıdır. Her rakibin son Android sürümünde birebir test yapıldığı veya performans kıyaslamasının yapıldığı iddia edilemez.

## Hedef kullanıcı işi (JTBD)
- Denememi 30–90 saniye içinde (giriş biçimine göre araştırma hedefi) kaydetmek.
- Doğru, yanlış, boş ve net hesabının doğru olduğuna güvenmek.
- Son denememin önceki denemelerle hangi derste nasıl değiştiğini görmek.
- Art arda yanlış yaptığım konu ve soru tiplerini bulmak.
- Bir veya iki net çalışma önceliğiyle uygulamadan çıkıp ders çalışmaya devam etmek.
- Önceki denemelerimi istediğim zaman ayrıntılarıyla açmak.

## Temel UX navigasyonu: ÜÇ ANA SEKME
1. **Özet:** son net, net değişimi, tek sade trend grafiği, yalnızca 1–3 somut öneri, güçlü 'Deneme Ekle' eylemi.
2. **Denemeler:** TYT/AYT/branş filtreleri, tarih sıralaması, arama, tüm geçmiş; her satır deneme ayrıntısına açılır.
3. **Analiz:** ders netleri, konu kayıpları, tekrarlayan yanlışlar, gelişim; yalnızca gerekli grafikler. Ayarlar/profil üst sağdaki menüde, ayrıca dördüncü ana sekme yok.

### İlk kullanım
Uygulama hesap açtırmadan başlayabilir; yerel veri gizlilik ilkesi önemlidir. İlk açılışta sadece 'TYT/AYT denemesi ekle' ve örnek ekranların ne işe yaradığını gösteren kısa içerik. Sahte ilerleme ve önceden girilmiş denemeler gerçek veri gibi gösterilmez.

## Tek ana akış
Deneme ekle -> [TYT/AYT/branş] -> [hızlı net girişi / soru-cevap ızgarası / ileride fotoğraf-PDF] -> gözden geçir -> kaydet -> sonuç/öneriler.

### Giriş biçimleri
- **P0: Hızlı giriş:** Ders başına D/Y/B ile net hesaplama; eksik/soru toplamı kontrolü; tek ekrandan kayıt.
- **P0: Soru ızgarası:** A–E + Boş işaretleri, cevap anahtarıyla karşılaştırma, küçük ekran ve tablette çalışır.
- **P1: Fotoğraf/PDF:** İşaretlenmiş kitapçık veya cevap formu + cevap anahtarı gerekir; yalnızca işaretlenmemiş PDF öğrencinin yanıtlarını söylemez. Modelin kararsız kaldığı sonuçlar kullanıcı onayına sunulur. Hiçbir belirsiz cevap otomatik olarak kesin notlandırılmaz.
- Kaydetme sırasında giriş türü ve manuel düzeltilen kayıtlar saklanır.

### Net hesaplama
Varsayılan YKS kuralı: net = doğru − yanlış / 4. Ders/deneme başına soru toplamları, D + Y + B = toplam soruya uygun olmalı. Sonuç güvenilir ve deterministik hesaplanmalı; AI net hesabına karışmamalı. Geçerli deneme formatlarına göre veri modeli esnek olmalı.

## Öncelik sırası
| Sınıf | Özellik | Kabul ölçütü |
| --- | --- | --- |
| P0 | Doğru net ve kayıt sistemi | Tüm matematiksel sınır durumları ve ders toplamları testli. |
| P0 | Hızlı deneme girişi | Hatalı formda kayıt yapılamaz; basit giriş kısa ve açık. |
| P0 | Deneme geçmişi ve tekil ayrıntı | Her kayıt ve ders dağılımı her zaman yeniden açılabilir. |
| P0 | Trend + ders kırılımı | Yanıltıcı eksen yok; birden az kayıt durumunda uydurma grafik yok. |
| P0 | Düşüş/iyileşme özeti | Yalnızca doğrulanmış sayısal farkları gösterir. |
| P1 | Konu bazlı yanlışlar ve tekrar | Düzeyleri bilinmeyen konular 'bilinmiyor' olarak kalır. |
| P1 | Yanlış soru görseli arşivi | Kaynak sayfa, soru referansı ve manuel etiket düzenleme. |
| P1 | Fotoğraf/PDF okuma | Gerçek yayınevi belge testleri, güven düzeyi ve onay akışı. |
| P1 | Kısa AI yorumları | Sadece gerçek veriye bağlı, kaynak soru/ders/konu gösterir. |
| P1 | Opsiyonel bulut yedekleme | Açık izin, senkronizasyon ve silme akışı. |
| P2 | Sıralama/puan tahmini | Yalnızca model/yıl/veri kaynağı, sınırlar ve belirsizlik açıklanırsa. |
| P2 | İleri seviye özel raporlar | Tekrar eden kullanıcı ihtiyacı kanıtlanırsa. |

## Bilinçli olarak **eklemeyeceğiz** (şimdilik)
- Sosyal ağ, lig, düello, leaderboard, coin, rozet yağmuru.
- Pomodoro, yapay ağaç/orman, gereksiz hatırlatıcılar.
- Kullanıcı istemediği hâlde otomatik açılan koç sohbeti.
- Sırf güzel görünsün diye 7 grafik, 20 kart ve her sayfada sayısız KPI.
- Dayanağı olmayan 'yarın +10 net' tahminleri, kesin üniversite sıralaması.
- Kaynağı bilinmeyen AI teşhisi: yalnızca yanlış şıktan 'dikkatsizlik' kesin olarak çıkarılamaz.

## Güvenilir analiz kuralları
1. **Bilinen, türetilen, tahmin edilen** veriler arayüzde ayrılır.
2. Bir AI konu veya cevap eşleşmesinde tereddüt ederse kullanıcıya onaylatılır.
3. Öğrencinin yanlış sebebini kullanıcı seçebilir; AI ancak 'olası neden' diyebilir.
4. Soru sayısı, ders neti, doğru/yanlış/boş hesapları %100 deterministik ve testli.
5. Veri silme, dışa aktarma, cihaz değişimi ve kullanıcı gizliliği tasarımın parçası.
6. Kayıtlı sonuçların üzerine yazma işlemleri geçmişi bozmamalı.

## İlk sürüm kalite kapıları
- Gerçek cihaz: en az 1 küçük Android telefon + 1 tablet (görsel kontrol).
- Otomatik test: TYT/AYT ve branş formatı, net matematiği, validasyon, kayıt/güncelleme.
- Boş durum: hiç deneme yokken sahte net veya tahmin gösterme.
- Performans: ana ekran kaydırma ve animasyonlarda gözle görünür takılma olmaması, profil sonrası ölçüm.
- Erişilebilirlik: yazı ölçeklendirme, >=48 dp dokunma hedefleri, yeterli kontrast.
- OCR kapsamı: ileri sürümde en az iki farklı yayınevinden gerçek deneme örnekleriyle cevap/kesim testleri. Her analizde çözülemeyen durum oranı raporlanır.
- 'Rakipsiz' veya 'hatasız OCR' gibi iddialar ancak rekabet ve gerçek cihaz/paket testlerinden sonra değerlendirilebilir.

## Tasarım ve hareket ilkeleri
Ayrıntı: [denemelab-premium-ui-motion-spec.md](denemelab-premium-ui-motion-spec.md).
