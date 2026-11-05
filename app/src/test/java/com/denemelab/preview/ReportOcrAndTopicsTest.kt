package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class ReportOcrAndTopicsTest {
    private val sample = """
        SONUÇ BELGESİ
        3D TYT TG 1.SAYI(26-27)
        Ders Soru Doğru Yanlış Net
        Türkçe 40 32 6 30,50 76
        Tarih-1 5 4 1 3,75 75
        Coğrafya-1 5 4 1 3,75 75
        Felsefe 5 4 1 3,75 75
        Din Kül. ve Ahl. Bil. 5 4 1 3,75 75
        Felsefe (Seçmeli) 5 0 0 0,00
        TYT Sosyal 20 16 4 15,00
        Matematik-1 30 18 2 17,50
        Geometri 10 2 1 1,75
        TYT Matematik 40 20 3 19,25
        Fizik 7 4 3 3,25
        Kimya 7 4 3 3,25
        Biyoloji 6 2 3 1,25
        TYT Fen 20 10 9 7,75
        Toplam 120 78 22 72,50
        DERSLERE GÖRE ANALİZ
        TYT Türkçe
        Paragrafta Ana Düşünce / Ana Fikir 6 3 2 50
        Paragrafta Yardımcı Düşünceler 7 6 1 86
        TYT Matematik
        Matematik-1
        Denklem ve Eşitsizlikler 10 6 4 60
        TYT Fen
        Fizik
        Fiziksel Niceliklerin Sınıflandırılması 1 0 1 0
    """.trimIndent()

    @Test fun fullSampleScansOnlyActual120Questions() {
        val r = ReportTableParser.parse(sample)
        assertEquals(10, r.counts.size)
        assertEquals(32 to 6, r.counts["tyt_turkce"])
        assertEquals(4 to 1, r.counts["tyt_tarih"])
        assertEquals(18 to 2, r.counts["tyt_mat"])
        assertEquals(2 to 1, r.counts["tyt_geo"])
        assertEquals(4 to 3, r.counts["tyt_fizik"])
        assertEquals(2 to 3, r.counts["tyt_biyoloji"])
        val templates = ExamStructure.subjectsForEntry("TYT", "")
        val scores = templates.map { t ->
            val values = r.counts.getValue(t.key)
            SubjectScore(t.key, t.title, t.questions, values.first, values.second)
        }
        assertTrue(ExamStructure.valid(scores, "TYT", ""))
        assertEquals(120, scores.sumOf { it.questions })
        assertEquals(78, scores.sumOf { it.correct })
        assertEquals(22, scores.sumOf { it.wrong })
        assertEquals(20, scores.sumOf { it.blank })
        assertEquals(72.5, scores.sumOf { it.net }, 1e-5)
    }

    @Test fun partiallyRecognizedReportNeverPretendsToBeComplete() {
        val scanned = ReportTableParser.parse("Türkçe 40 22 8 20,00\nFizik 7 5 1 4,75")
        assertEquals(2, scanned.foundCount)
        assertTrue(scanned.warnings.any { it.contains("okunamadı") })
        assertEquals(22 to 8, scanned.counts["tyt_turkce"])
    }

    @Test fun topicCountsNeverExceedWrongOrBlankTotals() {
        val entry = SubjectScore("tyt_turkce", "Türkçe", 40, 32, 6)
        val wrong = ExamTopicIssue(entry.key, entry.title, "Paragraf", "Yanlış", 4, "Dikkat hatası")
        assertNull(TopicBreakdown.validate(listOf(wrong), listOf(entry)))
        assertNotNull(TopicBreakdown.validate(listOf(wrong.copy(count=7)), listOf(entry)))
        assertNotNull(TopicBreakdown.validate(listOf(wrong.copy(kind="Boş",count=3)), listOf(entry)))
        assertNotNull(TopicBreakdown.validate(listOf(wrong.copy(reason="Bilinmeyen")), listOf(entry)))
    }

    @Test fun photoOcrTopicHintsNeverInventReason() {
        val parsed = ReportTableParser.parse(sample)
        assertTrue(parsed.topics.isNotEmpty())
        assertTrue(parsed.topics.all { it.reason == "Belirtilmedi" })
        assertTrue(parsed.topics.all { it.provenance == "Belge OCR" })
        val parts = ExamStructure.subjectsForEntry("TYT", "").map { t ->
            val (c,w) = parsed.counts.getValue(t.key)
            SubjectScore(t.key,t.title,t.questions,c,w)
        }
        assertNull(TopicBreakdown.validate(parsed.topics,parts))
    }

    @Test fun examTopicDataRoundTripPreservesOldRecords() {
        val exam = ExamRecord(10,"Deneme","Genel","TYT","",32,6,82,10,
            parts = emptyList(), topicIssues = emptyList(),source="Manuel")
        assertEquals(exam,ExamJsonCodec.decode(ExamJsonCodec.encode(listOf(exam))).single())
        val scores = ExamStructure.subjectsForEntry("TYT","").map {
            SubjectScore(it.key,it.title,it.questions,0,0)
        }
        val detail = listOf(ExamTopicIssue("tyt_turkce","Türkçe","Paragraf","Boş",1))
        val changed = exam.copy(correct=0,wrong=0,blank=120,parts=scores,topicIssues=detail,
            source="Belge OCR + kullanıcı onayı")
        assertEquals(changed,ExamJsonCodec.decode(ExamJsonCodec.encode(listOf(changed))).single())
    }
}
