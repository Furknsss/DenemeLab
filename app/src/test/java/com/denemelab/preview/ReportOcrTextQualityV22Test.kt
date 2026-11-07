package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class ReportOcrTextQualityV22Test {
    @Test fun completeSmallPrintCountsBeatLongerBrokenOCRLine() {
        val complete = "Sözcükte Anlam 2 1 1 50"
        val noisy = "Sözcükte Anlam 2 1 1 50 gereksiz satırdaki ikinci metin"
        assertTrue(ReportOcrTextQualityV22.score(complete) >
            ReportOcrTextQualityV22.score(noisy))
    }

    @Test fun compactNumberColumnStillCountsAsValid() {
        assertTrue(ReportOcrTextQualityV22.score("Rutin Olmayan Problemler 41025") >
            ReportOcrTextQualityV22.score("Rutin Olmayan Problemler 410251234x"))
    }

    @Test fun inconsistentCountsCannotBeTrustedMerelyBecauseTheyAreLong() {
        assertTrue(ReportOcrTextQualityV22.score("Konu A 2 1 1 50") >
            ReportOcrTextQualityV22.score(
                "Yüksek sayıdaki hatalı ama uzun kazanım adı 2 5 2 120"))
    }

    @Test fun zeroWrongAndZeroBlankMeansSuccessOnlyWhenActualCorrectCountRead() {
        assertTrue(ReportOcrTextQualityV22.score("Konu B 1 1 0 100") > 400)
        assertTrue(ReportOcrTextQualityV22.score("Konu B 1 0 0 0") > 400)
    }
}
