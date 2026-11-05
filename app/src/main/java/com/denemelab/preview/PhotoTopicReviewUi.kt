package com.denemelab.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val OcrInk = Color(0xFF101010)
private val OcrSecondary = Color(0xFF777777)
private val OcrSurface = Color.White
private val OcrSubtle = Color(0xFFF6F6F6)
private val OcrLine = Color(0xFFE9E9E9)

/**
 * Editable, per-topic proof of OCR results. Doğru, yanlış and boş are
 * separately visible, never hidden behind a percentage or guesswork.
 * This is a review interface, not a claim of automatic perfect recognition.
 */
@Composable
fun PhotoTopicReviewDialog(
    rows: List<TopicOutcome>,
    onChange: (List<TopicOutcome>) -> Unit,
    onDismiss: () -> Unit
) {
    val lessons = ExamStructure.subjectsForEntry("TYT", "")
    var selectedLesson by remember { mutableStateOf("Tümü") }
    var editing by remember { mutableStateOf<Pair<Int, TopicOutcome>?>(null) }
    val shown = rows.withIndex().filter { (index, row) ->
        selectedLesson == "Tümü" || row.lessonKey == selectedLesson
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.96f).fillMaxHeight(.91f),
            shape = RoundedCornerShape(26.dp),
            color = OcrSurface
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.padding(start = 19.dp, top = 17.dp, end = 17.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Konu sonuçları", fontSize = 21.sp,
                            fontWeight = FontWeight.Bold, color = OcrInk)
                        Spacer(Modifier.height(4.dp))
                        Text("${rows.size} konu · D / Y / Boş",
                            fontSize = 11.sp, color = OcrSecondary)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Bitti", color = OcrInk)
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Explicit compact filter, no crowded nested cards.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("Tümü" to "Tümü") + lessons.map { it.key to it.title }).forEach { (key, label) ->
                        Surface(
                            onClick = { selectedLesson = key },
                            shape = CircleShape,
                            color = if (selectedLesson == key) OcrInk else OcrSubtle
                        ) {
                            Text(label, Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                fontSize = 11.sp,
                                color = if (selectedLesson == key) OcrSurface else OcrSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = OcrLine)
                LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    itemsIndexed(shown) { _, entry ->
                        val index = entry.index
                        val item = entry.value
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.topic, color = OcrInk, fontSize = 12.sp,
                                    lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(5.dp))
                                Text("${item.lessonName} · ${item.questions} soru",
                                    color = OcrSecondary, fontSize = 10.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${item.correct} doğru · ${item.wrong} yanlış · ${item.blank} boş",
                                    color = OcrInk, fontSize = 11.sp)
                            }
                            TextButton(onClick = { editing = index to item }) {
                                Text("Düzenle", color = OcrInk, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = OcrLine)
                    }
                }
                HorizontalDivider(color = OcrLine)
                Row(Modifier.fillMaxWidth().padding(11.dp),
                    horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = {
                        val defaultLesson = lessons.firstOrNull {
                            it.key == selectedLesson
                        } ?: lessons.first()
                        editing = -1 to TopicOutcome(
                            defaultLesson.key, defaultLesson.title,
                            "", 0, 0, 1, "Manuel doğrulama"
                        )
                    }) {
                        Text("+ Eksik konu ekle", color = OcrInk,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    editing?.let { (index, row) ->
        TopicCountEditor(
            original = row,
            lessons = lessons,
            onDismiss = { editing = null },
            onRemove = {
                if (index >= 0) onChange(rows.filterIndexed { i, _ -> i != index })
                editing = null
            },
            onSave = { edited ->
                val updated = rows.toMutableList()
                if (index >= 0) updated[index] = edited else updated.add(edited)
                onChange(updated)
                editing = null
            }
        )
    }
}

@Composable
private fun TopicCountEditor(
    original: TopicOutcome,
    lessons: List<SubjectTemplate>,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onSave: (TopicOutcome) -> Unit
) {
    var name by remember(original) { mutableStateOf(original.topic) }
    var c by remember(original) { mutableStateOf(original.correct.toString()) }
    var w by remember(original) { mutableStateOf(original.wrong.toString()) }
    var b by remember(original) { mutableStateOf(original.blank.toString()) }
    var lessonKey by remember(original) { mutableStateOf(original.lessonKey) }
    var expanded by remember { mutableStateOf(false) }
    val corrected = c.toIntOrNull()
    val mistaken = w.toIntOrNull()
    val empty = b.toIntOrNull()
    val total = if (corrected != null && mistaken != null && empty != null)
        corrected + mistaken + empty else 0
    val valid = name.trim().isNotBlank() &&
        corrected != null && mistaken != null && empty != null &&
        corrected >= 0 && mistaken >= 0 && empty >= 0 && total in 1..60
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = OcrSurface, shape = RoundedCornerShape(23.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("Kazanımı düzelt", color = OcrInk,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Box {
                    Surface(onClick = { expanded = true },
                        shape = RoundedCornerShape(12.dp), color = OcrSubtle) {
                        Text(
                            lessons.firstOrNull { it.key == lessonKey }?.title ?: "Ders",
                            modifier = Modifier.fillMaxWidth().padding(12.dp), fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        lessons.forEach { lesson ->
                            DropdownMenuItem(
                                text = { Text(lesson.title, fontSize = 12.sp) },
                                onClick = { lessonKey = lesson.key; expanded = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it.take(175) },
                    label = { Text("Konu / kazanım") }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        Triple("Doğru", c, { v: String -> c = v }),
                        Triple("Yanlış", w, { v: String -> w = v }),
                        Triple("Boş", b, { v: String -> b = v })
                    ).forEach { (label, value, update) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { update(it.filter(Char::isDigit).take(2)) },
                            label = { Text(label, fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text("$total soru", fontSize = 11.sp, color = OcrSecondary)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (original.topic.isNotBlank()) {
                        OutlinedButton(onClick = onRemove) {
                            Text("Kaldır", color = OcrSecondary, fontSize = 12.sp)
                        }
                    }
                    Surface(onClick = {
                        if (valid) {
                            val lesson = lessons.first { it.key == lessonKey }
                            onSave(TopicOutcome(
                                lesson.key, lesson.title, name.trim(),
                                corrected!!, mistaken!!, empty!!, "Manuel doğrulama"
                            ))
                        }
                    }, enabled = valid, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (valid) OcrInk else OcrLine) {
                        Text("Uygula", modifier = Modifier.padding(13.dp),
                            color = if (valid) OcrSurface else OcrSecondary,
                            fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
