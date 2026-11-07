package com.denemelab.preview

/**
 * Descriptive analytics only. No coaching, predictions or invented topic
 * correctness: all totals here come from persisted subject rows and explicit
 * topic evidence. Untagged questions remain "not measured".
 */
data class AnalysisLessonCoverage(
    val key: String,
    val name: String,
    val questions: Int,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val observedTopicQuestions: Int,
    val hasContradiction: Boolean
) {
    val unseen: Int get() = (questions - observedTopicQuestions).coerceAtLeast(0)
    val coveragePercent: Int get() = if (questions == 0) 0
        else (observedTopicQuestions * 100 / questions).coerceIn(0, 100)
}

data class ObservedTopicSummary(
    val id: String,
    val lessonName: String,
    val topic: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val examsSeen: Int,
    val source: String
) {
    val measuredQuestions: Int get() = correct + wrong + blank
}

object AnalysisEvidence {
    fun perLesson(exam: ExamRecord): List<AnalysisLessonCoverage> {
        // Subject-level correct/wrong/blank are independent of topic OCR.
        val outcomes = exam.topicOutcomes
        return exam.parts.map { part ->
            val rows = outcomes.filter { it.lessonKey == part.key }
            val correct = rows.sumOf { it.correct }
            val wrong = rows.sumOf { it.wrong }
            val blank = rows.sumOf { it.blank }
            val hasContradiction = rows.any {
                it.correct < 0 || it.wrong < 0 || it.blank < 0 || it.questions < 1
            } || correct > part.correct || wrong > part.wrong ||
                blank > part.blank ||
                rows.map { TopicAnalyticsV3.identity(it.lessonKey, it.topic) }.distinct().size != rows.size
            AnalysisLessonCoverage(part.key, part.title, part.questions,
                part.correct, part.wrong, part.blank,
                correct + wrong + blank, hasContradiction)
        }
    }

    fun observedTopics(exams: List<ExamRecord>): List<ObservedTopicSummary> {
        val items = exams.flatMap { exam ->
            // Full D/Y/blank topic rows are primary evidence. Issue-only
            // entries are a fallback for manually tagged exams, not added
            // to the same topic again because they mirror wrong/blank rows.
            val byOutcome = exam.topicOutcomes.filter {
                it.topic.isNotBlank() && it.questions > 0 &&
                    it.correct >= 0 && it.wrong >= 0 && it.blank >= 0
            }.map { Triple(exam.id, it, "D/Y/Boş") }
            val knownIds = byOutcome.map {
                TopicAnalyticsV3.identity(it.second.lessonKey, it.second.topic)
            }.toSet()
            val issueRows = exam.topicIssues.filter {
                it.topic.isNotBlank() && it.count > 0 &&
                    it.kind in listOf("Yanlış", "Boş") &&
                    TopicAnalyticsV3.identity(it.lessonKey, it.topic) !in knownIds
            }.groupBy {
                TopicAnalyticsV3.identity(it.lessonKey, it.topic)
            }.values.map { rows ->
                val one = rows.first()
                Triple(exam.id, TopicOutcome(
                    one.lessonKey, one.lessonName, one.topic, 0,
                    rows.filter { it.kind == "Yanlış" }.sumOf { it.count },
                    rows.filter { it.kind == "Boş" }.sumOf { it.count },
                    "Konu etiketi"
                ), "Etiketlenen hata")
            }
            byOutcome + issueRows
        }
        return items.groupBy {
            TopicAnalyticsV3.identity(it.second.lessonKey, it.second.topic)
        }.values.map { entries ->
            val latest = entries.last().second
            ObservedTopicSummary(
                id = TopicAnalyticsV3.identity(latest.lessonKey, latest.topic),
                lessonName = latest.lessonName,
                topic = latest.topic,
                correct = entries.sumOf { it.second.correct },
                wrong = entries.sumOf { it.second.wrong },
                blank = entries.sumOf { it.second.blank },
                examsSeen = entries.map { it.first }.distinct().size,
                source = if (entries.any { it.third == "D/Y/Boş" }) "Konu satırları"
                    else "İşaretlenen yanlış/boşlar"
            )
        }.sortedWith(compareByDescending<ObservedTopicSummary> { it.wrong + it.blank }
            .thenByDescending { it.examsSeen }
            .thenBy { it.lessonName }
            .thenBy { it.topic })
    }
}
