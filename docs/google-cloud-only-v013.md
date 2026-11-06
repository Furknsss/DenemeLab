# DenemeLab v0.13 — Google hesabı ile bulut yedekleme

Bu dalda yalnızca **Google hesabıyla giriş** kullanılır. E-posta/şifre formu ve Google Drive/manuel ZIP aktarım düğmeleri **arayüzden kaldırılmıştır**. Veriler Google Drive'a yazılmaz; Firebase Authentication oturumuyla erişilen **kişiye özel Cloud Firestore** altında saklanır.

## Aktivasyon

1. Firebase Console > Authentication > Sign-in method > **Google** etkin olmalı.
2. Firebase Console > Project settings > Your apps > Android `com.denemelab.preview` > **SHA certificate fingerprints** alanına v0.13 GitHub Actions derlemesinin `Prepare and display signing SHA-1 / SHA-256` adımında yazdırdığı **SHA-1** (tercihen SHA-256 da) eklenmeli. Dallanmış test APK'sı kalıcı debug imzasını Actions cache içinde korur. Bu yalnızca **test APK** imzasıdır, release için ayrı gizli imza anahtarı gerekir.
3. Firebase Console'dan **yeniden indirilen** `google-services.json` dosyasının Android istemcisinde `oauth_client[].client_type == 3` olan **Web application OAuth client ID** bulunmalıdır. SHA eklenmeden indirilmiş eski JSON bu bilgiyi içermeyebilir.
4. Güncel `google-services.json` dosyasının Base64 karşılığını, özel GitHub Actions repository secret'ı olan **`FIREBASE_ANDROID_CONFIG_B64`** altında güncelle. JSON veya Base64 içeriğini açık repo commit'ine, PR'a ya da issue'ya **koyma**.
5. Workflow `testDebugUnitTest`, `assembleDebug` ve APK içindeki `google_app_id` ile `default_web_client_id` değerlerinin bulunmasını doğrular. OAuth verisi eksikse APK **yüklenmez**.
6. Telefon üzerinde Google hesabı seçimi, ilk bulut yüklemesi, uygulamayı kapatıp açınca oturumun korunması, çevrimdışı değişiklikler, yeni telefonda kullanıcı onaylı geri yükleme ve yanlış hesabın başkasının verisini okuyamaması test edilmeli.

## Çalışma şekli

- **İlk Google girişinde** sistem önce bu hesabın eski bulut yedeği var mı diye sunucuyu kontrol eder. Yoksa mevcut cihaz verilerinin bir yedeğini oluşturur ve sonraki değişiklikler için otomatik yedeklemeyi açar.
- Hesapta yedek **varsa**, sistem yerel kayıtları otomatik silmez veya buluta üzerine yazmaz. Önce kullanıcı buluttaki bilgileri inceleyip geri yüklemeyi onaylayabilir. Bilinçli bir karşılaştırma/karar sonrası cihaz verilerini yedekleyebilir; Firestore daha çok deneme/soru/logo içeren eski yedeğin daha küçük bir cihazla ezilmesini engeller.
- Yeni bir deneme/soru gibi kalıcı veri değişiklikleri **WorkManager** ile 45 saniye gecikmeli, ağ varsa arka planda yeniden denenen yedeklemeye alınır. Bu **anlık iki yönlü merge/senkronizasyon değildir**; diğer cihazlardan geri yüklemek manuel onay ister.
- Cloud Firestore her arşivi parçalara ayırır, sürüm işaretçisini yalnızca tüm parçalar yüklendikten sonra değiştirir. İndirirken SHA-256 ve arşiv içeriği denetlenir.
- Tek yedek için **8 MB** sınırı ve Firestore kota sınırları vardır. Büyük görseller veya bulutta diğer hesapla birleştirme desteklenmez. Google'ın altyapı hizmeti, gizlilik kuralları ve kota politikaları geçerlidir; **uçtan uca şifreleme değildir**.
- Google Drive arayüzünün kaldırılması, iç arşiv kodunun da kaldırılması anlamına gelmez. Firestore'un yüklediği veri arka planda arşiv biçimiyle hazırlanır; kullanıcıdan dosya seçmesi istenmez.
- Yerel veriler korunur. Debug APK imzası eski sürümden farklıysa mevcut uygulamanın üzerine yükleme başarısız olabilir. Uygulamayı kaldırmak verileri silebilir; güvenli yükseltme ve gerçek cihaz testi olmadan eski sürümü silme.
