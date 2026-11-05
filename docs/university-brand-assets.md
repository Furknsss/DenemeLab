# DenemeLab — üniversite logo ve kampüs görselleri

## Kaynak ve kapsam (5 Kasım 2026)

DenemeLab resmî yerleştirme/program bilgilerini YÖK Atlas'tan okur. Görseller YÖK Atlas verisi değildir. **Her görsel ayrı kaynağa ve hak sahibine aittir**; DenemeLab üniversiteler tarafından desteklendiğini iddia etmez.

- Üniversite kimlikleri için [Turkish-universities](https://github.com/izzetemredemir/Turkish-universities) 2026-08-06 CC0 JSON referansı kullanıldı. 228 YÖK Atlas ID + üniversite adı eşlemesi ayrı JSON manifestine kondu. ÖSYM/YÖK Atlas güncel veri önceliği korunur.
- Üçüncü taraf deposundaki 200 lisansı belirsiz logonun **hiçbiri** uygulama paketine alınmadı, URL ile gösterilmiyor.
- İlk aşamada lisansı belirtilmiş **16 Commons logosu** kullanılır (manifestte `logo` alanı); **5 doğrulanmış kampüs fotoğrafı** `campus` alanında kayıtlıdır.
- Bilinmeyen veya çelişen ID/isim eşleşmeleri için otomatik logo/fotoğraf yoktur; nötr gri-beyaz okul sembolü gösterilir.
- Üniversitenin renklerini değiştirmeyiz, logolara animasyon uygulamayız. Sadece kendi kart çerçevemizi animasyonlu tasarlarız.
- Görseller cihaz üzerinde Coil disk/bellek önbelleğinde tutulur; indirme hatasında arayüz bozulmaz.
- Görsel kaynaklarını ve lisansları hedef bölümün ayrıntı sayfasındaki **Görsel kaynakları** bağlantılarından açabiliriz.

## Kullandığımız kampüs fotoğrafları

| Üniversite | Fotoğraf kaynağı | Görsel lisansı / fotoğrafçı |
|---|---|---|
| ODTÜ | https://commons.wikimedia.org/wiki/File:MiddleEastTechnicalUniversityCampus800x470.jpg | Public domain · Atilim Gunes Baydin |
| Bilkent | https://commons.wikimedia.org/wiki/File:Bilkent-University.jpg | CC BY 2.5 · Bilkent University |
| Koç Üniversitesi | https://commons.wikimedia.org/wiki/File:Ko%C3%A7_University_Rumelifeneri_Campus.jpg | CC0 · Delphic Ephemera |
| İstanbul Teknik Üniversitesi | https://commons.wikimedia.org/wiki/File:Main_gate_of_the_Istanbul_Technical_University_campus.jpg | CC BY-SA 4.0 · Kurmanbek |
| Boğaziçi Üniversitesi | https://commons.wikimedia.org/wiki/File:Bogazici_University.jpg | CC BY-SA 3.0 · Turkmessage |

**Not:** Fotoğraflar 145dp geniş banner alanına kırpılarak yerleştirilir. Bu, orijinal fotoğrafın düzenlenmiş/kırpılmış gösterimidir; özgün dosya ve ilgili lisans için kaynak bağlantısına gidilebilir. Her fotoğrafın lisans yükümlülükleri korunmalıdır. Üçüncü tarafın logosu DenemeLab'ın kendi markası değildir.

Logo kaynakları/atfı tek tek `app/src/main/assets/university_visuals.json` içinde listelenmiştir. Kaynaklar, uygulamanın görsel listesinde **yer almayan** üniversitelere fotoğraf uydurmak için kullanılmaz.

## Yeni üniversite ekleme güven kapıları
1. Resmî isim + `universiteId` ile kimliği doğrula; başka üniversite ya da bağlı fakülte logosunu karıştırma.
2. Wikimedia Commons/üniversite izinli kurumsal medya sayfasından dosyayı kontrol et.
3. Logo/fotoğraf için kullanım izni ve marka tanımlama sınırını doğrula; belirsiz lisansı olanı ekleme.
4. Kaynak URL, lisans, fotoğrafçı/atfı ve eğer varsa izin belgesini kaydet.
5. Fotoğrafın gerçekten ilgili kampüse ait olduğunu doğrula; görsel boyutu/kadrajı 360dp ve tablette değerlendir.
6. Bozuk bağlantı, güncel olmayan logo, kampüs karışıklığı ve takedown başvurusu için görseli manifestten kaldırabilme.

## Teknik
- Görseli Compose tarafında `UniversityLogo`, `UniversityCampusBanner`, `UniversityVisualCredits` bileşenleri kullanır.
- Coil Compose 2.7 + coil-svg 2.7 logo ve fotoğrafı önbelleğe alır.
- Wikimedia Commons `Special:FilePath` endpointi dinamik görsel isteği için kullanılır. Kullanılamazsa uygulama nötr yer tutucu gösterir.
- Alt navigasyonda/deneme kayıt ekranında banner tekrarlanmaz. Kampüs hero yalnızca programın ayrıntı ekranındadır.
- Üretim sürümünde Commons kullanım oranları/Terms of Use ve görüntü dağıtım koşullarına göre kendi izinli CDN'mize/özgün varlık depomuza geçilebilir; telif izinleri ayrıca doğrulanır.
