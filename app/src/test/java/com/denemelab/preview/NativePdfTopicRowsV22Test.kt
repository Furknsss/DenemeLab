package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class NativePdfTopicRowsV22Test {
    @Test fun oneHundredTwentyPrintedQuestionsAreNotDiscardedByFuzzyOcrReconciliation() {
        val lessons=listOf(
            Triple("Turkce S D Y B%", "tyt_turkce", 40),
            Triple("Tarih-1 S D Y B%", "tyt_tarih", 5),
            Triple("Cografya-1 S D Y B%", "tyt_cografya", 5),
            Triple("Felsefe S D Y B%", "tyt_felsefe", 5),
            Triple("Din Kulturu S D Y B%", "tyt_din", 5),
            Triple("Matematik-1 S D Y B%", "tyt_mat", 30),
            Triple("Geometri S D Y B%", "tyt_geo", 10),
            Triple("Fizik S D Y B%", "tyt_fizik", 7),
            Triple("Kimya S D Y B%", "tyt_kimya", 7),
            Triple("Biyoloji S D Y B%", "tyt_biyoloji", 6)
        )
        val scores=mapOf(
            "tyt_turkce" to (30 to 7),
            "tyt_tarih" to (4 to 1),
            "tyt_cografya" to (4 to 1),
            "tyt_felsefe" to (4 to 1),
            "tyt_din" to (4 to 0),
            "tyt_mat" to (22 to 5),
            "tyt_geo" to (6 to 2),
            "tyt_fizik" to (5 to 1),
            "tyt_kimya" to (5 to 1),
            "tyt_biyoloji" to (4 to 1)
        )
        val lines=buildList {
            lessons.forEachIndexed { index, (title,key,questions) ->
                add(title)
                val counts=scores.getValue(key)
                repeat(questions) { j ->
                    val correct=if(j<counts.first) 1 else 0
                    val wrong=if(j>=counts.first && j<counts.first+counts.second) 1 else 0
                    add("Konu_"+(index+1)+"_"+(j+1)+" 1 "+correct+" "+wrong+
                        " "+if(correct==1) "100" else "0")
                }
            }
        }
        assertEquals(130,lines.size)
        val actual=NativePdfTopicRowsV22.parse(lines,scores)
        assertEquals(120,actual.recognizedRows)
        assertEquals(120,actual.outcomes.sumOf { it.questions })
        assertEquals(0,actual.unresolvedRows)
        val parts=ExamStructure.tytDetailed.map {
            val c=scores.getValue(it.key)
            SubjectScore(it.key,it.title,it.questions,c.first,c.second)
        }
        assertTrue(ReportCoverage.inspect(parts,actual.outcomes).complete)
    }

    @Test fun invalidNumbersRemainUnverified() {
        val lines=listOf("Matematik-1 S D Y B%",
            "Problemler 2 3 0 150", "Koklu sayilar 1 1 0 100")
        val result=NativePdfTopicRowsV22.parse(lines,mapOf("tyt_mat" to (1 to 0)))
        assertEquals(1,result.outcomes.sumOf { it.questions })
        assertEquals(1,result.unresolvedRows)
    }

    @Test fun noHeadingDoesNotGuessCourseIdentity() {
        val result=NativePdfTopicRowsV22.parse(
            listOf("Problemler 1 1 0 100"),
            mapOf("tyt_mat" to (1 to 0)))
        assertTrue(result.outcomes.isEmpty())
    }
}
