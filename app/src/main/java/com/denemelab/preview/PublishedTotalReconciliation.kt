package com.denemelab.preview

import kotlin.math.abs

/**
 * Printed overall totals may uniquely determine ONE missing TYT subtest.
 * This is derived evidence, not an OCR reading of that subtest: the UI must
 * explicitly require student confirmation. We never fill topic outcomes.
 */
object PublishedTotalReconciliation {
    data class Result(
        val counts: Map<String, Pair<Int, Int>>,
        val derivedKeys: Set<String>,
        val warnings: List<String>
    )

    private val printedTotal = Regex(
        """(?i)^\s*toplam\s*:?\s*120\s+(\d{1,3})\s+(\d{1,3})\s+(\d{1,3})[.,](\d{1,2})\b"""
    )

    fun completeOneCourse(raw: String, counts: Map<String, Pair<Int, Int>>): Result {
        val unchanged = Result(counts, emptySet(), emptyList())
        val templates = ExamStructure.tytDetailed
        if (counts.size != templates.size - 1) return unchanged
        val missing = templates.filterNot { counts.containsKey(it.key) }
        if (missing.size != 1) return unchanged
        val match = raw.lineSequence()
            .mapNotNull { line -> printedTotal.find(line)?.groupValues }
            .firstOrNull() ?: return unchanged
        val totalCorrect = match[1].toIntOrNull() ?: return unchanged
        val totalWrong = match[2].toIntOrNull() ?: return unchanged
        val whole = match[3].toIntOrNull() ?: return unchanged
        val decimals = match[4].padEnd(2, '0').take(2).toIntOrNull()
            ?: return unchanged
        val net = whole + decimals / 100.0
        if (totalCorrect < 0 || totalWrong < 0 ||
            totalCorrect + totalWrong > 120 ||
            abs(ExamRules.net(totalCorrect, totalWrong) - net) > 0.011)
            return unchanged

        // A wrong/missing school summary, conflicting subjects, or more than
        // one missing row must never produce fabricated data.
        val goodKnown = templates.filter { counts.containsKey(it.key) }.all {
            val score = counts.getValue(it.key)
            score.first >= 0 && score.second >= 0 &&
                score.first + score.second <= it.questions
        }
        if (!goodKnown) return unchanged

        val final = missing.single()
        val c = totalCorrect - counts.values.sumOf { it.first }
        val w = totalWrong - counts.values.sumOf { it.second }
        if (c < 0 || w < 0 || c + w > final.questions) return unchanged
        return Result(
            counts + (final.key to (c to w)),
            setOf(final.key),
            listOf(final.title + ": ders satırı doğrudan OCR ile okunamadı. " +
                "Belgedeki toplam D/Y/net değerlerinden " + c + " doğru, " + w +
                " yanlış hesaplandı. Bu değerleri belgeyle karşılaştırıp onayla.")
        )
    }
}
