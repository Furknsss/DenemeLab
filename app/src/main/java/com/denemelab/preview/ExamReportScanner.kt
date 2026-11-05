package com.denemelab.preview

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class ScannedWord(val value: String, val centerX: Float, val centerY: Float)

data class ReportResult(
    val counts: Map<String, Pair<Int, Int>>,
    val topics: List<ExamTopicIssue>,
    val title: String,
    val warnings: List<String>,
    val rawText: String
) {
    val foundCount: Int get() = counts.size
}

/**
 * ML Kit OCR runs on the Android device. No exam report, school, name or
 * other personal data is transmitted to any proprietary model endpoint.
 * The parser is format-aware but never claims perfect transcription:
 * all OCR results must be reviewed before save.
 */
object ExamReportScanner {
    suspend fun recognize(context: Context, uri: Uri): ReportResult {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine<Text> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.cancel(it) }
            }
            val words = text.textBlocks.flatMap { b -> b.lines }.flatMap { l ->
                l.elements.mapNotNull { e ->
                    val b = e.boundingBox ?: return@mapNotNull null
                    ScannedWord(e.text, b.exactCenterX(), b.exactCenterY())
                }
            }
            return ReportTableParser.parse(
                text.text, words, image.width.coerceAtLeast(1)
            )
        } finally { recognizer.close() }
    }
}

/** Offline, deterministic interpretation of a 3D-style TYT report sheet. */
object ReportTableParser {
    private val normLocale = Locale.forLanguageTag("tr-TR")
    private fun normalize(s: String): String = Normalizer.normalize(
        s.lowercase(normLocale).replace('ı', 'i'), Normalizer.Form.NFD
    ).replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    private data class Alias(val key: String, val label: String, val questions: Int, val names: List<String>)
    private val aliases = listOf(
        Alias("tyt_turkce", "Türkçe", 40, listOf("turkce")),
        Alias("tyt_tarih", "Tarih", 5, listOf("tarih 1","tarih")),
        Alias("tyt_cografya", "Coğrafya", 5, listOf("cografya 1","cografya")),
        Alias("tyt_felsefe", "Felsefe", 5, listOf("felsefe")),
        Alias("tyt_din", "Din / Ek Felsefe", 5, listOf("din kul ve ahl bil", "din kul", "din kulturu")),
        Alias("tyt_mat", "Matematik", 30, listOf("matematik 1","matematik i")),
        Alias("tyt_geo", "Geometri", 10, listOf("geometri")),
        Alias("tyt_fizik", "Fizik", 7, listOf("fizik")),
        Alias("tyt_kimya", "Kimya", 7, listOf("kimya")),
        Alias("tyt_biyoloji", "Biyoloji", 6, listOf("biyoloji"))
    )
    private val numbers = Regex("(\\d{1,3})\\s+(\\d{1,3})\\s+(\\d{1,3})(?:\\s|$)")
    private val topicNumbers = Regex("\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+(\\d{1,2})\\s+(\\d{1,3})\\s*$")

