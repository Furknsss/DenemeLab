package com.denemelab.preview

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Locale

private val Black = androidx.compose.ui.graphics.Color(0xFF111111)
private val Neutral = androidx.compose.ui.graphics.Color(0xFF666666)
private val Border = androidx.compose.ui.graphics.Color(0xFFEAEAEA)
private val Cloud = androidx.compose.ui.graphics.Color(0xFFF5F5F5)
private val Paper = androidx.compose.ui.graphics.Color.White

@Composable
fun AtlasSearchDialog(
    onDismiss: () -> Unit,
    onChoose: (AtlasProgram) -> Unit,
    initialProgram: AtlasProgram? = null
) {
    var search by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("SAY") }
    var category by remember { mutableStateOf("Tümü") }
    var catalog by remember { mutableStateOf<AtlasCatalog?>(null) }
    var loadingCatalog by remember { mutableStateOf(true) }
    var loadingResults by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<AtlasResult?>(null) }
    var page by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<AtlasProgram?>(initialProgram) }
    var retryCatalog by remember { mutableIntStateOf(0) }
    var retrySearch by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(retryCatalog) {
        loadingCatalog = true
        try { catalog = YokAtlasCatalog.catalog() }
        catch (e: Exception) { error = e.message ?: "Katalog indirilemedi" }
        finally { loadingCatalog = false }
    }

    LaunchedEffect(catalog, search, score, category, page, retrySearch) {
        val lookups = catalog ?: return@LaunchedEffect
        loadingResults = true
        if (page == 0) data = null
        error = null
        delay(280) // rate-conscious typeahead debounce
        try {
            val response = YokAtlasCatalog.search(
                lookups, search, score.takeUnless { it == "Tümü" },
                category.takeUnless { it == "Tümü" }, page
            )
            data = if (page == 0) response else {
                val before = data
                if (before == null) response
                else response.copy(programs = (before.programs + response.programs).distinctBy { it.code })
            }
            if (page == 0) listState.scrollToItem(0)
        } catch (e: Exception) {
            error = e.message ?: "Bağlantı kurulamadı."
        } finally { loadingResults = false }
    }

    fun reset(newSearch: String = search, newScore: String = score, newCategory: String = category) {
        search = newSearch
        score = newScore
        category = newCategory
        page = 0
        data = null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.89f),
            shape = RoundedCornerShape(27.dp), color = Paper) {
            Column(Modifier.fillMaxSize().padding(start = 17.dp, end = 17.dp, top = 16.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hedefini seç", fontWeight = FontWeight.Bold, fontSize = 21.sp,
                        color = Black, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (detail != null) detail = null else onDismiss() }) {
                        Icon(Icons.Rounded.Close, "Kapat", tint = Black)
                    }
                }
                if (detail != null) {
                    AtlasProgramDetail(detail!!, onChoose = { onChoose(detail!!) })
                } else {
                    Text("Üniversite veya bölüm ara. Bilgiler YÖK Atlas kaynaklıdır.",
                        fontSize = 12.sp, color = Neutral)
                    Spacer(Modifier.height(15.dp))
                    OutlinedTextField(
                        value = search, onValueChange = { reset(newSearch = it) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Neutral) },
                        label = { Text("Üniversite, bölüm veya program kodu") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp)
                    )
                    Spacer(Modifier.height(13.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("SAY", "EA", "SÖZ", "DİL", "TYT", "Tümü").forEach { type ->
                            AtlasChip(type, score == type) { reset(newScore = type) }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("Tümü", "DEVLET", "VAKIF").forEach { value ->
                            AtlasChip(
                                when(value) { "DEVLET" -> "Devlet"; "VAKIF" -> "Vakıf"; else -> "Tüm üniversiteler" },
                                category == value
                            ) { reset(newCategory = value) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ARAMA SONUÇLARI", fontSize = 11.sp, color = Neutral,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (data != null) Text("${data!!.total} program", fontSize = 12.sp, color = Neutral)
                    }
                    Spacer(Modifier.height(9.dp))
                    if (loadingCatalog) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Black, modifier = Modifier.size(27.dp), strokeWidth = 2.dp)
                        }
                    } else if (error != null && data == null) {
                        AtlasFailure(error ?: "", onRetry = { if (catalog == null) retryCatalog++ else retrySearch++ }) {
                            // An explicit retry is implemented by dismissing and reopening.
                            onDismiss()
                        }
                    } else {
                        val current = data
                        if (loadingResults && current == null) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Black, modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                            }
                        } else if (current != null && current.programs.isEmpty()) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text("Bu aramaya uygun program bulunamadı.",
                                    fontSize = 13.sp, color = Neutral)
                            }
                        } else {
                            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                                items(current?.programs ?: emptyList(), key = { it.code }) { program ->
                                    AtlasResultRow(program) { detail = program }
                                    HorizontalDivider(color = Border)
                                }
                                if ((current?.programs?.size ?: 0) < (current?.total ?: 0)) {
                                    item {
                                        TextButton(onClick = { page += 1 }, enabled = !loadingResults,
                                            modifier = Modifier.fillMaxWidth()) {
                                            Text(if (loadingResults) "Yükleniyor..." else "Daha fazla sonuç", color = Black)
                                        }
                                    }
                                }
                                if (error != null) item { Text(error ?: "", color = Neutral, fontSize = 12.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Veri yılı, burs ve dil bilgisi her programda farklı olabilir.",
                        color = Neutral, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun AtlasFailure(message: String, onRetry: () -> Unit, onExit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Resmî veri bağlantısı şu anda kullanılamıyor.", color = Black,
            fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(message, color = Neutral, fontSize = 12.sp)
        Spacer(Modifier.height(11.dp))
        Row {
            TextButton(onClick = onRetry) { Text("Yeniden dene", color = Black) }
            TextButton(onClick = onExit) { Text("Kapat", color = Black) }
        }
    }
}

@Composable
private fun AtlasChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Black else Cloud, shape = CircleShape) {
        Text(text, Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            color = if (selected) Paper else Black, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun AtlasTag(text: String) {
    if (text.isNotBlank() && text != "null") {
        Surface(color = Cloud, shape = RoundedCornerShape(7.dp)) {
            Text(text, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                fontSize = 10.sp, color = Black, maxLines = 1)
        }
    }
}

@Composable
private fun AtlasResultRow(program: AtlasProgram, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        UniversityLogo(program.universityId, program.university, 43.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(program.program, color = Black, fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(program.university, color = Neutral, fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AtlasTag(program.universityType.let { if (it == "DEVLET") "Devlet" else if (it.startsWith("VAKIF")) "Vakıf" else it })
                AtlasTag(program.language)
                AtlasTag(program.scholarship)
            }
            Spacer(Modifier.height(6.dp))
            Text(program.scoreType + "  ·  " + (program.current?.year?.toString() ?: "—") +
                " sıralama: " + (program.current?.lastRank?.let(::formatRank) ?: "Veri yok"),
                fontSize = 11.sp, color = Black, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AtlasProgramDetail(program: AtlasProgram, onChoose: () -> Unit) {
    val context = LocalContext.current
    var nets by remember(program.code) { mutableStateOf<List<AtlasNet>?>(null) }
    var netError by remember(program.code) { mutableStateOf<String?>(null) }
    LaunchedEffect(program.code) {
        try { nets = YokAtlasCatalog.lastPlacedNets(program) }
        catch (e: Exception) { netError = e.message ?: "Netler yüklenemedi" }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            UniversityCampusBanner(program.universityId, program.university)
            Spacer(Modifier.height(17.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UniversityLogo(program.universityId, program.university, 59.dp)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(program.program, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp, color = Black)
                    Spacer(Modifier.height(4.dp))
                    Text(program.university, color = Neutral, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AtlasTag(program.scoreType)
                AtlasTag(program.universityType)
                AtlasTag(program.language)
                AtlasTag(program.scholarship)
                AtlasTag(program.degree)
            }
            Spacer(Modifier.height(20.dp))
            AtlasSection("Yerleştirme bilgileri")
            val year = program.current
            AtlasStat("Veri yılı", year?.year?.toString() ?: "—")
            AtlasStat("Taban başarı sırası", year?.lastRank?.let(::formatRank) ?: "Veri yok")
            AtlasStat("Taban puan", year?.baseScore?.let(::decimal) ?: "Veri yok")
            AtlasStat("Kontenjan", year?.quota?.toString() ?: "Veri yok")
            AtlasStat("Yerleşen", year?.placed?.toString() ?: "Veri yok")
            AtlasStat("ÖSYM program kodu", program.code.toString())
            Spacer(Modifier.height(20.dp))
            AtlasSection("Yıllara göre sıralama")
            program.years.forEach { stat ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stat.year.toString(), color = Neutral, fontSize = 13.sp)
                    Text(stat.lastRank?.let(::formatRank) ?: "Veri yok",
                        fontWeight = FontWeight.Medium, color = Black, fontSize = 14.sp)
                }
                HorizontalDivider(color = Border)
            }
            Spacer(Modifier.height(20.dp))
            AtlasSection("Son yerleşen kişinin netleri ve OBP")
            if (nets == null && netError == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = Black)
                    Spacer(Modifier.width(9.dp))
                    Text("Kaynak sorgulanıyor...", fontSize = 12.sp, color = Neutral)
                }
            } else if (netError != null) {
                Text("Net verisi alınamadı: " + netError, fontSize = 12.sp, color = Neutral)
            } else if (nets!!.isEmpty()) {
                Text("Bu programın son yerleşen netleri henüz bulunamadı.",
                    fontSize = 12.sp, color = Neutral)
            } else {
                val latest = nets!!.first()
                Text("Veri yılı: " + latest.year, fontWeight = FontWeight.SemiBold,
                    color = Black, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                AtlasStat("Son yerleşen OBP", latest.obp?.let(::decimal) ?: "Veri yok")
                if (latest.nets.isNotEmpty()) {
                    AtlasStat("TYT toplam net", decimal(latest.tytTotal))
                    if (latest.nets.keys.any { it.startsWith("AYT") }) AtlasStat("AYT toplam net", decimal(latest.aytTotal))
                    latest.nets.forEach { (subject,net) -> AtlasStat(subject, decimal(net)) }
                }
                if (nets!!.size > 1) {
                    Spacer(Modifier.height(9.dp))
                    Text("Diğer yıllar: " + nets!!.drop(1).joinToString { it.year.toString() },
                        color = Neutral, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("Not: Netler, programın kontenjanından son yerleşen kişiye ait resmî kaynak alanlarıdır. Yıl, taban sırasıyla aynı olmayabilir.",
                    color = Neutral, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            UniversityVisualCredits(program.universityId, program.university) { sourcePage ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourcePage)))
            }
            Spacer(Modifier.height(10.dp))
            Surface(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yokatlas.yok.gov.tr/")))
            }, color = Cloud, shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Rounded.OpenInNew, null, Modifier.size(16.dp), tint = Black)
                    Spacer(Modifier.width(7.dp))
                    Text("YÖK Atlas kaynağını aç", color = Black, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(onClick = onChoose, color = Black, shape = RoundedCornerShape(17.dp),
            modifier = Modifier.fillMaxWidth()) {
            Text("Bu bölümü hedefim yap", Modifier.padding(vertical = 15.dp),
                color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun AtlasSection(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = Black, fontSize = 16.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun AtlasStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = Neutral, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Black)
    }
}

private fun formatRank(n: Int): String = "%,d".format(Locale.forLanguageTag("tr-TR"), n)
private fun decimal(n: Double): String = "%.2f".format(Locale.forLanguageTag("tr-TR"), n)
