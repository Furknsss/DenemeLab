package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class DetailedReportOcrTest {
    private val left = listOf(
        "Türkçe 40 32 6 30,50",
        "Tarih-1 5 4 1 3,75",
        "Coğrafya-1 5 4 1 3,75",
        "Felsefe 5 4 1 3,75",
        "Din Kül. ve Ahl. Bil. 5 4 1 3,75",
        "Felsefe (Seçmeli) 5 0 0 0,00",
        "TYT Sosyal 20 16 4 15,00",
        "Matematik-1 30 18 2 17,50",
        "Geometri 10 2 1 1,75",
        "TYT Matematik 40 20 3 19,25",
        "Fizik 7 4 3 3,25",
        "Kimya 7 4 3 3,25",
        "Biyoloji 6 2 3 1,25",
        "TYT Fen 20 10 9 7,75",
        "Toplam 120 78 22 72,50"
    )

    @Test fun scannedCourseTableIsCompleteAndKeepsReal72Point5Net() {
        val right = listOf(
            "TYT Türkçe",
            "Sözcükler Arası Anlam İlişkileri 1 1 0 100",
            "Ses Bilgisi - Karma 1 0 1 0",
            "Bu Konu Boş Bırakıldı 1 0 0 0",
            "TYT Matematik",
            "Matematik-1",
            "Denklem ve Eşitsizlikler 10 6 4 60",
            "TYT Fen",
            "Fizik",
            "Fiziksel Niceliklerin Sınıflandırılması 1 1 0 100"
        )
        val result = DetailedReportReconciliation.reconcile(
            "3D TYT TG 1.SAYI(26-27)\n" + left.joinToString("\n"),
            left, right
        )
        assertEquals(10, result.foundCount)
        assertEquals(32 to 6, result.counts["tyt_turkce"])
        assertEquals(4 to 3, result.counts["tyt_fizik"])
        val subjects = ExamStructure.subjectsForEntry("TYT", "").map {
            val (correct,wrong) = result.counts.getValue(it.key)
            SubjectScore(it.key, it.title, it.questions, correct,wrong)
        }
        assertEquals(120, subjects.sumOf { it.questions })
        assertEquals(78, subjects.sumOf { it.correct })
        assertEquals(22, subjects.sumOf { it.wrong })
        assertEquals(72.5, subjects.sumOf { it.net }, .001)
        assertTrue(result.outcomes.any {
            it.topic == "Bu Konu Boş Bırakıldı" &&
            it.correct == 0 && it.wrong == 0 && it.blank == 1
        })
        assertTrue(result.topics.any {
            it.topic == "Bu Konu Boş Bırakıldı" &&
            it.kind == "Boş" && it.count == 1
        })
        assertTrue(result.outcomes.any {
            it.topic == "Ses Bilgisi - Karma" &&
            it.correct == 0 && it.wrong == 1
        })
        assertNull(TopicBreakdown.validate(result.topics, subjects))
        assertTrue(TopicAnalyticsV3.validOutcomes(result.outcomes, subjects))
        assertTrue(result.validatedTopicQuestions >= 3)
    }

    @Test fun rightmostColumnIsSuccessPercentageNotBlankCount() {
        val result = DetailedReportReconciliation.reconcile(
            left.joinToString("\n"), left,
            listOf("TYT Türkçe", "Sözcüklerde Anlam 2 1 1 50"))
        val row = result.outcomes.first { it.topic == "Sözcüklerde Anlam" }
        assertEquals(2, row.questions)
        assertEquals(1, row.correct)
        assertEquals(1, row.wrong)
        assertEquals(0, row.blank)
    }

    @Test fun inconsistentOcrPercentageIsNotTrusted() {
        val result = DetailedReportReconciliation.reconcile(
            left.joinToString("\n"), left,
            listOf("TYT Türkçe", "Hatalı Okunan Başlık 1 0 1 100"))
        assertTrue(result.outcomes.none { it.topic == "Hatalı Okunan Başlık" })
        assertTrue(result.warnings.any { it.contains("tutarsız") })
    }

    @Test fun twoOcrPassesThatDisagreeNeedExplicitReview() {
        val alternate = left.toMutableList()
        alternate[0] = "Türkçe 40 30 7 28,25"
        val result = DetailedReportReconciliation.reconcile(left.joinToString("\n"),
            alternate, listOf("TYT Türkçe"))
        assertTrue(result.ambiguousLessons.contains("tyt_turkce"))
        assertEquals(30 to 7, result.counts["tyt_turkce"])
    }

    @Test fun unreadableTinyRowsRemainUnverified() {
        val result = DetailedReportReconciliation.reconcile(
            "TYT Türkçe", emptyList(),
            listOf("TYT Türkçe", "Küçük Konu 1 0 1 0"))
        assertTrue(result.counts.isEmpty())
        assertTrue(result.outcomes.isEmpty())
        assertTrue(result.warnings.any { it.contains("okunamadı") })
    }

    @Test fun tinyMissingPercentageCanBeReconstructedButIsFlagged() {
        val result = DetailedReportReconciliation.reconcile(
            left.joinToString("\n"), left,
            listOf("TYT Türkçe", "Sözcükte Anlam İlişkileri 2 1 1"))
        assertTrue(result.outcomes.any {
            it.topic == "Sözcükte Anlam İlişkileri" &&
            it.correct == 1 && it.wrong == 1 && it.blank == 0
        })
        assertTrue(result.warnings.any { it.contains("B% sütunu okunamadı") })
    }

    @Test fun optionalPhilosophyIsNeverMixedInto120Questions() {
        val result = DetailedReportReconciliation.reconcile(
            left.joinToString("\n"), left,
            listOf("TYT Sosyal", "Felsefe (Seçmeli)",
                "MS 2. yüzyıl - MS 15. yüzyıl felsefesi 1 0 0 0",
                "TYT Fen", "Fizik", "Hareket 1 1 0 100")
        )
        assertTrue(result.outcomes.none { it.lessonName.contains("Seçmeli") })
        assertTrue(result.outcomes.any { it.lessonKey == "tyt_fizik" })
    }
}
