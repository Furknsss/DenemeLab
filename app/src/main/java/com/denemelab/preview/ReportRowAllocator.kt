package com.denemelab.preview

/**
 * Finds the largest trustworthy subset of OCR topic rows that can fit the
 * verified D/Y/B counts of one course.
 *
 * Greedy processing loses valid later rows when an early OCR variant consumes
 * the counts. Multiple equally good subsets are not ground truth: only rows
 * present in EVERY best-scoring subset are returned. The others remain
 * unverified, never silently counted as correct.
 */
object ReportRowAllocator {
    data class Allocation(
        val accepted: List<TopicOutcome>,
        val rejected: Int,
        val unresolvedAlternatives: Int
    )

    private data class Key(val correct: Int, val wrong: Int, val blank: Int) {
        val questions: Int get() = correct + wrong + blank
        operator fun plus(row: TopicOutcome) =
            Key(correct + row.correct, wrong + row.wrong, blank + row.blank)
        fun within(limit: Key) =
            correct <= limit.correct && wrong <= limit.wrong && blank <= limit.blank
    }

    // path: one possible optimal subset; guaranteed: intersection of all
    // equally optimal subsets leading to this exact D/Y/B vector.
    private data class State(val path: Set<Int>, val guaranteed: Set<Int>) {
        val count: Int get() = path.size
    }

    fun allocate(
        rows: List<TopicOutcome>,
        totalCorrect: Int,
        totalWrong: Int,
        totalBlank: Int
    ): Allocation {
        if (totalCorrect < 0 || totalWrong < 0 || totalBlank < 0)
            return Allocation(emptyList(), rows.size, 0)
        val limit = Key(totalCorrect, totalWrong, totalBlank)
        val candidates = rows.filter { it.topic.isNotBlank() &&
            it.questions > 0 && it.correct >= 0 && it.wrong >= 0 &&
            it.blank >= 0 && Key(it.correct, it.wrong, it.blank).within(limit)
        }
        var states = hashMapOf(Key(0,0,0) to State(emptySet(), emptySet()))
        candidates.forEachIndexed { index, row ->
            val next = HashMap(states)
            states.forEach { (key, state) ->
                val target = key + row
                if (!target.within(limit)) return@forEach
                val path = state.path + index
                val guaranteed = state.guaranteed + index
                val old = next[target]
                next[target] = if (old == null) State(path, guaranteed)
                    else State(old.path, old.guaranteed.intersect(guaranteed))
            }
            states = next
        }
        if (states.isEmpty()) return Allocation(emptyList(), rows.size, 0)
        val maximumQuestions = states.keys.maxOf { it.questions }
        val finalists = states.filterKeys { it.questions == maximumQuestions }
        val best = finalists.values.toList()
        val common = best.map { it.guaranteed }.reduce { a, b -> a.intersect(b) }
        val accepted = candidates.filterIndexed { index, _ -> index in common }
        return Allocation(
            accepted = accepted,
            rejected = rows.size - accepted.size,
            unresolvedAlternatives = (best.maxOf { it.count } - common.size).coerceAtLeast(0)
        )
    }
}
