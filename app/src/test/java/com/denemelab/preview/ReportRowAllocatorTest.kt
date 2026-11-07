package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class ReportRowAllocatorTest {
    private fun row(name: String, c: Int, w: Int, b: Int = 0) =
        TopicOutcome("tyt_mat", "Matematik", name, c, w, b)

    @Test fun earlyIncorrectVariantCannotPushTwoLaterVerifiedRowsOutOfCoverage() {
        val fake = row("Gürültüye dönüşen erken OCR satırı", 2, 0)
        val first = row("Birinci güvenilir konu", 1, 0)
        val second = row("İkinci güvenilir konu", 1, 1)
        val result = ReportRowAllocator.allocate(listOf(fake,first,second), 2,1,0)
        assertEquals(3, result.accepted.sumOf { it.questions })
        assertEquals(listOf(first,second), result.accepted)
        assertEquals(1,result.rejected)
        assertEquals(0,result.unresolvedAlternatives)
    }

    @Test fun equalBestAlternativesAreNotAutoInvented() {
        val a = row("Alternatif A",1,0)
        val b = row("Alternatif B",1,0)
        val result = ReportRowAllocator.allocate(listOf(a,b),1,0,0)
        assertTrue(result.accepted.isEmpty())
        assertEquals(2,result.rejected)
        assertEquals(1,result.unresolvedAlternatives)
    }

    @Test fun mixedCorrectWrongBlankMustFitRealSubtest() {
        val a = row("Konu A",2,1,1)
        val b = row("Konu B",0,1,0)
        val extra = row("Fazla soru",1,0,0)
        val r=ReportRowAllocator.allocate(listOf(a,b,extra),2,2,1)
        assertEquals(listOf(a,b),r.accepted)
        assertEquals(5,r.accepted.sumOf { it.questions })
        assertEquals(2,r.accepted.sumOf { it.correct })
        assertEquals(2,r.accepted.sumOf { it.wrong })
        assertEquals(1,r.accepted.sumOf { it.blank })
    }

    @Test fun completeTrustedSetNeverChanges() {
        val rows=listOf(row("Konu 1",1,1),row("Konu 2",1,0,1),
            row("Konu 3",0,1))
        val a=ReportRowAllocator.allocate(rows,2,2,1)
        assertEquals(rows,a.accepted)
        assertEquals(0,a.rejected)
    }

    @Test fun invalidOrOversizedRowDoesNotConsumeSubtestBudget() {
        val rows=listOf(row("Oversized",9,0),row("Güvenilir",2,0))
        val r=ReportRowAllocator.allocate(rows,2,0,0)
        assertEquals(listOf(rows[1]),r.accepted)
        assertEquals(1,r.rejected)
    }
}
