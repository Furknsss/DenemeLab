package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class FocusActionEngineTest {
    private val topic = "Paragrafta Ana Düşünce"
    private fun exam(id: Long, date: Long, wrong: Int = 0,
                     issueCount: Int? = null,
                     outcome: TopicOutcome? = null): ExamRecord {
        val correct = 40 - wrong
        val issue = issueCount?.takeIf { it > 0 }?.let {
            listOf(ExamTopicIssue(
                "tyt_turkce", "Türkçe", topic,
                "Yanlış", it, "Bilgi eksikliği"))
        }.orEmpty()
        return ExamRecord(
            id = id, name = "TYT $id",
            category = "Genel", examType = "TYT", subject = "",
            correct = correct, wrong = wrong, blank = 80,
            createdAt = date, examDate = date,
            parts = listOf(
                SubjectScore("tyt_turkce", "Türkçe", 40, correct, wrong)
            ),
            topicIssues = issue,
            topicOutcomes = listOfNotNull(outcome)
        )
    }

    @Test fun repeatedObservedTopicBecomesTheSinglePriority() {
        val snapshot = AnalysisEngine.analyze(listOf(
            exam(1, 1_700_000_000_000L, 2, 2),
            exam(2, 1_700_086_400_000L, 3, 3)
        ), AnalysisScope("Genel", "TYT"))
        val advice = FocusActionEngine.recommend(snapshot)!!
        assertTrue(advice.hasTopicEvidence)
        assertEquals(topic, advice.title)
        assertEquals(2, advice.observedExams)
        assertEquals(5, advice.observedWrong)
        assertEquals(3, advice.steps.size)
        assertTrue(advice.explanation.contains("2 ayrı sınavda"))
    }

    @Test fun noTaggedTopicsNeverInventsAFakeTopic() {
        val snapshot = AnalysisEngine.analyze(
            listOf(exam(1, 1_700_000_000_000L, 6)),
            AnalysisScope("Genel", "TYT"))
        val advice = FocusActionEngine.recommend(snapshot)!!
        assertFalse(advice.hasTopicEvidence)
        assertNull(advice.topic)
        assertEquals("Türkçe", advice.title)
        assertTrue(advice.explanation.contains("belirtilmedi"))
    }

    @Test fun noDataMeansNoRecommendation() {
        assertNull(FocusActionEngine.recommend(
            AnalysisEngine.analyze(emptyList(), AnalysisScope("Genel", "TYT"))
        ))
    }

    @Test fun missingNewTopicTagIsUnknownNotImprovement() {
        val base = exam(1, 1_700_000_000_000L, 2, 2)
        val after = exam(2, 1_700_259_200_000L, 1)
        val scope = AnalysisScope("Genel", "TYT")
        val snapshot = AnalysisEngine.analyze(listOf(base, after), scope)
        val advice = FocusActionEngine.recommend(snapshot)!!
        val done = FocusCompletion(
            FocusJournal.focusId(scope, advice.topic!!), topic, "Türkçe",
            1_700_086_400_000L, base.examDate, base.net, base.id
        )
        assertEquals(FocusVerification.Unmeasured,
            FocusActionEngine.verify(advice, done, snapshot))
    }

    @Test fun confirmedNewOutcomeCanProveLowerLoss() {
        val base = exam(1, 1_700_000_000_000L, 2, 2)
        val outcome = TopicOutcome("tyt_turkce", "Türkçe", topic,
            correct = 3, wrong = 0, blank = 0)
        val after = exam(2, 1_700_259_200_000L, 1, outcome = outcome)
        val scope = AnalysisScope("Genel", "TYT")
        val snapshot = AnalysisEngine.analyze(listOf(base, after), scope)
        val advice = FocusActionEngine.recommend(snapshot)!!
        val done = FocusCompletion(
            FocusJournal.focusId(scope, advice.topic!!), topic, "Türkçe",
            1_700_086_400_000L, base.examDate, base.net, base.id
        )
        val verified = FocusActionEngine.verify(advice, done, snapshot)
        assertTrue(verified is FocusVerification.Measured)
        assertTrue((verified as FocusVerification.Measured).improved)
        assertEquals(0, verified.afterWrong)
    }

    @Test fun laterWrongTopicDoesNotCountAsCompleteOutcome() {
        val base = exam(1, 1_700_000_000_000L, 2, 2)
        val after = exam(2, 1_700_259_200_000L, 2, 1)
        val scope = AnalysisScope("Genel", "TYT")
        val snapshot = AnalysisEngine.analyze(listOf(base, after), scope)
        val advice = FocusActionEngine.recommend(snapshot)!!
        val done = FocusCompletion(
            FocusJournal.focusId(scope, advice.topic!!), topic, "Türkçe",
            1_700_086_400_000L, base.examDate, base.net, base.id
        )
        assertEquals(FocusVerification.Unmeasured,
            FocusActionEngine.verify(advice, done, snapshot))
    }
}
