package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

/**
 * Anonymized reference from the supplied Bilgi Sarmal TYT TG 1 screenshot.
 * Does NOT contain a student name, number, school or answer strings.
 * The supplied image ends after 4 chemistry rows; 3 chemistry and all 6
 * biology questions are physically absent from its topic table.
 */
class BilgiSarmalReportRegressionTest {
    private val summary = """
        Türkçe 40 28 11 25,25
        Tarih-1 5 5 0 5,00
        Coğrafya-1 5 2 3 1,25
        Felsefe 5 4 1 3,75
        Din Kül. ve Ahl. Bil. 5 4 0 4,00
        Felsefe (Seçmeli) 5 0 0 0,00
        TYT Sosyal 20 15 4 14,00
        Matematik-1 30 12 4 11,00
        Geometri 10 4 0 4,00
        TYT Matematik 40 16 4 15,00
        Fizik 7 5 2 4,50
        Kimya 7 3 4 2,00
        Biyoloji 6 4 1 3,75
        TYT Fen 20 12 7 10,25
        Toplam: 120 71 26 64,50
    """.trimIndent()

    // One topic row = visible label | S | D | Y. The printed B% is derived
    // only for this cross-checked TEST fixture (never for live unknown OCR).
    private val topics = """
        TYT Türkçe
        Türkçe S D Y B%
        Sözcükte Anlam|2|1|1
        Metne Sözcük Yerleştirme|1|0|1
        Söz Öbeklerinde Anlam|1|1|0
        Cümleyi Yorumlama ve Açıklama|1|1|0
        İki Cümleyi Karşılaştırma|1|1|0
        Parçadaki Cümlelerin İçerik ve Özellikleri|1|1|0
        Cümlenin Ögeleri Karma|1|0|1
        Bitişik ve Ayrı Yazılan Kelimeler|2|2|0
        Noktalama İşaretleri Karma|2|1|1
        Kök ve Kök Çeşitleri|1|0|1
        Fiilimsiler Karma|1|1|0
        Ad Soylu Sözcükler Karma|1|0|0
        Paragrafı İkiye Ayırma|1|1|0
        Paragrafta Anlatım Akışını Bozan Cümle|1|0|1
        Paragrafta Yardımcı Düşünceler|5|5|0
        Paragrafa Özgü Soru Kökü|2|1|1
        Paragrafta Boş Bırakılan Yerleri Tamamlama|1|1|0
        Paragrafta Ana Düşünce|4|3|1
        Paragrafa Cümle Yerleştirme|1|1|0
        Diyaloğun Yorumlanması|1|0|1
        Anlam ve Dil Bilgisiyle İlgili Ayrıntılar|1|0|1
        Paragrafın Yorumlanması|2|1|1
        Bir Paragrafa Bağlı Çoklu Sorular|6|6|0
        TYT Sosyal
        Tarih-1 S D Y B%
        İlk Türklerin coğrafyaları ve hayat tarzları|1|1|0
        Büyük Selçuklu yönetim ve toplum yapısı|1|1|0
        Osmanlı merkezi otorite|1|1|0
        Kuvayımilliye ve TBMM|1|1|0
        Milli Mücadele diplomatik başarıları|1|1|0
        Coğrafya-1 S D Y B%
        Yeryüzündeki İklim Tipleri|1|0|1
        Dünya'nın tektonik oluşumu|1|0|1
        Türkiye'de Nüfus|1|1|0
        Bölge örnekleri ve belirleme kriterleri|1|1|0
        Afetlerin nedenleri ve özellikleri|1|0|1
        Felsefe S D Y B%
        Bilim felsefesinin konusu ve problemleri|1|1|0
        Felsefi düşüncenin özellikleri|1|1|0
        Varlık felsefesinin konusu ve problemleri|1|0|1
        Sanat felsefesinin konusu ve problemleri|1|1|0
        Ahlak felsefesinin konusu ve problemleri|1|1|0
        Din Kül. ve Ahl. Bil. S D Y B%
        İsim ve sıfatları ile Allah'ı tanır|1|1|0
        İslamda bilginin kaynakları|1|1|0
        İslam ahlakında yerilen davranışlar|1|0|0
        Hz Muhammed'in gençlik yıllarında erdemli davranışlar|1|1|0
        İslamda ibadetin amaçları|1|1|0
        Felsefe (Seçmeli) S D Y B%
        Seçmeli felsefe testi dahil edilmez|1|0|0
        TYT Matematik
        Matematik-1 S D Y B%
        Rasyonel Sayılar|1|0|1
        Köklü İfadeler|2|1|0
        Ondalık Sayılar|1|1|0
        Dört İşlem Yeteneği|1|1|0
        Aralık Kavramı ve Basit Eşitsizlikler|1|1|0
        MUTLAK DEĞER|1|1|0
        Tek ve Çift Sayılar|1|1|0
        Birinci Dereceden Bir Bilinmeyenli Denklemler|1|0|0
        Küme Kavramı ve Kümelerde İşlemler|1|0|0
        MANTIK|1|0|1
        Fonksiyon Grafikleri|1|1|0
        Bölme Kuralları|1|1|0
        Rutin Olmayan Problemler|4|1|0
        Sayı Basamakları|1|0|0
        VERİ|1|0|0
        Sayı Problemleri|2|0|0
        Kesir Problemleri|1|1|0
        Yüzde Kar Zarar Problemleri|1|0|0
        Oran ve Orantı|2|2|0
        Yaş Problemleri|1|0|1
        Grafik Problemleri|1|0|0
        PROBLEMLER|1|0|1
        PERMÜTASYON KOMBİNASYON BİNOM AÇILIMI|1|0|0
        OLASILIK|1|0|0
        Geometri S D Y B%
        Üçgende Açılar|1|1|0
        Üçgende Kenar Açı İlişkileri|1|0|0
        ÖZEL ÜÇGENLER|1|0|0
        Üçgenlerin Benzerliği|1|1|0
        Üçgende Alan|1|1|0
        Deltoid|1|0|0
        Kare|1|1|0
        Çokgenler|1|0|0
        Prizmalar|2|0|0
        TYT Fen
        Fizik S D Y B%
        Fiziksel Niceliklerin Sınıflandırılması|1|1|0
        Basınç|1|1|0
        İş Enerji ve Güç|1|1|0
        Newton'un Hareket Yasaları|1|1|0
        Elektrik Devreleri|1|1|0
        Küresel Aynalar|1|0|1
        Dalgalar|1|0|1
        Kimya S D Y B%
        Kimya Uygulamalarında İş Sağlığı ve Güvenliği|1|0|1
        Atomun Yapısı|1|1|0
        Kimyasal Türler ve Etkileşimler|1|0|1
        Hâl Değişimi|1|1|0
    """.trimIndent().lines().map { line ->
        val v = line.split('|')
        if (v.size != 4) line else {
            val n = v[1].toInt()
            val d = v[2].toInt()
            val y = v[3].toInt()
            val pct = 100 * d / n
            v[0] + " " + n + " " + d + " " + y + " " + pct
        }
    }

