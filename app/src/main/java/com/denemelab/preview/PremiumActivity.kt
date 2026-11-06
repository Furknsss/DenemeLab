package com.denemelab.preview

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private val Ink = Color(0xFF111111)
private val Grey = Color(0xFF727272)
private val Light = Color(0xFFFAFAFA)
private val Line = Color(0xFFE9E9E9)
private val Pale = Color(0xFFF3F3F3)
private val Middle = Color(0xFFADADAD)

private data class NavTab(val title: String, val icon: ImageVector)
private val TABS = listOf(
    NavTab("Anasayfa", Icons.Rounded.Home),
    NavTab("Denemeler", Icons.Rounded.Description),
    NavTab("Analiz", Icons.Rounded.Analytics),
    NavTab("İlerlemem", Icons.Rounded.Timeline),
    NavTab("Soru Kumbaram", Icons.Rounded.Bookmarks)
)

class PremiumActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = AndroidColor.rgb(250, 250, 250)
        window.navigationBarColor = AndroidColor.rgb(250, 250, 250)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent { DenemeLabApp() }
    }
}

@Composable
private fun DenemeLabApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val publishers = remember { mutableStateListOf<PublisherBrand>().apply { addAll(PublisherCatalog.list(context)) } }
    var savingExam by remember { mutableStateOf(false) }
    val exams = remember { mutableStateListOf<ExamRecord>().apply { addAll(LocalData.loadExams(context)) } }
    val questions = remember { mutableStateListOf<QuestionRecord>().apply { addAll(LocalData.loadQuestions(context)) } }
    var target by remember { mutableStateOf(LocalData.loadTarget(context)) }
    var studentProfile by remember { mutableStateOf(LocalData.loadProfile(context)) }
    var reduceMotion by remember { mutableStateOf(LocalData.reduceMotion(context)) }
    var selectedProgram by remember { mutableStateOf<AtlasProgram?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDataPackDialog by remember { mutableStateOf(false) }
    var pendingReportUri by remember { mutableStateOf<Uri?>(null) }
    var showAtlasDialog by remember { mutableStateOf(false) }
    var openSelectedDetail by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var showAddExam by remember { mutableStateOf(false) }
    var showAddQuestion by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var openedExam by remember { mutableStateOf<ExamRecord?>(null) }
    var generalMode by remember { mutableStateOf("Genel") }
    var analysisMode by remember { mutableStateOf("Genel") }
    var progressMode by remember { mutableStateOf("Genel") }

    LaunchedEffect(target.programCode) {
        if (target.programCode > 0) {
            selectedProgram = try { YokAtlasCatalog.programByCode(target.programCode) }
                catch (_: Exception) { null }
        } else selectedProgram = null
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingReportUri = uri
            showAddExam = true
        }
    }

    val palette = lightColorScheme(
        primary = Ink, onPrimary = Color.White,
        surface = Color.White, onSurface = Ink,
        background = Light, onBackground = Ink,
        surfaceVariant = Pale, onSurfaceVariant = Grey,
        outline = Line
    )

    MaterialTheme(colorScheme = palette) {
        Box(Modifier.fillMaxSize().background(Light).statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                ProfileTopBar(
                    title = TABS[tab].title, profile = studentProfile,
                    showAddExam = tab == 1, showAddQuestion = tab == 4,
                    onAddExam = { showAddExam = true }, onAddQuestion = { showAddQuestion = true },
                    onProfile = { showProfileDialog = true }, onSettings = { showSettingsDialog = true }
                )
                Crossfade(targetState = tab, animationSpec = tween(if (reduceMotion) 0 else 190), label = "DenemeLabSection") { selected ->
                    when (selected) {
                        0 -> HomeScreen(exams, questions, onAdd = { showAddExam = true }, onNavigate = { tab = it })
                        1 -> ExamsScreen(
                            exams, generalMode, onMode = { generalMode = it },
                            onAdd = { showAddExam = true },
                            onPdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onDetail = { openedExam = it })
                        2 -> AnalysisDashboard(exams, analysisMode,
                            onCategory = { analysisMode = it },
                            onAdd = { showAddExam = true }, reduceMotion = reduceMotion)
                        3 -> ProgressScreen(exams, progressMode, onMode = { progressMode = it },
                            target = target, onGoal = { openSelectedDetail = false; showAtlasDialog = true },
                            onManualGoal = { showGoalDialog = true },
                            onInspect = { openSelectedDetail = true; showAtlasDialog = true },
                            program = selectedProgram)
                        else -> QuestionBankScreen(questions, onAdd = { showAddQuestion = true },
                            onUpdate = { question, correct ->
                                val index = questions.indexOfFirst { it.id == question.id }
                                if (index >= 0) {
                                    questions[index] = question.copy(
                                        correctStreak = ExamRules.nextCorrectStreak(question.correctStreak, correct)
                                    )
                                    LocalData.saveQuestions(context, questions)
                                }
                            })
                    }
                }
            }
            FloatingDock(tab, onSelect = { tab = it }, Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp), reduceMotion)
        }
    }

    if (showAddExam) ExamEntryV3(
        publishers = publishers,
        initialReportUri = pendingReportUri,
        isSaving = savingExam,
        onDismiss = {
            if (!savingExam) {
                showAddExam = false
                pendingReportUri = null
            }
        },
        onSave = { submission ->
            if (!savingExam) {
                savingExam = true
                val previousExams = exams.toList()
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) {
                        runCatching {
                            // A failed image import aborts the exam write; a
                            // failed exam write never claims a successful save.
                            PublisherCatalog.upsert(
                                context, submission.exam.publisher, submission.newPublisherLogo
                            )
                            LocalData.saveExams(context, listOf(submission.exam) + previousExams)
                        }
                    }
                    savingExam = false
                    if (outcome.getOrNull() == true) {
                        publishers.clear()
                        publishers.addAll(PublisherCatalog.list(context))
                        exams.add(0, submission.exam)
                        showAddExam = false
                        pendingReportUri = null
                        tab = 1
                        generalMode = submission.exam.category
                    } else {
                        Toast.makeText(context,
                            outcome.exceptionOrNull()?.message ?: "Deneme kaydedilemedi.",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        })

    if (showAddQuestion) QuestionEntryDialog(
        onDismiss = { showAddQuestion = false },
        onSave = { question ->
            questions.add(0, question)
            LocalData.saveQuestions(context, questions)
            showAddQuestion = false
            tab = 4
        })

    if (showProfileDialog) StudentProfileDialog(
        initial = studentProfile,
        onDismiss = { showProfileDialog = false },
        onSave = {
            studentProfile = it
            LocalData.saveProfile(context, it)
            showProfileDialog = false
        },
        onPickTarget = {
            showProfileDialog = false
            openSelectedDetail = false
            showAtlasDialog = true
        }
    )
    if (showSettingsDialog) DenemeLabSettingsDialog(
        reduceMotion = reduceMotion,
        onMotionChanged = {
            reduceMotion = it
            LocalData.saveReduceMotion(context, it)
        },
        onDataPack = {
            showSettingsDialog = false
            showDataPackDialog = true
        },
        onDismiss = { showSettingsDialog = false }
    )
    if (showDataPackDialog) PublisherDataPackDialog(
        publishers = publishers,
        onDismiss = { showDataPackDialog = false },
        onCatalogChanged = {
            publishers.clear()
            publishers.addAll(PublisherCatalog.list(context))
        }
    )
    if (showAtlasDialog) AtlasSearchDialog(
        onDismiss = { showAtlasDialog = false; openSelectedDetail = false },
        initialProgram = if (openSelectedDetail) selectedProgram else null,
        onChoose = { program ->
            selectedProgram = program
            target = target.copy(
                university = program.university, department = program.program,
                programCode = program.code, universityType = program.universityType,
                language = program.language, scholarship = program.scholarship,
                scoreType = program.scoreType
            )
            LocalData.saveTarget(context, target)
            showAtlasDialog = false
            openSelectedDetail = false
            tab = 3
        }
    )
    if (showGoalDialog) TargetEntryDialog(
        initial = target,
        onDismiss = { showGoalDialog = false },
        onSave = {
            target = it
            LocalData.saveTarget(context, it)
            showGoalDialog = false
        })

    if (openedExam != null) ExamDetailsV2(openedExam!!) { openedExam = null }
}

@Composable
private fun AppHeader(selected: Int, onAddExam: () -> Unit, onAddQuestion: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 17.dp, top = 16.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).background(Ink, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) { Text("D", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("DenemeLab", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
            Text(TABS[selected].title, fontSize = 11.sp, color = Grey)
        }
        when (selected) {
            1 -> HeaderAction("+ Ekle", onAddExam)
            4 -> HeaderAction("+ Soru", onAddQuestion)
            else -> IconButton(onClick = onSettings, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Settings, contentDescription = "Hedef ve ayarlar", tint = Ink)
            }
        }
    }
}

