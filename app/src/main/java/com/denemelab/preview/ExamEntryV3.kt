package com.denemelab.preview

import android.app.DatePickerDialog
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
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
 * Manual scores and locally scanned report documents require explicit
 * verification; unconfirmed OCR results are never silently saved.
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
    var publisherMenuOpen by remember { mutableStateOf(false) }
    var publisherSearch by remember { mutableStateOf("") }
    var examCategory by remember { mutableStateOf("Genel") }
    var examType by remember { mutableStateOf("TYT") }
    var track by remember { mutableStateOf("SAY") }
    var branchSubject by remember { mutableStateOf("") }
    var branchCount by remember { mutableStateOf("40") }
    var branchCorrect by remember { mutableIntStateOf(0) }
    var branchWrong by remember { mutableIntStateOf(0) }
    var mood by remember { mutableStateOf("Normal") }
    var difficulty by remember { mutableStateOf("Belirtilmedi") }

    // The date is local calendar midnight; createdAt remains the insertion time.
    val now = remember { Calendar.getInstance() }
    var examDate by remember {
        mutableLongStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis)
    }
    var resultsImage by remember { mutableStateOf<Uri?>(null) }
    var pendingReportUri by remember { mutableStateOf<Uri?>(null) }
    var reportSelectionId by remember { mutableIntStateOf(0) }
    var localReportFile by remember { mutableStateOf<File?>(null) }
    var preparingReport by remember { mutableStateOf(false) }
    var reportResult by remember { mutableStateOf<ReportResult?>(null) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var readingReport by remember { mutableStateOf(false) }
    var reviewedReport by remember { mutableStateOf(false) }
    var saveSubjectScoresOnly by remember { mutableStateOf(false) }
    var showMissingByCourse by remember { mutableStateOf(false) }
    val verifiedKeys = remember { mutableStateMapOf<String, Boolean>() }
    val issueItems = remember { mutableStateListOf<ExamTopicIssue>() }
    val successItems = remember { mutableStateListOf<TopicOutcome>() }
    var editingIssue by remember { mutableStateOf<Pair<String,String>?>(null) }
    var showPhotoTopicReview by remember { mutableStateOf(false) }
    var retryScanId by remember { mutableIntStateOf(0) }
    val pickReport: (Uri?) -> Unit = { uri ->
        if (uri != null) {
            // GetContent photo providers do not promise persistable grants.
            // Copy to cache immediately; permission persistence is best effort.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            reportResult = null
            reportError = null
            reviewedReport = false
            saveSubjectScoresOnly = false
            verifiedKeys.clear()
            issueItems.clear()
            successItems.clear()
            resultsImage = null
            pendingReportUri = uri
            reportSelectionId += 1
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(), onResult = pickReport)
    val pdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(), onResult = pickReport)
    LaunchedEffect(reportSelectionId) {
        val selected = pendingReportUri ?: return@LaunchedEffect
        preparingReport = true
        try {
            val staged = withContext(Dispatchers.IO) {
                ReportDocumentSource.stage(context, selected)
            }
            localReportFile?.delete()
            localReportFile = staged
            resultsImage = Uri.fromFile(staged)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            reportError = e.localizedMessage ?: "Belgeye erişilemiyor."
            resultsImage = null
        } finally {
            preparingReport = false
        }
    }
    DisposableEffect(Unit) {
        onDispose { localReportFile?.delete() }
    }

    val numbers = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
    LaunchedEffect(resultsImage, retryScanId) {
        val uri = resultsImage ?: return@LaunchedEffect
        if (selectedMethod != "Fotoğraf ile Ekle") return@LaunchedEffect
        readingReport = true
        reportError = null
        try {
            val parsed = ExamReportScanner.recognize(context, uri)
            reportResult = parsed
            saveSubjectScoresOnly = false
            // This parser has verified TYT templates. Never silently score
            // an AYT report using the TYT 120-question model.
            if (Regex("\\bAYT\\b", RegexOption.IGNORE_CASE).containsMatchIn(parsed.rawText) &&
                !Regex("\\bTYT\\b", RegexOption.IGNORE_CASE).containsMatchIn(parsed.rawText)) {
                throw java.io.IOException(
                    "Bu belge AYT karnesi olabilir. AYT için Manuel Ekle'yi kullan; " +
                        "bu OCR modeli yalnızca TYT alt derslerini doğrulayabiliyor.")
            }
            examType = "TYT"
            examCategory = "Genel"
            numbers.clear()
            verifiedKeys.clear()
            parsed.counts.forEach { (key, value) ->
                numbers[key] = value
                verifiedKeys[key] = key !in parsed.ambiguousLessons
            }
            issueItems.clear()
            issueItems.addAll(parsed.topics)
            successItems.clear()
            successItems.addAll(parsed.outcomes)
            if (examName.isBlank() && parsed.title.isNotBlank()) examName = parsed.title
        } catch (cancelled: CancellationException) {
            throw cancelled
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
    val successEvidenceValid = TopicAnalyticsV3.validOutcomes(
        successItems.toList(), if (examCategory == "Genel") parts else emptyList()
    )
    // A partial OCR report is NOT evidence of success in unseen questions.
    val coverage = ReportCoverage.inspect(parts, successItems.toList())
    val photoReady = reportResult != null && !readingReport && !preparingReport &&
        reviewedReport && examType == "TYT" && examCategory == "Genel" &&
        templates.all { verifiedKeys[it.key] == true } &&
        (saveSubjectScoresOnly || (coverage.valid && successEvidenceValid))
    val saveEnabled = examName.isNotBlank() &&
        publisherName.isNotBlank() && !isSaving &&
        (issueError == null ||
            (selectedMethod == "Fotoğraf ile Ekle" && saveSubjectScoresOnly)) &&
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
                    Text("Yayın", color = Black, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = {
                                publisherSearch = ""
                                publisherMenuOpen = true
                            },
                            shape = RoundedCornerShape(15.dp),
                            color = Soft,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                PublisherLogo(publisherName, size = 42.dp)
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (publisherName.isBlank()) "Yayın seç" else publisherName,
                                        color = Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(if (publisherName.isBlank()) "Listeden yayınevini belirle" else
                                        "Veri paketindeki kayıtlı logo otomatik kullanılır",
                                        color = Muted, fontSize = 11.sp)
                                }
                                Icon(Icons.Rounded.ExpandMore, contentDescription = "Yayın seç", tint = Black)
                            }
                        }
                        DropdownMenu(
                            expanded = publisherMenuOpen,
                            onDismissRequest = { publisherMenuOpen = false },
                            containerColor = Paper,
                            modifier = Modifier.fillMaxWidth(0.87f).heightIn(max = 420.dp)
                        ) {
                            OutlinedTextField(
                                value = publisherSearch,
                                onValueChange = { publisherSearch = it },
                                placeholder = { Text("Yayın ara", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp)
                            )
                            val matches = publishers.filter {
                                PublisherCatalog.normalize(it.displayName).contains(
                                    PublisherCatalog.normalize(publisherSearch))
                            }
                            matches.forEach { publisher ->
                                DropdownMenuItem(
                                    text = { Text(publisher.displayName, fontSize = 13.sp) },
                                    leadingIcon = {
                                        PublisherLogo(publisher.displayName, size = 30.dp)
                                    },
                                    onClick = {
                                        publisherName = publisher.displayName
                                        publisherMenuOpen = false
                                    }
                                )
                            }
                            if (matches.isEmpty()) {
                                Text("Yayın bulunamadı. Ayarlar > Veri Paketi'nden ekleyebilirsin.",
                                    color = Muted, fontSize = 12.sp,
                                    modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Yeni yayın veya logo eklemek için Ayarlar → Veri Paketi → Logo ekle.",
                        color = Muted, fontSize = 11.sp)
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
                        PhotoImportPanel(
                            resultsImage,
                            onPickPhoto = { imagePicker.launch("image/*") },
                            onPickPdf = { pdfPicker.launch(arrayOf("application/pdf")) }
                        )
                        Spacer(Modifier.height(13.dp))
                        if (preparingReport || readingReport) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Black, strokeWidth = 2.dp,
                                    modifier = Modifier.size(19.dp))
                                Spacer(Modifier.width(11.dp))
                                Text("Belge cihaz üzerinde okunuyor...", color = Muted, fontSize = 12.sp)
                            }
                        }
                        if (reportError != null) {
                            Text("Belge okuma sorunu: " + reportError,
                                color = Color(0xFFB42318), fontSize = 12.sp)
                            Text("Okuma hatası oluştu; mevcut denemelerin etkilenmedi. Belgeyi yeniden seçebilir veya elle doldurabilirsin.",
                                color = Muted, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (resultsImage != null) TextButton(
                                    onClick = { reportError = null; retryScanId += 1 }
                                ) { Text("Yeniden tara", color = Black) }
                                TextButton(onClick = {
                                    selectedMethod = "Manuel Ekle"
                                    reportResult = null
                                    verifiedKeys.clear()
                                }) { Text("Elle gir", color = Black) }
                            }
                        }
                        reportResult?.let { report ->
                            Text(
                                if (report.derivedLessons.isEmpty()) "${report.foundCount}/10 ders algılandı"
                                else "${report.foundCount - report.derivedLessons.size}/10 ders okundu · " +
                                    "${report.derivedLessons.size} ders toplamdan hesaplandı",
                                color = Black, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            val missingCourseNames = templates.filter { it.key !in report.counts }
                                .map { it.title }
                            if (missingCourseNames.isNotEmpty()) {
                                Text("Okunamayan dersler: " + missingCourseNames.joinToString(", ") +
                                    ". Bu dersin doğru/yanlış/boş değerlerini belgede kontrol et.",
                                    color = Color(0xFFB42318), fontSize = 11.sp)
                            }
                            val conflictedCourseNames = templates.filter {
                                it.key in report.ambiguousLessons &&
                                    it.key !in report.derivedLessons }.map { it.title }
                            if (conflictedCourseNames.isNotEmpty()) {
                                Text("Çelişkili OCR satırları: " +
                                    conflictedCourseNames.joinToString(", ") +
                                    ". Belgeye bakmadan otomatik doğru sayılmaz.",
                                    color = Color(0xFFB42318), fontSize = 11.sp)
                            }
                            report.warnings.forEach {
                                Text(it, color = Muted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "${successItems.size} kazanım · ${coverage.observed}/${coverage.total} soru konu bazında doğrulandı",
                                color = Black, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!coverage.complete) {
                                Text("${coverage.missing} sorunun konu dağılımı henüz doğrulanmadı. " +
                                        "Bu soruların doğru olduğu varsayılmaz.",
                                    color = Color(0xFFB42318), fontSize = 12.sp)
                                TextButton(onClick = { showMissingByCourse = !showMissingByCourse }) {
                                    Text(if (showMissingByCourse) "Ders dökümünü gizle" else
                                        "Eksik konu dağılımını derslere göre göster",
                                        color = Black, fontSize = 12.sp)
                                }
                                if (showMissingByCourse) coverage.incompleteCourses.forEach { course ->
                                    Text("${course.name}: ${course.observed}/${course.total} soru · " +
                                            "${course.missing} soru doğrulanmadı" +
                                            (if (course.valid) "" else " · Tutarsız sayılar"),
                                        color = if (course.valid) Muted else Color(0xFFB42318),
                                        fontSize = 11.sp)
                                }
                            }
                            if (!coverage.valid || !successEvidenceValid) Text(
                                "Konu D/Y/Boş toplamları derslerle çelişiyor. Önce düzelt.",
                                color = Color(0xFFB42318), fontSize = 11.sp)
                            Spacer(Modifier.height(9.dp))
                            Surface(
                                onClick = { showPhotoTopicReview = true },
                                shape = RoundedCornerShape(13.dp), color = Soft,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Tüm konuları ve D / Y / Boş sayılarını incele",
                                    Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                    color = Black, fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Konu sonuçları ancak bütün derslerin D/Y/Boş toplamıyla eşleşirse analizlere eklenir.",
                                color = Muted, fontSize = 11.sp)
                            if (!coverage.complete) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Eksik konu satırlarını tahmin etmiyoruz. Doğruladığın konu kayıtlarıyla " +
                                        "kaydettiğinde, ölçülmeyen soru sayısı Analiz ekranında gösterilir.",
                                    color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = saveSubjectScoresOnly,
                                        onCheckedChange = {
                                            saveSubjectScoresOnly = it
                                            reviewedReport = false
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = Black))
                                    Column(Modifier.weight(1f)) {
                                        Text("Yalnızca ders sonuçlarını kaydet",
                                            color = Black, fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold)
                                        Text("Eksik konu verisini analizlere ekleme; yalnızca kontrol ettiğim netleri kaydet.",
                                            color = Muted, fontSize = 11.sp)
                                    }
                                }
                            }
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
                                                        if (selectedMethod == "Fotoğraf ile Ekle")
                                                            reviewedReport = false
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
                            Text(if (selectedMethod == "Fotoğraf ile Ekle" && saveSubjectScoresOnly)
                                "Ders sonuçlarını belgeyle karşılaştırıp doğruladım"
                                else if (!coverage.complete)
                                    "Okunan konu satırlarını doğruladım; kalanlar ölçülmedi"
                                else "Belge sonuçlarını ve konu detaylarını kontrol ettim",
                                color = Black, fontSize = 12.sp, lineHeight = 17.sp,
                                modifier = Modifier.weight(1f))
                        }
                    }
                    if (issueError != null) {
                        Text(issueError, color = Color(0xFFB42318), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(23.dp))
                    DifficultyPicker(selected = difficulty,
                        onSelected = { difficulty = it })
                    Spacer(Modifier.height(21.dp))
                    Text("Deneme modu", color = Black, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text("Sınav sırasında nasıl hissettin?", color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(11.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("Sakin" to "😌", "Normal" to "🙂", "Heyecanlı" to "😬",
                            "Telaşlı" to "😵", "Stresli" to "😣").forEach { (label, emoji) ->
                            EntryChip(emoji + " " + label, selected = mood == label) { mood = label }
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
                                    "Belgeyi okuyup ders ve konu sonuçlarını doğrula",
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
                                difficulty = difficulty,
                                topicIssues = if (selectedMethod == "Fotoğraf ile Ekle" &&
                                    saveSubjectScoresOnly) emptyList() else issueItems.toList(),
                                topicOutcomes = if (selectedMethod == "Fotoğraf ile Ekle" &&
                                    !saveSubjectScoresOnly && coverage.valid &&
                                    successEvidenceValid) successItems.toList() else emptyList(),
                                source = when {
                                    selectedMethod == "Manuel Ekle" -> "Manuel"
                                    saveSubjectScoresOnly -> "Belge OCR · yalnız doğrulanan ders sonuçları"
                                    !coverage.complete -> "Belge OCR + kısmi doğrulanmış konu"
                                    else -> "Belge OCR + tam konu doğrulaması"
                                }
                            )
                            onSave(ExamEntrySubmission(exam, null))
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
    if (showPhotoTopicReview) {
        PhotoTopicReviewDialog(
            rows = successItems.toList(),
            onDismiss = { showPhotoTopicReview = false },
            onChange = { updated ->
                successItems.clear()
                successItems.addAll(updated)
                val manual = issueItems.filterNot { it.provenance == "Belge OCR" }
                issueItems.clear()
                issueItems.addAll(manual)
                updated.forEach { row ->
                    if (row.wrong > 0) issueItems.add(ExamTopicIssue(
                        row.lessonKey, row.lessonName, row.topic,
                        "Yanlış", row.wrong, "Belirtilmedi", "Belge OCR"
                    ))
                    if (row.blank > 0) issueItems.add(ExamTopicIssue(
                        row.lessonKey, row.lessonName, row.topic,
                        "Boş", row.blank, "Belirtilmedi", "Belge OCR"
                    ))
                }
                reviewedReport = false
                saveSubjectScoresOnly = false
            }
        )
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
private fun PhotoImportPanel(
    photo: Uri?,
    onPickPhoto: () -> Unit,
    onPickPdf: () -> Unit
) {
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
            Text("PDF veya yüksek çözünürlüklü fotoğraf seç. Küçük satırlar ayrı taranır; kaydetmeden önce bütün konuları kontrol et.",
                color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp)
            if (photo != null) {
                Spacer(Modifier.height(12.dp))
                val resolver = LocalContext.current.contentResolver
                val isPdf = photo.path?.let { path ->
                    ReportDocumentSource.isPdf(File(path))
                } == true || resolver.getType(photo)?.contains("pdf", ignoreCase = true) == true ||
                    photo.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true
                if (isPdf) {
                    Text("PDF belge seçildi · yüksek çözünürlüklü tarama",
                        color = Black, fontSize = 12.sp,
                        textAlign = TextAlign.Center)
                } else {
                    var previewFailed by remember(photo) { mutableStateOf(false) }
                    var showZoom by remember(photo) { mutableStateOf(false) }
                    AsyncImage(
                        model = photo,
                        contentDescription = "Belgeyi büyütmek için dokun",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                            .clickable { showZoom = true },
                        contentScale = ContentScale.Fit,
                        onError = { previewFailed = true },
                        onSuccess = { previewFailed = false }
                    )
                    TextButton(onClick = { showZoom = true }) {
                        Text("Belgeyi büyüt ve kontrol et", color = Black, fontSize = 12.sp)
                    }
                    if (showZoom) {
                        Dialog(
                            onDismissRequest = { showZoom = false },
                            properties = DialogProperties(usePlatformDefaultWidth = false)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(.99f).fillMaxHeight(.94f),
                                color = Color.White,
                                shape = RoundedCornerShape(17.dp)
                            ) {
                                Column {
                                    Row(Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("Orijinal belge", color = Black,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f))
                                        IconButton(onClick = { showZoom = false }) {
                                            Icon(Icons.Rounded.Close, "Kapat", tint = Black)
                                        }
                                    }
                                    var scale by remember(photo) { mutableFloatStateOf(1f) }
                                    var offset by remember(photo) { mutableStateOf(Offset.Zero) }
                                    Box(Modifier.weight(1f).fillMaxWidth()) {
                                        AsyncImage(
                                            model = photo,
                                            contentDescription = "Yakınlaştırılabilir karne görseli",
                                            modifier = Modifier.fillMaxSize()
                                                .pointerInput(photo) {
                                                    detectTransformGestures { _, pan, zoom, _ ->
                                                        scale = (scale * zoom).coerceIn(1f, 6f)
                                                        offset = if (scale > 1f)
                                                            Offset(offset.x + pan.x, offset.y + pan.y)
                                                        else Offset.Zero
                                                    }
                                                }
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                    translationX = offset.x
                                                    translationY = offset.y
                                                },
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(11.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        TextButton(onClick = {
                                            scale = (scale - .5f).coerceAtLeast(1f)
                                            if (scale == 1f) offset = Offset.Zero
                                        }) { Text("−", color = Black, fontSize = 21.sp) }
                                        Text("${(scale * 100).toInt()}%",
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                            color = Muted)
                                        TextButton(onClick = {
                                            scale = (scale + .5f).coerceAtMost(6f)
                                        }) { Text("+", color = Black, fontSize = 21.sp) }
                                        TextButton(onClick = {
                                            scale = 1f
                                            offset = Offset.Zero
                                        }) { Text("Sıfırla", color = Black) }
                                    }
                                }
                            }
                        }
                    }
                    if (previewFailed) {
                        Text("Küçük önizleme gösterilemedi; belge taraması ayrı çalışır.",
                            color = Muted, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(onClick = onPickPhoto, shape = RoundedCornerShape(14.dp), color = Paper,
                    border = BorderStroke(1.dp, Hairline), modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Rounded.AddAPhoto, null, tint = Black, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Fotoğraf", color = Black, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(onClick = onPickPdf, shape = RoundedCornerShape(14.dp), color = Paper,
                    border = BorderStroke(1.dp, Hairline), modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        Text("PDF belge", color = Black, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
