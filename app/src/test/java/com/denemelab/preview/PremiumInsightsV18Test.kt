package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class PremiumInsightsV18Test {
    private fun outcome(wrong: Int, correct: Int = 0, blank: Int = 0) =
        TopicOutcome("tyt_mat", "Matematik", "Problemler", correct, wrong, blank)

    private fun exam(
        id: Long,
        outcome: TopicOutcome? = null,
        issues: List<ExamTopicIssue> = emptyList(),
        mood: String = "Normal"
    ) = ExamRecord(
        id = id, name = "Deneme " + id, category = "Genel",
        examType = "TYT", subject = "",
        correct = 40, wrong = 10, blank = 70, createdAt = id,
        topicOutcomes = listOfNotNull(outcome), topicIssues = issues,
        mood = mood
    )

    @Test fun consecutiveMeansAllMostRecentExamsNotThreeOutOfFive() {
        val items = (1L..5L).map { id ->
            exam(id, if (id == 1L) outcome(0, correct = 1) else outcome(2))
        }
        val streak = PremiumInsightsV18.repeatedWrongTopics(items).single()
        assertEquals(4, streak.consecutiveExams)
        assertEquals(8, streak.wrong)
        assertEquals(10.0, streak.recoverableNet, 0.001)
    }

    @Test fun missingLatestOrMissingMiddleEvidenceBreaksStreak() {
        val records = listOf(exam(1, outcome(1)), exam(2),
            exam(3, outcome(2)), exam(4, outcome(1)))
        val streak = PremiumInsightsV18.repeatedWrongTopics(records).single()
        assertEquals(2, streak.consecutiveExams)

        assertTrue(PremiumInsightsV18.repeatedWrongTopics(
            records + exam(5)).isEmpty())
    }

    @Test fun aRecordedCorrectResultEndsTheWrongSeries() {
        val records = listOf(exam(1, outcome(2)), exam(2, outcome(0, 2)),
            exam(3, outcome(1)), exam(4, outcome(1)))
        assertEquals(2,
            PremiumInsightsV18.repeatedWrongTopics(records).single().consecutiveExams)
    }

    @Test fun taggedIssueAndOutcomeForSameTopicAreNotDoubleCounted() {
        val issue = ExamTopicIssue(
            "tyt_mat", "Matematik", "Problemler", "Yanlış", 2)
        val records = listOf(exam(1, outcome(2), listOf(issue)),
            exam(2, outcome(1), listOf(issue)))
        val repeated = PremiumInsightsV18.repeatedWrongTopics(records).single()
        assertEquals(3, repeated.wrong)
        assertEquals(3.75, repeated.recoverableNet, 0.001)
        assertEquals(3, PremiumInsightsV18.topics(records).single().wrong)
    }

    @Test fun wrongAndBlankTagsWithinOneExamProduceOneObservation() {
        val issues = listOf(
            ExamTopicIssue("tyt_mat","Matematik","Problemler","Yanlış",2),
            ExamTopicIssue("tyt_mat","Matematik","Problemler","Boş",1))
        val result = PremiumInsightsV18.topics(listOf(exam(1, issues = issues)))
        assertEquals(1, result.size)
        assertEquals(1, result.single().exams)
        assertEquals(2, result.single().wrong)
        assertEquals(1, result.single().blank)
        assertEquals(3.5, result.single().recoverableNet, 0.001)
        assertTrue(PremiumInsightsV18.gains(listOf(exam(1, issues = issues))).isEmpty())
    }

    @Test fun gainsNeedExplicitlyObservedCorrectAnswers() {
        val tagged = exam(1, issues = listOf(
            ExamTopicIssue("tyt_mat","Matematik","Problemler","Yanlış",2)))
        val verified = exam(2, outcome(1, correct = 4))
        val gain = PremiumInsightsV18.gains(listOf(tagged, verified)).single()
        assertEquals(4, gain.correct)
        assertEquals(3.25, gain.earnedNet, 0.001)
    }

    @Test fun moodUsesLastFiveSavedSelectionsAndLegacyStress() {
        val records = listOf(
            exam(1, mood = "Sakin"), exam(2, mood = "Normal"),
            exam(3, mood = "Heyecanlı"), exam(4, mood = "Telaşlı"),
            exam(5, mood = "Çok stresli"))
        val result = PremiumInsightsV18.mood(records)!!
        assertEquals(5, result.numberOfExams)
        assertEquals(2.0, result.averageIndex, 0.001)
        assertEquals("Heyecanlı", result.title)
        assertEquals(5L, result.latestExam.id)
    }

    @Test fun latestSubjectRowKeepsCorrectExamIdentity() {
        fun withPart(id: Long, correct: Int) = exam(id).copy(parts = listOf(
            SubjectScore("tyt_turkce", "Türkçe",40,correct,4)))
        val rows = PremiumInsightsV18.lessons(listOf(
            withPart(1L,20), exam(3L), withPart(2L,26)))
        assertEquals(1, rows.size)
        assertEquals(2L, rows.single().exam.id)
        assertEquals(25.0, rows.single().score.net, 0.001)
    }
}
