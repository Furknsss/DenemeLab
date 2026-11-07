package com.denemelab.preview

import kotlin.math.roundToInt

/**
 * Some small-font reports are OCR'ed as "Konu adı 21150" instead of
 * "Konu adı 2 1 1 50". Restore S/D/Y/B% ONLY when the whole digit suffix
 * admits exactly one mathematically valid segmentation.
 *
 * The maxS bound is derived from the report layout, not guessed per question.
 * A mathematically ambiguous row is never auto-corrected.
 */
object CompactTopicColumns {
    private val compactSuffix = Regex("""^(.{4,175}?)\s+(\d{4,8})\s*%?\s*$""")

    fun expand(line: String, maxQuestionsPerTopic: Int): String? {
        val match = compactSuffix.matchEntire(line.trim()) ?: return null
        val label = match.groupValues[1].trim()
        val digits = match.groupValues[2]
        val cap = maxQuestionsPerTopic.coerceIn(1, 40)
        var candidate: String? = null
        var candidates = 0
        for (s in 1..cap) {
            for (d in 0..s) {
                for (y in 0..s-d) {
                    val pct = (100.0 * d / s).roundToInt()
                    if ("$s$d$y$pct" == digits) {
                        candidates++
                        if (candidates > 1) return null
                        candidate = "$label $s $d $y $pct"
                    }
                }
            }
        }
        return if (candidates == 1) candidate else null
    }
}