    @Test fun tenCourseTotalsMatchActualDocument() {
        val parsed = FlexibleReportTableParser.parse(summary.lines())
        assertEquals("Printed TYT has ten subtests", 10, parsed.values.size)
        val parts = ExamStructure.tytDetailed.map {
            val pair = parsed.values[it.key] ?: throw AssertionError("Missing " + it.key)
            SubjectScore(it.key, it.title, it.questions, pair.first, pair.second)
        }
        assertEquals(71, parts.sumOf { it.correct })
        assertEquals(26, parts.sumOf { it.wrong })
        assertEquals(23, parts.sumOf { it.blank })
        assertEquals(64.5, ExamRules.net(71, 26), 0.0001)
        assertEquals(28 to 11, parsed.values["tyt_turkce"])
        assertEquals(4 to 1, parsed.values["tyt_biyoloji"])
        assertEquals(12 to 4, parsed.values["tyt_mat"])
    }

    @Test fun croppedPhotoMayNeverClaimToRecognizeHiddenNineTopics() {
        val report = DetailedReportReconciliation.reconcile(
            summary, summary.lines(), topics
        )
        assertEquals("Ten course totals",10,report.counts.size)
        val parts = ExamStructure.tytDetailed.map {
            val score = report.counts.getValue(it.key)
            SubjectScore(it.key,it.title,it.questions,score.first,score.second)
        }
        val audit = ReportCoverage.inspect(parts, report.outcomes)
        assertTrue("Observed rows should not contradict subject totals", audit.valid)
        assertEquals("Visible topic table contains 111 of 120 questions", 111, audit.observed)
        assertEquals(9, audit.missing)
        assertFalse(audit.complete)
        assertEquals(3,audit.courses.first { it.key == "tyt_kimya" }.missing)
        assertEquals(6,audit.courses.first { it.key == "tyt_biyoloji" }.missing)
        assertTrue(report.outcomes.none { it.lessonKey == "tyt_biyoloji" })
        assertEquals(40, report.outcomes.filter { it.lessonKey == "tyt_turkce" }
            .sumOf { it.questions })
    }