    /**
     * Testing accepts text without layout. Real scanner additionally creates
     * coordinate-grouped rows to reconstruct table cells recognized separately.
     */
    fun parse(raw: String, words: List<ScannedWord> = emptyList(), imageWidth: Int = 1000): ReportResult {
        val fromImage = if (words.isEmpty()) emptyList() else groupedLines(words, imageWidth)
        val left = if (fromImage.isNotEmpty())
            fromImage.filter { it.second < imageWidth * 0.505f }.map { it.first }
        else raw.lines()
        val right = if (fromImage.isNotEmpty())
            fromImage.filter { it.second >= imageWidth * 0.505f }.map { it.first }
        else raw.lines()

        val counts = linkedMapOf<String, Pair<Int, Int>>()
        for (line in left) {
            val normalized = normalize(line)
            // Avoid summary rows, headings and the non-attempted optional
            // philosophy test which is not added to the required 120.
            if (normalized.contains("sec meli") || normalized.contains("secmeli")) continue
            if (normalized.startsWith("tyt ") || normalized.startsWith("toplam")) continue
            for (alias in aliases) {
                val starts = alias.names.firstOrNull {
                    normalized == it || normalized.startsWith("$it ")
                } ?: continue
                val remainder = normalized.removePrefix(starts).trim()
                val m = numbers.find(remainder) ?: continue
                val n = m.groupValues[1].toIntOrNull() ?: continue
                val c = m.groupValues[2].toIntOrNull() ?: continue
                val w = m.groupValues[3].toIntOrNull() ?: continue
                if (n == alias.questions && c >= 0 && w >= 0 && c + w <= n) {
                    counts[alias.key] = c to w
                    break
                }
            }
        }
        val title = raw.lines().firstOrNull {
            it.contains("TYT", true) && (it.contains("TG", true) || it.contains("SAYI", true))
        }?.trim()?.take(90).orEmpty()

        val warnings = mutableListOf<String>()
        if (counts.size != aliases.size)
            warnings.add("${aliases.size - counts.size} alt ders güvenle okunamadı; kaydetmeden önce elle kontrol et.")
        if (raw.isBlank() && fromImage.isEmpty()) warnings.add("Belgeden yazı okunamadı.")
        val topicIssues = mutableListOf<ExamTopicIssue>()
        var current = ""
        for (line in right) {
            val lowered = normalize(line)
            when {
                lowered.startsWith("tyt turkce") -> { current = "tyt_turkce"; continue }
                lowered.startsWith("tyt sosyal") -> { current = ""; continue }
                lowered.startsWith("tyt matematik") -> { current = ""; continue }
                lowered.startsWith("tyt fen") -> { current = ""; continue }
                lowered.startsWith("tarih 1") -> { current = "tyt_tarih"; continue }
                lowered.startsWith("cografya 1") -> { current = "tyt_cografya"; continue }
                lowered.startsWith("felsefe secmeli") -> { current = ""; continue }
                lowered == "felsefe" -> { current = "tyt_felsefe"; continue }
                lowered.startsWith("din kul") -> { current = "tyt_din"; continue }
                lowered.startsWith("matematik 1") -> { current = "tyt_mat"; continue }
                lowered == "geometri" -> { current = "tyt_geo"; continue }
                lowered == "fizik" -> { current = "tyt_fizik"; continue }
                lowered == "kimya" -> { current = "tyt_kimya"; continue }
                lowered == "biyoloji" -> { current = "tyt_biyoloji"; continue }
            }
            if (current.isEmpty()) continue
            val m = topicNumbers.find(line) ?: continue
            val questionCount = m.groupValues[1].toIntOrNull() ?: continue
            val correctCount = m.groupValues[2].toIntOrNull() ?: continue
            val wrongCount = m.groupValues[3].toIntOrNull() ?: continue
            val blankCount = questionCount - correctCount - wrongCount
            val topic = line.substring(0, m.range.first).trim().trim('-', '·', '.')
            if (topic.length < 4 || topic.length > 175 ||
                questionCount !in 1..15 || blankCount < 0) continue
            val titleForLesson = aliases.firstOrNull { it.key == current }?.label ?: continue
            if (wrongCount > 0) topicIssues.add(ExamTopicIssue(
                current, titleForLesson, topic, "Yanlış", wrongCount, "Belirtilmedi", "Belge OCR"))
            if (blankCount > 0) topicIssues.add(ExamTopicIssue(
                current, titleForLesson, topic, "Boş", blankCount, "Belirtilmedi", "Belge OCR"))
        }
        // A result sheet may have overlapping, duplicated or incorrectly
        // recognized topic rows. Never let them inflate actual exam counts.
        val validTopics = topicIssues.groupBy { it.lessonKey }.flatMap { (key, candidates) ->
            val pair = counts[key] ?: return@flatMap emptyList()
            val alias = aliases.first { it.key == key }
            val wrongLimit = pair.second
            val blankLimit = alias.questions - pair.first - pair.second
            var wrongUsed = 0
            var blankUsed = 0
            candidates.filter { issue ->
                if (issue.kind == "Yanlış" && wrongUsed + issue.count <= wrongLimit) {
                    wrongUsed += issue.count; true
                } else if (issue.kind == "Boş" && blankUsed + issue.count <= blankLimit) {
                    blankUsed += issue.count; true
                } else false
            }
        }
        if (validTopics.size < topicIssues.size)
            warnings.add("Bazı konu satırları belirsiz olduğu için alınmadı; konuları kontrol et.")
        return ReportResult(counts, validTopics, title, warnings, raw)
    }

    /** Regroup OCR words by source-image y. Preserve x order in tabular rows. */
    private fun groupedLines(words: List<ScannedWord>, width: Int): List<Pair<String, Float>> {
        val tolerance = max(5f, width * 0.009f)
        fun grouped(subset: List<ScannedWord>): List<Pair<String, Float>> {
            val clusters = mutableListOf<MutableList<ScannedWord>>()
            subset.sortedBy { it.centerY }.forEach { word ->
                val last = clusters.lastOrNull()
                if (last != null && abs(last.map { it.centerY }.average() - word.centerY) <= tolerance)
                    last.add(word)
                else clusters.add(mutableListOf(word))
            }
            return clusters.map { group ->
                group.sortedBy { it.centerX }.joinToString(" ") { it.value } to
                    group.map { it.centerX }.average().toFloat()
            }
        }
        return grouped(words.filter { it.centerX < width * 0.505f }) +
            grouped(words.filter { it.centerX >= width * 0.505f })
    }
}
