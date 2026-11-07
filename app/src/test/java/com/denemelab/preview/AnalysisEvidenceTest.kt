package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class AnalysisEvidenceTest {
    private val parts = ExamStructure.tytDetailed.map { sub ->
        when (sub.key) {
            "tyt_turkce" -> SubjectScore(sub.key, sub.title, sub.questions, 30, 7)
            "tyt_geo" -> SubjectScore(sub.key, sub.title, sub.questions, 3, 1)
            else -> SubjectScore(sub.key, sub.title, sub.questions, 0, 0)
        }
    }
    private fun exam(
        id: Long = 1L,
        outcomes: List<TopicOutcome> = emptyList(),
        issues: List<ExamTopicIssue> = emptyList()
    ) = ExamRecord(id, "Deneme $id", "Genel", "TYT", "", 33, 8, 79, id,
        parts = parts, topicOutcomes = outcomes, topicIssues = issues)

    @Test fun unmeasuredQuestionsAreNotClaimedCorrect() {
        val outcomes = listOf(
            TopicOutcome("tyt_turkce", "Türkçe", "Paragraf", 23, 5, 2)
        )
        val result = AnalysisEvidence.perLesson(exam(outcomes = outcomes))
        val turkish = result.first { it.key == "tyt_turkce" }
        assertEquals(30, turkish.observedTopicQuestions)
        assertEquals(10, turkish.unseen)
        assertEquals(30, turkish.correct)
        assertFalse(turkish.hasContradiction)
        assertEquals(120, result.sumOf { it.questions })
        assertEquals(30, result.sumOf { it.observedTopicQuestions })
    }

    @Test fun multipleTopicsFromOneExamCountAsOneEvidenceExam() {
        val outcomes = listOf(
            TopicOutcome("tyt_turkce", "Türkçe", "Paragraf", 6, 2, 1)
        )
        val issues = listOf(
            ExamTopicIssue("tyt_turkce","Türkçe","Paragraf","Yanlış",2,
                "Belirtilmedi","Belge OCR")
        )
        val rows = AnalysisEvidence.observedTopics(listOf(exam(outcomes=outcomes,issues=issues)))
        val p = rows.single()
        assertEquals(2,p.wrong)
        assertEquals(6,p.correct)
        assertEquals(1,p.examsSeen)
    }

    @Test fun otherManuallyTaggedLossIsShownWithoutClaimingSuccess() {
        val issues = listOf(
            ExamTopicIssue("tyt_geo","Geometri","Üçgenler","Boş",3,"Belirtilmedi","Manuel")
        )
        val rows = AnalysisEvidence.observedTopics(listOf(exam(issues=issues)))
        assertEquals(3,rows.single().blank)
        assertEquals(0,rows.single().correct)
        assertEquals("İşaretlenen yanlış/boşlar",rows.single().source)
    }
}
