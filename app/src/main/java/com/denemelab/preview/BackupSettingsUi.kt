package com.denemelab.preview

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
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
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android's account-backed Auto Backup is configured in the manifest.
 * This panel provides an additional portable archive for user-chosen storage
 * providers, including Google Drive. No claim of completed cloud sync until
 * the selected provider confirms the write.
 */
@Composable
fun BackupSettingsDialog(onDismiss: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupSummary?>(null) }
    var incoming by remember { mutableStateOf<File?>(null) }
    DisposableEffect(Unit) {
        onDispose { incoming?.delete() }
    }
    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            error = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { DenemeLabBackup.exportTo(context, uri) }
                }
                busy = false
                result.onSuccess { summary ->
                    message = "Yedek dosyası kaydedildi: ${summary.examCount} deneme, " +
                        "${summary.questionCount} soru. Drive seçtiysen eşitleme durumunu Drive'dan kontrol et."
                }.onFailure {
                    error = it.localizedMessage ?: "Yedek kaydedilemedi."
                }
            }
        }
    }
    val openPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            error = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = DenemeLabBackup.stageImport(context, uri)
                        file to DenemeLabBackup.inspect(file)
                    }
                }
                busy = false
                result.onSuccess {
                    incoming?.delete()
                    incoming = it.first
                    preview = it.second
                }.onFailure {
                    error = it.localizedMessage ?: "Yedek okunamadı."
                }
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(color = Color.White, shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Icon(Icons.Rounded.CloudDone, contentDescription = null,
                    tint = Color(0xFF151515), modifier = Modifier.size(30.dp))
                Text("Bulut ve yedekleme", fontWeight = FontWeight.Bold,
                    color = Color(0xFF151515), fontSize = 22.sp)
                Text("Android'in Google hesabı yedeği, cihaz ayarların izin veriyorsa uygulama verilerini arka planda korur. " +
                    "Ek güvenlik için denemelerini, soru kayıtlarını, hedefini, profilini, çalışma geçmişini ve yayın logolarını " +
                    "Google Drive'a veya başka bir güvenli konuma yedekleyebilirsin.",
                    color = Color(0xFF696969), fontSize = 12.sp, lineHeight = 18.sp)
                Surface(onClick = {
                    if (!busy) savePicker.launch("DenemeLab-Yedek.zip")
                }, color = Color(0xFF151515), shape = RoundedCornerShape(15.dp),
                    enabled = !busy) {
                    Row(Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudUpload, null, tint = Color.White,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Yedek oluştur / Drive'a kaydet", color = Color.White,
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                Surface(onClick = {
                    if (!busy) openPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                }, color = Color(0xFFF5F5F5), shape = RoundedCornerShape(15.dp),
                    enabled = !busy) {
                    Row(Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Restore, null, tint = Color(0xFF151515),
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Yedekten geri yükle", color = Color(0xFF151515),
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                if (busy) CircularProgressIndicator(
                    modifier = Modifier.size(22.dp), color = Color.Black)
                message?.let { Text(it, fontSize = 12.sp, color = Color(0xFF18784B)) }
                error?.let { Text(it, fontSize = 12.sp, color = Color(0xFFB42318)) }
                Text("Google Drive seçersen dosyan Google hesabının Drive alanında saklanır. " +
                    "Bu işlem sürekli canlı senkronizasyon değildir; yedeği güncellemek için yeniden kaydet.",
                    fontSize = 11.sp, color = Color(0xFF818181))
                TextButton(onClick = onDismiss, enabled = !busy) {
                    Text("Kapat", color = Color(0xFF151515))
                }
            }
        }
    }

    preview?.let { summary ->
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    incoming?.delete()
                    incoming = null
                    preview = null
                }
            },
            title = { Text("Yedeği geri yükle?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Bu arşivde ${summary.examCount} deneme, ${summary.questionCount} soru ve " +
                    "${summary.publisherLogoCount} yayın logosu var. " +
                    "Geri yükleme mevcut cihaz kayıtlarını değiştirecek. " +
                    "Önce güncel kayıtlarını ayrıca yedeklemen önerilir.")
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val file = incoming ?: return@TextButton
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { DenemeLabBackup.restore(context, file) }
                        }
                        busy = false
                        result.onSuccess {
                            file.delete()
                            incoming = null
                            preview = null
                            onRestored()
                        }.onFailure {
                            error = it.localizedMessage ?: "Geri yükleme başarısız."
                        }
                    }
                }) { Text("Geri yükle", color = Color(0xFF151515)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    incoming?.delete()
                    incoming = null
                    preview = null
                }) { Text("İptal", color = Color(0xFF777777)) }
            },
            containerColor = Color.White
        )
    }
}
