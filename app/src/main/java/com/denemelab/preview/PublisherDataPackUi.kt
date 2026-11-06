package com.denemelab.preview

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PackInk = Color(0xFF101010)
private val PackSecondary = Color(0xFF717171)
private val PackLight = Color(0xFFF5F5F5)

@Composable
fun PublisherDataPackDialog(
    publishers: List<PublisherBrand>,
    onDismiss: () -> Unit,
    onCatalogChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var chosenLogo by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            chosenLogo = uri
            failure = null
        }
    }
    fun startEdit(publisher: String) {
        editName = publisher
        chosenLogo = null
        failure = null
        editing = true
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.White, shape = RoundedCornerShape(27.dp),
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.90f)) {
            Column(Modifier.fillMaxSize().padding(19.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Veri Paketi", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = PackInk)
                        Text("Yayınlar ve kişisel görsel arşivin", fontSize = 11.sp, color = PackSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Kapat", tint = PackInk)
                    }
                }
                Spacer(Modifier.height(17.dp))
                Text("Veri paketi türü", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = PackInk)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = PackInk, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Image, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Logo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(onClick = { startEdit("") }, color = PackInk,
                        shape = RoundedCornerShape(13.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Logo ekle", color = Color.White, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                OutlinedTextField(value = filter, onValueChange = { filter = it },
                    placeholder = { Text("Yayın ara", fontSize = 13.sp) },
                    shape = RoundedCornerShape(15.dp),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                notice?.let {
                    Text(it, color = Color(0xFF18784B), fontSize = 12.sp)
                    Spacer(Modifier.height(7.dp))
                }
                val filtered = publishers.filter {
                    PublisherCatalog.normalize(it.displayName).contains(PublisherCatalog.normalize(filter))
                }
                LazyColumn(modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(filtered, key = { it.key }) { brand ->
                        Surface(onClick = { startEdit(brand.displayName) },
                            color = PackLight, shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                PublisherLogo(brand.displayName, size = 43.dp)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(brand.displayName, color = PackInk,
                                        fontWeight = FontWeight.Medium, fontSize = 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (brand.logoFile.isNotBlank()) "Logo kayıtlı" else "Logo yüklenmedi",
                                        color = PackSecondary, fontSize = 11.sp)
                                }
                                Text(if (brand.logoFile.isBlank()) "Ekle" else "Değiştir",
                                    color = PackInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Logolar cihazında saklanır; uygulama başka yayınların görsellerini otomatik indirmez.",
                    fontSize = 10.sp, color = PackSecondary)
            }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { if (!saving) editing = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(23.dp),
            title = { Text("Yayın logosu ekle", color = PackInk, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Yayın seç veya farklı bir yayın adı yaz.", color = PackSecondary, fontSize = 12.sp)
                    OutlinedTextField(value = editName, onValueChange = {
                        editName = it.take(90)
                        failure = null
                    }, label = { Text("Yayın adı") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    val suggestions = if (editName.isBlank()) emptyList() else publishers
                        .filter { PublisherCatalog.normalize(it.displayName)
                            .contains(PublisherCatalog.normalize(editName)) }
                        .take(4)
                    suggestions.forEach { brand ->
                        Text(brand.displayName,
                            modifier = Modifier.fillMaxWidth().clickable {
                                editName = brand.displayName
                            }.padding(vertical = 3.dp),
                            color = PackInk, fontSize = 12.sp)
                    }
                    Surface(onClick = { logoPicker.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(14.dp), color = PackLight,
                        border = BorderStroke(1.dp, Color(0xFFE8E8E8))) {
                        Row(Modifier.fillMaxWidth().padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            PublisherLogo(editName, size = 43.dp, pendingSelection = chosenLogo)
                            Spacer(Modifier.width(10.dp))
                            Text(if (chosenLogo == null) "Görsel seç (PNG, JPG, WEBP)" else "Yeni görsel seçildi · değiştir",
                                color = PackInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    failure?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editName.isNotBlank() && chosenLogo != null && !saving,
                    onClick = {
                        val uri = chosenLogo ?: return@TextButton
                        val name = editName.trim()
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { PublisherCatalog.upsert(context, name, uri) }
                            }
                            saving = false
                            result.onSuccess {
                                onCatalogChanged()
                                notice = "Logo veri paketine eklendi: " + it.displayName
                                editing = false
                                chosenLogo = null
                            }.onFailure {
                                failure = it.localizedMessage ?: "Logo kaydedilemedi."
                            }
                        }
                    }) {
                    Text(if (saving) "Kaydediliyor..." else "Veri paketine ekle", color = PackInk)
                }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { editing = false }) {
                    Text("Vazgeç", color = PackSecondary)
                }
            }
        )
    }
}
