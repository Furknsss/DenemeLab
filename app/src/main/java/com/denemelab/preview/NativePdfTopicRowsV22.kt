package com.denemelab.preview

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dedicated parser for actual selectable-PDF curriculum rows.
 *
 * PDFBox already extracted all native glyphs into LEFT / RIGHT columns.
 * Old fuzzy OCR reconciliation lost complete native rows by interpreting
 * multiple independent parses as conflicts. This parser uses the printed
 * S/D/Y/B% table headers to maintain exact section identity, and checks
 * every row against the corresponding verified D/Y/B lesson totals.
 *
 * There is no imputation. Absent topic data remain missing.
 */
object NativePdfTopicRowsV22 {
    data class Result(
        val outcomes: List<TopicOutcome>,
        val recognizedRows: Int,
        val unresolvedRows: Int
    )

    private val header = Regex(
        """^\s*(.{3,90}?)\s+S\s+D\s+Y\s+B\s*%?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val columns = Regex(
        """^\s*(.{4,185}?)\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,3})\s*%?\s*$"""
    )
    private val shortColumns = Regex(
        """^\s*(.{4,185}?)\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s*$"""
    )
    private val nameToKey = mapOf(
        "turkce" to "tyt_turkce",
        "tyt turkce" to "tyt_turkce",
        "tarih 1" to "tyt_tarih",
        "tarih" to "tyt_tarih",
        "cografya 1" to "tyt_cografya",
        "cografya" to "tyt_cografya",
        "felsefe" to "tyt_felsefe",
        "din kulturu" to "tyt_din",
        "din kul ve ahl bil" to "tyt_din",
        "din kul" to "tyt_din",
        "matematik 1" to "tyt_mat",
        "matematik" to "tyt_mat",
        "temel matematik" to "tyt_mat",
        "geometri" to "tyt_geo",
        "fizik" to "tyt_fizik",
        "kimya" to "tyt_kimya",
        "biyoloji" to "tyt_biyoloji"
    )

    fun parse(
        rightRows: List<String>,
        counts: Map<String, Pair<Int,Int>>
    ): Result {
        val subjects = ExamStructure.tytDetailed.associateBy { it.key }
        val identified = mutableListOf<TopicOutcome>()
        var recognized = 0
        var invalid = 0
        var current = ""
        for (raw in rightRows) {
            val label = raw.trim()
            if (label.isBlank()) continue
            val h = header.matchEntire(label)
            if (h != null) {
                val canonical = TopicAnalyticsV3.normalize(h.groupValues[1])
                current = nameToKey[canonical] ?: ""
                continue
            }
            if (current.isEmpty() || current !in counts) continue
            val template = subjects[current] ?: continue
            val expanded = CompactTopicColumns.expand(label, template.questions.coerceAtMost(6))
            val readable = expanded ?: label
            val m = columns.matchEntire(readable)
            val short = if (m == null) shortColumns.matchEntire(readable) else null
            if (m == null && short == null) continue
            val v = m?.groupValues ?: short!!.groupValues
            val name = v[1].trim()
            val q = v[2].toIntOrNull() ?: continue
            val d = v[3].toIntOrNull() ?: continue
            val y = v[4].toIntOrNull() ?: continue
            recognized++
            if (name.length < 4 || q !in 1..template.questions ||
                d < 0 || y < 0 || d+y > q) {
                invalid++
                continue
            }
            val remaining = q-d-y
            if (m != null) {
                val last = v[5].toIntOrNull() ?: continue
                val expectedPct = (100.0 * d / q).roundToInt()
                // The printer may output success percent or blank count.
                if (!(last == remaining ||
                    (last in 0..100 && abs(last-expectedPct) <= 2))) {
                    invalid++
                    continue
                }
            }
            identified += TopicOutcome(current, template.title, name, d,y,remaining,
                "PDF metin tablosu")
        }
        val unique = identified.groupBy { TopicAnalyticsV3.identity(it.lessonKey,it.topic) }
            .values.mapNotNull { group ->
                group.first().takeIf {
                    group.map { r -> Triple(r.correct,r.wrong,r.blank) }.distinct().size == 1
                }
            }
        val selected = mutableListOf<TopicOutcome>()
        var rejected = 0
        for ((key,rows) in unique.groupBy { it.lessonKey }) {
            val total = counts[key] ?: continue
            val template = subjects[key] ?: continue
            val blank = template.questions - total.first - total.second
            if (blank < 0) continue
            val allocation = ReportRowAllocator.allocate(rows,total.first,total.second,blank)
            selected.addAll(allocation.accepted)
            rejected += allocation.rejected
        }
        return Result(
            outcomes=selected,
            recognizedRows=recognized,
            unresolvedRows=invalid + rejected + (identified.size-unique.size)
        )
    }
}
