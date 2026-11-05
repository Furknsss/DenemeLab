package com.denemelab.preview

import org.junit.Assert.*
import org.junit.Test

class AnalysisEngineTest {
    private fun exam(
        id: Long, date: Long,
        category: String = "Genel", type: String = "TYT",
        track: String = "", subject: String = "",
        correct: Int = 80, wrong: Int = 20, blank: Int = 20,
        parts: List<SubjectScore> = emptyList(),
        issues: List<ExamTopicIssue> = emptyList()
    ) = ExamRecord(
        id, "Deneme $id", category, type, subject,
        correct, wrong, blank, date,
        track = track, parts = parts,
        examDate = date, topicIssues = issues
    )

    @Test fun tytNeverMixesWithAytAndAytTracksNeverMix() {
        val all = listOf(
            exam(1, 1000),
            exam(2, 2000, type = "AYT", track = "SAY"),
            exam(3, 3000, type = "AYT", track = "EA"),
            exam(4, 4000, type = "AYT", track = "SAY"),
            exam(5, 5000, type = "AYT", track = "")
        )
        val tyt = AnalysisEngine.analyze(all, AnalysisScope("Genel", "TYT"))
        assertEquals(listOf(1L),tyt.history.map { it.id })
        val say = AnalysisEngine.analyze(all, AnalysisScope("Genel", "AYT","SAY"))
        assertEquals(listOf(2L, 4L),say.history.map { it.id })
        assertEquals(1,AnalysisEngine.analyze(all,AnalysisScope("Genel","AYT","EA")).history.size)
        assertEquals(1,AnalysisEngine.analyze(all,AnalysisScope("Genel","AYT","")).history.size)
    }

    @Test fun branchedSubjectsAreIsolatedByNameAndType() {
        val items = listOf(
            exam(1, 1000, "Branş", "TYT", subject = "Matematik"),
            exam(2, 2000, "Branş", "TYT", subject = "Türkçe"),
            exam(3, 3000, "Branş", "AYT", subject = "Matematik"),
            exam(4, 4000, "Branş", "TYT", subject = "matematik")
        )
        assertEquals(listOf(1L,4L),AnalysisEngine.analyze(items,
            AnalysisScope("Branş","TYT","Matematik")).history.map { it.id })
        assertEquals(3,AnalysisEngine.scopes(items,"Branş").size)
        assertEquals(1,AnalysisEngine.analyze(items,
            AnalysisScope("Branş","AYT","Matematik")).history.size)
    }

    @Test fun noFabricatedTrendFromOnlyOneOrTwoExams() {
        val one = AnalysisEngine.analyze(listOf(exam(1,1000)),AnalysisScope("Genel","TYT"))
        assertEquals(1,one.history.size)
        assertNull(one.previousAverage)
        assertNull(one.mostRecentDelta)
        assertTrue(one.priorities.isEmpty())
        assertTrue(one.subjectInsights.isEmpty())
        val two = AnalysisEngine.analyze(listOf(exam(1,1000),exam(2,2000,correct=90)),
            AnalysisScope("Genel","TYT"))
        assertEquals(10.0,two.mostRecentDelta!!,0.00001)
        assertNull(two.trendDelta)
    }

    @Test fun movingAveragesUseDisjointThreeAndTwoWindows() {
        val all = (1..5).map { number ->
            exam(number.toLong(),number.toLong()*1000,correct=60+number*5,wrong=0,blank=0)
        }
        val snap=AnalysisEngine.analyze(all,AnalysisScope("Genel","TYT"))
        assertEquals(80.0,snap.average!!,0.00001) // 75,80,85
        assertEquals(67.5,snap.previousAverage!!,0.00001) // 65,70
        assertEquals(12.5,snap.trendDelta!!,0.00001)
        assertEquals(65.0,snap.minNet!!,0.00001)
        assertEquals(85.0,snap.maxNet!!,0.00001)
    }

