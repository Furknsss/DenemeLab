package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class ReportTopicEvidenceV22Test {
    private fun row(d: Int, y: Int, b: Int = 0, title: String = "Problemler") =
        TopicOutcome("tyt_mat", "Matematik", title, d, y, b)

    @Test fun aWrongCoarseOverviewDoesNotVetoAgreeingHighResolutionRows() {
        val precise = row(1, 2)
        val fuzzy = row(2, 1)
        val r = ReportTopicEvidenceV22.select(
            highResolution = listOf(precise),
            rightFallback = listOf(precise),
            overview = listOf(fuzzy)
        )
        assertEquals(listOf(precise), r.outcomes)
        assertEquals(1,r.coarseDisagreements)
        assertEquals(0,r.unresolvedTopics)
    }

    @Test fun differentHighResolutionReadingsStayUnresolvedWithoutIndependentEvidence() {
        val first = row(1, 2)
        val second = row(2, 1)
        val r = ReportTopicEvidenceV22.select(listOf(first), listOf(second), emptyList())
        assertEquals(1,r.unresolvedTopics)
        assertTrue(r.outcomes.isEmpty())
    }

    @Test fun independentCoarseReadingMayResolveDisagreementBetweenTwoHighResParsers() {
        val first = row(1, 2)
        val second = row(2, 1)
        val r = ReportTopicEvidenceV22.select(listOf(first), listOf(second), listOf(first))
        assertEquals(listOf(first),r.outcomes)
    }

    @Test fun safeFallbackPreservesTopicMissingFromHighResolution() {
        val high = row(1, 1, 0, "Temel Kavramlar")
        val fallback = row(2, 0, 0, "Paragraf Problemleri")
        val coarse = row(3, 0, 0, "Eşitsizlikler")
        val r=ReportTopicEvidenceV22.select(listOf(high),listOf(fallback),listOf(coarse))
        assertEquals(3,r.outcomes.size)
        assertEquals(0,r.unresolvedTopics)
    }

    @Test fun duplicatedIdenticalReadingsAreCountedOnce() {
        val one = row(1,1)
        val r=ReportTopicEvidenceV22.select(listOf(one,one),listOf(one),listOf(one))
        assertEquals(listOf(one),r.outcomes)
    }
}
