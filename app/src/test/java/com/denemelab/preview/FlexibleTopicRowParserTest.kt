package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class FlexibleTopicRowParserTest {
    private val counts = mapOf("tyt_turkce" to (32 to 6), "tyt_mat" to (18 to 2))

    @Test fun supportsPercentageAndExplicitBlankCountWithoutInventingWords() {
        val rows = listOf(
            "TYT Türkçe",
            "Konu S D Y B%",
            "Paragrafta Ana Düşünce 6 3 2 50",
            "Ses Bilgisi 2 1 1 50",
            "TYT Matematik",
            "Matematik-1",
            "Konu S D Y B",
            "Denklemler ve Eşitsizlikler 3 2 0 1"
        )
        val parsed = FlexibleTopicRowParser.parse(rows, counts)
        assertEquals(3, parsed.rows.size)
        val first = parsed.rows.first()
        assertEquals("tyt_turkce", first.lessonKey)
        assertEquals(1, first.blank)
        val last = parsed.rows.last()
        assertEquals("tyt_mat", last.lessonKey)
        assertEquals(2, last.correct)
        assertEquals(0, last.wrong)
        assertEquals(1, last.blank)
    }

    @Test fun contradictoryNumbersAreDroppedNotUsedAsSuccess() {
        val parsed = FlexibleTopicRowParser.parse(listOf(
            "TYT Türkçe",
            "Başarı Konusu 1 0 1 100",
            "Geçerli Konu 1 0 1 0"
        ), counts)
        assertEquals(1, parsed.rows.size)
        assertEquals("Geçerli Konu", parsed.rows.single().topic)
        assertEquals(1, parsed.uncertain)
    }

    @Test fun absentSubjectHeadingsDoNotCauseGuessedSubjectAttribution() {
        val parsed = FlexibleTopicRowParser.parse(listOf(
            "Paragrafta Ana Düşünce 6 3 2 50",
            "TYT Matematik",
            "Bilinmeyen Problem 2 1 1 50"
        ), counts)
        assertTrue(parsed.rows.isEmpty())
    }

    @Test fun optionalPhilosophyDoesNotInflateRequired120() {
        val parsed = FlexibleTopicRowParser.parse(listOf(
            "Felsefe (Seçmeli)",
            "Seçmeli Felsefe Konusu 2 2 0 100",
            "TYT Türkçe",
            "Paragraf Anlama 2 2 0 100"
        ), counts)
        assertEquals(1, parsed.rows.size)
        assertEquals("tyt_turkce", parsed.rows.single().lessonKey)
    }
}