    @Test fun tytOldFourAndNewTenSubjectsReconcileExactly() {
        val oldParts=ExamStructure.tyt.map {
            SubjectScore(it.key,it.title,it.questions,it.questions/2,0)
        }
        val newParts=ExamStructure.tytDetailed.map {
            SubjectScore(it.key,it.title,it.questions,it.questions/2,0)
        }
        val old=exam(1,1000,correct=oldParts.sumOf{it.correct},
            wrong=0,blank=120-oldParts.sumOf{it.correct},parts=oldParts)
        val new=exam(2,2000,correct=newParts.sumOf{it.correct},
            wrong=0,blank=120-newParts.sumOf{it.correct},parts=newParts)
        val snap=AnalysisEngine.analyze(listOf(new,old),AnalysisScope("Genel","TYT"))
        assertEquals(4,snap.subjectInsights.size)
        assertEquals(120,snap.subjectInsights.sumOf{it.questions})
        assertEquals(setOf("Türkçe","Sosyal Bilimler","Matematik","Fen Bilimleri"),
            snap.subjectInsights.map{it.name}.toSet())
        assertTrue(snap.subjectInsights.all{it.previousNet != null})
    }

    @Test fun unknownLegacySubjectDataIsNotInvented() {
        val old = exam(1,1000)
        val snap=AnalysisEngine.analyze(listOf(old),AnalysisScope("Genel","TYT"))
        assertTrue(snap.subjectInsights.isEmpty())
        assertNull(snap.subjectSource)
        assertEquals(0,snap.coverageExams)
    }

    @Test fun repeatedTopicHasPriorityOverOneOffLargeMistake() {
        val a = ExamTopicIssue("tyt_mat","Matematik","Problemler","Yanlış",2,"Bilgi eksikliği")
        val b = ExamTopicIssue("tyt_mat","Matematik","problemler","Boş",1)
        val big = ExamTopicIssue("tyt_fizik","Fizik","Basınç","Yanlış",5)
        val items = listOf(exam(3,3000,issues=listOf(b,big)),
            exam(2,2000,issues=listOf(a)),exam(1,1000))
        val snap=AnalysisEngine.analyze(items,AnalysisScope("Genel","TYT"))
        assertEquals("tyt_mat",snap.priorities.first().lessonKey)
        assertEquals(2,snap.priorities.first().recordedExams)
        assertEquals(2,snap.priorities.first().wrong)
        assertEquals(1,snap.priorities.first().blank)
        assertEquals(2,snap.latestTaggedExams)
    }

    @Test fun prioritiesOnlyReadFiveMostRecentComparableExams() {
        val issues = listOf(ExamTopicIssue("tyt_turkce","Türkçe","Paragraf","Yanlış",1))
        val six=(1..6).map{ i ->
            exam(i.toLong(),(i*1000).toLong(),issues=if(i==1||i==6)issues else emptyList())
        }
        val result=AnalysisEngine.analyze(six,AnalysisScope("Genel","TYT"))
        assertEquals(1,result.priorities.single().recordedExams)
        assertEquals(5,result.recent.size)
    }

    @Test fun lostScoreUpperBoundUsesCorrectPenaltyMath() {
        val subject=SubjectInsight("Fen",20,10,8,2)
        assertEquals(8.0,subject.net,0.00001)
        assertEquals(12.0,subject.theoreticalRoom,0.00001) // 8*1.25 + 2
        assertEquals(0.4,subject.fraction.toDouble(),0.00001)
    }

    @Test fun focusJournalRoundTripAndPostStudyCheckIsObservedNotForecast() {
        val scope=AnalysisScope("Genel","TYT")
        val priority=TopicPriority("tyt_mat|problemler","Problemler","tyt_mat","Matematik",
            3,1,2,1,"Bilgi eksikliği",1000)
        val rec=FocusCompletion(FocusJournal.focusId(scope,priority),
            "Problemler","Matematik",1500,1000,75.0,1)
        assertEquals(rec,FocusJournal.decode(FocusJournal.encode(listOf(rec))).single())
        val snap=AnalysisEngine.analyze(listOf(
            exam(1,1000,correct=80,wrong=20),
            exam(2,2000,correct=82,wrong=20)
        ),scope)
        assertEquals(2L,FocusJournal.nextExamAfter(rec,snap)?.id)
        assertEquals(77.0,FocusJournal.nextExamAfter(rec,snap)!!.net,0.00001)
        assertEquals(3,FocusJournal.plan("Bilgi eksikliği").size)
    }

    @Test fun noPostStudyExamIsNotPretended() {
        val scope=AnalysisScope("Genel","TYT")
        val rec=FocusCompletion("id","Konu","Ders",3000,1000,75.0,1)
        val snap=AnalysisEngine.analyze(listOf(exam(1,1000),exam(2,2000)),scope)
        assertNull(FocusJournal.nextExamAfter(rec,snap))
    }
}
