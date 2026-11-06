package com.denemelab.preview

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-time import for students moving from a legacy debug-signed APK.
 * No new Drive export or routine manual backup option is exposed.
 */
@Composable
fun LegacyBackupMigration(onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var staged by remember { mutableStateOf<File?>(null) }
    var summary by remember { mutableStateOf<BackupSummary?>(null) }

    DisposableEffect(Unit) { onDispose { staged?.delete() } }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            busy = true
            error = null
            scope.launch {
                val incoming = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = DenemeLabBackup.stageImport(context, uri)
                        file to DenemeLabBackup.inspect(file)
                    }
                }
                busy = false
                incoming.onSuccess { (file, info) ->
                    staged?.delete()
                    staged = file
                    summary = info
                }.onFailure {
                    error = it.localizedMessage ?: "Eski yedek okunamadı."
                }
            }
        }
    }
    HorizontalDivider()
    TextButton(onClick = { show = !show }) {
        Text(if (show) "Eski yedek aktarımını kapat" else "Eski DenemeLab kayıtlarımı taşı",
            fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
    if (show) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Eski APK'nın imzası farklıysa uygulama güncellenemeyebilir. " +
            "Eski sürümde oluşturulmuş DenemeLab ZIP yedeğini buradan tek seferlik aktarabilirsin. " +
            "Bu Google Drive yedekleme özelliği değildir.",
            fontSize = 11.sp)
        OutlinedButton(
            onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp)
        ) { Text("Eski DenemeLab yedeğini seç") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
    }
    val pending = summary
    if (pending != null) AlertDialog(
        onDismissRequest = { if (!busy) { summary = null; staged?.delete(); staged = null } },
        title = { Text("Eski veriler aktarılsın mı?") },
        text = {
            Text("Bu yedekte ${pending.examCount} deneme, " +
                "${pending.questionCount} soru ve ${pending.publisherLogoCount} logo var. " +
                "Aktarım mevcut telefon kayıtlarının yerine geçer. " +
                "İşlemden önce mevcut verilerini koruduğundan emin ol.")
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                val file = staged ?: return@TextButton
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { DenemeLabBackup.restore(context, file) }
                    }
                    busy = false
                    result.onSuccess {
                        file.delete()
                        staged = null
                        summary = null
                        onRestored()
                        FirebaseBackupScheduler.onLocalChange(context)
                    }.onFailure {
                        error = it.localizedMessage ?: "Eski yedek aktarılamadı."
                    }
                }
            }) { Text("Onayla ve aktar") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                staged?.delete(); staged = null; summary = null
            }) { Text("İptal") }
        }
    )
}
