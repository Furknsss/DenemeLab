package com.denemelab.preview

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/**
 * Explicitly observed success in a topic, from a verified result report.
 * Absence of a wrong-topic tag is NOT evidence of correctness.
 */
data class TopicOutcome(
    val lessonKey: String,
    val lessonName: String,
    val topic: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val provenance: String = "Belge OCR"
) {
    val questions: Int get() = correct + wrong + blank
}

data class TopicRiskV3(
    val id: String,
    val lessonKey: String,
    val lessonName: String,
    val topic: String,
    val wrong: Int,
    val blank: Int,
    val seenInExams: Int,
    val recentExamCount: Int,
    val taggedExamCount: Int,
    val consecutiveTaggedExams: Int,
    val commonReason: String,
    val referenceLossPerExam: Double,
    val recentLoss: Double,
    val lastDate: Long
) {
    val theoreticalRoom: Double get() = wrong * 1.25 + blank
    val recurrenceRate: Float get() = if (taggedExamCount == 0) 0f else
        seenInExams.toFloat() / taggedExamCount
}

data class StrongTopicV3(
    val id: String,
    val lessonName: String,
    val topic: String,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val examsSeen: Int,
    val accuracy: Double
)

data class ReasonShareV3(val reason: String, val count: Int)

data class SubjectOpportunityV3(
    val name: String,
    val theoreticalRoomPerExam: Double,
    val observedTags: Int,
    val focusTopic: String
)

data class TopicAnalysisV3(
    val cohortCount: Int,
    val taggedExamCount: Int,
    val successesExamCount: Int,
    val risks: List<TopicRiskV3>,
    val strengths: List<StrongTopicV3>,
    val reasons: List<ReasonShareV3>,
    val subjects: List<SubjectOpportunityV3>,
    val referenceNet: Double?,
    val theoreticalTaggedGain: Double,
    val examLimit: Int
) {
    /** Scenario, NOT a predicted gain. Only observed topic-tagged losses count. */
    fun scenarioGain(percent: Int): Double {
        val fraction = (percent.coerceIn(0, 100) / 100.0)
        val capped = (examLimit - (referenceNet ?: 0.0)).coerceAtLeast(0.0)
        return min(theoreticalTaggedGain * fraction, capped)
    }
    fun scenarioBySubject(percent: Int): List<Pair<String, Double>> {
        val fraction = percent.coerceIn(0,100)/100.0
        return subjects.map { it.name to (it.theoreticalRoomPerExam * fraction) }
    }
}

object TopicAnalyticsV3 {
    private val tr = Locale.forLanguageTag("tr-TR")

    /** Stable across spelling/case variations and OCR punctuation. */
    fun normalize(value: String): String = Normalizer.normalize(
        value.trim().lowercase(tr).replace('ı', 'i'), Normalizer.Form.NFD
    ).replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
        .replace(Regex("\\s+"), " ")

    fun identity(key: String, topic: String): String = "$key|${normalize(topic)}"

    /**
     * A topic outcome must be a genuine explicitly recorded question
     * distribution. We never label an unmentioned topic as strong.
     */
    fun validOutcomes(outcomes: List<TopicOutcome>, parts: List<SubjectScore>): Boolean {
        if (outcomes.isEmpty()) return true
        val subjects = parts.associateBy { it.key }
        if (outcomes.any {
                it.topic.isBlank() || it.correct < 0 || it.wrong < 0 ||
                it.blank < 0 || it.questions == 0 || !subjects.containsKey(it.lessonKey)
            }) return false
        for ((key, observations) in outcomes.groupBy { it.lessonKey }) {
            val lesson = subjects.getValue(key)
            if (observations.sumOf { it.correct } > lesson.correct ||
                observations.sumOf { it.wrong } > lesson.wrong ||
                observations.sumOf { it.blank } > lesson.blank) return false
            if (observations.map { identity(key, it.topic) }.distinct().size != observations.size)
                return false
        }
        return true
    }

