package com.denemelab.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Login is explicit; cached local exams are never pushed or replaced on sign-in. */
@Composable
fun FirebaseCloudSection(onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = remember { FirebaseCloudBackup.configured(context) }
    var account by remember { mutableStateOf(FirebaseCloudBackup.accountEmail(context)) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(FirebaseCloudBackup.autoEnabled(context)) }
    var current by remember { mutableStateOf<FirebaseBackupInfo?>(null) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var restoreInfo by remember { mutableStateOf<FirebaseBackupInfo?>(null) }

    DisposableEffect(Unit) {
        onDispose { restoreFile?.delete() }
    }
    LaunchedEffect(account) {
        if (account != null && active) {
            val result = runCatching { FirebaseCloudBackup.current(context) }
            current = result.getOrNull()
            result.exceptionOrNull()?.let { failure = "Bulut durumu okunamadı: " + (it.localizedMessage ?: "Bağlantı hatası") }
        } else current = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        HorizontalDivider()
        Text("Firebase hesabım", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (!active) {
            Text("Firebase projesi henüz bu APK'ya bağlanmamış. Firebase Console'da " +
                "com.denemelab.preview Android uygulamasını oluşturup google-services.json " +
                "yapılandırmasını eklemek gerekiyor. Yerel ve Drive ZIP yedeğin çalışmaya devam eder.",
                fontSize = 12.sp, color = Color(0xFF8C5316))
            return@Column
        }

        if (account == null) {
            Text("Hesap açarak denemelerini başka bir telefondan da geri yükleyebilirsin. " +
                "Oturum açmak mevcut verilerini otomatik değiştirmez.",
                fontSize = 12.sp, color = Color.Gray)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().take(160) },
                label = { Text("E-posta") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(selected = !register, onClick = { register = false },
                    label = { Text("Giriş yap") })
                FilterChip(selected = register, onClick = { register = true },
                    label = { Text("Hesap oluştur") })
            }
            Button(
                enabled = !busy && email.isNotBlank() && password.length >= 6,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    failure = null
                    scope.launch {
                        val result = runCatching {
                            FirebaseCloudBackup.signIn(context, email, password, register)
                        }
                        busy = false
                        result.onSuccess {
                            account = it
                            password = ""
                            auto = FirebaseCloudBackup.autoEnabled(context)
                            message = "Oturum açıldı. Buluta yedekle seçeneğiyle ilk kopyanı oluştur."
                        }.onFailure {
                            failure = it.localizedMessage ?: "Firebase giriş işlemi başarısız."
                        }
                    }
                }
            ) { Text(if (register) "Hesap oluştur" else "Giriş yap") }
        } else {
            Text("Giriş yapıldı: " + account, fontSize = 12.sp, color = Color(0xFF155A36))
            Text(if (current == null) "Bu hesap için doğrulanmış yedek görünmüyor."
                else "Bulutta: ${current!!.summary.examCount} deneme · " +
                    "${current!!.summary.questionCount} soru · " +
                    "${current!!.summary.publisherLogoCount} logo",
                color = Color.Gray, fontSize = 12.sp)
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    failure = null
                    scope.launch {
                        val result = runCatching { FirebaseCloudBackup.upload(context) }
                        busy = false
                        result.onSuccess {
                            current = it
                            message = "Bulut yedeği doğrulandı: ${it.summary.examCount} deneme."
                        }.onFailure {
                            failure = it.localizedMessage ?: "Buluta yazılamadı."
                        }
                    }
                }
            ) { Text("Şimdi Firebase'e yedekle") }
            OutlinedButton(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true
                    failure = null
                    scope.launch {
                        val result = runCatching { FirebaseCloudBackup.download(context) }
                        busy = false
                        result.onSuccess { (file, info) ->
                            restoreFile?.delete()
                            restoreFile = file
                            restoreInfo = info
                        }.onFailure {
                            failure = it.localizedMessage ?: "Buluttan indirilemedi."
                        }
                    }
                }
            ) { Text("Buluttan yedeği incele ve geri yükle") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Otomatik yedekleme", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Değişikliklerden sonra Wi-Fi/mobil internet üzerinden zamanlı yedekler.",
                        fontSize = 11.sp, color = Color.Gray)
                }
                Switch(
                    checked = auto,
                    enabled = !busy,
                    onCheckedChange = { requested ->
                        busy = true
                        failure = null
                        scope.launch {
                            val result = runCatching {
                                if (requested && FirebaseCloudBackup.current(context) == null)
                                    error("Önce 'Şimdi Firebase'e yedekle' ile ilk bulut kopyasını oluştur.")
                                FirebaseCloudBackup.setAuto(context, requested)
                            }
                            busy = false
                            result.onSuccess {
                                auto = requested
                                message = if (requested) "Otomatik bulut yedeği açıldı."
                                    else "Otomatik bulut yedeği kapatıldı."
                            }.onFailure { failure = it.localizedMessage ?: "Ayar kaydedilemedi." }
                        }
                    }
                )
            }
            TextButton(onClick = {
                FirebaseCloudBackup.signOut(context)
                account = null
                auto = false
                current = null
                message = "Oturum kapatıldı; cihazdaki denemeler korunuyor."
            }, enabled = !busy) { Text("Firebase hesabından çıkış yap") }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, color = Color(0xFF1B784E), fontSize = 12.sp) }
        failure?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
        Text("Firebase yedeği otomatik iki yönlü veri birleştirme değildir. " +
            "Geri yükleme sadece onayınla gerçekleşir. ZIP yedeğini de sakla.",
            fontSize = 11.sp, color = Color.Gray)
    }

    val confirmation = restoreInfo
    if (confirmation != null) AlertDialog(
        onDismissRequest = { if (!busy) {
            restoreFile?.delete()
            restoreFile = null
            restoreInfo = null
        } },
        title = { Text("Firebase yedeğini geri yükle?") },
        text = { Text(
            "${confirmation.summary.examCount} deneme, ${confirmation.summary.questionCount} soru, " +
                "${confirmation.summary.publisherLogoCount} logo geri yüklenecek. " +
                "Bu işlem cihazdaki mevcut kayıtların yerine buluttakileri koyar. " +
                "Önce güncel verilerin için ZIP yedeği oluşturman önerilir.")
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val file = restoreFile ?: return@TextButton
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { DenemeLabBackup.restore(context, file) }
                    }
                    busy = false
                    result.onSuccess {
                        file.delete()
                        restoreFile = null
                        restoreInfo = null
                        message = "Firebase yedeği doğrulanarak geri yüklendi."
                        onRestored()
                    }.onFailure {
                        failure = it.localizedMessage ?: "Geri yükleme başarısız."
                    }
                }
            }) { Text("Onayla ve geri yükle") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                restoreFile?.delete()
                restoreFile = null
                restoreInfo = null
            }) { Text("İptal") }
        }
    )
}
