package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class ReportEvidenceMergerTest {
    private fun result(
        counts: Map<String, Pair<Int,Int>>,
        outcomes: List<TopicOutcome> = emptyList()
    ) = ReportResult(counts, emptyList(), "TYT", emptyList(), "TYT", outcomes)

    @Test fun complementaryNativePdfAndImageReadsRecoverMissingSubjects() {
        val raster = result(mapOf("tyt_turkce" to (32 to 6)))
        val pdf = result(mapOf("tyt_tarih" to (4 to 1)))
        val merged = ReportEvidenceMerger.merge(raster,pdf)
        assertEquals(2,merged.foundCount)
        assertEquals(32 to 6,merged.counts["tyt_turkce"])
        assertEquals(4 to 1,merged.counts["tyt_tarih"])
        assertTrue(merged.ambiguousLessons.isEmpty())
    }

    @Test fun contradictoryPdfCourseIsNotAutoVerified() {
        val raster = result(mapOf("tyt_turkce" to (32 to 6)))
        val pdf = result(mapOf("tyt_turkce" to (33 to 5)))
        val merged = ReportEvidenceMerger.merge(raster,pdf)
        assertEquals(32 to 6,merged.counts["tyt_turkce"])
        assertTrue(merged.ambiguousLessons.contains("tyt_turkce"))
    }

    @Test fun conflictingTopicCountsAreNotCopiedToAnalysis() {
        val raster = result(mapOf("tyt_turkce" to (32 to 6)), listOf(
            TopicOutcome("tyt_turkce","Türkçe","Paragraf",5,2,1)))
        val pdf = result(mapOf("tyt_turkce" to (32 to 6)), listOf(
            TopicOutcome("tyt_turkce","Türkçe","PARAGRAF",6,1,1)))
        val merged = ReportEvidenceMerger.merge(raster,pdf)
        assertTrue(merged.outcomes.isEmpty())
        assertTrue(merged.warnings.any { it.contains("çeliş") || it.contains("uyuşmadı") })
    }
}
