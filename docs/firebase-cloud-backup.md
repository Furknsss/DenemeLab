# DenemeLab Firebase hesabı ve bulut yedeği (v0.12)

Bu dalda DenemeLab verileri için Firebase Authentication (e-posta/şifre) ve Cloud Firestore üstünde **hesaba özel, geri yüklemesi kullanıcı onaylı ZIP anlık görüntüsü** uygulama kodu vardır.

## Firebase projesini bağlamak için gerekenler

1. Firebase Console'da DenemeLab için proje aç (mevcut SoruLab projesinden ayrı olması tercih edilir). Firebase eklentisi bağlanınca proje kuruluşunu oradan yönetmek de mümkündür.
2. Android uygulaması ekle: **package/applicationId `com.denemelab.preview`**.
3. Firebase Authentication > Sign-in method > **Email/Password** etkinleştir.
4. Firestore Database oluştur. Yayına almadan önce bu depodaki `firestore.rules` içeriğini **Firestore > Rules** bölümüne ekleyip yayınla; herkese açık test kuralları kullanma.
5. Firebase Console'dan **`google-services.json`** indir. Bu yapılandırmayı herkese açık GitHub deposuna commit etme.
6. Yerel Android derlemesinde dosyayı `app/google-services.json` konumuna koy. GitHub Actions'ta Firebase bağlı APK derlemek için dosyanın *Base64 kodlu tam içeriğini* repository Actions secret **`FIREBASE_ANDROID_CONFIG_B64`** olarak ekle (GitHub > Settings > Secrets and variables > Actions). Dosyanın kendisini veya kodunu sohbete yapıştırma.
7. Bulut bağlantısını gerçek Android cihazında şu akışla doğrula: Ayarlar > Bulut ve yedekleme > Firebase hesabım > Hesap oluştur / Giriş yap > Şimdi Firebase'e yedekle > Buluttan yedeği incele ve geri yükle (açıklayıcı onay).

## Veri koruma ve sınırlamalar

- Oturum açmak mevcut denemeleri silmez, boş bir telefonu otomatik buluta yüklemez. İlk yükleme kullanıcı tarafından başlatılır.
- İsteğe bağlı otomatik yedekleme, WorkManager ile değişikliklerden sonra iş kuyruğuna eklenir; bağlantı geldiğinde yeniden denenir. Anlık, çift yönlü merge/senkronizasyon değildir.
- Son tamamlanmış sürüm ve bir önceki sürüm bulutta korunur. Yeni sürümün pointer'ı ancak tüm parçalar yüklendiğinde değiştirilir. İndirirken SHA-256 ve ZIP içerikleri doğrulanır, sonra geri yükleme için açık onay istenir.
- Yedek, şifrelenmiş ağ bağlantısıyla Firestore'a gider; **uçtan uca şifrelenmiş değildir**. Firebase Authentication ve kurallar kullanıcı hesaplarını birbirinden ayırır.
- Ücretsiz Spark kullanımı için **Cloud Storage kullanılmaz**. Firestore ücretsiz kullanım kotası aşılırsa yedekleme başarısız olabilir. Her bulut ZIP yedeğinin üst sınırı 8 MB'dir; daha büyük yedekler için mevcut Drive ZIP dışa aktarması kullanılmalıdır.
- Firebase yapılandırması eklenmeden üretilmiş test APK'larında Firebase hesabı özelliği devre dışıdır; bu normaldir. Build başarısı Firebase sunucuya bağlandığını doğrulamaz.
- Firebase Console projesi, Authentication provider, veritabanı kuralları ve gerçek hesap testi yapılmadan üretimde kullanılmamalıdır.
- Kullanıcı kayıtlarını korumak için eski APK'yı kaldırıp yeniden yüklemekten kaçın; önce ZIP yedeği al. Farklı debug imzaları yerinde yükseltmeyi engelleyebilir.
- Google Drive manuel ZIP dışa aktarma ve Android Auto Backup mevcut hâliyle çalışmaya devam eder.

## Test kapısı

`testDebugUnitTest`, `assembleDebug`, APK bütünlük denetimi ve **Firebase yapılandırmalı APK** üzerinde oturum aç / yükle / ağ kesintisi / geri yükle testleri yapılmadan main'e merge yok.
