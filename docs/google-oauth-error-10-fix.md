# DenemeLab: Google girişindeki OAuth hata 10'u giderme

## Doğrulanmış neden (2026-11-07)

Firebase Google OAuth ayarları tek Android sertifika parmak izi içeriyor.
`DenemeLab-v0.19-Test.apk` dosyasının gerçek SHA-1'i buna uymuyor.
Android 16'daki `DEVELOPER_ERROR(10)` bu nedenle ortaya çıkıyor.

**Mevcut v0.19 APK SHA-1**
```
CC:DD:EE:B6:F7:84:D0:0F:AB:77:F9:8B:D3:EA:91:75:12:FE:4F:E3
```

Eski v0.18 APK başka bir test sertifikasıyla imzalanmış. GitHub
Actions'taki eski debug keystore cache'i dal bazında değişebiliyordu.
Önceki iş akışı kayıtlı Firebase SHA eşleşmesini yalnızca bir özel
dal için denetlediği hâlde diğer dallarda da yanlışlıkla "matches
Firebase" sonucunu yazıyordu.

## Şu anda yüklü v0.19 sürümünde hızlı düzeltme

1. Firebase Console > `denemelab-yks-2026` > Project settings >
   General > Your apps > `com.denemelab.preview`.
2. Add fingerprint ile yukarıdaki v0.19 SHA-1'i ekleyin.
3. Authentication > Sign-in method > Google sağlayıcısının açık
   olduğunu kontrol edin.
4. Gerekirse güncel `google-services.json` indirin. Bu dosyanın
   yenilenmiş içeriğini GitHub Actions secret
   `FIREBASE_ANDROID_CONFIG_B64` üzerinden sonraki derlemelere
   aktarın. Gizli değerleri depoya eklemeyin.
5. Google hesabıyla oturum açmayı **gerçek cihazda** tekrar deneyin.
   Ayarların yayılmasını beklemek gerekebilir; başarı test edilmeden
   "düzeltildi" kabul etmeyin.

Google/Firebase oturum açma yapılandırması sunucu tarafında
düzenlendiğinden, mevcut v0.19 APK'da yalnızca SHA kayıt eksikse
APK'yı silmeden giriş tekrar denenebilir.

## Kalıcı çözüm: değişmeyen APK imza anahtarı

Yeni sürümler rastgele üretilen `debug.keystore` ile imzalanmamalı.
GitHub Actions, artık `DENEMELAB_SIGNING_KEYSTORE_B64` adlı gizli
değerde saklanan aynı imza anahtarını **zorunlu** tutuyor.
Kullanıcı onaylı kalıcı anahtarın güvenli bilgisayarda bir kere
oluşturulup GitHub Actions secret olarak yüklenmesi gerekir.

Geliştirici bilgisayarında (özel anahtar veya keystore hiçbir zaman
git deposuna eklenmemeli):

```sh
keytool -genkeypair -noprompt -keystore denemelab-debug.keystore \
  -alias androiddebugkey -storepass android -keypass android \
  -keyalg RSA -keysize 3072 -validity 3650 \
  -dname "CN=DenemeLab Debug,O=DenemeLab,C=TR"
keytool -list -v -keystore denemelab-debug.keystore \
  -alias androiddebugkey -storepass android
base64 -w 0 denemelab-debug.keystore
```

Son komutun çıktısı yalnızca GitHub Repository > Settings >
Secrets and variables > Actions > `DENEMELAB_SIGNING_KEYSTORE_B64`
adlı gizli alana kaydedilmeli. **Bu gizli değeri sohbete yapıştırmayın.**
Aynı keystore SHA-1'i Firebase Android uygulamasına eklenmeli,
güncel google-services.json da mevcut `FIREBASE_ANDROID_CONFIG_B64`
secret'ına yeniden kaydedilmeli.

**Önemli:** Yeni oluşturulan anahtarın SHA'sı mevcut v0.19
APK'sından farklıysa, yeni APK mevcut kurulumun üzerine Android
imza kısıtları nedeniyle yüklenmeyebilir. Eskisini kaldırmadan
önce yerel verilerin doğrulanmış yedeği alınmalı. Yayın
ortamında debug sertifikası yerine ayrı ve kalıcı bir release imzası
kullanılmalıdır.

Artık `scripts/verify_google_oauth_signing.py` gerçek APK
imzasını paket adına bağlı Firebase Android OAuth sertifikasıyla
karşılaştırıyor. Uyuşmazsa derleme çıktılarını **yayımlamıyor**.
Bu kontrol gerçek bir Google hesabıyla giriş testi yerine geçmez.

Kaynak: https://firebase.google.com/docs/auth/android/google-signin
