package com.denemelab.preview

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.util.Calendar
import kotlin.math.min
import kotlin.math.roundToInt

private val Black = Color(0xFF111111)
private val Muted = Color(0xFF737373)
private val Hairline = Color(0xFFEAEAEA)
private val Paper = Color.White
private val Soft = Color(0xFFF6F6F6)

data class ExamEntrySubmission(val exam: ExamRecord, val newPublisherLogo: Uri?)

/**
 * One clear save action. No misleading draft save button.
 * Manual score input is fully functional; photo document parsing is not yet
 * enabled and therefore never silently saves fake OCR data.
 */
@Composable
fun ExamEntryV3(
    publishers: List<PublisherBrand>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (ExamEntrySubmission) -> Unit
) {
    val context = LocalContext.current
    var selectedMethod by remember { mutableStateOf("Manuel Ekle") }
    var examName by remember { mutableStateOf("") }
    var publisherName by remember { mutableStateOf("") }
    var newLogo by remember { mutableStateOf<Uri?>(null) }
    var examCategory by remember { mutableStateOf("Genel") }
    var examType by remember { mutableStateOf("TYT") }
    var track by remember { mutableStateOf("SAY") }
    var branchSubject by remember { mutableStateOf("") }
    var branchCount by remember { mutableStateOf("40") }
    var branchCorrect by remember { mutableIntStateOf(0) }
    var branchWrong by remember { mutableIntStateOf(0) }
    var mood by remember { mutableStateOf("Normal") }

    // The date is local calendar midnight; createdAt remains the insertion time.
    val now = remember { Calendar.getInstance() }
    var examDate by remember {
        mutableLongStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) newLogo = uri
    }
    var resultsImage by remember { mutableStateOf<Uri?>(null) }
    var reportResult by remember { mutableStateOf<ReportResult?>(null) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var readingReport by remember { mutableStateOf(false) }
    var reviewedReport by remember { mutableStateOf(false) }
    val verifiedKeys = remember { mutableStateMapOf<String, Boolean>() }
    val issueItems = remember { mutableStateListOf<ExamTopicIssue>() }
    var editingIssue by remember { mutableStateOf<Pair<String,String>?>(null) }
    val reportPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            reportResult = null
            reportError = null
            reviewedReport = false
            verifiedKeys.clear()
            issueItems.clear()
            resultsImage = uri
        }
    }

    val selectedBrand = publishers.firstOrNull {
        it.key == PublisherCatalog.normalize(publisherName)
    }
    val numbers = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    LaunchedEffect(resultsImage) {
        val uri = resultsImage ?: return@LaunchedEffect
        if (selectedMethod != "Fotoğraf ile Ekle") return@LaunchedEffect
        readingReport = true
        reportError = null
        try {
            val parsed = ExamReportScanner.recognize(context, uri)
            reportResult = parsed
            // Only the TYT type is supported by this published report parser.
            examType = "TYT"
            examCategory = "Genel"
            numbers.clear()
            verifiedKeys.clear()
            parsed.counts.forEach { (key, value) ->
                numbers[key] = value
                verifiedKeys[key] = true
            }
            issueItems.clear()
            issueItems.addAll(parsed.topics)
            if (examName.isBlank() && parsed.title.isNotBlank()) examName = parsed.title
        } catch (e: Exception) {
            reportResult = null
            reportError = e.localizedMessage ?: "Belge okunamadı."
        } finally {
            readingReport = false
        }
    }
    val templates = ExamStructure.subjectsForEntry(examType, if (examType == "AYT") track else "")
    val parts = templates.map { item ->
        val (c, w) = numbers[item.key] ?: (0 to 0)
        SubjectScore(item.key, item.title, item.questions, c, w)
    }
    val generalOk = ExamStructure.valid(parts, examType, if (examType == "AYT") track else "") &&
        parts.any { it.correct + it.wrong > 0 }
    val selectedCount = branchCount.toIntOrNull() ?: 0
    val branchOk = branchSubject.isNotBlank() && selectedCount in 1..120 &&
        branchCorrect + branchWrong in 1..selectedCount
    val correct = if (examCategory == "Genel") parts.sumOf { it.correct } else branchCorrect
    val wrong = if (examCategory == "Genel") parts.sumOf { it.wrong } else branchWrong
    val blanks = (if (examCategory == "Genel") templates.sumOf { it.questions } else selectedCount) -
        correct - wrong
    val issueError = TopicBreakdown.validate(
        issueItems.toList(), if (examCategory == "Genel") parts else emptyList(),
        if (examCategory == "Branş") SubjectScore(
            "branch", branchSubject.ifBlank { "Branş" }, selectedCount, branchCorrect, branchWrong
        ) else null
    )
    val photoReady = reportResult != null && !readingReport && reviewedReport &&
        examType == "TYT" && examCategory == "Genel" &&
        templates.all { verifiedKeys[it.key] == true }
    val saveEnabled = examName.isNotBlank() &&
        publisherName.isNotBlank() && !isSaving && issueError == null &&
        (if (examCategory == "Genel") generalOk else branchOk) &&
        (selectedMethod == "Manuel Ekle" || photoReady)

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.97f).fillMaxHeight(0.95f),
            shape = RoundedCornerShape(28.dp), color = Paper) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 17.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Deneme Ekle", fontWeight = FontWeight.Bold, color = Black,
                        fontSize = 23.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Rounded.Close, "Kapat", tint = Black)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 19.dp)
                    .background(Soft, RoundedCornerShape(18.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Manuel Ekle", "Fotoğraf ile Ekle").forEach { method ->
                        Surface(
                            onClick = { selectedMethod = method },
                            modifier = Modifier.weight(1f),
                            color = if (selectedMethod == method) Black else Soft,
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Text(method, Modifier.padding(vertical = 12.dp),
                                color = if (selectedMethod == method) Paper else Muted,
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 19.dp, vertical = 8.dp)
                ) {
                    Text("Deneme bilgileri", fontWeight = FontWeight.SemiBold,
                        color = Black, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = examName, onValueChange = { examName = it.take(90) },
                        label = { Text("Deneme adı") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = publisherName,
                        onValueChange = { input ->
                            if (PublisherCatalog.normalize(input) != PublisherCatalog.normalize(publisherName))
                                newLogo = null
                            publisherName = input.take(90)
                        },
                        label = { Text("Yayın adı") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
                    if (publishers.isNotEmpty()) {
                        Spacer(Modifier.height(9.dp))
                        Text("Daha önce eklediğin yayınlar", color = Muted, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            publishers.forEach { publisher ->
                                EntryChip(publisher.displayName,
                                    selected = publisher.key == PublisherCatalog.normalize(publisherName)) {
                                    publisherName = publisher.displayName
                                    newLogo = null
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Surface(color = Soft, shape = RoundedCornerShape(17.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PublisherLogo(publisherName, size = 55.dp, pendingSelection = newLogo)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        newLogo != null -> "Yeni yayın logosu seçildi"
                                        selectedBrand?.logoFile?.isNotBlank() == true ->
                                            "Kayıtlı yayın logosu kullanılacak"
                                        else -> "Yayın logosu ekle"
                                    },
                                    color = Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Text("Bir kez ekle; aynı yayında otomatik gelsin.",
                                    color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            TextButton(onClick = { logoPicker.launch("image/*") }) {
                                Text(if (newLogo == null && selectedBrand?.logoFile?.isNotBlank() != true) "Seç" else "Değiştir",
                                    color = Black, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Deneme tarihi", color = Black, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Surface(onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = examDate }
                            DatePickerDialog(context, { _, year, month, day ->
                                examDate = Calendar.getInstance().apply {
                                    set(year, month, day, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }, calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                        }, shape = RoundedCornerShape(14.dp), color = Soft) {
                            Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(17.dp), tint = Black)
                                Spacer(Modifier.width(8.dp))
                                Text(LocalData.date(examDate), color = Black, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(19.dp))
                    Text("Sınav türü seçiniz", color = Black, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EntryChip("TYT", examType == "TYT") { examType = "TYT" }
                        EntryChip("AYT", examType == "AYT") { examType = "AYT" }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("Deneme kategorisi", color = Black, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EntryChip("Genel", examCategory == "Genel") { examCategory = "Genel" }
                        EntryChip("Branş", examCategory == "Branş") { examCategory = "Branş" }
                    }
                    if (examCategory == "Genel" && examType == "AYT") {
                        Spacer(Modifier.height(13.dp))
                        Text("AYT puan türü", color = Black, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SAY", "EA", "SÖZ").forEach { value ->
                                EntryChip(value, value == track) { track = value }
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("AYT alanına ait 80 sorunun alt dersleri kaydedilir.",
                            color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(22.dp))
                    if (selectedMethod == "Fotoğraf ile Ekle") {
                        PhotoImportPanel(resultsImage) { reportPicker.launch("image/*") }
                        Spacer(Modifier.height(13.dp))
                        if (readingReport) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Black, strokeWidth = 2.dp,
                                    modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(11.dp))
                                Text("Belge cihaz üzerinde okunuyor...", color = Muted, fontSize = 12.sp)
                            }
                        }
                        if (reportError != null) Text("Okuma hatası: " + reportError,
                            color = Color(0xFFB42318), fontSize = 12.sp)
                        reportResult?.let { report ->
                            Text("${report.foundCount}/10 ders algılandı",
                                color = Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            report.warnings.forEach {
                                Text(it, color = Muted, fontSize = 11.sp)
                            }
                            Text("OCR ile okunan sayıları ve konuları kontrol et. Yanlış okunan dersin sürgüsünü düzelt.",
                                color = Muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    if (selectedMethod == "Manuel Ekle" || reportResult != null) {
                        Text("Ders sonuçları", color = Black, fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Sürgüleri kaydırarak doğru ve yanlış sayılarını ayarla. Boş ve net otomatik hesaplanır.",
                            fontSize = 12.sp, color = Muted, lineHeight = 17.sp)
                        Spacer(Modifier.height(13.dp))
                        if (examCategory == "Genel") {
                            val grouped = templates.groupBy { ExamStructure.groupName(examType, it.key) }
                            val expanded = remember { mutableStateMapOf<String, Boolean>() }
                            grouped.forEach { (group, items) ->
                                val groupCorrect = items.sumOf { numbers[it.key]?.first ?: 0 }
                                val groupWrong = items.sumOf { numbers[it.key]?.second ?: 0 }
                                val count = items.sumOf { it.questions }
                                val open = expanded[group] ?: (group == "Türkçe" || examType == "AYT" ||
                                    selectedMethod == "Fotoğraf ile Ekle")
                                Surface(Modifier.fillMaxWidth().padding(bottom = 11.dp)
                                    .animateContentSize(animationSpec = tween(240)),
                                    color = Paper, shape = RoundedCornerShape(21.dp),
                                    border = BorderStroke(1.dp, Hairline)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(Modifier.fillMaxWidth().clickable { expanded[group] = !open },
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(group, fontWeight = FontWeight.SemiBold,
                                                    color = Black, fontSize = 15.sp)
                                                Spacer(Modifier.height(4.dp))
                                                Text("$count soru · ${LocalData.net(ExamRules.net(groupCorrect, groupWrong))} net",
                                                    color = Muted, fontSize = 12.sp)
                                            }
                                            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                contentDescription = if (open) "Kapat" else "Dersleri aç", tint = Black)
                                        }
                                        AnimatedVisibility(visible = open) {
                                            Column {
                                                Spacer(Modifier.height(14.dp))
                                                items.forEachIndexed { index, lesson ->
                                                    val values = numbers[lesson.key] ?: (0 to 0)
                                                    ScoreSliderRow(
                                                        label = lesson.title, questions = lesson.questions,
                                                        correct = values.first, wrong = values.second
                                                    ) { c, w ->
                                                        numbers[lesson.key] = c to w
                                                        if (selectedMethod == "Fotoğraf ile Ekle")
                                                            verifiedKeys[lesson.key] = true
                                                    }
                                                    Spacer(Modifier.height(8.dp))
                                                    if (selectedMethod == "Fotoğraf ile Ekle" &&
                                                        verifiedKeys[lesson.key] != true) {
                                                        Text("Bu ders OCR ile okunamadı; rakamları kontrol edip doğrula.",
                                                            color = Color(0xFFB42318), fontSize = 10.sp)
                                                        TextButton(onClick = {
                                                            verifiedKeys[lesson.key] = true
                                                        }) {
                                                            Text("Bu dersi kontrol ettim", color = Black, fontSize = 11.sp)
                                                        }
                                                    }
                                                    LessonIssueButtons(
                                                        lesson = SubjectScore(lesson.key, lesson.title,
                                                            lesson.questions, values.first, values.second),
                                                        issues = issueItems.filter { it.lessonKey == lesson.key },
                                                        onOpen = { kind -> editingIssue = lesson.key to kind }
                                                    )
                                                    if (index < items.lastIndex)
                                                        HorizontalDivider(color = Hairline, modifier = Modifier.padding(vertical = 10.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = branchSubject, onValueChange = { branchSubject = it.take(60) },
                                label = { Text("Branş dersi") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = branchCount,
                                onValueChange = { branchCount = it.filter(Char::isDigit).take(3) },
                                label = { Text("Toplam soru sayısı") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp))
                            Spacer(Modifier.height(12.dp))
                            Surface(shape = RoundedCornerShape(20.dp), color = Paper,
                                border = BorderStroke(1.dp, Hairline)) {
                                Column(Modifier.padding(14.dp)) {
                                    ScoreSliderRow(branchSubject.ifBlank { "Branş" },
                                        selectedCount, branchCorrect, branchWrong) { c, w ->
                                        branchCorrect = c
                                        branchWrong = w
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    LessonIssueButtons(
                                        lesson = SubjectScore("branch",
                                            branchSubject.ifBlank { "Branş" }, selectedCount,
                                            branchCorrect, branchWrong),
                                        issues = issueItems.toList(),
                                        onOpen = { kind -> editingIssue = "branch" to kind }
                                    )
                                }
                            }
                        }
                    }
                    if (selectedMethod == "Fotoğraf ile Ekle" && reportResult != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = reviewedReport,
                                onCheckedChange = { reviewedReport = it },
                                colors = CheckboxDefaults.colors(checkedColor = Black))
                            Text("Belge sonuçlarını ve konu detaylarını kontrol ettim",
                                color = Black, fontSize = 12.sp, lineHeight = 17.sp,
                                modifier = Modifier.weight(1f))
                        }
                    }
                    if (issueError != null) {
                        Text(issueError, color = Color(0xFFB42318), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(23.dp))
                    Text("Deneme modu", color = Black, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Sınav sırasında nasıl hissettin?", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(11.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("Sakin", "Normal", "Stresli", "Çok stresli").forEach { label ->
                            EntryChip(label, selected = mood == label) { mood = label }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                HorizontalDivider(color = Hairline)
                Column(Modifier.padding(horizontal = 19.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (saveEnabled) LocalData.net(ExamRules.net(correct, wrong)) + " net" else
                                if (selectedMethod == "Manuel Ekle") "Net hesaplanıyor" else "Fotoğraf analizi hazırlanıyor",
                                color = Black, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text(if (saveEnabled) "$correct doğru · $wrong yanlış · $blanks boş" else
                                if (selectedMethod == "Manuel Ekle") "Gerekli alanları doldur" else
                                    "AI belge okuma henüz etkin değil",
                                color = Muted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(11.dp))
                    Surface(
                        onClick = {
                            if (!saveEnabled) return@Surface
                            val ts = System.currentTimeMillis()
                            val exam = ExamRecord(
                                id = ts, name = examName.trim(), category = examCategory,
                                examType = examType, subject = if (examCategory == "Branş") branchSubject.trim() else "",
                                correct = correct, wrong = wrong, blank = blanks, createdAt = ts,
                                track = if (examCategory == "Genel" && examType == "AYT") track else "",
                                parts = if (examCategory == "Genel") parts else emptyList(),
                                publisher = publisherName.trim(), examDate = examDate, mood = mood,
                                topicIssues = issueItems.toList(),
                                source = if (selectedMethod == "Fotoğraf ile Ekle") "Belge OCR + kullanıcı onayı"
                                    else "Manuel"
                            )
                            onSave(ExamEntrySubmission(exam, newLogo))
                        },
                        enabled = saveEnabled,
                        color = if (saveEnabled) Black else Hairline,
                        shape = RoundedCornerShape(17.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSaving) "Kaydediliyor..." else "Denemeyi Kaydet",
                            Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            color = if (saveEnabled) Paper else Muted,
                            fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    editingIssue?.let { (key, kind) ->
        val lesson = if (key == "branch") SubjectScore(
            "branch", branchSubject.ifBlank { "Branş" }, selectedCount,
            branchCorrect, branchWrong
        ) else parts.firstOrNull { it.key == key }
        if (lesson != null) {
            ExamTopicEditor(lesson, kind, issueItems.toList(),
                onDismiss = { editingIssue = null },
                onChange = { updated ->
                    issueItems.clear()
                    issueItems.addAll(updated)
                })
        } else editingIssue = null
    }
}

@Composable
private fun EntryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = if (selected) Black else Soft,
        shape = CircleShape) {
        Text(label, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (selected) Paper else Black, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScoreSliderRow(
    label: String, questions: Int, correct: Int, wrong: Int,
    onChange: (Int, Int) -> Unit
) {
    val allowed = questions.coerceAtLeast(1)
    val c = min(correct, questions.coerceAtLeast(0))
    val w = min(wrong, (questions - c).coerceAtLeast(0))
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text("${LocalData.net(ExamRules.net(c, w))} net", color = Black,
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text("$questions soru · ${(questions-c-w).coerceAtLeast(0)} boş",
            fontSize = 11.sp, color = Muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Doğru $c", fontSize = 11.sp, color = Black, modifier = Modifier.width(75.dp))
            Slider(
                value = c.toFloat(), onValueChange = { raw ->
                    val next = raw.roundToInt().coerceIn(0, questions.coerceAtLeast(0))
                    onChange(next, min(w, questions - next))
                }, valueRange = 0f..allowed.toFloat(),
                enabled = questions > 0,
                steps = (allowed - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label doğru sayısı" },
                colors = SliderDefaults.colors(thumbColor = Black,
                    activeTrackColor = Black, inactiveTrackColor = Hairline)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Yanlış $w", fontSize = 11.sp, color = Muted, modifier = Modifier.width(75.dp))
            Slider(
                value = w.toFloat(), onValueChange = { raw ->
                    val next = raw.roundToInt().coerceIn(0, questions.coerceAtLeast(0))
                    onChange(min(c, questions - next), next)
                }, valueRange = 0f..allowed.toFloat(), enabled = questions > 0,
                steps = (allowed - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f).semantics { contentDescription = "$label yanlış sayısı" },
                colors = SliderDefaults.colors(thumbColor = Muted,
                    activeTrackColor = Muted, inactiveTrackColor = Hairline)
            )
        }
    }
}

@Composable
private fun PhotoImportPanel(photo: Uri?, onPick: () -> Unit) {
    Surface(
        color = Soft, shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(17.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PhotoLibrary, null, tint = Black, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(11.dp))
            Text("Sonuç belgeni seç", color = Black,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            Text("ML Kit belgeyi cihazında analiz eder. Okunamayan dersleri elle düzelt; kontrol etmeden kayıt yapılmaz.",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp)
            if (photo != null) {
                Spacer(Modifier.height(12.dp))
                AsyncImage(model = photo, contentDescription = "Seçilen sonuç belgesi",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                    contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(13.dp))
            Surface(onClick = onPick, shape = RoundedCornerShape(14.dp), color = Paper,
                border = BorderStroke(1.dp, Hairline)) {
                Row(Modifier.padding(horizontal = 19.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AddAPhoto, null, tint = Black, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (photo == null) "Fotoğraf seç" else "Fotoğrafı değiştir",
                        color = Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
