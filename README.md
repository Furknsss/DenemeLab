# DenemeLab

Bağımsız Android YKS deneme analizi uygulaması.

**Durum: Android arayüz önizlemesi v0.1.0.** Deneme sonuçları, grafikler, konu başarıları ve yapay zekâ öneri metinleri örnektir; henüz gerçek veri/analiz işleme yoktur.

## Özellikler
- Premium koyu tema ile dört ekran: Ana Sayfa, Denemeler, Analiz ve Hesabım
- Android PDF dosya seçici (analiz henüz bağlı değil)
- SoruLab'dan bağımsız Android paket kimliği `com.denemelab.preview`

## Derleme

JDK 21, Android SDK 36.1 ve Android Build Tools 36.0.0 gerekir.

```sh
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions
`main` dalına yüklenen kod otomatik APK derlemesi başlatır. **Actions → Android preview APK → başarılı çalışma → Artifacts → DenemeLab-Android-Preview-APK** yolundan indirilebilir.

## Sonraki adımlar
Gerçek TYT/AYT net hesaplama, deneme kayıtları, PDF/optik okuma, cevap anahtarı eşleştirme, konu-kazanım raporları ve isteğe bağlı bulut senkronizasyonu.

**Güvenlik:** Git deposuna şifre, keystore, token, özel anahtar veya kullanıcı PDF'si eklenmemelidir.
