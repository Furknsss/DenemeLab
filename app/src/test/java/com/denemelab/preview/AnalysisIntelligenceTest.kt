package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class AnalysisIntelligenceTest {
    private fun exam(id: Long, correct: Int, wrong: Int = 0) =
        ExamRecord(id,"TYT $id","Genel","TYT","",correct,wrong,120-correct-wrong,id,
            examDate=id)
    @Test fun insufficientDataIsNotCalledStable() {
        val s=AnalysisIntelligence.stability(listOf(exam(1,80),exam(2,81)))
        assertNull(s.spread)
        assertEquals("Veri birikiyor",s.status)
    }
    @Test fun consistentScoresAreStable() {
        val s=AnalysisIntelligence.stability(listOf(exam(1,80),exam(2,81),exam(3,79)))
        assertEquals("İstikrarlı",s.status)
        assertEquals(80.0,s.average!!,0.0001)
    }
    @Test fun lossRankingUsesTheoreticalPotentialNotFakePrediction() {
        val r=AnalysisIntelligence.lossRanking(listOf(
            SubjectInsight("Türkçe",40,35,5,0),
            SubjectInsight("Matematik",40,10,0,30)
        ))
        assertEquals("Matematik",r.first().lesson)
        assertEquals(30.0,r.first().lostPotential,0.0001)
    }
    @Test fun adviceDiffersByReason() {
        assertNotEquals(AnalysisIntelligence.prescription("Dikkat hatası"),
            AnalysisIntelligence.prescription("Bilgi eksikliği"))
    }
}
