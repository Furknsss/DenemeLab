package com.denemelab.preview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val CloudInk = Color(0xFF121212)
private val CloudGray = Color(0xFF777777)
private val CloudPale = Color(0xFFF6F6F6)

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext ?: return null
    }
}

/**
 * One Google account -> Firebase Authentication -> private Firestore backup.
 * Drive/file picker and email-password signup are intentionally absent.
 * A remote archive is NEVER restored without explicit confirmation.
 */
@Composable
fun FirebaseCloudSection(onRestored: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val active = remember(context) { FirebaseCloudBackup.configured(context) }
    val oauthReady = remember(context) { GoogleAccountAuth.webClientConfigured(context) }
    var account by remember { mutableStateOf(GoogleAccountAuth.accountEmail(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(FirebaseCloudBackup.autoEnabled(context)) }
    var remote by remember { mutableStateOf<FirebaseBackupInfo?>(null) }
    var loadedRemote by remember { mutableStateOf(false) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var restoreInfo by remember { mutableStateOf<FirebaseBackupInfo?>(null) }

    DisposableEffect(Unit) {
        onDispose { restoreFile?.delete() }
    }
    LaunchedEffect(account) {
        if (account != null && active) {
            loadedRemote = false
            val result = runCatching { FirebaseCloudBackup.current(context) }
            result.onSuccess {
                remote = it
                loadedRemote = true
            }.onFailure {
                loadedRemote = false
                failure = "Bulut durumu okunamadı: " +
                    (it.localizedMessage ?: "İnternet bağlantısını kontrol et.")
            }
        } else {
            remote = null
            loadedRemote = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = CloudPale, shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Rounded.CloudDone, null, tint = CloudInk,
                    modifier = Modifier.padding(10.dp).size(25.dp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Bulut yedekleme", fontWeight = FontWeight.Bold,
                    color = CloudInk, fontSize = 20.sp)
                Text("Google hesabınla güvenli yedek", color = CloudGray, fontSize = 11.sp)
            }
        }
        Text(
            "Denemelerin, soruların, profilin, hedeflerin, çalışma geçmişin ve " +
                "yayın logoların kendi Firebase hesabının bulut arşivinde korunur.",
            color = CloudGray, fontSize = 12.sp, lineHeight = 18.sp
        )

        if (!active || !oauthReady) {
            Surface(color = CloudPale, shape = RoundedCornerShape(15.dp)) {
                Text(
                    if (!active) "Firebase bu APK'ya bağlanmamış. Google hesabıyla giriş etkin değil."
                    else "Google giriş kimliği eksik. Firebase'e uygulama SHA-1 imzasını ekleyip " +
                        "güncel google-services.json dosyasıyla yeni APK oluşturulmalı.",
                    modifier = Modifier.padding(14.dp), fontSize = 12.sp,
                    color = Color(0xFF95541B)
                )
            }
        } else if (account == null) {
            Text("Tek dokunuşla giriş yap; ayrıca şifre veya Drive bağlantısı gerekmez.",
                fontSize = 12.sp, color = CloudGray)
            Button(
                onClick = {
                    val host = activity ?: run {
                        failure = "Google giriş ekranı bu cihazda açılamıyor."
                        return@Button
                    }
                    busy = true
                    message = null
                    failure = null
                    scope.launch {
                        try {
                            val signedIn = GoogleAccountAuth.signIn(host)
                            account = signedIn
                            // Never upload before checking whether the account
                            // already owns a potentially richer cloud archive.
                            val existing = FirebaseCloudBackup.current(context)
                            remote = existing
                            loadedRemote = true
                            if (existing == null) {
                                val first = FirebaseCloudBackup.upload(context)
                                FirebaseCloudBackup.setAuto(context, true)
                                remote = first
                                auto = true
                                message = "Google hesabın bağlandı. İlk bulut yedeğin alındı; " +
                                    "sonraki değişiklikler otomatik yedeklenecek."
                            } else {
                                FirebaseCloudBackup.setAuto(context, false)
                                auto = false
                                message = "Hesabında önceden kayıtlı bir yedek bulundu. " +
                                    "Önce buluttaki bilgileri incele; mevcut kayıtlarını " +
                                    "senin onayın olmadan değiştirmiyoruz."
                            }
                        } catch (e: Exception) {
                            failure = e.localizedMessage ?: "Google ile oturum açılamadı."
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && activity != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CloudInk),
                contentPadding = PaddingValues(vertical = 15.dp)
            ) {
                Text("G", fontWeight = FontWeight.Black, fontSize = 17.sp)
                Spacer(Modifier.width(12.dp))
                Text("Google ile devam et", fontWeight = FontWeight.SemiBold)
            }
        } else {
            Surface(shape = RoundedCornerShape(16.dp), color = CloudPale) {
                Column(Modifier.fillMaxWidth().padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("BAĞLI GOOGLE HESABI", color = CloudGray,
                        fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Text(account.orEmpty(), color = CloudInk,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val remoteText = when {
                        !loadedRemote -> "Bulut bilgileri kontrol ediliyor…"
                        remote == null -> "Bu hesapta henüz bulut yedeği yok."
                        else -> "${remote!!.summary.examCount} deneme · " +
                            "${remote!!.summary.questionCount} soru · " +
                            "${remote!!.summary.publisherLogoCount} logo bulutta"
                    }
                    Text(remoteText, color = CloudGray, fontSize = 12.sp)
                }
            }

            Button(
                onClick = {
                    busy = true
                    failure = null
                    message = null
                    scope.launch {
                        try {
                            // Check server copy before any user-initiated upload.
                            FirebaseCloudBackup.current(context)
                            val saved = FirebaseCloudBackup.upload(context)
                            FirebaseCloudBackup.setAuto(context, true)
                            auto = true
                            remote = saved
                            loadedRemote = true
                            message = "Yedek başarıyla kaydedildi; otomatik bulut yedekleme açık."
                        } catch (e: Exception) {
                            failure = e.localizedMessage ?: "Bulut yedeği alınamadı."
                        } finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CloudInk)
            ) {
                Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text("Şimdi buluta yedekle")
            }

            OutlinedButton(
                onClick = {
                    busy = true
                    failure = null
                    scope.launch {
                        try {
                            val (file, info) = FirebaseCloudBackup.download(context)
                            restoreFile?.delete()
                            restoreFile = file
                            restoreInfo = info
                        } catch (e: Exception) {
                            failure = e.localizedMessage ?: "Bulut yedeği indirilemedi."
                        } finally { busy = false }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Icon(Icons.Rounded.Restore, null, Modifier.size(18.dp),
                    tint = CloudInk)
                Spacer(Modifier.width(9.dp))
                Text("Buluttan geri yükle", color = CloudInk)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Otomatik yedekleme", fontWeight = FontWeight.SemiBold,
                        color = CloudInk, fontSize = 13.sp)
                    Text("Değişikliklerden sonra bağlantı geldiğinde çalışır.",
                        color = CloudGray, fontSize = 11.sp)
                }
                Switch(
                    checked = auto,
                    enabled = !busy,
                    onCheckedChange = { requested ->
                        busy = true
                        failure = null
                        scope.launch {
                            try {
                                if (requested) {
                                    if (FirebaseCloudBackup.current(context) == null)
                                        throw IllegalStateException(
                                            "Önce ilk yedeğini 'Şimdi buluta yedekle' ile oluştur.")
                                }
                                FirebaseCloudBackup.setAuto(context, requested)
                                auto = requested
                                message = if (requested) "Otomatik bulut yedekleme açıldı."
                                    else "Otomatik bulut yedekleme kapatıldı."
                            } catch (e: Exception) {
                                failure = e.localizedMessage ?: "Bulut ayarı kaydedilemedi."
                            } finally { busy = false }
                        }
                    }
                )
            }

            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        activity?.let { GoogleAccountAuth.signOut(it) }
                        account = null
                        remote = null
                        auto = false
                        message = "Google hesabından çıkıldı. Telefonda kayıtlı denemeler silinmedi."
                        busy = false
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Rounded.Logout, null, Modifier.size(16.dp), tint = CloudGray)
                Spacer(Modifier.width(6.dp))
                Text("Hesaptan çıkış", color = CloudGray, fontSize = 12.sp)
            }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        message?.let { Text(it, color = Color(0xFF18784B), fontSize = 12.sp) }
        failure?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
        Text("Yedekler Google Drive dosyası değildir; hesabına özel Firebase Firestore'da saklanır. " +
                "Yeni telefonda geri yükleme onay gerektirir. Tek seferlik yedek sınırı 8 MB.",
            color = CloudGray, fontSize = 11.sp, lineHeight = 16.sp)
    }

    val selected = restoreInfo
    if (selected != null) AlertDialog(
        onDismissRequest = {
            if (!busy) {
                restoreFile?.delete()
                restoreFile = null
                restoreInfo = null
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        title = { Text("Bulut yedeği geri yüklensin mi?", color = CloudInk,
            fontWeight = FontWeight.Bold) },
        text = {
            Text("Bulutta ${selected.summary.examCount} deneme, " +
                "${selected.summary.questionCount} soru ve " +
                "${selected.summary.publisherLogoCount} yayın logosu var. " +
                "Geri yükleme bu telefondaki mevcut kayıtların yerine buluttakileri koyar. " +
                "Onaylamadan hiçbir veri değişmez.", color = CloudGray)
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
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
                            FirebaseCloudBackup.setAuto(context, true)
                            auto = true
                            message = "Bulut yedeği doğrulandı ve geri yüklendi."
                            onRestored()
                        }.onFailure {
                            failure = it.localizedMessage ?: "Geri yükleme başarısız."
                        }
                    }
                }
            ) { Text("Geri yükle", color = CloudInk, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                restoreFile?.delete()
                restoreFile = null
                restoreInfo = null
            }) { Text("İptal", color = CloudGray) }
        }
    )
}
