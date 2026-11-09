package com.denemelab.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale

private val HomeInk = DenemeVibrant.Ink
private val HomeMuted = DenemeVibrant.Muted
private val HomeBorder = DenemeVibrant.Border
private val HomeSoft = DenemeVibrant.MintSoft
private val HomeTeal = DenemeVibrant.Mint

object HomeGreetingV19 {
    fun forHour(hour: Int): String = when (hour) {
        in 5..11 -> "Günaydın"
        in 12..16 -> "İyi öğlenler"
        in 17..21 -> "İyi akşamlar"
        else -> "İyi geceler"
    }
}

@Composable
fun HomeOverviewV19(
    profile: StudentProfile,
    exams: List<ExamRecord>,
    questions: List<QuestionRecord>,
    onAddExam: () -> Unit,
    onNavigate: (Int) -> Unit
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = HomeGreetingV19.forHour(hour)
    val latest = exams.maxWithOrNull(compareBy<ExamRecord> { it.examDate }
        .thenBy { it.createdAt }.thenBy { it.id })
    val previous = latest?.let { current ->
        exams.filter { it.id != current.id && ExamRules.comparable(it, current) &&
            (it.examDate < current.examDate ||
                (it.examDate == current.examDate && it.createdAt < current.createdAt)) }
            .maxWithOrNull(compareBy<ExamRecord> { it.examDate }.thenBy { it.createdAt })
    }
    val name = profile.firstName.trim().ifBlank {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
            ?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
                .ifBlank { "Öğrenci" }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 19.dp, end = 19.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Column {
            Text("$greeting, $name", color = HomeInk,
                fontSize = 27.sp, lineHeight = 33.sp,
                fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text("DenemeLab · Kişisel performans alanın", color = HomeMuted,
                fontSize = 12.sp)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White, shape = RoundedCornerShape(23.dp),
            border = BorderStroke(1.dp, Color(0xFF2C3647))
        ) {
            Column(Modifier.padding(19.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Son denemen", color = Color(0xFFB9C6D3),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (latest != null) {
                        Text(latest.examType + " · " + latest.category,
                            color = DenemeVibrant.Mint, fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(15.dp))
                if (latest == null) {
                    Text("İlk denemeni ekle", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 23.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("İlk kayıtla birlikte performansın burada görünecek.",
                        color = Color(0xFFB9C6D3), fontSize = 12.sp)
                    Spacer(Modifier.height(15.dp))
                    HomeAction(label = "Deneme ekle",
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.fillMaxWidth(), onClick = onAddExam)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PublisherLogo(latest.publisher, size = 56.dp)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(latest.name, fontSize = 15.sp, color = Color.White,
                                fontWeight = FontWeight.Bold, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text((latest.publisher.ifBlank { "Deneme" }) + " · " +
                                LocalData.date(latest.examDate),
                                fontSize = 11.sp, color = Color(0xFFB9C6D3),
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(LocalData.net(latest.net), fontSize = 38.sp,
                            fontWeight = FontWeight.Bold, color = HomeInk)
                        Spacer(Modifier.width(6.dp))
                        Text("net", color = Color(0xFFB9C6D3), fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 7.dp))
                        Spacer(Modifier.weight(1f))
                        previous?.let { prev ->
                            val delta = latest.net - prev.net
                            Text((if (delta > 0) "+" else "") + LocalData.net(delta),
                                color = if (delta >= 0) DenemeVibrant.Mint else Color(0xFFFF7896),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 9.dp))
                        }
                    }
                    HorizontalDivider(color = Color(0xFF344051),
                        modifier = Modifier.padding(vertical = 13.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()) {
                        HomeMiniStat("Doğru", latest.correct.toString(),true)
                        HomeMiniStat("Yanlış", latest.wrong.toString(),true)
                        HomeMiniStat("Boş", latest.blank.toString(),true)
                        HomeMiniStat("Sınav", latest.examType,true)
                    }
                    Spacer(Modifier.height(15.dp))
                    Surface(
                        shape = RoundedCornerShape(13.dp), color = HomeSoft,
                        onClick = { onNavigate(2) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Analizi görüntüle", color = HomeInk,
                                fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                                modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.ArrowForward, null,
                                tint = HomeInk, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("Hızlı erişim", color = HomeInk,
                fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeAction("Deneme Ekle", Icons.Rounded.Add,
                    Modifier.weight(1f), onAddExam)
                HomeAction("Analiz", Icons.Rounded.Analytics,
                    Modifier.weight(1f)) { onNavigate(2) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeAction("İlerlemem", Icons.Rounded.Timeline,
                    Modifier.weight(1f)) { onNavigate(3) }
                HomeAction("Soru Kumbaram", Icons.Rounded.Bookmarks,
                    Modifier.weight(1f)) { onNavigate(4) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeCountCard("Toplam deneme", exams.size, Modifier.weight(1f))
            HomeCountCard("Tekrar bekleyen", questions.count { !it.completed },
                Modifier.weight(1f))
        }
    }
}

@Composable
private fun HomeMiniStat(label: String, value: String, onDark: Boolean = false) {
    Column {
        Text(value, fontSize = 15.sp,
            color = if (onDark) Color.White else HomeInk,
            fontWeight = FontWeight.Bold)
        Text(label, fontSize = 10.sp,
            color = if (onDark) Color(0xFFAABCC8) else HomeMuted)
    }
}

@Composable
private fun HomeCountCard(label: String, count: Int, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(17.dp), color = Color.White,
        border = BorderStroke(1.dp, HomeBorder), modifier = modifier) {
        Column(Modifier.padding(15.dp)) {
            Text(count.toString(), fontWeight = FontWeight.Bold,
                fontSize = 24.sp, color = HomeInk)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, color = HomeMuted)
        }
    }
}

@Composable
private fun HomeAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val accent = when {
        label.contains("Deneme") -> DenemeVibrant.Mint
        label.contains("Analiz") -> DenemeVibrant.Blue
        label.contains("İlerleme") -> DenemeVibrant.Gold
        else -> DenemeVibrant.Coral
    }
    val background = when {
        label.contains("Deneme") -> DenemeVibrant.MintSoft
        label.contains("Analiz") -> DenemeVibrant.BlueSoft
        label.contains("İlerleme") -> DenemeVibrant.GoldSoft
        else -> DenemeVibrant.CoralSoft
    }
    Surface(
        onClick = onClick, modifier = modifier,
        color = background, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .23f))
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Text(label, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = HomeInk, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
}
