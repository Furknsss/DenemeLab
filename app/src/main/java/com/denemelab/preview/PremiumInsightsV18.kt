package com.denemelab.preview

/**
 * Premium Analiz'in kanita dayali ozetleri.
 *
 * Onemli: konu kaydi olmayan deneme bir konunun yanlis yapilmadigini
 * kanitlamaz. Art arda yanlis serisinde bu durum seriyi keser; asla
 * "temiz deneme" olarak sayilmaz. Konu sonucu ve hata etiketi ayni
 * sorulari ikinci kez saymaz.
 */
data class TopicMetricV18(
    val id: String,
    val lessonKey: String,
    val lessonName: String,
    val topic: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val exams: Int
) {
    val earnedNet: Double get() = correct - wrong / 4.0
    val recoverableNet: Double get() = wrong * 1.25 + blank
}

data class RepeatedLossV18(
    val id: String,
    val lessonName: String,
    val topic: String,
    val consecutiveExams: Int,
    val wrong: Int,
    val blank: Int
) {
    val recoverableNet: Double get() = wrong * 1.25 + blank
}

data class LessonResultV18(val exam: ExamRecord, val score: SubjectScore)

data class ExamMoodV18(
    val title: String,
    val averageIndex: Double,
    val numberOfExams: Int,
    val latestExam: ExamRecord
)

object PremiumInsightsV18 {
    val modes = listOf("Sakin", "Normal", "Heyecanlı", "Telaşlı", "Stresli")

    private data class Evidence(
        val lessonKey: String,
        val lessonName: String,
        val topic: String,
        val correct: Int,
        val wrong: Int,
        val blank: Int
    ) {
        val id: String get() = TopicAnalyticsV3.identity(lessonKey, topic)
    }

    /**
     * Prefer full D/Y/B records. Issue-only wrong/blank tags fill gaps,
     * without adding the same topic twice, including when the issue has
     * separate wrong and blank rows.
     */
    private fun evidence(exam: ExamRecord): Map<String, Evidence> {
        val results = linkedMapOf<String, Evidence>()
        exam.topicOutcomes.filter {
            it.topic.isNotBlank() && it.questions > 0 &&
                it.correct >= 0 && it.wrong >= 0 && it.blank >= 0
        }.forEach { row ->
            val entry = Evidence(row.lessonKey, row.lessonName, row.topic,
                row.correct, row.wrong, row.blank)
            results[entry.id] = entry
        }
        exam.topicIssues.filter {
            it.topic.isNotBlank() && it.count > 0 && it.kind in listOf("Yanlış", "Boş")
        }.groupBy { TopicAnalyticsV3.identity(it.lessonKey, it.topic) }
            .forEach { (id, rows) ->
                if (id !in results) {
                    val first = rows.first()
                    results[id] = Evidence(first.lessonKey, first.lessonName,
                        first.topic, 0,
                        rows.filter { it.kind == "Yanlış" }.sumOf { it.count },
                        rows.filter { it.kind == "Boş" }.sumOf { it.count })
                }
            }
        return results
    }

    /** Oldest to newest, stable when several tests share one calendar day. */
    private fun ordered(history: List<ExamRecord>) =
        history.sortedWith(compareBy<ExamRecord> { it.examDate }
            .thenBy { it.createdAt }.thenBy { it.id })

    /** The current uninterrupted streak. A missing tag is NOT a pass. */
    fun repeatedWrongTopics(history: List<ExamRecord>): List<RepeatedLossV18> {
        val newestFirst = ordered(history).asReversed()
        val recent = newestFirst.firstOrNull() ?: return emptyList()
        val newest = evidence(recent)
        return newest.values.filter { it.wrong > 0 }.mapNotNull { current ->
            var count = 0
            var wrong = 0
            var blank = 0
            for (exam in newestFirst) {
                val observation = evidence(exam)[current.id] ?: break
                if (observation.wrong <= 0) break
                count++
                wrong += observation.wrong
                blank += observation.blank
            }
            if (count < 2) null else RepeatedLossV18(
                current.id, current.lessonName, current.topic, count, wrong, blank)
        }.sortedWith(compareByDescending<RepeatedLossV18> { it.consecutiveExams }
            .thenByDescending { it.recoverableNet }
            .thenBy { it.topic })
    }

    fun topics(history: List<ExamRecord>, limit: Int = 5): List<TopicMetricV18> {
        val grouped = linkedMapOf<String, MutableList<Evidence>>()
        ordered(history).takeLast(limit.coerceAtLeast(1)).forEach { exam ->
            evidence(exam).forEach { (id, entry) ->
                grouped.getOrPut(id) { mutableListOf() }.add(entry)
            }
        }
        return grouped.map { (id, rows) ->
            val last = rows.last()
            TopicMetricV18(id, last.lessonKey, last.lessonName, last.topic,
                rows.sumOf { it.correct }, rows.sumOf { it.wrong },
                rows.sumOf { it.blank }, rows.size)
        }
    }

    fun losses(history: List<ExamRecord>, limit: Int = 5): List<TopicMetricV18> =
        topics(history, limit).filter { it.recoverableNet > 0.0 }
            .sortedWith(compareByDescending<TopicMetricV18> { it.recoverableNet }
                .thenByDescending { it.exams }.thenBy { it.topic })

    /** Gain requires actual correct-question observations; tags alone are excluded. */
    fun gains(history: List<ExamRecord>, limit: Int = 5): List<TopicMetricV18> =
        topics(history, limit).filter { it.correct > 0 && it.earnedNet > 0.0 }
            .sortedWith(compareByDescending<TopicMetricV18> { it.earnedNet }
                .thenByDescending { it.correct }.thenBy { it.topic })

    /** Each subject points at the *actual latest exam* containing that row. */
    fun lessons(history: List<ExamRecord>): List<LessonResultV18> {
        val latest = linkedMapOf<String, LessonResultV18>()
        ordered(history).asReversed().forEach { exam ->
            exam.parts.forEach { part ->
                if (part.key !in latest) latest[part.key] = LessonResultV18(exam, part)
            }
        }
        return latest.values.sortedWith(compareBy<LessonResultV18> {
            val key = it.score.key
            val all = if (it.exam.examType == "TYT") ExamStructure.tytDetailed +
                ExamStructure.tyt else ExamStructure.subjects(it.exam.examType, it.exam.track)
            all.indexOfFirst { t -> t.key == key }.let { rank ->
                if (rank < 0) 999 else rank
            }
        }.thenBy { it.score.title })
    }

    fun modeIndex(mood: String): Int? = when (mood.trim()) {
        "Sakin" -> 0
        "Normal" -> 1
        "Heyecanlı" -> 2
        "Telaşlı" -> 3
        "Stresli", "Çok stresli" -> 4 // older saved exams
        else -> null
    }

    fun mood(history: List<ExamRecord>, limit: Int = 5): ExamMoodV18? {
        val recent = ordered(history).takeLast(limit.coerceAtLeast(1))
        val observed = recent.mapNotNull { exam -> modeIndex(exam.mood)?.let { exam to it } }
        if (observed.isEmpty()) return null
        val score = observed.map { it.second }.average()
        return ExamMoodV18(modes[score.toInt().coerceIn(0,4)],
            score, observed.size, observed.last().first)
    }
}
