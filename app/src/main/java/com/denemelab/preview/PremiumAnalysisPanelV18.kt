package com.denemelab.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val V18Ink = V23Navy
private val V18Muted = V23Muted
private val V18Line = V23Border
private val V18Pale = Color(0xFFF2F6F8)
private val V18Accent = V23Teal
private val V18Green = V23Teal
private val V18Red = V23Coral
private val V18Gold = V23Gold

/**
 * Compact evidence-only analytics. No goal comparison (it lives in
 * Ilerlemem), no recommendations, and no made-up topic completeness.
 */
@Composable
fun PremiumAnalysisPanelV18(snapshot: AnalysisSnapshot, allExams: List<ExamRecord>) {
    val history = snapshot.history
    val recent = history.takeLast(5)
    val repeated = remember(history) { PremiumInsightsV18.repeatedWrongTopics(history) }
    var window by remember(snapshot.scope) { mutableStateOf(1) }
    val losses = remember(history, window) { PremiumInsightsV18.losses(history, window) }
    val gains = remember(history, window) { PremiumInsightsV18.gains(history, window) }
    val lessons = remember(history) { PremiumInsightsV18.lessons(history) }
    val mood = remember(allExams) {
        PremiumInsightsV18.mood(allExams.filter { it.category == "Genel" })
    }
    var expandedLesson by remember(snapshot.scope) { mutableStateOf<String?>(null) }
    var lessonTab by remember(snapshot.scope) { mutableStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        V18Card {
            V18Header("Net Gelişimi", "Son " + recent.size + " deneme")
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                V18Metric("Son 3 ort.", snapshot.average?.let(LocalData::net) ?: "—",
                    Modifier.weight(1f))
                V18Metric("En iyi", history.maxOfOrNull { it.net }
                    ?.let(LocalData::net) ?: "—", Modifier.weight(1f))
                V18Metric("Fark", snapshot.mostRecentDelta?.let {
                    (if (it > 0) "+" else "") + LocalData.net(it)
                } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            if (recent.size >= 2) {
                V18NetChart(recent, snapshot.questionLimit)
            } else {
                V18Empty("Gelişim grafiği için ikinci denemeni ekle")
            }
            Spacer(Modifier.height(12.dp))
            recent.asReversed().take(3).forEachIndexed { index, exam ->
                if (index != 0) Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PublisherLogo(exam.publisher, size = 31.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exam.name, fontSize = 12.sp, color = V18Ink,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(LocalData.date(exam.examDate), fontSize = 10.sp, color = V18Muted)
                    }
                    Text(LocalData.net(exam.net), color = V18Ink,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        V18Card {
            V18Header("Tekrarlayan Kayıplar", "Son denemelerden kesintisiz yanlışlar")
            Spacer(Modifier.height(13.dp))
            if (repeated.isEmpty()) {
                V18Empty("Henüz ardışık yanlış serisi kaydedilmedi")
            } else {
                repeated.take(5).forEachIndexed { index, record ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFFFF3F4),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(color = Color(0xFFF8DCE0), shape = CircleShape) {
                                Text((index + 1).toString(),
                                    color = V18Red, fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.size(27.dp).wrapContentHeight())
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(record.topic, color = V18Ink,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text(record.lessonName, color = V18Muted,
                                    fontSize = 10.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(5.dp))
                                Text(record.consecutiveExams.toString() + " denemedir",
                                    color = V18Red, fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text("−" + LocalData.net(record.recoverableNet),
                                    color = V18Red, fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold)
                                Text("toplam kayıp", color = V18Muted, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()) {
            V18Pill(if (window == 1) "Son Deneme ✓" else "Son Deneme",
                window == 1, Modifier.weight(1f)) { window = 1 }
            V18Pill(if (window == Int.MAX_VALUE) "Tüm Zamanlar ✓" else "Tüm Zamanlar",
                window == Int.MAX_VALUE, Modifier.weight(1f)) { window = Int.MAX_VALUE }
        }

        V18TopicCard("En Çok Net Kaybettirenler", losses.take(5), negative = true)
        V18TopicCard("En Çok Net Kazandıranlar", gains.take(5), negative = false)

        V18Card {
            V18Header("Ders Bazlı Analiz", "Ders kartına dokunarak ayrıntıyı aç")
            Spacer(Modifier.height(13.dp))
            if (lessons.isEmpty()) {
                V18Empty("Ders bazlı kayıt bulunmuyor")
            } else {
                lessons.chunked(2).forEachIndexed { index, group ->
                    if (index > 0) Spacer(Modifier.height(9.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        group.forEach { result ->
                            V23LessonTile(
                                result,
                                expandedLesson == result.score.key,
                                modifier = Modifier.weight(1f)
                            ) {
                                expandedLesson = if (expandedLesson == result.score.key)
                                    null else result.score.key
                                lessonTab = 0
                            }
                        }
                        if (group.size == 1) Spacer(Modifier.weight(1f))
                    }
                    group.firstOrNull { it.score.key == expandedLesson }?.let { selected ->
                        Spacer(Modifier.height(8.dp))
                        V18LessonDetail(selected, history, lessonTab) { lessonTab = it }
                    }
                }
            }
        }

        V18Card {
            V18Header("Genel Deneme Modum", "Son " + (mood?.numberOfExams ?: 0) + " kayıt")
            if (mood == null) {
                Spacer(Modifier.height(12.dp))
                V18Empty("Deneme modu kaydı yok")
            } else {
                Spacer(Modifier.height(8.dp))
                V18MoodGauge(mood.averageIndex)
                Spacer(Modifier.height(3.dp))
                val lastIndex = PremiumInsightsV18.modeIndex(mood.latestExam.mood) ?: 1
                HorizontalDivider(color = V18Line)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PublisherLogo(mood.latestExam.publisher, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Son Deneme Modum", color = V18Muted, fontSize = 11.sp)
                        Text(mood.latestExam.name + " · " +
                            LocalData.date(mood.latestExam.examDate),
                            color = V18Ink, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(V18Emoji(lastIndex) + "  " + PremiumInsightsV18.modes[lastIndex],
                        color = V18Ink, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun V18TopicCard(
    title: String,
    rows: List<TopicMetricV18>,
    negative: Boolean
) {
    val accent = if (negative) V18Red else V18Green
    val tint = if (negative) Color(0xFFFFF4F4) else Color(0xFFEEF8F4)
    V18Card {
        V18Header(title, if (negative) "En yüksek ölçülen kayıp" else "En yüksek ölçülen katkı")
        Spacer(Modifier.height(13.dp))
        if (rows.isEmpty()) {
            V18Empty(if (negative) "Bu aralıkta konu kaybı ölçülmedi"
                else "Bu aralıkta konu doğrusu ölçülmedi")
        } else {
            val largest = rows.maxOf {
                if (negative) it.recoverableNet else it.earnedNet
            }.coerceAtLeast(0.001)
            rows.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                val value = if (negative) item.recoverableNet else item.earnedNet
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = tint
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(9.dp),
                                color = accent.copy(alpha = .14f)) {
                                Text((index+1).toString().padStart(2,'0'),
                                    color = accent, fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal=8.dp,vertical=7.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.topic, color = V18Ink, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Text(item.lessonName + " · " + item.exams + " deneme",
                                    color = V18Muted, fontSize = 10.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(5.dp))
                            Text((if (negative) "−" else "+") + LocalData.net(value),
                                color = accent, fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.height(11.dp))
                        LinearProgressIndicator(
                            progress = { (value/largest).toFloat().coerceIn(0f,1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = accent,
                            trackColor = accent.copy(alpha = .14f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun V23LessonTile(
    result: LessonResultV18,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val key = result.score.key
    val accent = when {
        key.contains("turkce") -> Color(0xFF547EB2)
        key.contains("mat") -> V18Green
        key.contains("geo") -> Color(0xFF8476B8)
        key.contains("fizik") -> Color(0xFFC77767)
        key.contains("kimya") -> Color(0xFFB99145)
        key.contains("biyoloji") -> Color(0xFF58A47E)
        else -> Color(0xFF768DB0)
    }
    val fraction = (result.score.correct.toFloat() /
        result.score.questions.coerceAtLeast(1)).coerceIn(0f,1f)
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(17.dp),
        color = if (selected) accent.copy(alpha = .14f) else accent.copy(alpha = .065f),
        border = BorderStroke(1.dp,
            if (selected) accent.copy(alpha = .65f) else accent.copy(alpha = .13f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PublisherLogo(result.exam.publisher, size = 28.dp)
                Spacer(Modifier.weight(1f))
                Canvas(Modifier.size(25.dp)) {
                    val stroke=4.dp.toPx()
                    drawArc(accent.copy(alpha = .18f),-90f,360f,false,
                        style=Stroke(stroke,cap=StrokeCap.Round))
                    drawArc(accent,-90f,360f*fraction,false,
                        style=Stroke(stroke,cap=StrokeCap.Round))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(result.score.title, fontSize = 12.sp, color = V18Ink,
                fontWeight = FontWeight.SemiBold, maxLines = 2,
                minLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp)
            Row(verticalAlignment=Alignment.Bottom) {
                Text(LocalData.net(result.score.net),
                    color = V18Ink, fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(4.dp))
                Text("net", color=V18Muted,fontSize=10.sp,
                    modifier=Modifier.padding(bottom=3.dp))
            }
            Text(result.exam.name, color = V18Muted,
                fontSize = 9.sp, maxLines=1,
                overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                color = accent,
                trackColor = accent.copy(alpha=.18f)
            )
            Text(if (selected) "Detayı kapat  ↑" else "Detayı incele  ↗",
                color = accent, fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun V18LessonDetail(
    lesson: LessonResultV18,
    history: List<ExamRecord>,
    tab: Int,
    onTab: (Int) -> Unit
) {
    val context = LocalContext.current
    Surface(color = V18Pale, shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                V18Pill("Konu Kayıpları", tab == 0, Modifier.weight(1f)) { onTab(0) }
                V18Pill("ÖSYM · 5 Yıl", tab == 1, Modifier.weight(1f)) { onTab(1) }
            }
            Spacer(Modifier.height(13.dp))
            if (tab == 0) {
                val all = PremiumInsightsV18.losses(history, 5)
                    .filter { it.lessonKey == lesson.score.key }
                if (all.isEmpty()) V18Empty("Bu derste ölçülen konu kaybı yok")
                all.take(10).forEachIndexed { index, topic ->
                    if (index != 0) HorizontalDivider(color = V18Line)
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(topic.topic, color = V18Ink,
                            fontSize = 12.sp, modifier = Modifier.weight(1f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("−" + LocalData.net(topic.recoverableNet) + " net",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = V18Red)
                    }
                }
            } else {
                val byTopic = remember(context, lesson.score.key) {
                    VerifiedTopicArchiveV18.counts(context, lesson.score.key)
                }
                if (byTopic.isEmpty()) {
                    V18Empty("2022–2026 için doğrulanmış konu dağılımı bulunmuyor")
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        Text("KONU", color = V18Muted, fontSize = 10.sp,
                            modifier = Modifier.weight(1f))
                        (2022..2026).forEach { year ->
                            Text(year.toString().takeLast(2), color = V18Muted,
                                fontSize = 10.sp, modifier = Modifier.width(25.dp),
                                textAlign = TextAlign.Center)
                        }
                    }
                    byTopic.entries.sortedBy { it.key }.forEach { (topic, counts) ->
                        HorizontalDivider(color = V18Line)
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(topic, color = V18Ink, fontSize = 11.sp,
                                modifier = Modifier.weight(1f))
                            (2022..2026).forEach { year ->
                                Text(counts[year]?.toString() ?: "—",
                                    color = V18Ink, fontSize = 10.sp,
                                    modifier = Modifier.width(25.dp),
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun V18MoodGauge(score: Double) {
    val colors = listOf(Color(0xFF86AA88), Color(0xFF89B7C4),
        Color(0xFFD7BD6C), Color(0xFFC8946F), Color(0xFFAF8092))
    Canvas(Modifier.fillMaxWidth().height(154.dp)) {
        val mid = Offset(size.width / 2f, size.height - 13.dp.toPx())
        val radius = min(size.width * 0.43f, size.height * 0.80f)
        val arcSize = Size(radius * 2, radius * 2)
        val origin = Offset(mid.x - radius, mid.y - radius)
        colors.forEachIndexed { index, color ->
            drawArc(
                color = color,
                startAngle = 182f + index * 36f,
                sweepAngle = 32f, useCenter = false,
                topLeft = origin, size = arcSize,
                style = Stroke(14.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        val angle = Math.toRadians(180.0 + score.coerceIn(0.0, 4.0) * 45)
        val endpoint = Offset(mid.x + cos(angle).toFloat() * radius * .71f,
            mid.y + sin(angle).toFloat() * radius * .71f)
        drawLine(V18Ink, mid, endpoint, strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round)
        drawCircle(V18Ink, radius = 7.dp.toPx(), center = mid)
        drawCircle(Color.White, radius = 2.3.dp.toPx(), center = mid)
    }
    val rounded = (score + 0.5).toInt().coerceIn(0, 4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Text(V18Emoji(rounded), fontSize = 22.sp)
        Spacer(Modifier.width(7.dp))
        Text(PremiumInsightsV18.modes[rounded], color = V18Ink,
            fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
    Spacer(Modifier.height(13.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PremiumInsightsV18.modes.forEachIndexed { index, title ->
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)) {
                Text(V18Emoji(index), fontSize = 16.sp)
                Text(title, fontSize = 9.sp, color = V18Muted,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

private fun V18Emoji(i: Int) = listOf("😌", "🙂", "😬", "😵", "😣")[i.coerceIn(0, 4)]

@Composable
private fun V18NetChart(exams: List<ExamRecord>, questionLimit: Int) {
    if (exams.size < 2) return
    val values = exams.map { it.net }
    val highest = ((values.maxOrNull() ?: 1.0) + 7.0)
        .coerceAtMost(questionLimit.toDouble().coerceAtLeast(1.0))
    val lowest = ((values.minOrNull() ?: 0.0) - 7.0).coerceAtLeast(0.0)
    val range = (highest - lowest).coerceAtLeast(1.0)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF0F8F6),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 13.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("NET EĞRİSİ", color = V18Accent,
                    fontWeight = FontWeight.Bold, letterSpacing = .7.sp,
                    fontSize = 9.sp)
                Text("Son " + values.size + " deneme", color = V18Muted,
                    fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(126.dp)) {
                val left = 8.dp.toPx()
                val right = size.width - 8.dp.toPx()
                val top = 9.dp.toPx()
                val bottom = size.height - 11.dp.toPx()
                repeat(3) { i ->
                    val y = top + (bottom-top)*i/2f
                    drawLine(Color(0xFFD8E8E6),Offset(left,y),Offset(right,y),
                        strokeWidth = 1.dp.toPx())
                }
                val points = values.mapIndexed { index, value ->
                    val x = left + (right-left)*index/
                        (values.size-1).coerceAtLeast(1)
                    val fraction = ((value-lowest)/range).toFloat().coerceIn(0f,1f)
                    Offset(x,bottom-(bottom-top)*fraction)
                }
                val area = Path().apply {
                    moveTo(points.first().x,bottom)
                    lineTo(points.first().x,points.first().y)
                    points.drop(1).forEach { lineTo(it.x,it.y) }
                    lineTo(points.last().x,bottom)
                    close()
                }
                drawPath(area,brush=androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(V18Accent.copy(alpha=.23f),V18Accent.copy(alpha=.01f))
                ))
                val line=Path().apply {
                    moveTo(points.first().x,points.first().y)
                    points.drop(1).forEach { lineTo(it.x,it.y) }
                }
                drawPath(line,V18Accent,
                    style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
                points.forEachIndexed { index,point ->
                    drawCircle(Color.White,5.dp.toPx(),center=point)
                    drawCircle(if(index==points.lastIndex) V18Accent
                        else Color(0xFF68B9AA),3.3.dp.toPx(),center=point)
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(LocalData.date(exams.first().examDate),color=V18Muted,fontSize=9.sp)
                Text("Net aralığına göre ölçekli",
                    color=V18Muted,fontSize=9.sp)
                Text(LocalData.date(exams.last().examDate),color=V18Muted,fontSize=9.sp)
            }
        }
    }
}

@Composable
private fun V18Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, V18Line)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp),content=content)
    }
}

@Composable
private fun V18Header(title: String, subtitle: String) {
    val tint = when {
        "Kaybettir" in title || "Kayıp" in title -> V18Red
        "Kazandır" in title -> V18Green
        "Modum" in title -> Color(0xFF9A75B7)
        "Ders" in title -> Color(0xFF6389BA)
        else -> V18Accent
    }
    val icon = when {
        "Tekrarlayan" in title -> Icons.Rounded.Repeat
        "Kaybettir" in title || "Kayıp" in title -> Icons.Rounded.TrendingDown
        "Kazandır" in title -> Icons.Rounded.TrendingUp
        "Modum" in title -> Icons.Rounded.Speed
        "Ders" in title -> Icons.Rounded.GridView
        else -> Icons.Rounded.AutoGraph
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = tint.copy(alpha = 0.11f),
            shape = RoundedCornerShape(13.dp)) {
            Icon(icon, contentDescription = null, tint = tint,
                modifier = Modifier.padding(10.dp).size(20.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = V18Ink, fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = V18Muted, fontSize = 10.5.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun V18Metric(label: String, value: String, modifier: Modifier = Modifier) {
    val color = when(label) {
        "En iyi" -> V18Green
        "Fark" -> Color(0xFF8A72BE)
        else -> V18Accent
    }
    Surface(modifier = modifier,shape = RoundedCornerShape(15.dp),
        color = color.copy(alpha = .09f)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 12.dp)) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Spacer(Modifier.height(8.dp))
            Text(value,color=V18Ink,fontSize=18.sp,
                fontWeight=FontWeight.ExtraBold,
                maxLines=1,overflow=TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(label,color=V18Muted,fontSize=10.sp)
        }
    }
}

@Composable
private fun V18Pill(
    label: String, selected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(modifier = modifier,shape=RoundedCornerShape(13.dp),
        color=if(selected) V18Ink else V18Pale,onClick=onClick) {
        Text(label,fontSize=11.sp,textAlign=TextAlign.Center,
            color=if(selected) Color.White else V18Muted,
            fontWeight=FontWeight.SemiBold,
            modifier=Modifier.padding(horizontal=9.dp,vertical=11.dp),
            maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun V18Empty(message: String) {
    Surface(
        color = Color(0xFFF1F7F7),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.AutoGraph,contentDescription=null,tint=V18Accent,
                modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(message,color=V18Muted,fontSize=11.sp,lineHeight=16.sp,
                modifier=Modifier.weight(1f))
        }
    }
}
