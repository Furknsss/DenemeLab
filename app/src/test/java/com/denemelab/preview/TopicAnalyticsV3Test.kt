package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class TopicAnalyticsV3Test {
    private fun exam(
        id: Long,
        topics: List<ExamTopicIssue> = emptyList(),
        outcomes: List<TopicOutcome> = emptyList(),
        netCorrect: Int = 70,
        category: String = "Genel",
        examType: String = "TYT",
        track: String = ""
    ): ExamRecord = ExamRecord(
        id=id,name="Test $id",category=category,examType=examType,
        subject="",correct=netCorrect,wrong=20,blank=30-netCorrect+70-70,createdAt=id,
        track=track,examDate=id,topicIssues=topics,topicOutcomes=outcomes
    ).copy(blank=120-netCorrect-20)

    private fun issue(id:String, kind:String, count:Int, reason:String="Bilgi eksikliği") =
        ExamTopicIssue("tyt_mat","Matematik",id,kind,count,reason)

    @Test fun topicRepeatedInLastFiveHasRealCountsAndStreak() {
        val exams = listOf(
            exam(1,listOf(issue("Üslü Sayılar","Yanlış",2))),
            exam(2,listOf(issue("Üslü Sayılar","Yanlış",1))),
            exam(3,listOf(issue("Problemler","Boş",2))),
            exam(4,listOf(issue("Üslü Sayılar","Yanlış",1))),
            exam(5,listOf(issue("Üslü Sayılar","Boş",1)))
        )
        val result=TopicAnalyticsV3.analyze(exams,AnalysisScope("Genel","TYT"))
        val t=result.risks.first { it.topic=="Üslü Sayılar" }
        assertEquals(4,t.wrong)
        assertEquals(1,t.blank)
        assertEquals(4,t.seenInExams)
        assertEquals(5,t.taggedExamCount)
        assertEquals(2,t.consecutiveTaggedExams)
        assertEquals(1.2,t.referenceLossPerExam,0.000001)
        assertEquals("Bilgi eksikliği",t.commonReason)
    }

    @Test fun scenarioIsObservedAverageNotWrongCountOrFalsePrediction() {
        val exams=(1L..5L).map { exam(it,listOf(issue("Üslü Sayılar","Yanlış",1))) }
        val x=TopicAnalyticsV3.analyze(exams,AnalysisScope("Genel","TYT"))
        assertEquals(1.25,x.theoreticalTaggedGain,0.00001)
        assertEquals(0.625,x.scenarioGain(50),0.00001)
        assertEquals(1.25,x.scenarioGain(100),0.00001)
        assertEquals(0.0,x.scenarioGain(0),0.00001)
        assertEquals(1.25,x.scenarioGain(150),0.00001)
        assertEquals("Matematik",x.subjects.first().name)
    }

    @Test fun noTaggedErrorsDoesNotCreateFalseStrongTopicsOrGains() {
        val exams=(1L..5L).map { exam(it) }
        val x=TopicAnalyticsV3.analyze(exams,AnalysisScope("Genel","TYT"))
        assertEquals(0,x.taggedExamCount)
        assertTrue(x.risks.isEmpty())
        assertTrue(x.strengths.isEmpty())
        assertEquals(0.0,x.scenarioGain(100),0.00001)
    }

    @Test fun explicitCorrectEvidenceAcrossExamsCanProduceStrongTopic() {
        val positive=TopicOutcome("tyt_mat","Matematik","Üslü Sayılar",5,0,0)
        val exams=listOf(exam(1,outcomes=listOf(positive)),exam(2,outcomes=listOf(positive)))
        val x=TopicAnalyticsV3.analyze(exams,AnalysisScope("Genel","TYT"))
        assertEquals(1,x.strengths.size)
        assertEquals("Üslü Sayılar",x.strengths.first().topic)
        assertEquals(1.0,x.strengths.first().accuracy,0.00001)
    }

    @Test fun oneExamNeverEstablishesStrongTopic() {
        val positive=TopicOutcome("tyt_mat","Matematik","Üslü Sayılar",10,0,0)
        val x=TopicAnalyticsV3.analyze(listOf(exam(1,outcomes=listOf(positive))),
            AnalysisScope("Genel","TYT"))
        assertTrue(x.strengths.isEmpty())
    }

    @Test fun preventsOverlappingOrInventedPositiveTopicEvidence() {
        val parts=listOf(SubjectScore("tyt_mat","Matematik",30,8,2))
        assertFalse(TopicAnalyticsV3.validOutcomes(listOf(
            TopicOutcome("tyt_mat","Matematik","Üslü",9,0,0)),parts))
        assertFalse(TopicAnalyticsV3.validOutcomes(listOf(
            TopicOutcome("tyt_mat","Matematik","Üslü",4,0,0),
            TopicOutcome("tyt_mat","Matematik","üslü",4,0,0)),parts))
        assertTrue(TopicAnalyticsV3.validOutcomes(listOf(
            TopicOutcome("tyt_mat","Matematik","Üslü",4,1,0)),parts))
    }

    @Test fun mixedScopesNeverCompareTytAndAytOrDifferentTracks() {
        val i=issue("Üslü Sayılar","Yanlış",1)
        val x=TopicAnalyticsV3.analyze(listOf(
            exam(1,listOf(i)),exam(2,listOf(i),examType="AYT",track="SAY"),
            exam(3,listOf(i),examType="AYT",track="EA")),
            AnalysisScope("Genel","TYT"))
        assertEquals(1,x.cohortCount)
        assertEquals(1,x.risks.first().seenInExams)
    }

    @Test fun scenarioSubjectRowsSumToSameCappedTotal() {
        val exams=(1L..5L).map { id ->
            exam(id,listOf(issue("Üslü Sayılar","Yanlış",3),
                ExamTopicIssue("tyt_turkce","Türkçe","Paragraf","Boş",2)))
        }
        val x=TopicAnalyticsV3.analyze(exams,AnalysisScope("Genel","TYT"))
        for (pct in listOf(0,25,50,75,100)) {
            assertEquals(x.scenarioGain(pct),
                x.scenarioBySubject(pct).sumOf { it.second },0.000001)
        }
    }

    @Test fun positiveTopicAndDifficultyPersistAcrossExamJson() {
        val e=exam(19,outcomes=listOf(
            TopicOutcome("tyt_mat","Matematik","Üslü Sayılar",4,1,0)
        )).copy(difficulty="Orta-Zor",parts=ExamStructure.subjectsForEntry("TYT","")
            .map { t -> if(t.key=="tyt_mat") SubjectScore(t.key,t.title,t.questions,4,1)
                else SubjectScore(t.key,t.title,t.questions,0,0) },
            correct=4,wrong=1,blank=115)
        val decoded=ExamJsonCodec.decode(ExamJsonCodec.encode(listOf(e))).single()
        assertEquals(e,decoded)
        assertEquals("Orta-Zor",decoded.difficulty)
        assertEquals(1,decoded.topicOutcomes.size)
    }

    @Test fun countReasonUsesOnlyIdentifiedWrong() {
        val exams=listOf(exam(1,listOf(
            issue("Üslü","Yanlış",2,"Dikkat hatası"),
            issue("Problemler","Yanlış",1,"Belirtilmedi"),
            issue("Problemler","Boş",3,"Süre yetersizliği")
        )))
        val x=TopicAnalyticsV3.analyze(exams,AnalysisScope("Genel","TYT"))
        assertEquals(1,x.reasons.size)
        assertEquals(2,x.reasons.single().count)
        assertEquals("Dikkat hatası",x.reasons.single().reason)
    }
}
