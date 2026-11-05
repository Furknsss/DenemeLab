package com.denemelab.preview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val DeepBlack = androidx.compose.ui.graphics.Color(0xFF111111)
private val MutedBlack = androidx.compose.ui.graphics.Color(0xFF777777)
private val WhiteCard = androidx.compose.ui.graphics.Color.White
private val GreyTrack = androidx.compose.ui.graphics.Color(0xFFEDEDED)
private val LightBorder = androidx.compose.ui.graphics.Color(0xFFEAEAEA)

@Composable
fun SubjectBreakdownSection(parts: List<SubjectScore>, reduceMotion: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        parts.forEach { score ->
            val normalized = (score.net / score.questions.toDouble()).toFloat().coerceIn(0f, 1f)
            val animated by animateFloatAsState(normalized,
                animationSpec = tween(if (reduceMotion) 0 else 360), label = "SubjectPerformance")
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(score.title, modifier = Modifier.weight(1f), fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, color = DeepBlack)
                    Text(LocalData.net(score.net) + " net", color = DeepBlack,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(7.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).background(GreyTrack, CircleShape)) {
                    Box(Modifier.fillMaxWidth(animated).fillMaxHeight().background(DeepBlack, CircleShape))
                }
                Spacer(Modifier.height(5.dp))
                Text("${score.correct} doğru  ·  ${score.wrong} yanlış  ·  ${score.blank} boş  ·  ${score.questions} soru",
                    fontSize = 11.sp, color = MutedBlack)
            }
        }
    }
}

@Composable
fun ExamDetailsV2(exam: ExamRecord, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.86f),
            color = WhiteCard, shape = RoundedCornerShape(27.dp)) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 21.dp, vertical = 20.dp)) {
                    Text(exam.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DeepBlack)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        listOf(exam.category, exam.examType, exam.track, exam.subject)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp, color = MutedBlack)
                    Spacer(Modifier.height(19.dp))
                    Surface(Modifier.fillMaxWidth(), color = WhiteCard,
                        shape = RoundedCornerShape(19.dp),
                        border = BorderStroke(1.dp, LightBorder)) {
                        Column(Modifier.padding(18.dp)) {
                            Text("TOPLAM NET", color = MutedBlack, fontSize = 11.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(LocalData.net(exam.net), fontSize = 37.sp,
                                fontWeight = FontWeight.Bold, color = DeepBlack)
                            Spacer(Modifier.height(6.dp))
                            Text("${exam.correct} doğru   ·   ${exam.wrong} yanlış   ·   ${exam.blank} boş",
                                fontSize = 12.sp, color = MutedBlack)
                        }
                    }
                    Spacer(Modifier.height(23.dp))
                    if (exam.parts.isNotEmpty()) {
                        Text("Ders analizi", fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold, color = DeepBlack)
                        Spacer(Modifier.height(5.dp))
                        Text("2026 YKS test dağılımına göre doğrulanmış sonuçların.",
                            fontSize = 11.sp, color = MutedBlack)
                        Spacer(Modifier.height(16.dp))
                        SubjectBreakdownSection(exam.parts)
                    } else {
                        Text("Ders kırılımı bulunmuyor", color = DeepBlack,
                            fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(7.dp))
                        Text("Bu deneme yalnızca toplam doğru, yanlış ve boş olarak kaydedilmiş. Ders sonuçlarını uydurmuyoruz.",
                            color = MutedBlack, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Kayıt tarihi: ${LocalData.date(exam.examDate)}", color = MutedBlack, fontSize = 11.sp)
                }
                HorizontalDivider(color = LightBorder)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text("Kapat", color = DeepBlack, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
