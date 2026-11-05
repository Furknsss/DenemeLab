package com.denemelab.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamRulesTest {
    @Test fun tyTNetCalculation() {
        assertEquals(75.0, ExamRules.net(80, 20), 0.000001)
        assertEquals(-1.25, ExamRules.net(0, 5), 0.000001)
        assertEquals(0.0, ExamRules.net(0, 0), 0.000001)
    }

    @Test fun validGeneralTyT() {
        assertTrue(ExamRules.validCounts("Genel", "TYT", "", 80, 20, 20))
        assertFalse(ExamRules.validCounts("Genel", "TYT", "", 80, 20, 0))
        assertFalse(ExamRules.validCounts("Genel", "TYT", "", -1, 1, 120))
    }

    @Test fun validGeneralAyt() {
        assertTrue(ExamRules.validCounts("Genel", "AYT", "", 55, 15, 10))
        assertFalse(ExamRules.validCounts("Genel", "AYT", "", 55, 15, 50))
        assertFalse(ExamRules.validCounts("Genel", "UNKNOWN", "", 55, 15, 10))
    }

    @Test fun validBranchNeedsSubject() {
        assertTrue(ExamRules.validCounts("Branş", "TYT", "Matematik", 25, 10, 5))
        assertFalse(ExamRules.validCounts("Branş", "TYT", "", 25, 10, 5))
        assertFalse(ExamRules.validCounts("Branş", "TYT", "Fizik", null, 0, 0))
        assertFalse(ExamRules.validCounts("Branş", "TYT", "Fizik", 0, 0, 0))
    }

    @Test fun sameCategoryAndSubjectOnly() {
        val generalTyt = sample("Genel", "TYT", "")
        val generalAyt = sample("Genel", "AYT", "")
        val math = sample("Branş", "TYT", "Matematik")
        val mathCaps = sample("Branş", "TYT", " MATEMATİK ")
        val physics = sample("Branş", "TYT", "Fizik")
        assertTrue(ExamRules.comparable(generalTyt, generalTyt))
        assertFalse(ExamRules.comparable(generalTyt, generalAyt))
        assertFalse(ExamRules.comparable(generalTyt, math))
        assertTrue(ExamRules.comparable(math, mathCaps))
        assertFalse(ExamRules.comparable(math, physics))
    }

    @Test fun twoCorrectRepeatsAndWrongReset() {
        assertEquals(1, ExamRules.nextCorrectStreak(0, true))
        assertEquals(2, ExamRules.nextCorrectStreak(1, true))
        assertEquals(2, ExamRules.nextCorrectStreak(2, true))
        assertEquals(0, ExamRules.nextCorrectStreak(1, false))
        assertEquals(0, ExamRules.nextCorrectStreak(2, false))
    }

    private fun sample(category: String, type: String, subject: String) =
        ExamRecord(1, "Test", category, type, subject, 20, 10, 10, 0)
}