    fun analyze(exams: List<ExamRecord>, scope: AnalysisScope): TopicAnalysisV3 {
        val recent = exams.filter(scope::contains)
            .sortedWith(compareBy<ExamRecord> { it.examDate }.thenBy { it.createdAt }.thenBy { it.id })
            .takeLast(5)
        val n = recent.size
        val known = recent.filter { it.topicIssues.isNotEmpty() }
        val observed = recent.filter { it.topicOutcomes.isNotEmpty() }
        val byId = linkedMapOf<String, MutableList<Pair<ExamRecord, ExamTopicIssue>>>()
        recent.forEach { exam ->
            exam.topicIssues.filter { it.kind in listOf("Yanlış","Boş") && it.count > 0 }
                .forEach { issue ->
                    byId.getOrPut(identity(issue.lessonKey,issue.topic)) { mutableListOf() }
                        .add(exam to issue)
                }
        }
        val risks = byId.map { (id, entries) ->
            val latest = entries.maxWith(compareBy<Pair<ExamRecord, ExamTopicIssue>> {
                it.first.examDate }.thenBy { it.first.createdAt })
            val wrong = entries.filter { it.second.kind == "Yanlış" }.sumOf { it.second.count }
            val blank = entries.filter { it.second.kind == "Boş" }.sumOf { it.second.count }
            val recurringExams = entries.map { it.first.id }.distinct().size
            // Number of consecutive *tagged* exams ending with this issue.
            // Untagged exams are unknown, not scored as clean.
            var streak = 0
            for (exam in known.asReversed()) {
                if (entries.any { it.first.id == exam.id }) streak++ else break
            }
            val reason = entries.map { it.second.reason }
                .filter { it.isNotBlank() && it != "Belirtilmedi" }
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                ?: "Belirtilmedi"
            TopicRiskV3(
                id, latest.second.lessonKey, latest.second.lessonName,
                latest.second.topic, wrong, blank, recurringExams, n,
                known.size, streak, reason,
                if(n > 0) (wrong * 1.25 + blank) / n else 0.0,
                wrong * 1.25 + blank,
                latest.first.examDate
            )
        }.sortedWith(compareByDescending<TopicRiskV3> { it.seenInExams }
            .thenByDescending { it.referenceLossPerExam }.thenByDescending { it.lastDate })

        val strengths = recent.flatMap { exam ->
            exam.topicOutcomes.filter { it.questions > 0 }.map { exam to it }
        }.groupBy { (_, o) -> identity(o.lessonKey,o.topic) }
            .mapNotNull { (id, rows) ->
                val examCount = rows.map { it.first.id }.distinct().size
                val right = rows.sumOf { it.second.correct }
                val total = rows.sumOf { it.second.questions }
                val wrong = rows.sumOf { it.second.wrong }
                val blank = rows.sumOf { it.second.blank }
                val accuracy = if(total == 0) 0.0 else right.toDouble()/total
                // Require explicit correct observations on >=2 separate exams
                // and >=5 actual attempted questions before praise.
                if(examCount < 2 || total < 5 || accuracy < 0.85) null else
                    StrongTopicV3(id, rows.last().second.lessonName,
                        rows.last().second.topic, right, wrong, blank, examCount, accuracy)
            }.sortedWith(compareByDescending<StrongTopicV3> { it.accuracy }
                .thenByDescending { it.examsSeen }.thenByDescending { it.correct })

        // "Belirtilmedi" is explicitly excluded from reason-share denominator.
        // A mistake may be tagged twice by reason only when the student
        // records it twice in separate issue entries.
        val reasons = recent.flatMap { it.topicIssues }
            .filter { it.kind == "Yanlış" && it.count > 0 &&
                it.reason.isNotBlank() && it.reason != "Belirtilmedi" }
            .groupBy { it.reason }
            .map { (reason, errors) -> ReasonShareV3(reason, errors.sumOf { it.count }) }
            .sortedByDescending { it.count }

        val subjects = risks.groupBy { it.lessonName }.map { (name, topicRisks) ->
            val sorted = topicRisks.sortedByDescending { it.referenceLossPerExam }
            SubjectOpportunityV3(name, topicRisks.sumOf { it.referenceLossPerExam },
                topicRisks.sumOf { it.wrong+it.blank }, sorted.first().topic)
        }.sortedByDescending { it.theoreticalRoomPerExam }

        val limit = when {
            scope.category == "Genel" && scope.examType == "TYT" -> 120
            scope.category == "Genel" -> 80
            else -> recent.lastOrNull()?.let { it.correct + it.wrong + it.blank } ?: 0
        }
        val reference = recent.takeLast(3).takeIf { it.isNotEmpty() }
            ?.map { it.net }?.average()
        return TopicAnalysisV3(n,known.size,observed.size,risks,strengths,reasons,subjects,
            reference, subjects.sumOf { it.theoreticalRoomPerExam },limit)
    }
}
