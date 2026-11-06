package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class FlexibleReportTableParserTest {
    @Test fun publisherTableWithCorrectWrongBlankNetWithoutQuestionCount() {
        val rows = listOf(
            "Türkçe 32 6 2 30,50",
            "Tarih-1 4 1 0 3,75",
            "Coğrafya-1 4 1 0 3,75",
            "Felsefe 4 1 0 3,75",
            "Din Kültürü 4 1 0 3,75",
            "Matematik-1 18 2 10 17,50",
            "Geometri 2 1 7 1,75",
            "Fizik 4 3 0 3,25",
            "Kimya 4 3 0 3,25",
            "Biyoloji 2 3 1 1,25"
        )
        val parsed = FlexibleReportTableParser.parse(rows)
        assertEquals(10, parsed.values.size)
        assertEquals(32 to 6, parsed.values["tyt_turkce"])
        assertEquals(18 to 2, parsed.values["tyt_mat"])
        assertEquals(2 to 3, parsed.values["tyt_biyoloji"])
        val report = DetailedReportReconciliation.reconcile(
            "TYT deneme sonuç karnesi", rows, emptyList()
        )
        assertEquals(10, report.foundCount)
        assertEquals(72.5, ExamStructure.subjectsForEntry("TYT", "").sumOf {
            val p = report.counts.getValue(it.key)
            ExamRules.net(p.first, p.second)
        }, 0.001)
    }

    @Test fun genericTableWithFullQuestionCountIsSupported() {
        val rows = listOf(
            "Türkçe 40 34 4 2 33,00",
            "Matematik-1 30 20 6 4 18,50",
            "Geometri 10 4 2 4 3,50"
        )
        val r = FlexibleReportTableParser.parse(rows)
        assertEquals(34 to 4, r.values["tyt_turkce"])
        assertEquals(20 to 6, r.values["tyt_mat"])
        assertEquals(4 to 2, r.values["tyt_geo"])
    }

    @Test fun groupSummaryIsNotInventedAsTenSubjects() {
        val r = FlexibleReportTableParser.parse(listOf(
            "TYT Matematik 40 26 10 4 23,50",
            "TYT Fen 20 14 5 1 12,75",
            "TYT Sosyal 20 15 5 0 13,75"
        ))
        assertTrue(r.values.isEmpty())
    }

    @Test fun duplicateConflictingRowsNeedReview() {
        val r = FlexibleReportTableParser.parse(listOf(
            "Türkçe 40 34 4 2 33,00",
            "Türkçe 40 31 7 2 29,25"
        ))
        assertFalse(r.values.containsKey("tyt_turkce"))
        assertTrue(r.warnings.any { it.contains("güvenli") })
    }

    @Test fun optionalPhilosophyDoesNotCorruptMandatoryCourse() {
        val r = FlexibleReportTableParser.parse(listOf(
            "Felsefe (Seçmeli) 5 5 0 0 5,00",
            "Felsefe 5 2 3 0 1,25"
        ))
        assertEquals(2 to 3, r.values["tyt_felsefe"])
    }
}
