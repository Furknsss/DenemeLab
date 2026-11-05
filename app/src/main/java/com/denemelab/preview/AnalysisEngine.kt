package com.denemelab.preview

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Every insight is calculated from recorded exams, never illustrative numbers.
 * An 'exam' here is a comparable TYT, AYT score type, or exact branch subject.
 */
data class AnalysisScope(
    val category: String,
    val examType: String,
    val detail: String = ""
) {
    val label: String get() = when {
        category == "Branş" -> detail
        examType == "AYT" -> "AYT $detail"
        else -> examType
    }

    fun contains(exam: ExamRecord): Boolean {
        if (exam.category != category || exam.examType != examType) return false
        return if (category == "Branş")
            normalize(exam.subject) == normalize(detail)
        else examType != "AYT" || exam.track == detail
    }

    private fun normalize(value: String) = value.trim().lowercase(Locale.forLanguageTag("tr-TR"))
}

data class SubjectInsight(
    val name: String,
    val questions: Int,
    val correct: Int,
    val wrong: Int,
    val blank: Int,
    val previousNet: Double? = null
) {
    val net: Double get() = ExamRules.net(correct, wrong)
    val theoreticalRoom: Double get() = 1.25 * wrong + blank
    val fraction: Float get() = (net / questions.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    val difference: Double? get() = previousNet?.let { net - it }
}

data class TopicPriority(
    val key: String,
    val label: String,
    val lessonKey: String,
    val lessonName: String,
    val wrong: Int,
    val blank: Int,
    val recordedExams: Int,
    val mostRecentExamId: Long,
    val commonReason: String,
    val mostRecentDate: Long
) {
    val total: Int get() = wrong + blank
    val recurring: Boolean get() = recordedExams >= 2
}

data class AnalysisSnapshot(
    val scope: AnalysisScope,
    val history: List<ExamRecord>, // oldest to newest
    val recent: List<ExamRecord>, // newest first
    val last: ExamRecord?,
    val average: Double?,
    val previousAverage: Double?,
    val minNet: Double?,
    val maxNet: Double?,
    val subjectInsights: List<SubjectInsight>,
    val subjectSource: ExamRecord?,
    val priorities: List<TopicPriority>,
    val coverageExams: Int,
    val latestTaggedExams: Int
) {
    val lastFive: List<ExamRecord> get() = history.takeLast(5)
    val trendDelta: Double? get() =
        if (average != null && previousAverage != null) average - previousAverage else null
    val mostRecentDelta: Double? get() =
        if (history.size >= 2) history.last().net - history[history.lastIndex-1].net else null
    val questionLimit: Int get() =
        if (scope.category == "Genel") {
            if (scope.examType == "TYT") 120 else 80
        } else last?.let { it.correct + it.wrong + it.blank } ?: 0
}

object AnalysisEngine {
    fun scopes(exams: List<ExamRecord>, category: String): List<AnalysisScope> {
        val within = exams.filter { it.category == category }
        if (category == "Branş") {
            return within.filter { it.subject.isNotBlank() }
                .sortedWith(compareByDescending<ExamRecord> { it.examDate }.thenByDescending { it.createdAt })
                .distinctBy { it.examType + "|" + it.subject.trim().lowercase(Locale.forLanguageTag("tr-TR")) }
                .map { AnalysisScope(category, it.examType, it.subject) }
        }
        val choices = listOf(AnalysisScope(category, "TYT")) +
            listOf("SAY", "EA", "SÖZ").map { AnalysisScope(category, "AYT", it) }
        val extraTracks = within.filter { it.examType == "AYT" }
            .map { it.track }.distinct()
            .filter { it !in listOf("SAY", "EA", "SÖZ") }
            .map { AnalysisScope(category, "AYT", it) }
        // All three AYT tracks remain selectable even with zero recorded tests.
        // Legacy AYT with unspecified track is visible in a separate cohort.
        return choices + extraTracks
    }

    fun analyze(exams: List<ExamRecord>, scope: AnalysisScope): AnalysisSnapshot {
        val history = exams.filter(scope::contains)
            .sortedWith(compareBy<ExamRecord> { it.examDate }.thenBy { it.createdAt }.thenBy { it.id })
        val recent = history.asReversed().take(5)
        val n = recent.size
        val last = history.lastOrNull()
        // Use two non-overlapping windows. >=2 in each avoids a one-exam
        // "trend" being portrayed as a reliable change.
        val newerWindow = history.takeLast(3)
        val olderWindow = history.dropLast(3).takeLast(3)
        val movingAverage = if (newerWindow.isNotEmpty()) newerWindow.map { it.net }.average() else null
        val oldAverage = if (newerWindow.size >= 2 && olderWindow.size >= 2)
            olderWindow.map { it.net }.average() else null
        val lastWithParts = history.lastOrNull { it.parts.isNotEmpty() }
        val priorWithParts = if (lastWithParts != null)
            history.dropLastWhile { it.id != lastWithParts.id }.dropLast(1)
                .lastOrNull { it.parts.isNotEmpty() } else null
        val subjects = lastWithParts?.let { buildSubjectInsights(it, priorWithParts) }.orEmpty()

        val coverage = recent.count { it.parts.isNotEmpty() }
        val topicsExams = recent.count { it.topicIssues.isNotEmpty() }
        val priorities = buildPriorities(recent)
        return AnalysisSnapshot(scope, history, recent, last, movingAverage, oldAverage,
            recent.minOfOrNull { it.net }, recent.maxOfOrNull { it.net },
            subjects, lastWithParts, priorities, coverage, topicsExams)
    }

    private fun groupId(examType: String, item: SubjectScore): String {
        if (examType == "AYT") return item.key
        return when (item.key) {
            "tyt_turkce" -> "Türkçe"
            "tyt_sosyal","tyt_tarih","tyt_cografya","tyt_felsefe","tyt_din" -> "Sosyal"
            "tyt_matematik","tyt_mat","tyt_geo" -> "Matematik"
            "tyt_fen","tyt_fizik","tyt_kimya","tyt_biyoloji" -> "Fen"
            else -> item.key
        }
    }

    private fun buildSubjectInsights(
        last: ExamRecord,
        previous: ExamRecord?
    ): List<SubjectInsight> {
        val prev = previous?.parts.orEmpty().groupBy { groupId(last.examType, it) }
            .mapValues { (_, values) -> values.sumOf { it.net } }
        return last.parts.groupBy { groupId(last.examType, it) }.map { (key, values) ->
            SubjectInsight(
                name = if (last.examType == "TYT") when (key) {
                    "Sosyal" -> "Sosyal Bilimler"
                    "Fen" -> "Fen Bilimleri"
                    else -> key
                } else values.first().title,
                questions = values.sumOf { it.questions },
                correct = values.sumOf { it.correct },
                wrong = values.sumOf { it.wrong },
                blank = values.sumOf { it.blank },
                previousNet = prev[key]
            )
        }.sortedWith(compareBy<SubjectInsight> {
            if (last.examType == "TYT") when (it.name) {
                "Türkçe" -> 0
                "Sosyal Bilimler" -> 1
                "Matematik" -> 2
                "Fen Bilimleri" -> 3
                else -> 4
            } else 0
        }.thenBy { it.name })
    }

    private fun topicKey(issue: ExamTopicIssue): String {
        val norm = Normalizer.normalize(
            issue.topic.trim().lowercase(Locale.forLanguageTag("tr-TR")).replace('ı','i'),
            Normalizer.Form.NFD
        ).replace(Regex("[\\u0300-\\u036f]"), "")
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        return issue.lessonKey + "|" + norm
    }

    /**
     * Take at most five comparable recent exams. Sum actual marked wrong/blank
     * counts; don't infer untagged mistakes. Distinct exam IDs decide whether
     * a topic reoccurred. Nothing here estimates a future YKS score or rank.
     */
    fun buildPriorities(newestFirst: List<ExamRecord>): List<TopicPriority> {
        data class Seen(val exam: ExamRecord, val issue: ExamTopicIssue)
        val grouped = mutableMapOf<String, MutableList<Seen>>()
        newestFirst.take(5).forEach { exam ->
            exam.topicIssues.forEach { issue ->
                if (issue.count > 0 && issue.topic.isNotBlank() &&
                    issue.kind in listOf("Yanlış", "Boş")) {
                    grouped.getOrPut(topicKey(issue)) { mutableListOf() }.add(Seen(exam, issue))
                }
            }
        }
        return grouped.map { (key, observations) ->
            val latest = observations.maxWith(
                compareBy<Seen> { it.exam.examDate }.thenBy { it.exam.createdAt }
            )
            val reason = observations.map { it.issue.reason }
                .filter { it != "Belirtilmedi" && it.isNotBlank() }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key ?: "Belirtilmedi"
            TopicPriority(
                key = key, label = latest.issue.topic,
                lessonKey = latest.issue.lessonKey,
                lessonName = latest.issue.lessonName,
                wrong = observations.filter { it.issue.kind == "Yanlış" }.sumOf { it.issue.count },
                blank = observations.filter { it.issue.kind == "Boş" }.sumOf { it.issue.count },
                recordedExams = observations.map { it.exam.id }.distinct().size,
                mostRecentExamId = latest.exam.id,
                commonReason = reason, mostRecentDate = latest.exam.examDate
            )
        }.sortedWith(compareByDescending<TopicPriority> { it.recordedExams }
            .thenByDescending { it.wrong * 1.25 + it.blank }
            .thenByDescending { it.mostRecentDate }
            .thenBy { it.label })
    }
}
