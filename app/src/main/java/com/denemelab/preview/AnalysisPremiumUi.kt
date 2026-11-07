package com.denemelab.preview

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AnalysisInk = Color(0xFF111111)
private val AnalysisSecondary = Color(0xFF747474)
private val AnalysisSurface = Color.White
private val AnalysisSoft = Color(0xFFF5F5F5)
private val AnalysisLine = Color(0xFFEAEAEA)

/**
 * Minimal analysis, deliberately reduced to one meaningful result.
 *
 * v0.8 rendered repeated cards for trend, stability, loss, topic workspace,
 * simulated outcomes and suggestions. Users found that difficult to scan.
 * Keep the liked "last exam net" gauge and actual D/Y/B values. No other
 * analytics blocks are rendered. Domain data remains intact for later work.
 */
@Composable
fun AnalysisDashboard(
    exams: List<ExamRecord>,
    category: String,
    onCategory: (String) -> Unit,
    onAdd: () -> Unit,
    reduceMotion: Boolean
) {
    var selection by remember(category) { mutableStateOf("") }
    val allScopes = AnalysisEngine.scopes(exams, category)
    // Show only exam types the student has actually entered. No empty
    // TYT/AYT area chips stealing space on the default dashboard.
    val existingScopes = allScopes.filter { choice -> exams.any(choice::contains) }
    val available = existingScopes.ifEmpty { allScopes.take(1) }

    // Initial selection: most recent recorded exam, not an empty TYT
    // placeholder when the student only has AYT results.
    val newest = exams.filter { it.category == category }
        .maxWithOrNull(compareBy<ExamRecord> { it.examDate }.thenBy { it.createdAt })
    val initial = available.firstOrNull { scope ->
        newest != null && scope.contains(newest)
    } ?: available.firstOrNull()

    val scope = available.firstOrNull { it.examType + "|" + it.detail == selection }
        ?: initial
    val snapshot = scope?.let { AnalysisEngine.analyze(exams, it) }
    val last = snapshot?.last

    val dashboardScroll = rememberScrollState()
    LaunchedEffect(category, selection) { dashboardScroll.scrollTo(0) }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(dashboardScroll)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 124.dp)
    ) {
        Text(
            text = "Analiz",
            color = AnalysisInk,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Netlerin, derslerin ve eksik konuların.",
            color = AnalysisSecondary, fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            MinimalChoice(
                title = "Genel", selected = category == "Genel",
                modifier = Modifier.weight(1f),
                onClick = { onCategory("Genel") }
            )
            MinimalChoice(
                title = "Branş", selected = category == "Branş",
                modifier = Modifier.weight(1f),
                onClick = { onCategory("Branş") }
            )
        }
        if (available.size > 1 || (available.size == 1 && category == "Branş")) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                available.forEach { choice ->
                    MinimalChoice(
                        title = choice.label,
                        selected = choice == scope,
                        onClick = { selection = choice.examType + "|" + choice.detail }
                    )
                }
            }
        }
        Spacer(Modifier.height(23.dp))

        if (last == null || snapshot == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(25.dp),
                color = AnalysisSurface,
                border = BorderStroke(1.dp, AnalysisLine)
            ) {
                Column(Modifier.padding(23.dp)) {
                    Text("Henüz deneme yok", color = AnalysisInk, fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "Bu sınav türünde ilk denemeni kaydettiğinde net göstergen burada olacak.",
                        color = AnalysisSecondary, fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        onClick = onAdd,
                        color = AnalysisInk
                    ) {
                        Text(
                            "Deneme ekle", color = AnalysisSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                }
            }
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = AnalysisSurface,
            border = BorderStroke(1.dp, AnalysisLine)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SON DENEME NETİN",
                        color = AnalysisSecondary,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${snapshot.questionLimit} soru",
                        color = AnalysisSecondary, fontSize = 11.sp
                    )
                }

                // Preserve the existing 240° speedometer gauge visual and
                // its accessible motion preference. The value is real net.
                LastExamNetGauge(
                    net = last.net,
                    questionLimit = snapshot.questionLimit,
                    reduceMotion = reduceMotion
                )

                Text(
                    text = last.name,
                    color = AnalysisSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = AnalysisLine)
                Spacer(Modifier.height(17.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResultStat("Doğru", last.correct, Modifier.weight(1f))
                    ResultStat("Yanlış", last.wrong, Modifier.weight(1f))
                    ResultStat("Boş", last.blank, Modifier.weight(1f))
                }
                Spacer(Modifier.height(3.dp))
            }
        }

        // Show the student's measured gaps, not coaching or work plans.
        // The subtest and topic layers remain truthful when an OCR report
        // has only partial curriculum coverage.
        Spacer(Modifier.height(17.dp))
        PremiumAnalysisPanelV18(snapshot, exams)
    }
}

@Composable
private fun MinimalChoice(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (selected) AnalysisInk else AnalysisSoft,
        animationSpec = tween(durationMillis = 180),
        label = "AnalysisChoiceBackground"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) AnalysisSurface else AnalysisSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "AnalysisChoiceText"
    )
    Surface(modifier = modifier, shape = CircleShape, color = bg, onClick = onClick) {
        Text(
            title, color = fg, textAlign = TextAlign.Center,
            maxLines = 1, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Composable
private fun ResultStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = AnalysisInk, fontSize = 19.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(label, color = AnalysisSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun LastExamNetGauge(net: Double, questionLimit: Int, reduceMotion: Boolean) {
    val limit = questionLimit.coerceAtLeast(1)
    val progress = (net / limit).toFloat().coerceIn(0f, 1f)
    var revealed by remember(net, limit) { mutableFloatStateOf(0f) }
    LaunchedEffect(net, limit) { revealed = 1f }
    val animated by animateFloatAsState(
        progress * revealed,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 750),
        label = "MinimalLastExamGauge"
    )

    Box(
        modifier = Modifier.fillMaxWidth().height(226.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(211.dp).semantics {
                contentDescription = "${LocalData.net(net)} net, $questionLimit soruda"
            }
        ) {
            val width = 14.dp.toPx()
            val inset = width / 2f + 8.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val origin = Offset(inset, inset)
            drawArc(
                color = AnalysisLine, startAngle = 150f, sweepAngle = 240f,
                useCenter = false, topLeft = origin, size = arcSize,
                style = Stroke(width, cap = StrokeCap.Round)
            )
            drawArc(
                color = AnalysisInk, startAngle = 150f,
                sweepAngle = 240f * animated, useCenter = false,
                topLeft = origin, size = arcSize,
                style = Stroke(width, cap = StrokeCap.Round)
            )
        }
        Column(
            modifier = Modifier.padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                LocalData.net(net), color = AnalysisInk,
                fontWeight = FontWeight.Bold, fontSize = 38.sp
            )
            Spacer(Modifier.height(4.dp))
            Text("NET", fontSize = 11.sp, color = AnalysisSecondary,
                letterSpacing = 1.sp, fontWeight = FontWeight.Medium)
        }
    }
}
