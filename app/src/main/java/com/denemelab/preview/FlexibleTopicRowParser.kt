package com.denemelab.preview

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Layout-independent fallback for recognised topic table rows.
 * Both S/D/Y/B% (success %) and S/D/Y/B (blank count) are supported.
 * A missing/contradictory number is never guessed.
 */
object FlexibleTopicRowParser {
    data class Scan(val rows: List<TopicOutcome>, val uncertain: Int)
    private val numericRow = Regex(
        """^(.{4,185}?)\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})(?:\s+(\d{1,3})\s*%?)?\s*$"""
    )
    private val subjects = mapOf(
        "tyt turkce" to "tyt_turkce",
        "turkce" to "tyt_turkce",
        "tarih 1" to "tyt_tarih",
        "tarih" to "tyt_tarih",
        "cografya 1" to "tyt_cografya",
        "cografya" to "tyt_cografya",
        "felsefe" to "tyt_felsefe",
        "din kul ve ahl bil" to "tyt_din",
        "din kulturu" to "tyt_din",
        "din kul" to "tyt_din",
        "matematik 1" to "tyt_mat",
        "temel matematik" to "tyt_mat",
        "geometri" to "tyt_geo",
        "fizik" to "tyt_fizik",
        "kimya" to "tyt_kimya",
        "biyoloji" to "tyt_biyoloji"
    )
    private val groups = listOf("tyt sosyal","tyt matematik","tyt fen","sosyal bilimler","fen bilimleri")
    fun parse(lines: List<String>, scores: Map<String, Pair<Int, Int>>): Scan {
        val templates = ExamStructure.tytDetailed.associateBy { it.key }
        val result = mutableListOf<TopicOutcome>()
        var current = ""
        var uncertain = 0
        var mode = "" // blank or percent, only when the actual heading is readable
        for (raw in lines) {
            val label = TopicAnalyticsV3.normalize(raw)
            if (label.isBlank()) continue
            if (label.contains("secmeli") || label.contains("sec meli")) {
                current = ""
                continue
            }
            if (groups.any { label == it || label.startsWith("$it ") }) {
                current = ""
                continue
            }
            val matchedHeading = subjects.entries
                .sortedByDescending { it.key.length }
                .firstOrNull { label == it.key ||
                    (label.startsWith(it.key + " ") &&
                    label.removePrefix(it.key).trim().matches(
                        Regex("""(?:soru|s|d|y|b|net|\d|\s|%)*""")
                    ))
                }
            if (matchedHeading != null) {
                current = matchedHeading.value
                mode = ""
                continue
            }
            if (label.matches(Regex("""^(?:s|soru|dogru|yanlis|bos|d|y|b|b yuzde|basari|orani|%|\s|\d)+$"""))) {
                mode = when {
                    label.contains("bos") -> "blank"
                    label.contains("yuzde") || label.contains("basari") || raw.contains('%') -> "percent"
                    else -> mode
                }
                continue
            }
            val course = templates[current] ?: continue
            if (!scores.containsKey(course.key)) continue
            val match = numericRow.matchEntire(raw.trim()) ?: continue
            val name = match.groupValues[1].trim(' ', '-', '.', ':', '·', '|')
            val q = match.groupValues[2].toIntOrNull() ?: continue
            val c = match.groupValues[3].toIntOrNull() ?: continue
            val w = match.groupValues[4].toIntOrNull() ?: continue
            if (name.length < 4 || q !in 1..course.questions ||
                c < 0 || w < 0 || c + w > q) { uncertain++; continue }
            val b = q - c - w
            val last = match.groupValues[5].toIntOrNull()
            if (last != null) {
                val pct = (100.0 * c / q).roundToInt()
                val percentFits = last in 0..100 && abs(last - pct) <= 2
                val blankFits = last == b
                val supported = when (mode) {
                    "percent" -> percentFits
                    "blank" -> blankFits
                    else -> percentFits || blankFits
                }
                if (!supported) { uncertain++; continue }
            }
            result.add(TopicOutcome(course.key, course.title, name, c, w, b))
        }
        return Scan(result, uncertain)
    }
}
