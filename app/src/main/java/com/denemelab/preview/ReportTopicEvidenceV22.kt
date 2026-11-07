package com.denemelab.preview

/**
 * A low resolution overview is FALLBACK evidence, not a veto over a
 * successfully parsed high-resolution curriculum strip. The old code
 * invalidated entire topic rows whenever its coarse page scan disagreed
 * with a better crop, losing real questions from the final 120.
 *
 * Conflicts *inside* the high-resolution evidence are never resolved by
 * guessing. A high-res row with a consistent independent parser can be
 * preferred over a contradictory coarse reading, while unresolved readings
 * remain out of the measured question count.
 */
object ReportTopicEvidenceV22 {
    data class Result(
        val outcomes: List<TopicOutcome>,
        val unresolvedTopics: Int,
        val coarseDisagreements: Int
    )
    private fun signature(t: TopicOutcome): Triple<Int, Int, Int> =
        Triple(t.correct, t.wrong, t.blank)

    private fun unique(rows: List<TopicOutcome>): TopicOutcome? =
        rows.takeIf { it.isNotEmpty() &&
            it.map(::signature).distinct().size == 1 }?.first()

    fun select(
        highResolution: List<TopicOutcome>,
        rightFallback: List<TopicOutcome>,
        overview: List<TopicOutcome>
    ): Result {
        val h = highResolution.groupBy { TopicAnalyticsV3.identity(it.lessonKey, it.topic) }
        val f = rightFallback.groupBy { TopicAnalyticsV3.identity(it.lessonKey, it.topic) }
        val o = overview.groupBy { TopicAnalyticsV3.identity(it.lessonKey, it.topic) }
        val keys = linkedSetOf<String>().apply {
            addAll(h.keys); addAll(f.keys); addAll(o.keys)
        }
        val selected = mutableListOf<TopicOutcome>()
        var unresolved = 0
        var coarseMismatch = 0
        for (key in keys) {
            val high = unique(h[key].orEmpty())
            val fallback = unique(f[key].orEmpty())
            val coarse = unique(o[key].orEmpty())
            val highPresent = !h[key].isNullOrEmpty()
            val fallbackPresent = !f[key].isNullOrEmpty()
            val highConflicts = highPresent && high == null
            val fallbackConflicts = fallbackPresent && fallback == null
            if (highConflicts || fallbackConflicts) {
                // A second high-res parser may disambiguate a duplicated
                // high-res row, but otherwise no result can be declared safe.
                val legitimate = when {
                    highConflicts && fallback != null &&
                        h.getValue(key).any { signature(it) == signature(fallback) } ->
                        fallback
                    fallbackConflicts && high != null &&
                        f.getValue(key).any { signature(it) == signature(high) } ->
                        high
                    else -> null
                }
                if (legitimate != null) selected.add(legitimate)
                else unresolved++
                continue
            }
            if (high != null && fallback != null &&
                signature(high) != signature(fallback)) {
                // Both are derived from the same high-res OCR text. A
                // consistent *third*, independently read overview can
                // disambiguate; otherwise fail closed.
                when {
                    coarse != null && signature(coarse) == signature(high) ->
                        selected.add(high)
                    coarse != null && signature(coarse) == signature(fallback) ->
                        selected.add(fallback)
                    else -> unresolved++
                }
                continue
            }
            val reliable = high ?: fallback
            if (reliable != null) {
                if (coarse != null && signature(coarse) != signature(reliable))
                    coarseMismatch++
                selected.add(reliable)
            } else if (coarse != null) {
                selected.add(coarse)
            } else {
                unresolved++
            }
        }
        return Result(selected, unresolved, coarseMismatch)
    }
}
