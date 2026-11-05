package com.denemelab.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
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

private val Black = androidx.compose.ui.graphics.Color(0xFF111111)
private val Muted = androidx.compose.ui.graphics.Color(0xFF737373)
private val Edge = androidx.compose.ui.graphics.Color(0xFFE9E9E9)
private val Soft = androidx.compose.ui.graphics.Color(0xFFF6F6F6)
private val White = androidx.compose.ui.graphics.Color.White

@Composable
fun LessonIssueButtons(
    lesson: SubjectScore,
    issues: List<ExamTopicIssue>,
    onOpen: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Yanlış" to lesson.wrong, "Boş" to lesson.blank).forEach { (kind, count) ->
                Surface(onClick = { onOpen(kind) }, enabled = count > 0,
                    color = Soft, shape = CircleShape) {
                    Text(
                        "$kind detaylandır" + if (count > 0) " · $count" else "",
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        fontSize = 10.sp, color = if (count > 0) Black else Muted)
                }
            }
        }
        if (issues.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Text("${issues.size} konu kaydedildi", fontSize = 10.sp, color = Muted)
        }
    }
}

/**
 * A small independent sheet per subject, never a large crowded page. The
 * user explicitly chooses a topic, count and reason; OCR gets no guessed reason.
 */
@Composable
fun ExamTopicEditor(
    lesson: SubjectScore,
    kind: String,
    initial: List<ExamTopicIssue>,
    onDismiss: () -> Unit,
    onChange: (List<ExamTopicIssue>) -> Unit
) {
    val matching = initial.filter { it.lessonKey == lesson.key && it.kind == kind }
    val total = if (kind == "Yanlış") lesson.wrong else lesson.blank
    val used = matching.sumOf { it.count }
    val available = (total - used).coerceAtLeast(0)
    val options = TopicBreakdown.choices(lesson.key)
    var topic by remember(lesson.key, kind) { mutableStateOf("") }
    var custom by remember(lesson.key, kind) { mutableStateOf("") }
    var reason by remember(lesson.key, kind) { mutableStateOf("Belirtilmedi") }
    var count by remember(lesson.key, kind) { mutableIntStateOf(1) }
    var topicsExpanded by remember { mutableStateOf(false) }
    var reasonsExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.80f),
            shape = RoundedCornerShape(26.dp), color = White) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(start = 19.dp, end = 8.dp, top = 15.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(lesson.title, color = Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("$kind detaylandır · toplam $total", color = Muted, fontSize = 12.sp)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Kapat", tint = Black) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 19.dp)) {
                    Spacer(Modifier.height(13.dp))
                    matching.forEachIndexed { index, issue ->
                        Surface(shape = RoundedCornerShape(15.dp), color = Soft,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp)) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Hata ${index+1} · ${issue.topic}", color = Black,
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(3.dp))
                                    Text("${issue.count} soru · ${issue.reason}",
                                        color = Muted, fontSize = 11.sp)
                                }
                                IconButton(onClick = { onChange(initial - issue) }) {
                                    Icon(Icons.Rounded.DeleteOutline, "Bu konuyu kaldır",
                                        modifier = Modifier.size(20.dp), tint = Muted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Yeni $kind kaydı", fontWeight = FontWeight.SemiBold,
                        color = Black, fontSize = 15.sp)
                    Spacer(Modifier.height(9.dp))
                    Box {
                        Surface(onClick = { topicsExpanded = true },
                            color = Soft, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(topic.ifBlank { "Konu seçiniz" }, color = if (topic.isBlank()) Muted else Black,
                                    fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.ExpandMore, null, tint = Black)
                            }
                        }
                        DropdownMenu(expanded = topicsExpanded,
                            onDismissRequest = { topicsExpanded = false },
                            modifier = Modifier.heightIn(max = 295.dp).widthIn(min = 265.dp),
                            containerColor = White) {
                            options.forEach { name ->
                                DropdownMenuItem(text = { Text(name, fontSize = 12.sp) },
                                    onClick = { topic = name; topicsExpanded = false })
                            }
                        }
                    }
                    if (topic == "Diğer / özel kazanım") {
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(custom, { custom = it.take(140) },
                            label = { Text("Konu veya kazanım adı") }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp), singleLine = true)
                    }
                    Spacer(Modifier.height(15.dp))
                    Text("Bu konudan kaç $kind?",
                        fontSize = 13.sp, color = Black, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        OutlinedButton(onClick = { count = (count-1).coerceAtLeast(1) },
                            enabled = count > 1) { Text("−", color = Black) }
                        Text("$count", fontSize = 21.sp, color = Black, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { count = (count+1).coerceAtMost(available) },
                            enabled = count < available) { Text("+", color = Black) }
                        Text("$available soru kaldı", fontSize = 11.sp, color = Muted)
                    }
                    Spacer(Modifier.height(13.dp))
                    Text("Hata nedeni", color = Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Box {
                        Surface(onClick = { reasonsExpanded = true }, color = Soft,
                            shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(reason, color = Black, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                Icon(Icons.Rounded.ExpandMore, null, tint = Black)
                            }
                        }
                        DropdownMenu(expanded = reasonsExpanded, onDismissRequest = { reasonsExpanded = false },
                            containerColor = White) {
                            TopicBreakdown.reasons.forEach { option ->
                                DropdownMenuItem(text = { Text(option, fontSize = 12.sp) },
                                    onClick = { reason = option; reasonsExpanded = false })
                            }
                        }
                    }
                    Spacer(Modifier.height(17.dp))
                    val selectedTopic = if (topic == "Diğer / özel kazanım") custom.trim() else topic
                    val canAdd = available > 0 && selectedTopic.isNotBlank() && count in 1..available
                    Surface(onClick = {
                        if (canAdd) {
                            onChange(initial + ExamTopicIssue(lesson.key, lesson.title, selectedTopic,
                                kind, count, reason))
                            topic = ""; custom = ""; reason = "Belirtilmedi"; count = 1
                        }
                    }, enabled = canAdd, shape = RoundedCornerShape(14.dp),
                        color = if (canAdd) Black else Edge, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp),
                                tint = if (canAdd) White else Muted)
                            Spacer(Modifier.width(6.dp))
                            Text("Ekle", color = if (canAdd) White else Muted,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Edge)
                TextButton(onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("Bitti", color = Black, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
