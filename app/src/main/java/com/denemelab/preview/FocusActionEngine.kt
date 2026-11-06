package com.denemelab.preview

/**
 * One next action, not another dashboard.
 *
 * Topic evidence is always taken from explicitly tagged mistakes in comparable
 * denemeler. Missing tags are unknown, NEVER zero or successful answers.
 */
data class FocusAdvice(
    val title: String,
    val lessonName: String,
    val topic: TopicPriority?,
    val explanation: String,
    val evidence: String,
    val reason: String,
    val steps: List<String>,
    val observedWrong: Int,
    val observedBlank: Int,
    val observedExams: Int,
    val hasTopicEvidence: Boolean
)

sealed class FocusVerification {
    data object Pending : FocusVerification()
    data object Unmeasured : FocusVerification()
    data class Measured(
        val beforeWrong: Int,
        val beforeBlank: Int,
        val afterWrong: Int,
        val afterBlank: Int
    ) : FocusVerification() {
        val improved: Boolean
            get() = afterWrong * 1.25 + afterBlank <
                beforeWrong * 1.25 + beforeBlank
    }
}

object FocusActionEngine {
    fun recommend(snapshot: AnalysisSnapshot): FocusAdvice? {
        val priority = snapshot.priorities.firstOrNull()
        if (priority != null) {
            val repeatInfo = if (priority.recordedExams >= 2)
                "Son denemelerde ${priority.recordedExams} ayrı sınavda tekrar ediyor."
            else "Şimdilik 1 denemede etiketlendi; tekrar edip etmediği bilinmiyor."
            val hint = when (priority.commonReason) {
                "Belirtilmedi" -> "Hata nedeni henüz belirlenmemiş."
                else -> "Kaydedilen baskın neden: ${priority.commonReason}."
            }
            return FocusAdvice(
                title = priority.label,
                lessonName = priority.lessonName,
                topic = priority,
                explanation = "$repeatInfo $hint",
                evidence = "${priority.recordedExams} denemede · ${priority.wrong} yanlış · ${priority.blank} boş",
                reason = priority.commonReason,
                steps = FocusJournal.plan(priority.commonReason),
                observedWrong = priority.wrong,
                observedBlank = priority.blank,
                observedExams = priority.recordedExams,
                hasTopicEvidence = true
            )
        }
        // Fallback uses recorded subject totals only, not a fictional topic
        // weakness. It should prompt tagging, rather than pretending that all
        // untagged questions are lost in the same curriculum concept.
        val subject = snapshot.subjectInsights
            .filter { it.wrong + it.blank > 0 }
            .maxByOrNull { it.theoreticalRoom } ?: return null
        return FocusAdvice(
            title = subject.name,
            lessonName = subject.name,
            topic = null,
            explanation = "Bu derste kayıp var; hangi konudan kaynaklandığı henüz belirtilmedi.",
            evidence = "${subject.wrong} yanlış · ${subject.blank} boş",
            reason = "Belirtilmedi",
            steps = listOf(
                "Bu dersin yanlış ve boş sorularını konuya göre etiketle.",
                "En sık tekrarlanan konuyu belirleyip hedefli çalışma yap.",
                "Sonraki denemede aynı konuyu yeniden ölç."
            ),
            observedWrong = subject.wrong,
            observedBlank = subject.blank,
            observedExams = 1,
            hasTopicEvidence = false
        )
    }

    /**
     * An absent topic in a later report is NOT proof of improvement. A report
     * explicitly recording the topic with zero errors is sufficient evidence.
     */
    fun verify(
        advice: FocusAdvice,
        completion: FocusCompletion,
        snapshot: AnalysisSnapshot
    ): FocusVerification {
        val topic = advice.topic ?: return FocusVerification.Unmeasured
        val later = FocusJournal.nextExamAfter(completion, snapshot)
            ?: return FocusVerification.Pending
        val previous = snapshot.history.firstOrNull {
            it.id == completion.baselineExamId
        } ?: return FocusVerification.Unmeasured
        val previousIssues = previous.topicIssues.filter {
            TopicAnalyticsV3.identity(it.lessonKey, it.topic) == topic.key
        }
        if (previousIssues.isEmpty()) return FocusVerification.Unmeasured

        val previousWrong = previousIssues.filter { it.kind == "Yanlış" }.sumOf { it.count }
        val previousBlank = previousIssues.filter { it.kind == "Boş" }.sumOf { it.count }

        val newIssues = later.topicIssues.filter {
            TopicAnalyticsV3.identity(it.lessonKey, it.topic) == topic.key
        }
        val detailedOutcome = later.topicOutcomes.firstOrNull {
            TopicAnalyticsV3.identity(it.lessonKey, it.topic) == topic.key
        }
        // A negative or partial manual tag is not a complete new outcome:
        // it may omit blanks entirely. Require an explicit full D/Y/B topic
        // observation before declaring the original weakness improved.
        if (detailedOutcome == null)
            return FocusVerification.Unmeasured
        val wrong = detailedOutcome.wrong
        val blank = detailedOutcome.blank
        return FocusVerification.Measured(
            beforeWrong = previousWrong, beforeBlank = previousBlank,
            afterWrong = wrong, afterBlank = blank
        )
    }
}
