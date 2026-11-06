package com.denemelab.preview

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

private val Ink = androidx.compose.ui.graphics.Color(0xFF111111)
private val Muted = androidx.compose.ui.graphics.Color(0xFF6B6B6B)
private val Border = androidx.compose.ui.graphics.Color(0xFFEAEAEA)
private val Paper = androidx.compose.ui.graphics.Color.White
private val Subtle = androidx.compose.ui.graphics.Color(0xFFF5F5F5)

@Composable
fun ProfileTopBar(
    title: String,
    profile: StudentProfile,
    showAddExam: Boolean,
    showAddQuestion: Boolean,
    onAddExam: () -> Unit,
    onAddQuestion: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 19.dp, end = 14.dp, top = 16.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Image(painter = painterResource(id = R.drawable.ic_denemelab),
            contentDescription = "DenemeLab", modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("DenemeLab", color = Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
        if (showAddExam || showAddQuestion) {
            Surface(onClick = if (showAddExam) onAddExam else onAddQuestion,
                shape = CircleShape, color = Ink) {
                Icon(Icons.Rounded.Add, "Ekle", tint = Paper, modifier = Modifier.padding(9.dp).size(18.dp))
            }
            Spacer(Modifier.width(9.dp))
        }
        Box {
            Row(Modifier.clickable { expanded = true }.padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.pictureUri, 37)
                Spacer(Modifier.width(6.dp))
                Text(profile.displayName, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 78.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                containerColor = Paper) {
                DropdownMenuItem(text = { Text("Profilim", color = Ink) },
                    leadingIcon = { Icon(Icons.Rounded.Person, null, tint = Ink) },
                    onClick = { expanded = false; onProfile() })
                DropdownMenuItem(text = { Text("Ayarlar", color = Ink) },
                    leadingIcon = { Icon(Icons.Rounded.Settings, null, tint = Ink) },
                    onClick = { expanded = false; onSettings() })
            }
        }
    }
}

@Composable
private fun Avatar(pictureUri: String, diameter: Int) {
    Box(Modifier.size(diameter.dp).clip(CircleShape).background(Subtle),
        contentAlignment = Alignment.Center) {
        if (pictureUri.isBlank()) Icon(Icons.Rounded.Person, "Profil fotoğrafı", tint = Ink,
            modifier = Modifier.size((diameter * 0.62f).dp))
        else AsyncImage(model = Uri.parse(pictureUri), contentDescription = "Profil fotoğrafı",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
    }
}

@Composable
fun StudentProfileDialog(
    initial: StudentProfile,
    onDismiss: () -> Unit,
    onSave: (StudentProfile) -> Unit,
    onPickTarget: () -> Unit
) {
    val context = LocalContext.current
    var firstName by remember(initial) { mutableStateOf(initial.firstName) }
    var surname by remember(initial) { mutableStateOf(initial.surname) }
    var grade by remember(initial) { mutableStateOf(initial.grade) }
    var avatarUri by remember(initial) { mutableStateOf(initial.pictureUri) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: SecurityException) {}
            avatarUri = uri.toString()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(27.dp), color = Paper, modifier = Modifier.fillMaxWidth(0.93f)) {
            Column(Modifier.padding(22.dp).heightIn(max = 610.dp).verticalScroll(rememberScrollState())) {
                Text("Profilim", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(5.dp))
                Text("DenemeLab'ı kendine göre düzenle.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(23.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(avatarUri, 68)
                    Spacer(Modifier.width(15.dp))
                    Surface(color = Subtle, shape = CircleShape, onClick = { picker.launch(arrayOf("image/*")) }) {
                        Text("Fotoğraf seç", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Ad") }, singleLine = true,
                    shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(surname, { surname = it }, label = { Text("Soyad") }, singleLine = true,
                    shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("Eğitim durumun", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("9. sınıf", "10. sınıf", "11. sınıf", "12. sınıf", "Mezun").forEach { option ->
                        Surface(onClick = { grade = option },
                            color = if (grade == option) Ink else Subtle, shape = CircleShape) {
                            Text(option, Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                                color = if (grade == option) Paper else Ink, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(23.dp))
                Surface(color = Subtle, shape = RoundedCornerShape(15.dp), onClick = {
                    onSave(StudentProfile(firstName.trim(), surname.trim(), grade, avatarUri))
                    onPickTarget()
                }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Hedef üniversitem ve bölümüm", fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp, color = Ink)
                            Spacer(Modifier.height(4.dp))
                            Text("Resmî program kataloğundan seç", fontSize = 11.sp, color = Muted)
                        }
                        Text("Seç →", fontSize = 12.sp, color = Ink)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Surface(
                    color = Ink, shape = RoundedCornerShape(16.dp),
                    onClick = { onSave(StudentProfile(firstName.trim(), surname.trim(), grade, avatarUri)) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Bilgilerimi kaydet", Modifier.padding(vertical = 15.dp),
                        color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        textAlign = TextAlign.Center)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Vazgeç", color = Muted)
                }
            }
        }
    }
}

@Composable
fun DenemeLabSettingsDialog(
    reduceMotion: Boolean,
    onMotionChanged: (Boolean) -> Unit,
    onDataPack: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ayarlar", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hareketleri azalt", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Ekran geçişlerini ve büyüme animasyonlarını kapat.",
                            color = Muted, fontSize = 11.sp)
                    }
                    Switch(checked = reduceMotion, onCheckedChange = onMotionChanged)
                }
                HorizontalDivider(color = Border)
                Text("Tema", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Beyaz / siyah premium tema", color = Muted, fontSize = 12.sp)
                HorizontalDivider(color = Border)
                Surface(onClick = onDataPack, color = Subtle, shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Veri Paketi", color = Ink, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Logo paketini yönet, yayın seç ve görsel yükle",
                                color = Muted, fontSize = 11.sp)
                        }
                        Text("Aç →", color = Ink, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = Border)
                Text("Veri ve gizlilik", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Profilin ve denemelerin bu sürümde cihazında tutulur. Üniversite aramaları YÖK Atlas sunucusuna iletilir.",
                    color = Muted, fontSize = 12.sp)
                HorizontalDivider(color = Border)
                Surface(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yokatlas.yok.gov.tr/")))
                }, color = Subtle, shape = RoundedCornerShape(13.dp)) {
                    Text("Resmî YÖK Atlas kaynak sitesi ↗", Modifier.fillMaxWidth().padding(13.dp),
                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Text("DenemeLab · 0.11.0 geliştirme sürümü", color = Muted, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", color = Ink) } },
        containerColor = Paper
    )
}
