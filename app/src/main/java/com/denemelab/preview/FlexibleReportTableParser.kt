package com.denemelab.preview

import java.text.Normalizer
import java.util.Locale

/**
 * Supplemental TYT score-table decoder for publishers that print a different
 * subject table than 3D. Only exact 10-subject totals are accepted: grouped
 * "Matematik 40" or "Fen 20" cannot be silently divided into sub-subjects.
 */
object FlexibleReportTableParser {
    data class Counts(val values: Map<String, Pair<Int, Int>>, val warnings: List<String>)

    private data class Subject(val key: String, val questions: Int, val aliases: List<String>)
    private val subjects = listOf(
        Subject("tyt_turkce", 40, listOf("tyt turkce", "turkce")),
        Subject("tyt_tarih", 5, listOf("tarih 1", "tarih")),
        Subject("tyt_cografya", 5, listOf("cografya 1", "cografya")),
        Subject("tyt_felsefe", 5, listOf("felsefe")),
        Subject("tyt_din", 5, listOf("din kul ve ahl bil", "din kul", "din kulturu", "din ve ahlak", "din")),
        Subject("tyt_mat", 30, listOf("matematik 1", "temel matematik", "matematik")),
        Subject("tyt_geo", 10, listOf("geometri")),
        Subject("tyt_fizik", 7, listOf("fizik")),
        Subject("tyt_kimya", 7, listOf("kimya")),
        Subject("tyt_biyoloji", 6, listOf("biyoloji"))
    )
    private val turkish = Locale.forLanguageTag("tr-TR")
    private val chunks = Regex("""\d+(?:[.,]\d+)?""")

    private fun normalize(s: String): String = Normalizer.normalize(
        s.lowercase(turkish).replace('ı','i'),
        Normalizer.Form.NFD
    ).replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    fun parse(rows: List<String>): Counts {
        val counts = linkedMapOf<String, Pair<Int, Int>>()
        val conflicts = linkedSetOf<String>()
        for (raw in rows) {
            val row = normalize(raw)
            if (row.startsWith("toplam") || row.startsWith("tyt sosyal") ||
                row.startsWith("tyt fen") || row.startsWith("tyt matematik") ||
                row.contains("felsefe secmeli") || row.startsWith("secmeli"))
                continue
            for (subject in subjects) {
                val alias = subject.aliases.firstOrNull { row == it || row.startsWith("$it ") }
                    ?: continue
                // Trim the matched name before reading numbers. This matters
                // for titles like Tarih-1 and Matematik-1.
                val rest = row.removePrefix(alias).trim()
                val numbers = chunks.findAll(rest).mapNotNull { it.value.toIntOrNull() }.toList()
                val q = subject.questions
                val pair = when {
                    numbers.size >= 3 && numbers[0] == q &&
                        numbers[1] + numbers[2] <= q -> numbers[1] to numbers[2]
                    numbers.size >= 3 && numbers[0] + numbers[1] + numbers[2] == q ->
                        numbers[0] to numbers[1]
                    else -> null
                }
                if (pair != null) {
                    val previous = counts[subject.key]
                    if (previous != null && previous != pair) conflicts.add(subject.key)
                    else counts[subject.key] = pair
                }
                break
            }
        }
        // Conflicting rows should require human verification instead of
        // randomly choosing the last OCR recognition of the same subject.
        conflicts.forEach { counts.remove(it) }
        return Counts(
            counts,
            if (conflicts.isEmpty()) emptyList()
            else listOf("Farklı okunan ${conflicts.size} ders satırı güvenli olmadığı için kullanıcı doğrulamasına bırakıldı.")
        )
    }
}