    @Test fun tinyCompressedNumericColumnsAreParsedWithoutInventingScores() {
        assertEquals("Sözcükte Anlam 2 1 1 50",
            CompactTopicColumns.expand("Sözcükte Anlam 21150", 6))
        assertEquals("Fiilimsiler Karma 1 1 0 100",
            CompactTopicColumns.expand("Fiilimsiler Karma 110100", 6))
        assertEquals("Rutin Olmayan Problemler 4 1 0 25",
            CompactTopicColumns.expand("Rutin Olmayan Problemler 41025", 6))
        assertEquals("Ad Soylu Sözcükler Karma 1 0 0 0",
            CompactTopicColumns.expand("Ad Soylu Sözcükler Karma 1000", 6))
        // Broader publishers may have an 11-question topic, where 110100
        // admits more than one interpretation; never choose a random split.
        assertNull(CompactTopicColumns.expand("Fiilimsiler Karma 110100", 40))
        assertNull(CompactTopicColumns.expand("Hatalı kazanım 111100", 6))
    }

    @Test fun oneMissingLessonMayBeDerivedButMustBeMarkedUnverified() {
        val missingBiology = summary.lines()
            .filterNot { it.startsWith("Biyoloji 6 ") }.joinToString("\n")
        val report = DetailedReportReconciliation.reconcile(
            missingBiology, missingBiology.lines(), topics
        )
        assertEquals(10, report.counts.size)
        assertEquals(4 to 1, report.counts["tyt_biyoloji"])
        assertTrue(report.derivedLessons.contains("tyt_biyoloji"))
        assertTrue(report.ambiguousLessons.contains("tyt_biyoloji"))
        assertTrue(report.outcomes.none { it.lessonKey == "tyt_biyoloji" })
        assertTrue(report.warnings.any { it.contains("toplam",ignoreCase = true) })
        // An OCR-ed overall net inconsistent with 71D/26Y cannot infer.
        val wrongNet = missingBiology.replace("120 71 26 64,50", "120 71 26 65,50")
        val rejected = PublishedTotalReconciliation.completeOneCourse(
            wrongNet, FlexibleReportTableParser.parse(wrongNet.lines()).values)
        assertFalse(rejected.counts.containsKey("tyt_biyoloji"))
    }

    @Test fun mergedSmallFontColumnsStillRetainAllVisibleTopics() {
        val compact = topics.map {
            when {
                it.startsWith("Sözcükte Anlam ") ->
                    it.replace("2 1 1 50", "21150")
                it.startsWith("Fiilimsiler Karma ") ->
                    it.replace("1 1 0 100", "110100")
                it.startsWith("Rutin Olmayan Problemler ") ->
                    it.replace("4 1 0 25", "41025")
                it.startsWith("Ad Soylu Sözcükler Karma ") ->
                    it.replace("1 0 0 0", "1000")
                else -> it
            }
        }
        val report = DetailedReportReconciliation.reconcile(
            "BİLGİ SARMAL TYT TG 1\n" + summary, summary.lines(), compact)
        val parts = ExamStructure.tytDetailed.map {
            val score = report.counts.getValue(it.key)
            SubjectScore(it.key,it.title,it.questions,score.first,score.second)
        }
        assertTrue(ReportCoverage.inspect(parts,report.outcomes).valid)
        assertEquals(111, ReportCoverage.inspect(parts,report.outcomes).observed)
        assertTrue(report.warnings.any { it.contains("bitişik", ignoreCase = true) })
    }

    @Test fun optionalPhilosophyAndGroupTotalsAreNotDoubleCounted() {
        val parsed = FlexibleReportTableParser.parse(summary.lines())
        assertEquals(10,parsed.values.size)
        assertEquals(5 to 0, parsed.values["tyt_tarih"])
        assertEquals(4 to 1, parsed.values["tyt_felsefe"])
    }
}
