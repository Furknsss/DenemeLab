package com.denemelab.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlin.math.roundToInt

private val InkV3 = Color(0xFF141820)
private val MutedV3 = Color(0xFF77818F)
private val BorderV3 = Color(0xFFE8ECF2)
private val PanelV3 = Color(0xFFF7F9FC)
private val BlueV3 = Color(0xFF3479DC)
private val BlueTintV3 = Color(0xFFEAF2FF)
private val GreenV3 = Color(0xFF16866A)
private val GreenTintV3 = Color(0xFFEAF8F1)
private val AmberV3 = Color(0xFFD99324)
private val RedV3 = Color(0xFFD45460)
private val WhiteV3 = Color.White

/**
 * A compact, genuinely data-driven analysis workspace. No invented
 * strong topics, no "100% predicted YKS gain": all scenario outcomes
 * are explicitly hypothetical conversions of recorded wrong/blank topics.
 */
@Composable
fun TopicOpportunityWorkspace(
    exams: List<ExamRecord>,
    scope: AnalysisScope,
    reduceMotion: Boolean,
    onAddExam: () -> Unit
) {
    val data = remember(exams, scope) { TopicAnalyticsV3.analyze(exams, scope) }
    var gainPercent by remember(scope) { mutableFloatStateOf(50f) }
    var selectedLesson by remember(scope) { mutableStateOf("Tümü") }
    var expandedId by remember(scope) { mutableStateOf<String?>(null) }
    val shown = data.risks.filter {
        selectedLesson == "Tümü" || it.lessonName == selectedLesson
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Text("Konu bazlı net simülatörü", color = InkV3, fontSize = 21.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Hangi konuyu toparlarsan ne kadar net alanı açılabilir?",
                color = MutedV3, fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (data.cohortCount == 0 || data.taggedExamCount == 0) {
            V3Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AmberV3)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Konu verisi bekleniyor", color = InkV3,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("Deneme eklerken yanlış/boş konularını detaylandır. Karne OCR'dan okunan konu kayıtları da kullanılabilir.",
                            color = MutedV3, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                Spacer(Modifier.height(15.dp))
                V3Action("Konu detaylı deneme ekle", onAddExam)
            }
            return@Column
        }

        V3Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SENARYO • SON ${data.cohortCount} DENEME", color = BlueV3,
                        fontSize = 10.sp, letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Kayıpların ne kadarı kapanırsa?", color = MutedV3,
                        fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("%${gainPercent.roundToInt()}", color = InkV3,
                        fontSize = 31.sp, fontWeight = FontWeight.Bold)
                }
                GainRingV3(
                    gain = data.scenarioGain(gainPercent.roundToInt()),
                    maximum = data.theoreticalTaggedGain,
                    fraction = gainPercent / 100f,
                    reduceMotion = reduceMotion
                )
            }
            Slider(
                value = gainPercent,
                onValueChange = { gainPercent = (it / 25f).roundToInt() * 25f },
                valueRange = 0f..100f,
                steps = 3,
                colors = SliderDefaults.colors(
                    thumbColor = BlueV3, activeTrackColor = BlueV3,
                    inactiveTrackColor = BlueTintV3
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("%0", fontSize = 10.sp, color = MutedV3)
                Text("%25", fontSize = 10.sp, color = MutedV3)
                Text("%50", fontSize = 10.sp, color = MutedV3)
                Text("%75", fontSize = 10.sp, color = MutedV3)
                Text("%100", fontSize = 10.sp, color = MutedV3)
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = BlueTintV3, shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp)) {
                    Text(
                        "Varsayımsal artış: +${LocalData.net(data.scenarioGain(gainPercent.roundToInt()))} net",
                        color = BlueV3, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Önceki ${data.cohortCount} denemede kaydedilen konu yanlış ve boşlarının ortalaması esas alındı. Garanti veya puan tahmini değildir.",
                        color = InkV3, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(11.dp))
            Text("Konu etiketli ${data.taggedExamCount}/${data.cohortCount} deneme • Eksik etiketler için kazanç uydurulmadı.",
                color = MutedV3, fontSize = 10.sp, lineHeight = 15.sp)
        }

        if (data.subjects.isNotEmpty()) {
            V3Card {
                Text("Ders ders açılabilecek net alanı", color = InkV3,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("Seçtiğin senaryoya göre; en fazla kayıp bıraktığın konuyla birlikte",
                    color = MutedV3, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(15.dp))
                val highest = data.subjects.maxOf { it.theoreticalRoomPerExam }.coerceAtLeast(0.001)
                data.subjects.forEachIndexed { index, subject ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(subject.name, fontSize = 13.sp, color = InkV3,
                                fontWeight = FontWeight.SemiBold)
                            Text("Öncelik: ${subject.focusTopic}", fontSize = 10.sp,
                                color = MutedV3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("+${LocalData.net(subject.theoreticalRoomPerExam * gainPercent / 100)}",
                            color = BlueV3, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (subject.theoreticalRoomPerExam / highest).toFloat().coerceIn(0f,1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = BlueV3, trackColor = BlueTintV3,
                        strokeCap = StrokeCap.Round
                    )
                    if (index != data.subjects.lastIndex) Spacer(Modifier.height(15.dp))
                }
            }
        }

        Column {
            Text("Kronik açık radarım", fontSize = 20.sp,
                color = InkV3, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Üst üste tekrarlanan konu hataları ve nedenleri. Her sayının arkasında gerçek deneme var.",
                color = MutedV3, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                (listOf("Tümü") + data.subjects.map { it.name }.distinct()).forEach { label ->
                    Surface(onClick = { selectedLesson = label; expandedId = null },
                        shape = CircleShape,
                        color = if (selectedLesson == label) InkV3 else PanelV3) {
                        Text(label, Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            fontSize = 11.sp,
                            color = if (selectedLesson == label) WhiteV3 else InkV3,
                            fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (shown.isEmpty()) {
            V3Card {
                Text("Bu derste etiketlenmiş konu açığı bulunmuyor.",
                    color = InkV3, fontSize = 13.sp)
                Text("Bu, konunun tamamen öğrenildiğini kanıtlamaz.",
                    color = MutedV3, fontSize = 11.sp)
            }
        } else shown.take(9).forEachIndexed { index, risk ->
            V3Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = if (risk.seenInExams >= 3) Color(0xFFFFF0EB) else BlueTintV3) {
                        Text("${index+1}", Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                            color = if (risk.seenInExams >= 3) RedV3 else BlueV3,
                            fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(risk.topic, color = InkV3, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(3.dp))
                        Text(risk.lessonName, color = MutedV3, fontSize = 11.sp)
                    }
                    Text("+${LocalData.net(risk.referenceLossPerExam)}",
                        color = BlueV3, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniLabelV3(
                        "${risk.seenInExams}/${risk.taggedExamCount} etiketli sınav",
                        if (risk.seenInExams >= 3) RedV3 else BlueV3)
                    if (risk.consecutiveTaggedExams >= 2)
                        MiniLabelV3("${risk.consecutiveTaggedExams} kez üst üste", AmberV3)
                }
                Spacer(Modifier.height(8.dp))
                Text("${risk.wrong} yanlış · ${risk.blank} boş · Son ${risk.recentExamCount} sınav",
                    color = InkV3, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text("Baskın neden: ${risk.commonReason}",
                    color = MutedV3, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = PanelV3, shape = RoundedCornerShape(13.dp),
                    onClick = { expandedId = if (expandedId == risk.id) null else risk.id }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Nasıl kapatırım?", color = BlueV3,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Icon(if (expandedId == risk.id) Icons.Rounded.KeyboardArrowUp
                            else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null, tint = BlueV3)
                    }
                }
                AnimatedVisibility(visible = expandedId == risk.id) {
                    Column(Modifier.padding(start = 8.dp, top = 13.dp, end = 8.dp)) {
                        FocusJournal.plan(risk.commonReason).forEachIndexed { step, message ->
                            Row(Modifier.padding(bottom = 9.dp), verticalAlignment = Alignment.Top) {
                                Text("${step+1}.", color = BlueV3, fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(message, color = InkV3, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        Text("Sonraki denemede bu konunun yanlış/boş sayısını yeniden karşılaştır.",
                            color = MutedV3, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
            }
        }
        if (shown.size > 9) Text("+${shown.size - 9} ek konu: ders filtresiyle daha fazlasını gör.",
            fontSize = 11.sp, color = MutedV3)

        Column {
            Text("En sağlam konuların", fontSize = 20.sp,
                color = InkV3, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Sadece karnede açıkça görülen doğru soru verileriyle belirlenir.",
                fontSize = 11.sp, color = MutedV3, lineHeight = 16.sp)
        }
        V3Card {
            if (data.strengths.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = AmberV3)
                    Spacer(Modifier.width(10.dp))
                    Text("Henüz yeterli konu doğrusu kanıtı yok.",
                        fontWeight = FontWeight.SemiBold, color = InkV3,
                        fontSize = 13.sp)
                }
                Spacer(Modifier.height(7.dp))
                Text("En az iki ayrı denemede konu bazlı doğru/yanlış dağılımı bulunan belgeleri eklediğinde gerçekten güçlü olduğun konular görünür. Etiketlenmemiş hata, başarı sayılmaz.",
                    color = MutedV3, fontSize = 11.sp, lineHeight = 16.sp)
            } else data.strengths.take(6).forEachIndexed { index, strength ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Güçlü konu",
                        tint = GreenV3, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(strength.topic, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = InkV3, maxLines = 2)
                        Text("${strength.lessonName} · ${strength.examsSeen} deneme · ${strength.correct} doğru",
                            fontSize = 10.sp, color = MutedV3)
                    }
                    Text("%${(strength.accuracy*100).roundToInt()}",
                        color = GreenV3, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if(index < minOf(6,data.strengths.size)-1)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = BorderV3)
            }
        }

        Column {
            Text("Kayıp nedenleri", fontSize = 20.sp,
                color = InkV3, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Sadece nedeni belirtilmiş, konuya bağlanmış yanlışlar.",
                color = MutedV3, fontSize = 11.sp)
        }
        V3Card {
            if (data.reasons.isEmpty()) {
                Text("Henüz hata nedeni girilmedi. Bilgi mi, dikkat mi, süre mi? Yanlışları detaylandırınca bu dağılım görünür.",
                    color = MutedV3, fontSize = 12.sp, lineHeight = 18.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReasonDonutV3(data.reasons, reduceMotion)
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val total = data.reasons.sumOf { it.count }.coerceAtLeast(1)
                        val colors = listOf(BlueV3, RedV3, AmberV3, GreenV3, MutedV3)
                        data.reasons.take(5).forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(colors[index % colors.size], CircleShape))
                                Spacer(Modifier.width(7.dp))
                                Text(item.reason, modifier = Modifier.weight(1f),
                                    color = InkV3, fontSize = 11.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("%${item.count*100/total}", color = InkV3,
                                    fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Bu yüzdeler tüm yanlışların değil, nedeni etiketlenmiş yanlışların dağılımıdır.",
                    color = MutedV3, fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun V3Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = WhiteV3, shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, BorderV3),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(17.dp), content = content)
    }
}

@Composable
private fun MiniLabelV3(value: String, tint: Color) {
    Surface(shape = CircleShape, color = tint.copy(alpha = 0.085f)) {
        Text(value, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = tint, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
    }
}
@Composable
private fun V3Action(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = InkV3, shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), color = WhiteV3,
            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GainRingV3(gain: Double, maximum: Double, fraction: Float,
                       reduceMotion: Boolean) {
    val pct by animateFloatAsState(fraction.coerceIn(0f,1f),
        animationSpec = tween(if(reduceMotion) 0 else 540), label = "GainScenarioRing")
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp).semantics {
            contentDescription = "Varsayımsal artış ${LocalData.net(gain)} net"
        }) {
            val width = 12.dp.toPx()
            drawArc(BlueTintV3, -90f, 360f, useCenter = false,
                style = Stroke(width, cap = StrokeCap.Round))
            drawArc(BlueV3, -90f, 360f * pct, useCenter = false,
                style = Stroke(width, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+${LocalData.net(gain)}", color = BlueV3,
                fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text("NET SENARYO", color = MutedV3, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ReasonDonutV3(items: List<ReasonShareV3>, reduceMotion: Boolean) {
    val complete by animateFloatAsState(1f,
        animationSpec = tween(if(reduceMotion) 0 else 750), label = "ReasonDonut")
    val total = items.sumOf { it.count }.coerceAtLeast(1)
    val colors = listOf(BlueV3, RedV3, AmberV3, GreenV3, MutedV3)
    Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp).semantics {
            contentDescription = "Hata nedenlerine göre ${items.size} kategori"
        }) {
            val stroke = 13.dp.toPx()
            var start = -90f
            items.forEachIndexed { index, item ->
                val sweep = 360f * item.count / total * complete
                drawArc(colors[index % colors.size], start, sweep, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", color = InkV3, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("etiketli", color = MutedV3, fontSize = 10.sp)
        }
    }
}
