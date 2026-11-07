package com.denemelab.preview

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The longest OCR string is not necessarily the best OCR string: a raw
 * scan can add stray characters after S/D/Y/B%, hiding a numerically
 * complete row. Use verified numeric structure first, then label detail.
 */
object ReportOcrTextQualityV22 {
    private val four = Regex("""^(.{4,180}?)\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,3})\s*%?$""")
    private val three = Regex("""^(.{4,180}?)\s+(\d{1,2})\s+(\d{1,2})\s+(\d{1,2})$""")
    private val heading = Regex("""^(?:tyt\s+\S+|türkçe|turkce|tarih.*|coğrafya.*|cografya.*|felsefe.*|din\s+kül.*|matematik.*|geometri.*|fizik.*|kimya.*|biyoloji.*)\s*(?:s\s+d\s+y.*)?$""", RegexOption.IGNORE_CASE)

    fun score(raw: String): Int {
        val line = (CompactTopicColumns.expand(raw, 6) ?: raw).trim()
        val m = four.matchEntire(line)
        if (m != null) {
            val (q, c, w, last) = (2..5).map { m.groupValues[it].toInt() }
            if (q in 1..40 && c >= 0 && w >= 0 && c+w <= q) {
                val correctPct = (100.0*c/q).roundToInt()
                val percentage = last in 0..100 && abs(last-correctPct) <= 2
                val blank = last == q-c-w
                if (percentage || blank)
                    return 400 + m.groupValues[1].length.coerceAtMost(100) +
                        if (percentage) 25 else 5
            }
        }
        val partial = three.matchEntire(line)
        if (partial != null) {
            val (q,c,w) = (2..4).map { partial.groupValues[it].toInt() }
            if(q in 1..40 && c+w <= q)
                return 200 + partial.groupValues[1].length.coerceAtMost(100)
        }
        if (heading.matches(line)) return 90 + line.length.coerceAtMost(30)
        return line.length.coerceAtMost(80)
    }
}
