package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class ReportCoverageTest {
    private val turkish = SubjectScore("tyt_turkce", "Türkçe", 40, 32, 6)
    private val math = SubjectScore("tyt_mat", "Matematik", 30, 20, 5)
    private val fullTurkish = TopicOutcome("tyt_turkce", "Türkçe",
        "Paragraf", 32, 6, 2)

    @Test fun screenshotLikePartialCoverageIsNeverMarkedComplete() {
        val rows = listOf(TopicOutcome(
            "tyt_turkce", "Türkçe", "Paragraf", 15, 3, 2
        ), TopicOutcome(
            "tyt_mat", "Matematik", "Problemler", 10, 4, 5
        ))
        val result = ReportCoverage.inspect(listOf(turkish, math), rows)
        assertEquals(39, result.observed)
        assertEquals(70, result.total)
        assertEquals(31, result.missing)
        assertFalse(result.complete)
        assertTrue(result.valid)
    }

    @Test fun fullBreakdownMustMatchAllThreeCounters() {
        val result = ReportCoverage.inspect(listOf(turkish), listOf(fullTurkish))
        assertTrue(result.valid)
        assertTrue(result.complete)
        assertEquals(0, result.missing)
    }

    @Test fun aCompleteQuestionCountWithWrongAnswerSplitIsRejected() {
        val result = ReportCoverage.inspect(listOf(turkish), listOf(
            TopicOutcome("tyt_turkce", "Türkçe", "Paragraf", 33, 5, 2)))
        assertFalse(result.valid)
        assertFalse(result.complete)
    }

    @Test fun repeatedOrUnknownTopicRowsAreNotFullyTrusted() {
        val duplicate = ReportCoverage.inspect(listOf(turkish), listOf(
            TopicOutcome("tyt_turkce", "Türkçe", "Paragraf", 16, 3, 1),
            TopicOutcome("tyt_turkce", "Türkçe", "PARAGRAF", 16, 3, 1)))
        assertFalse(duplicate.valid)
        assertFalse(duplicate.complete)
        val unknown = ReportCoverage.inspect(listOf(turkish), listOf(
            TopicOutcome("tyt_fizik", "Fizik", "Hareket", 4, 2, 1)))
        assertFalse(unknown.valid)
    }
}
