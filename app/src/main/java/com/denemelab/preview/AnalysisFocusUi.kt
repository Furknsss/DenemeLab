package com.denemelab.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Fg = Color(0xFF111111)
private val Subtle = Color(0xFF737373)
private val Border = Color(0xFFE9E9E9)
private val White = Color.White
private val Soft = Color(0xFFF8F8F8)

/**
 * A single useful follow-up below the user's preferred net gauge.
 * The actual student-facing plan opens only on demand. No redundant charts.
 */
@Composable
fun AnalysisFocusPanel(
    snapshot: AnalysisSnapshot,
    reduceMotion: Boolean,
    onAddExam: () -> Unit
) {
    val context = LocalContext.current
    val advice = FocusActionEngine.recommend(snapshot)
    var open by remember(snapshot.scope, advice?.title) { mutableStateOf(false) }
    var saveError by remember { mutableStateOf("") }
    val completions = remember(context) {
        mutableStateListOf<FocusCompletion>().apply {
            addAll(FocusJournal.load(context))
        }
    }
    val selected = advice?.topic
    val identifier = selected?.let { FocusJournal.focusId(snapshot.scope, it) }
    val completed = completions.firstOrNull { it.id == identifier }

    Spacer(Modifier.height(18.dp))
    Surface(
        modifier = Modifier.fillMaxWidth()
            .animateContentSize(animationSpec = tween(if (reduceMotion) 0 else 220)),
        shape = RoundedCornerShape(25.dp), color = White,
        border = BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(horizontal = 19.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BUGÜNÜN ODAĞI", color = Subtle, fontSize = 11.sp,
                    letterSpacing = 1.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = CircleShape, color = Soft) {
                    Text(
                        if (advice?.hasTopicEvidence == true) "Kayıtlara dayalı" else "Veri gerekiyor",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = Subtle, fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(15.dp))
            if (advice == null) {
                Text("Henüz öncelik belirlenemiyor", color = Fg,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text(
                    "Önce doğru, yanlış ve boşları ders bazında kaydet. Konu bilgisi gelince gerçek önceliğin belirecek.",
                    color = Subtle, fontSize = 12.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(13.dp))
                FocusActionButton("Deneme ekle", onAddExam)
                return@Column
            }
            Text(advice.lessonName, color = Subtle, fontSize = 12.sp)
            Spacer(Modifier.height(3.dp))
            Text(
                advice.title, color = Fg, fontWeight = FontWeight.Bold,
                fontSize = 21.sp, lineHeight = 27.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(11.dp))
            Text(
                advice.evidence, color = Fg, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                advice.explanation, color = Subtle, fontSize = 12.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            if (!advice.hasTopicEvidence) {
                FocusActionButton("Eksik konuları detaylandır", onAddExam)
                return@Column
            }

            if (completed != null) {
                val assessment = FocusActionEngine.verify(advice, completed, snapshot)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null,
                        tint = Fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "Çalışma kaydedildi", color = Fg, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(7.dp))
                when (assessment) {
                    FocusVerification.Pending -> Text(
                        "Sonraki karşılaştırılabilir denemeyi bekliyor.",
                        color = Subtle, fontSize = 11.sp, lineHeight = 16.sp
                    )
                    FocusVerification.Unmeasured -> Text(
                        "Yeni sonuçta bu kazanımın tam D/Y/Boş dağılımı yok; iyileşme henüz kanıtlanmadı.",
                        color = Subtle, fontSize = 11.sp, lineHeight = 16.sp
                    )
                    is FocusVerification.Measured -> {
                        val oldLoss = assessment.beforeWrong * 1.25 + assessment.beforeBlank
                        val newLoss = assessment.afterWrong * 1.25 + assessment.afterBlank
                        Text(
                            "Kazanım kaybı: ${LocalData.net(oldLoss)} → ${LocalData.net(newLoss)}",
                            color = Fg, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (assessment.improved) "Bu kazanımda ölçülen kayıp azaldı."
                            else "Henüz kalıcı bir azalma gözlenmedi. Aynı kazanımı tekrar ölç.",
                            color = Subtle, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (completed == null) Fg else Soft,
                modifier = Modifier.fillMaxWidth(),
                onClick = { open = !open }
            ) {
                Row(Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (open) "Çalışma adımlarını gizle" else
                            if (completed == null) "Nasıl kapatacağım?" else "Çalışma adımlarını gör",
                        color = if (completed == null) White else Fg,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (completed == null) White else Fg,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(tween(if (reduceMotion) 0 else 160)) +
                    expandVertically(tween(if (reduceMotion) 0 else 200)),
                exit = fadeOut(tween(if (reduceMotion) 0 else 120)) +
                    shrinkVertically(tween(if (reduceMotion) 0 else 180))
            ) {
                Column {
                    Spacer(Modifier.height(17.dp))
                    advice.steps.forEachIndexed { index, step ->
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(shape = CircleShape, color = Soft,
                                modifier = Modifier.size(25.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("${index + 1}", color = Fg,
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(step, color = Fg, fontSize = 12.sp, lineHeight = 18.sp,
                                modifier = Modifier.weight(1f))
                        }
                    }
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(14.dp))
                    if (completed == null && selected != null && identifier != null) {
                        FocusActionButton("Çalışmayı tamamladım") {
                            val result = FocusCompletion(
                                id = identifier, topicName = selected.label,
                                lessonName = selected.lessonName,
                                completedAt = System.currentTimeMillis(),
                                baselineExamDate = selected.mostRecentDate,
                                baselineExamNet = snapshot.last?.net ?: 0.0,
                                baselineExamId = selected.mostRecentExamId
                            )
                            val updated = completions.filterNot { it.id == identifier } + result
                            if (FocusJournal.save(context, updated)) {
                                completions.clear()
                                completions.addAll(updated)
                                saveError = ""
                                open = false
                            } else {
                                saveError = "Çalışma kaydı telefona yazılamadı."
                            }
                        }
                    } else {
                        Text(
                            "İlerlemenin doğrulanması için sonraki denemede aynı kazanımın sonucunu kaydet.",
                            fontSize = 11.sp, lineHeight = 16.sp, color = Subtle
                        )
                    }
                    if (saveError.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(saveError, color = Color(0xFFB42318), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusActionButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        color = Fg, shape = RoundedCornerShape(15.dp)) {
        Text(
            label, color = White, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(14.dp)
        )
    }
}