@Composable
private fun HeaderAction(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Ink) {
        Text(label, Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
            color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun FloatingDock(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, reduceMotion: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth(0.96f).shadow(16.dp, RoundedCornerShape(33.dp)),
        shape = RoundedCornerShape(33.dp), color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            TABS.forEachIndexed { index, entry ->
                val active = selected == index
                val dimension by animateDpAsState(
                    if (active) 57.dp else 47.dp, animationSpec = tween(if (reduceMotion) 0 else 200), label = "DockIconSize")
                Surface(
                    onClick = { onSelect(index) },
                    modifier = Modifier.size(dimension).semantics { contentDescription = entry.title },
                    shape = CircleShape,
                    color = if (active) Ink else Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(entry.icon, contentDescription = entry.title,
                            tint = if (active) Color.White else Grey,
                            modifier = Modifier.size(if (active) 26.dp else 23.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrollScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 21.dp, end = 21.dp, top = 13.dp, bottom = 102.dp),
        content = content
    )
}

@Composable
private fun Headline(title: String, subtitle: String) {
    Text(title, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, color = Ink)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, fontSize = 13.sp, color = Grey)
    Spacer(Modifier.height(23.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Ink)
    Spacer(Modifier.height(13.dp))
}

@Composable
private fun PremiumCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(22.dp), color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Grey, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
private fun Pill(title: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier,
        color = if (active) Ink else Pale, shape = CircleShape) {
        Text(title, Modifier.padding(horizontal = 17.dp, vertical = 11.dp),
            color = if (active) Color.White else Grey, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun CategoryPicker(selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Pill("Genel denemeler", selected == "Genel", { onSelect("Genel") })
        Pill("Branş denemeleri", selected == "Branş", { onSelect("Branş") })
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(),
        color = Ink, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(vertical = 16.dp, horizontal = 18.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun BlankState(title: String, explanation: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    PremiumCard(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Spacer(Modifier.height(9.dp))
        Muted(explanation)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            PrimaryAction(actionText, onAction)
        }
        Spacer(Modifier.height(15.dp))
    }
}

@Composable
private fun HomeScreen(exams: List<ExamRecord>, questions: List<QuestionRecord>,
                       onAdd: () -> Unit, onNavigate: (Int) -> Unit) {
    ScrollScreen {
        Headline("Genel Bakış", "Netlerin, odağın ve bir sonraki adımın.")
        val generals = exams.filter { it.category == "Genel" }.sortedByDescending { it.examDate }
        val last = generals.firstOrNull()
        if (last == null) {
            BlankState("İlk denemenle başla",
                "İstatistikleri gerçek sonuçlarına göre oluşturacağız. Henüz kayıtlı denemen yok.",
                "Deneme ekle", onAdd)
        } else {
            PremiumCard(Modifier.fillMaxWidth()) {
                Muted("SON " + last.examType + " DENEMEN")
                Spacer(Modifier.height(9.dp))
                Text(LocalData.net(last.net), fontSize = 43.sp, color = Ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("net · " + last.name, color = Grey, fontSize = 13.sp)
                Spacer(Modifier.height(13.dp))
                val prev = generals.drop(1).firstOrNull { ExamRules.comparable(it, last) }
                if (prev != null) {
                    val diff = last.net - prev.net
                    Text((if (diff > 0) "+" else "") + LocalData.net(diff) + " net · önceki denemeye göre",
                        color = Ink, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("Net eğilimi")
            PremiumCard(Modifier.fillMaxWidth()) {
                TrendChart(generals.filter { ExamRules.comparable(it, last) }.sortedBy { it.examDate }, 145)
            }
            Spacer(Modifier.height(18.dp))
            PrimaryAction("Yeni deneme ekle", onAdd)
        }
        Spacer(Modifier.height(27.dp))
        SectionTitle("Gündeminde ne var?")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PremiumCard(Modifier.weight(1f).clickable { onNavigate(3) }) {
                Text(exams.size.toString(), fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(6.dp))
                Muted("Kayıtlı deneme")
            }
            PremiumCard(Modifier.weight(1f).clickable { onNavigate(4) }) {
                Text(questions.count { !it.completed }.toString(), fontSize = 29.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(6.dp))
                Muted("Tekrar bekleyen soru")
            }
        }
        Spacer(Modifier.height(20.dp))
        PremiumCard(Modifier.fillMaxWidth().clickable { onNavigate(4) }) {
            Text("0 Hata Kitapçığı", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
            Spacer(Modifier.height(6.dp))
            Muted("Yanlış ve boş bıraktığın soruları tekrar gör. Her soruyu iki başarılı tekrarla tamamla.")
            Spacer(Modifier.height(12.dp))
            Text("Soru Kumbarama git  →", color = Ink, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ExamsScreen(exams: List<ExamRecord>, mode: String, onMode: (String) -> Unit,
                        onAdd: () -> Unit, onPdf: () -> Unit, onDetail: (ExamRecord) -> Unit) {
    ScrollScreen {
        Headline("Denemeler", "Bütün sonuçların, tek bir düzenli arşivde.")
        CategoryPicker(mode, onMode)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(onClick = onAdd, modifier = Modifier.weight(1f),
                color = Ink, shape = RoundedCornerShape(16.dp)) {
                Text("+  Deneme ekle", Modifier.padding(15.dp), color = Color.White,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontSize = 13.sp)
            }
            Surface(onClick = onPdf, modifier = Modifier.weight(1f),
                color = Pale, shape = RoundedCornerShape(16.dp)) {
                Text("PDF seç", Modifier.padding(15.dp), color = Ink,
                    fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(11.dp))
        Muted("PDF seçerek sonuç belgesini aç, OCR bulgularını incele ve onaylayarak kaydet.")
        Spacer(Modifier.height(26.dp))
        val filtered = exams.filter { it.category == mode }.sortedByDescending { it.examDate }
        SectionTitle(if (mode == "Genel") "Genel denemelerim" else "Branş denemelerim")
        if (filtered.isEmpty()) {
            BlankState("Burada henüz kayıt yok", "İlk " + mode.lowercase(Locale("tr", "TR")) +
                " denemeni ekleyerek arşivini başlatabilirsin.")
        } else {
            filtered.forEach { exam ->
                Surface(onClick = { onDetail(exam) }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp), color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (exam.publisher.isNotBlank()) {
                            PublisherLogo(exam.publisher, size = 47.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            if (exam.publisher.isNotBlank()) {
                                Text(exam.publisher, color = Grey, fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(5.dp))
                            }
                            Text(exam.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(7.dp))
                            Muted(exam.examType + (if (exam.subject.isNotBlank()) " · " + exam.subject else "") +
                                " · " + LocalData.date(exam.examDate))
                            Spacer(Modifier.height(7.dp))
                            Muted(exam.correct.toString() + " D  ·  " + exam.wrong + " Y  ·  " + exam.blank + " B")
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(LocalData.net(exam.net), fontSize = 21.sp,
                                fontWeight = FontWeight.Bold, color = Ink)
                            Muted("net")
                            Spacer(Modifier.height(7.dp))
                            DifficultyMiniBadge(exam.difficulty)
                        }
                    }
                }
                Spacer(Modifier.height(11.dp))
            }
        }
    }
}

@Composable
private fun ChartLegend(label: String, amount: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, color = Grey, modifier = Modifier.weight(1f))
        Text(amount.toString(), fontSize = 12.sp, color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DonutChart(correct: Int, wrong: Int, blank: Int) {
    val total = max(1, correct + wrong + blank).toFloat()
    var reveal by remember(correct, wrong, blank) { mutableFloatStateOf(0f) }
    LaunchedEffect(correct, wrong, blank) { reveal = 1f }
    val fraction by animateFloatAsState(targetValue = reveal, animationSpec = tween(450), label = "DonutReveal")
    Box(Modifier.size(147.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(13.dp).semantics {
            contentDescription = "Doğru " + correct + ", yanlış " + wrong + ", boş " + blank
        }) {
            val width = 13.dp.toPx()
            var start = -90f
            listOf(Pair(correct, Ink), Pair(wrong, Middle), Pair(blank, Line)).forEach { (count, color) ->
                val angle = 360f * count / total * fraction
                if (angle > 0.001f) {
                    drawArc(color, start, angle, useCenter = false,
                        style = Stroke(width = width, cap = StrokeCap.Butt))
                }
                start += angle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text((correct + wrong + blank).toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("soru", color = Grey, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TrendChart(exams: List<ExamRecord>, height: Int) {
    if (exams.size < 2) {
        Column(Modifier.fillMaxWidth().height(height.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Muted("Gelişim grafiği için en az iki deneme gerekiyor.")
        }
        return
    }
    val values = exams.takeLast(12).map { it.net.toFloat() }
    val hi = (values.maxOrNull() ?: 1f) + 7f
    val lo = (values.minOrNull() ?: 0f) - 7f
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val pad = 14.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        repeat(3) { row ->
            val y = pad + h * (row + 1) / 4
            drawLine(Line, Offset(pad, y), Offset(size.width - pad, y), strokeWidth = 1.dp.toPx())
        }
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = pad + w * index / (values.size - 1).toFloat()
            val y = pad + h * (1f - (value - lo) / (hi - lo).coerceAtLeast(1f))
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(Ink, radius = 3.dp.toPx(), center = Offset(x, y))
        }
        drawPath(path, Ink, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round))
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Muted(LocalData.date(exams.first().examDate))
        Muted(LocalData.date(exams.last().examDate))
    }
}

@Composable
private fun ProgressScreen(exams: List<ExamRecord>, mode: String, onMode: (String) -> Unit,
                           target: TargetRecord, onGoal: () -> Unit, onManualGoal: () -> Unit,
                           onInspect: () -> Unit, program: AtlasProgram?) {
    val context = LocalContext.current
    ScrollScreen {
        Headline("İlerlemem", "Ay ay gelişimin ve hedefin bir arada.")
        CategoryPicker(mode, onMode)
        val selected = exams.filter { it.category == mode }.sortedBy { it.examDate }
        val latest = selected.lastOrNull()
        val comparable = if (latest == null) emptyList() else selected.filter {
            ExamRules.comparable(it, latest)
        }
        SectionTitle("Aylık gelişim")
        if (selected.isEmpty()) {
            BlankState("İlk ayın burada başlayacak",
                "Aylık ortalama netleri gerçek deneme kayıtlarından oluşturacağız.")
        } else {
            Muted("Karşılaştırılan sınav: " + latest!!.examType + if (mode == "Branş") " · " + latest.subject else "")
            Spacer(Modifier.height(11.dp))
            val groups = comparable.groupBy { LocalData.monthKey(it.examDate) }
            PremiumCard(Modifier.fillMaxWidth()) {
                groups.forEach { (_, items) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalData.month(items.first().examDate), Modifier.weight(1f),
                            color = Grey, fontSize = 13.sp)
                        Text(LocalData.net(items.map { it.net }.average()) + " net",
                            color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (groups.size > 1) HorizontalDivider(color = Line)
                }
            }
            Spacer(Modifier.height(15.dp))
            PremiumCard(Modifier.fillMaxWidth()) {
                Muted("Deneme bazında net eğilimin")
                Spacer(Modifier.height(12.dp))
                TrendChart(comparable, 142)
            }
        }
        Spacer(Modifier.height(25.dp))
        SectionTitle("Hedef üniversitem")
        PremiumCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (program != null) {
                    UniversityLogo(program.universityId, program.university, 46.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(if (target.university.isNotBlank()) target.university else "Hedef üniversiteni belirle",
                        fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(if (target.department.isNotBlank()) target.department else "Bölümünü ve hedef sıralamanı kaydet.",
                        fontSize = 13.sp, color = Grey)
                }
            }
            if (target.targetRank.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text("Kişisel hedef sıralaman · " + target.targetRank,
                    fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(18.dp))
            if (target.programCode > 0L) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(target.scoreType, target.universityType, target.language, target.scholarship)
                        .filter { it.isNotBlank() && it != "null" }.forEach { name ->
                            Surface(color = Pale, shape = CircleShape) {
                                Text(name, Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = Ink, fontSize = 11.sp)
                            }
                        }
                }
            }
            Spacer(Modifier.height(11.dp))
            PrimaryAction(if (target.programCode > 0) "Hedef bölümümü değiştir" else "Üniversite ve bölüm seç", onGoal)
            Spacer(Modifier.height(7.dp))
            TextButton(onClick = onManualGoal) {
                Text("Kişisel hedef sıralamam ve diploma notum", color = Grey, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        PremiumCard(Modifier.fillMaxWidth()) {
            Text("Resmî yerleştirme verisi", fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            if (target.programCode == 0L) {
                Muted("Hedef programını seçtiğinde yıl etiketli başarı sıralaması, kontenjan ve son yerleşen verileri burada görünecek.")
            } else if (program == null) {
                Muted("Seçili programın resmî verilerine şu anda ulaşılamıyor. Bilgileri uydurmuyoruz; bağlantını kontrol edip tekrar deneyebilirsin.")
                Spacer(Modifier.height(8.dp))
                Muted("Program kodu: " + target.programCode)
            } else {
                val latest = program.current
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Muted("Taban sıralama · " + (latest?.year ?: "—"))
                        Text(latest?.lastRank?.let { "%,d".format(Locale("tr", "TR"), it) } ?: "Veri yok",
                            color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Muted("Kontenjan · " + (latest?.year ?: "—"))
                        Text(latest?.quota?.toString() ?: "Veri yok", color = Ink,
                            fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Surface(onClick = onInspect, shape = RoundedCornerShape(15.dp), color = Pale) {
                    Text("Yıllık sıralama, OBP ve net ayrıntılarını gör",
                        color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(13.dp),
                        textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(15.dp))
            Surface(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://yokatlas.yok.gov.tr/")))
            }, shape = RoundedCornerShape(14.dp), color = Pale) {
                Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Resmî YÖK Atlas'ı aç", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.Rounded.OpenInNew, null, tint = Ink, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun QuestionBankScreen(questions: List<QuestionRecord>,
                               onAdd: () -> Unit, onUpdate: (QuestionRecord, Boolean) -> Unit) {
    var showOnlyPending by remember { mutableStateOf(true) }
    ScrollScreen {
        Headline("Soru Kumbaram", "Yanlışlar tekrar edildiğinde gelişime dönüşür.")
        PremiumCard(Modifier.fillMaxWidth()) {
            Muted("0 HATA KİTAPÇIĞI")
            Spacer(Modifier.height(10.dp))
            Text(questions.count { !it.completed }.toString() + " soru",
                fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Ink)
            Muted("Tekrar bekliyor")
            Spacer(Modifier.height(11.dp))
            Muted("Bir soruyu iki başarılı tekrardan sonra tamamlanmış sayıyoruz. Şimdilik kayıtlar elle eklenir.")
        }
        Spacer(Modifier.height(16.dp))
        PrimaryAction("Kumbarama soru ekle", onAdd)
        Spacer(Modifier.height(27.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Pill("Tekrar bekleyen", showOnlyPending, { showOnlyPending = true })
            Pill("Tamamlanan", !showOnlyPending, { showOnlyPending = false })
        }
        Spacer(Modifier.height(18.dp))
        val visible = questions.filter { it.completed != showOnlyPending }
        if (visible.isEmpty()) {
            BlankState(if (showOnlyPending) "Kumbaran temiz" else "Henüz tamamlanan soru yok",
                if (showOnlyPending) "Yanlış ve boş sorularını eklediğinde burada göreceksin."
                else "Bir soruyu iki başarılı tekrarla tamamlayabilirsin.")
        } else {
            visible.forEach { item ->
                PremiumCard(Modifier.fillMaxWidth()) {
                    Text(item.title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 16.sp)
                    Spacer(Modifier.height(7.dp))
                    Muted(item.subject + (if (item.topic.isNotBlank()) " · " + item.topic else "") +
                        " · " + item.outcome)
                    if (item.source.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Muted("Kaynak: " + item.source)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(if (item.completed) "0 Hata · tamamlandı" else
                        "Doğru tekrar: " + item.correctStreak + "/2",
                        color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (!item.completed) {
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(onClick = { onUpdate(item, true) }, modifier = Modifier.weight(1f),
                                color = Ink, shape = RoundedCornerShape(14.dp)) {
                                Text("Doğru çözdüm", Modifier.padding(13.dp), textAlign = TextAlign.Center,
                                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Surface(onClick = { onUpdate(item, false) }, modifier = Modifier.weight(1f),
                                color = Pale, shape = RoundedCornerShape(14.dp)) {
                                Text("Tekrar yanlış", Modifier.padding(13.dp), textAlign = TextAlign.Center,
                                    color = Ink, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun QuestionEntryDialog(onDismiss: () -> Unit, onSave: (QuestionRecord) -> Unit) {
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf("Yanlış") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Kumbaraya soru ekle", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 385.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Yanlış", outcome == "Yanlış", { outcome = "Yanlış" })
                    Pill("Boş", outcome == "Boş", { outcome = "Boş" })
                }
                OutlinedTextField(title, { title = it }, label = { Text("Soru başlığı / numarası") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text("Ders") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(topic, { topic = it }, label = { Text("Konu (isteğe bağlı)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(source, { source = it }, label = { Text("Deneme adı / kaynak") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Muted("Soru görseli bağlama ve otomatik yanlış aktarımı daha sonra gelecek.")
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && subject.isNotBlank(), onClick = {
                onSave(QuestionRecord(System.currentTimeMillis(), title.trim(), subject.trim(),
                    topic.trim(), source.trim(), outcome, 0, System.currentTimeMillis()))
            }) { Text("Ekle") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
        containerColor = Color.White
    )
}

@Composable
private fun TargetEntryDialog(initial: TargetRecord, onDismiss: () -> Unit, onSave: (TargetRecord) -> Unit) {
    var university by remember { mutableStateOf(initial.university) }
    var department by remember { mutableStateOf(initial.department) }
    var rank by remember { mutableStateOf(initial.targetRank) }
    var grade by remember { mutableStateOf(initial.schoolGrade) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Hedefim", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 385.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(university, { university = it },
                    label = { Text("Hedef üniversite") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(department, { department = it },
                    label = { Text("Hedef bölüm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(rank, { rank = it.filter { c -> c.isDigit() } },
                    label = { Text("Kişisel hedef sıralama") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(grade, { grade = it },
                    label = { Text("Diploma notu (isteğe bağlı)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Muted("Bu alanlar şu an elle kaydedilir. Resmî YÖK/ÖSYM verisiyle eşleştirme bağlı değil.")
            }
        },
        confirmButton = { TextButton(onClick = {
            onSave(initial.copy(university = university.trim(), department = department.trim(), targetRank = rank.trim(), schoolGrade = grade.trim()))
        }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
        containerColor = Color.White
    )
}

