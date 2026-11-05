package com.denemelab.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val MainInk = androidx.compose.ui.graphics.Color(0xFF111111)
private val SecondaryInk = androidx.compose.ui.graphics.Color(0xFF777777)
private val WhiteSurface = androidx.compose.ui.graphics.Color.White
private val FormBg = androidx.compose.ui.graphics.Color(0xFFFAFAFA)
private val FormLine = androidx.compose.ui.graphics.Color(0xFFEAEAEA)

@Composable
fun ExamEntryV2(
    onDismiss: () -> Unit,
    onSave: (ExamRecord) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Genel") }
    var examType by remember { mutableStateOf("TYT") }
    var track by remember { mutableStateOf("SAY") }
    var branchSubject by remember { mutableStateOf("") }
    var branchQuestions by remember { mutableStateOf("40") }
    var branchCorrect by remember { mutableStateOf("") }
    var branchWrong by remember { mutableStateOf("") }
    val numbers = remember { mutableStateMapOf<String, Pair<String, String>>() }
    val templates = ExamStructure.subjects(examType, if (examType == "AYT") track else "")
    val parts = templates.mapNotNull { template ->
        val values = numbers[template.key] ?: ("" to "")
        val correct = values.first.toIntOrNull()
        val wrong = values.second.toIntOrNull()
        if (correct == null || wrong == null) null else
            SubjectScore(template.key, template.title, template.questions, correct, wrong)
    }
    val generalValid = parts.size == templates.size &&
        ExamStructure.valid(parts, examType, if (examType == "AYT") track else "")
    val branchedCount = branchQuestions.toIntOrNull()
    val branchedCorrect = branchCorrect.toIntOrNull()
    val branchedWrong = branchWrong.toIntOrNull()
    val branchValid = !branchSubject.isBlank() &&
        branchedCount != null && branchedCount in 1..120 &&
        branchedCorrect != null && branchedWrong != null &&
        branchedCorrect >= 0 && branchedWrong >= 0 &&
        branchedCorrect.toLong() + branchedWrong.toLong() <= branchedCount.toLong()
    val valid = title.isNotBlank() && if (category == "Genel") generalValid else branchValid
    val score = if (category == "Genel") ExamStructure.net(parts)
        else if (branchedCorrect != null && branchedWrong != null)
            ExamRules.net(branchedCorrect, branchedWrong) else 0.0
    val correctTotal = if (category == "Genel") parts.sumOf { it.correct } else branchedCorrect ?: 0
    val wrongTotal = if (category == "Genel") parts.sumOf { it.wrong } else branchedWrong ?: 0
    val totalQuestions = if (category == "Genel") templates.sumOf { it.questions } else branchedCount ?: 0
    val blankTotal = totalQuestions - correctTotal - wrongTotal

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            color = WhiteSurface, shape = RoundedCornerShape(26.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(start = 19.dp, end = 19.dp, top = 20.dp, bottom = 12.dp)
                ) {
                    Text("Deneme ekle", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MainInk)
                    Spacer(Modifier.height(5.dp))
                    Text("Gerçek sonuçlarını ders ders kaydet.", fontSize = 13.sp, color = SecondaryInk)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        EntryTab("Genel deneme", category == "Genel", { category = "Genel" })
                        EntryTab("Branş denemesi", category == "Branş", { category = "Branş" })
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        EntryTab("TYT", examType == "TYT", { examType = "TYT" })
                        EntryTab("AYT", examType == "AYT", { examType = "AYT" })
                    }
                    if (category == "Genel" && examType == "AYT") {
                        Spacer(Modifier.height(13.dp))
                        Text("Analiz alanın", color = MainInk, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("SAY", "EA", "SÖZ").forEach { mode ->
                                EntryTab(mode, track == mode, { track = mode })
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("AYT oturumunda 160 soru vardır. Burada seçtiğin alanın 80 soruluk dersleri kaydedilir.",
                            fontSize = 11.sp, color = SecondaryInk, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = title, onValueChange = { title = it.take(90) },
                        label = { Text("Deneme adı") }, singleLine = true,
                        shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(20.dp))
                    if (category == "Genel") {
                        Text("Ders sonuçları", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MainInk)
                        Spacer(Modifier.height(4.dp))
                        Text("Doğru ve yanlış gir. Boş sayısı otomatik hesaplanır.",
                            color = SecondaryInk, fontSize = 12.sp)
                        Spacer(Modifier.height(11.dp))
                        templates.forEach { template ->
                            val value = numbers[template.key] ?: ("" to "")
                            ScoreInputRow(
                                title = template.title, questions = template.questions,
                                correct = value.first, wrong = value.second,
                                onCorrect = { numbers[template.key] = it to value.second },
                                onWrong = { numbers[template.key] = value.first to it }
                            )
                            Spacer(Modifier.height(9.dp))
                        }
                    } else {
                        OutlinedTextField(
                            value = branchSubject, onValueChange = { branchSubject = it.take(50) },
                            label = { Text("Branş dersi (örn. Matematik)") }, singleLine = true,
                            shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(11.dp))
                        OutlinedTextField(
                            value = branchQuestions, onValueChange = { branchQuestions = digits(it) },
                            label = { Text("Denemenin toplam soru sayısı") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(11.dp))
                        ScoreInputRow(
                            title = branchSubject.ifBlank { "Branş sonuçları" },
                            questions = branchedCount ?: 0,
                            correct = branchCorrect, wrong = branchWrong,
                            onCorrect = { branchCorrect = it }, onWrong = { branchWrong = it }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (category == "Genel") "Dersler: ${parts.size}/${templates.size} tamamlandı"
                        else "Branş için toplam soru, doğru ve yanlış bilgilerini gir.",
                        color = SecondaryInk, fontSize = 11.sp)
                }
                HorizontalDivider(color = FormLine)
                Column(Modifier.padding(horizontal = 19.dp, vertical = 13.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(if (valid) LocalData.net(score) + " net" else "— net",
                                fontSize = 25.sp, fontWeight = FontWeight.Bold, color = MainInk)
                            Text(if (valid) "$correctTotal D  ·  $wrongTotal Y  ·  $blankTotal B"
                                else "Eksik veya geçersiz alanları tamamla",
                                fontSize = 11.sp, color = SecondaryInk)
                        }
                        TextButton(onClick = onDismiss) { Text("Vazgeç", color = SecondaryInk) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        onClick = {
                            if (!valid) return@Surface
                            val created = System.currentTimeMillis()
                            val result = if (category == "Genel") ExamRecord(
                                id = created, name = title.trim(), category = "Genel",
                                examType = examType, subject = "",
                                correct = correctTotal, wrong = wrongTotal, blank = blankTotal,
                                createdAt = created, track = if (examType == "AYT") track else "",
                                parts = parts
                            ) else ExamRecord(
                                id = created, name = title.trim(), category = "Branş",
                                examType = examType, subject = branchSubject.trim(),
                                correct = correctTotal, wrong = wrongTotal, blank = blankTotal,
                                createdAt = created
                            )
                            onSave(result)
                        },
                        enabled = valid,
                        color = if (valid) MainInk else FormLine,
                        shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Denemeyi kaydet", Modifier.padding(vertical = 15.dp),
                            color = if (valid) WhiteSurface else SecondaryInk,
                            textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(color = if (selected) MainInk else FormBg,
        shape = CircleShape, onClick = onClick, border = if (selected) null else BorderStroke(1.dp, FormLine)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = if (selected) WhiteSurface else MainInk, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun ScoreInputRow(
    title: String, questions: Int, correct: String, wrong: String,
    onCorrect: (String) -> Unit, onWrong: (String) -> Unit
) {
    val c = correct.toIntOrNull()
    val w = wrong.toIntOrNull()
    val countsValid = c != null && w != null && c >= 0 && w >= 0 &&
        c.toLong() + w.toLong() <= questions.toLong()
    Surface(
        shape = RoundedCornerShape(19.dp),
        color = WhiteSurface, border = BorderStroke(1.dp, FormLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), color = MainInk,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("$questions soru", color = SecondaryInk, fontSize = 11.sp)
            }
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = correct, onValueChange = { onCorrect(digits(it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Doğru", fontSize = 11.sp) },
                    isError = c != null && w != null && !countsValid,
                    singleLine = true, shape = RoundedCornerShape(13.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = wrong, onValueChange = { onWrong(digits(it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Yanlış", fontSize = 11.sp) },
                    isError = c != null && w != null && !countsValid,
                    singleLine = true, shape = RoundedCornerShape(13.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(if (countsValid) "Boş: ${questions - c!! - w!!}   ·   Net: ${LocalData.net(ExamRules.net(c!!, w!!))}"
                else "Doğru + yanlış toplamı soru sayısını aşamaz.",
                fontSize = 11.sp, color = SecondaryInk)
        }
    }
}

private fun digits(text: String): String = text.filter(Char::isDigit).take(3)
