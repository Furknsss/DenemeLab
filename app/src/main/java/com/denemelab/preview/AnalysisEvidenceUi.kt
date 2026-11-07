package com.denemelab.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DetailInk = Color(0xFF171717)
private val DetailMuted = Color(0xFF747474)
private val DetailLine = Color(0xFFE9E9E9)
private val DetailPale = Color(0xFFF7F7F7)
private val DetailWarning = Color(0xFF9C482F)

/**
 * A measured performance dashboard: subject-level loss, net history, 10 TYT
 * subtests and known topic evidence. There are deliberately no coaching
 * actions, daily focus, study prescriptions or inferred mastered topics.
 */
@Composable
fun AnalysisEvidencePanel(snapshot: AnalysisSnapshot) {
    val lastDetailed = snapshot.subjectSource
    val recent = snapshot.history.takeLast(5)
    val latest = snapshot.history.lastOrNull()
    val topics = AnalysisEvidence.observedTopics(recent)
    val lessonDetails = lastDetailed?.let(AnalysisEvidence::perLesson).orEmpty()
    var selectedLesson by remember(snapshot.scope) { mutableStateOf("Tümü") }
    var showAllTopics by remember(snapshot.scope) { mutableStateOf(false) }
    var onlyWeakTopics by remember(snapshot.scope) { mutableStateOf(true) }
    val selectedTopics = topics.filter {
        (selectedLesson == "Tümü" || it.lessonName == selectedLesson) &&
            (!onlyWeakTopics || it.wrong + it.blank > 0)
    }
    val visibleTopics = if (showAllTopics) selectedTopics else selectedTopics.take(12)

    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        EvidenceCard {
            EvidenceHeading("Genel performans", "${snapshot.history.size} karşılaştırılabilir deneme")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EvidenceMetric("Son net", latest?.net?.let(LocalData::net) ?: "—",
                    Modifier.weight(1f))
                EvidenceMetric("Son 3 ort.", snapshot.average?.let(LocalData::net) ?: "—",
                    Modifier.weight(1f))
                EvidenceMetric("En yüksek", snapshot.history.maxOfOrNull { it.net }
                    ?.let(LocalData::net) ?: "—", Modifier.weight(1f))
            }
            if (snapshot.history.size >= 2) {
                Spacer(Modifier.height(13.dp))
                val change = snapshot.mostRecentDelta ?: 0.0
                Text(
                    "Önceki denemeye göre: " +
                        (if (change > 0) "+" else "") + LocalData.net(change) + " net",
                    color = if (change < 0) DetailWarning else DetailInk,
                    fontSize = 12.sp, fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(15.dp))
            HorizontalDivider(color = DetailLine)
            Spacer(Modifier.height(12.dp))
            Text("SON 5 DENEME", color = DetailMuted, letterSpacing = .6.sp,
                fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(9.dp))
            recent.forEach { exam ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(exam.name, color = DetailMuted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(115.dp))
                    Spacer(Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            (exam.net / snapshot.questionLimit.coerceAtLeast(1))
                                .toFloat().coerceIn(0f,1f)
                        },
                        modifier = Modifier.weight(1f).height(7.dp),
                        color = DetailInk,
                        trackColor = DetailLine
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(LocalData.net(exam.net),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = DetailInk, modifier = Modifier.width(46.dp))
                }
            }
            if (recent.size == 1) {
                Spacer(Modifier.height(5.dp))
                Text("Gelişim eğilimi için aynı türden en az iki deneme gerekir.",
                    color = DetailMuted, fontSize = 11.sp)
            }
        }

        EvidenceCard {
            EvidenceHeading("Ders sonuçları",
                if (lastDetailed == null) "Ders bazında kayıt bulunmuyor" else
                    "Son ayrıntılı deneme: ${lastDetailed.name}")
            Spacer(Modifier.height(5.dp))
            Text("Derslerdeki gerçek doğru, yanlış ve boşlar. " +
                "Konu satırı eksik olsa bile ders sonucuyla karıştırılmaz.",
                color = DetailMuted, fontSize = 11.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(16.dp))
            if (lessonDetails.isEmpty()) {
                Text("Bu sınavda alt ders verisi kaydedilmemiş. Net ve doğru/yanlış/boş verileri mevcut.",
                    color = DetailMuted, fontSize = 12.sp)
            } else {
                lessonDetails.forEachIndexed { index, lesson ->
                    if (index != 0) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = DetailLine)
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lesson.name, color = DetailInk,
                            modifier = Modifier.weight(1f), fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("${LocalData.net(ExamRules.net(lesson.correct,lesson.wrong))} net",
                            color = DetailInk, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${lesson.correct} D  ·  ${lesson.wrong} Y  ·  " +
                            "${lesson.blank} Boş  /  ${lesson.questions} soru",
                        color = DetailMuted, fontSize = 11.sp)
                    val max = lesson.questions.coerceAtLeast(1)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (lesson.correct.toFloat()/max).coerceIn(0f,1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp),
                        color = DetailInk, trackColor = DetailLine
                    )
                }
            }
        }

        EvidenceCard {
            EvidenceHeading("Konu verisi kapsama",
                if (lastDetailed == null) "Bu denemede ölçülmedi"
                else "Son ayrıntılı denemenin gerçek konu kayıtları")
            Spacer(Modifier.height(12.dp))
            if (lastDetailed == null) {
                Text("Konu dağılımı eklenmedi; ders sonuçlarını yine yukarıda görebilirsin.",
                    color = DetailMuted, fontSize = 12.sp)
            } else {
                val observed = lessonDetails.sumOf { it.observedTopicQuestions }
                val total = lessonDetails.sumOf { it.questions }
                val missing = (total - observed).coerceAtLeast(0)
                Text("${observed}/${total} soru konu bazında kayıtlı",
                    color = DetailInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else (observed.toFloat()/total)
                        .coerceIn(0f,1f) },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = DetailInk, trackColor = DetailLine
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (missing == 0 && lessonDetails.none { it.hasContradiction })
                        "Kaydedilmiş konu dağılımı ders soru sayılarıyla eşleşiyor."
                    else "${missing} sorunun konu dağılımı kayıtlarda yok. " +
                        "Bu sorular doğru kabul edilmiyor; konu düzeyindeki analiz eksik.",
                    color = if (missing == 0) DetailMuted else DetailWarning,
                    fontSize = 11.sp, lineHeight = 16.sp
                )
                if (lessonDetails.any { it.hasContradiction }) {
                    Spacer(Modifier.height(6.dp))
                    Text("Bazı konu toplamları ders toplamıyla çelişiyor; bu satırları kontrol et.",
                        color = DetailWarning, fontSize = 11.sp)
                }
                Spacer(Modifier.height(13.dp))
                lessonDetails.filter { it.unseen > 0 }.forEach { part ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(part.name, fontSize = 11.sp, color = DetailInk,
                            modifier = Modifier.weight(1f))
                        Text("${part.observedTopicQuestions}/${part.questions} · " +
                                "${part.unseen} eksik",
                            fontSize = 11.sp, color = DetailMuted)
                    }
                }
            }
        }

        EvidenceCard {
            EvidenceHeading("Konu sonuçları", "Son ${recent.size} karşılaştırılabilir denemede görülenler")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape=RoundedCornerShape(11.dp),
                    color=if (onlyWeakTopics) DetailInk else DetailPale,
                    onClick={ onlyWeakTopics=true; showAllTopics=false }) {
                    Text("Eksik konular", Modifier.padding(horizontal=12.dp, vertical=9.dp),
                        fontSize=11.sp,
                        color=if (onlyWeakTopics) Color.White else DetailInk)
                }
                Surface(shape=RoundedCornerShape(11.dp),
                    color=if (!onlyWeakTopics) DetailInk else DetailPale,
                    onClick={ onlyWeakTopics=false; showAllTopics=false }) {
                    Text("Tüm ölçülen konular", Modifier.padding(horizontal=12.dp, vertical=9.dp),
                        fontSize=11.sp,
                        color=if (!onlyWeakTopics) Color.White else DetailInk)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Yanlış ve boş sayıları yalnızca kaydedilmiş konu satırlarından veya " +
                "öğrencinin etiketlediği hatalardan gelir. Ölçülmeyen konular listelenmez.",
                fontSize = 11.sp, color = DetailMuted, lineHeight = 16.sp)
            Spacer(Modifier.height(13.dp))
            if (topics.isEmpty()) {
                Text("Henüz konu düzeyinde kayıt yok. Konu adı ve D/Y/Boş olmadan " +
                    "eksik konu belirlenemez.",
                    fontSize = 12.sp, color = DetailMuted)
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    (listOf("Tümü") + topics.map { it.lessonName }.distinct())
                        .forEach { name ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (name == selectedLesson) DetailInk else DetailPale,
                                onClick = {
                                    selectedLesson = name
                                    showAllTopics = false
                                }
                            ) {
                                Text(name, modifier = Modifier.padding(horizontal=12.dp,vertical=8.dp),
                                    color = if (name == selectedLesson) Color.White else DetailMuted,
                                    fontSize = 11.sp)
                            }
                        }
                }
                Spacer(Modifier.height(10.dp))
                if (visibleTopics.isEmpty()) {
                    Text(
                        if (onlyWeakTopics) "Seçilen derste kaydedilmiş yanlış veya boş konu yok."
                        else "Seçilen derste konu düzeyinde ölçüm yok.",
                        color = DetailMuted, fontSize = 12.sp)
                } else visibleTopics.forEachIndexed { index, topic ->
                    if (index != 0) HorizontalDivider(color = DetailLine)
                    Column(Modifier.fillMaxWidth().padding(vertical=11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(topic.topic, modifier = Modifier.weight(1f),
                                fontSize = 12.sp, color = DetailInk,
                                fontWeight = FontWeight.SemiBold)
                            Text("${topic.wrong} Y · ${topic.blank} Boş",
                                fontSize = 11.sp, color =
                                    if (topic.wrong + topic.blank > 0) DetailWarning else DetailMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("${topic.lessonName} · ${topic.examsSeen} deneme · " +
                            (if (topic.source == "Konu satırları") "${topic.correct} doğru · " else
                                "Doğru sayısı ölçülmedi · ") + topic.source,
                            color = DetailMuted, fontSize = 10.sp)
                    }
                }
                if (selectedTopics.size > 12 && !showAllTopics) {
                    Spacer(Modifier.height(9.dp))
                    Surface(shape=RoundedCornerShape(12.dp), color=DetailPale,
                        onClick={showAllTopics=true}, modifier=Modifier.fillMaxWidth()) {
                        Text("Diğer ${selectedTopics.size - 12} konu kaydını göster",
                            modifier=Modifier.padding(13.dp),
                            fontSize=12.sp, color=DetailInk)
                    }
                } else if (showAllTopics && selectedTopics.size > 12) {
                    Surface(shape=RoundedCornerShape(12.dp), color=DetailPale,
                        onClick={showAllTopics=false}, modifier=Modifier.fillMaxWidth()) {
                        Text("Listeyi kısalt", modifier=Modifier.padding(13.dp),
                            fontSize=12.sp, color=DetailInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidenceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(),
        shape=RoundedCornerShape(24.dp), color=Color.White,
        border=BorderStroke(1.dp, DetailLine)) {
        Column(Modifier.padding(horizontal=17.dp,vertical=19.dp),content=content)
    }
}

@Composable
private fun EvidenceHeading(title:String, subtitle:String) {
    Text(title, fontSize=17.sp, fontWeight=FontWeight.Bold, color=DetailInk)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, fontSize=11.sp, color=DetailMuted,
        maxLines=2, overflow=TextOverflow.Ellipsis)
}

@Composable
private fun EvidenceMetric(label:String, value:String, modifier:Modifier=Modifier) {
    Column(modifier=modifier) {
        Text(value, fontSize=18.sp, color=DetailInk, fontWeight=FontWeight.Bold,
            maxLines=1, overflow=TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        Text(label, fontSize=10.sp, color=DetailMuted)
    }
}
