package com.denemelab.preview

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Capture ACTUAL rendered Compose analysis UI on Android 16.
 * Fixture data are synthetic; neither Firebase login nor the real user's
 * exam history are touched. These images are visual QA, not a data claim.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisScreenshotV24Test {
    @Test fun renderRealAndroidAnalysisAtTopAndScrolledPositions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dir = File(context.filesDir, "analysis-visual")
        assertTrue(dir.mkdirs() || dir.isDirectory)

        val details = ExamStructure.tytDetailed.map { lesson ->
            val (correct, wrong) = when (lesson.key) {
                "tyt_turkce" -> 28 to 11
                "tyt_tarih" -> 5 to 0
                "tyt_cografya" -> 2 to 3
                "tyt_felsefe" -> 4 to 1
                "tyt_din" -> 4 to 0
                "tyt_mat" -> 12 to 4
                "tyt_geo" -> 4 to 0
                "tyt_fizik" -> 5 to 2
                "tyt_kimya" -> 3 to 4
                "tyt_biyoloji" -> 4 to 1
                else -> 0 to 0
            }
            SubjectScore(lesson.key, lesson.title, lesson.questions, correct, wrong)
        }
        val now = System.currentTimeMillis()
        val repeated = TopicOutcome("tyt_mat","Matematik","Rutin Olmayan Problemler",0,2,1)
        val latest = ExamRecord(
            id=101, name="TG TYT-1", category="Genel", examType="TYT",
            subject="", correct=71, wrong=26, blank=23,
            createdAt=now, examDate=now, track="SAY",
            publisher="Bilgi Sarmal", parts=details,
            topicOutcomes=listOf(
                repeated,
                TopicOutcome("tyt_geo","Geometri","Prizmalar",0,1,0),
                TopicOutcome("tyt_turkce","Türkçe","Paragrafta Ana Düşünce",6,0,0),
                TopicOutcome("tyt_turkce","Türkçe","Cümlenin Ögeleri",0,1,0),
                TopicOutcome("tyt_mat","Matematik","Sayı Problemleri",1,2,0)
            ), mood="Heyecanlı"
        )
        val previous = latest.copy(
            id=100,name="TYT Genel-2",correct=73,wrong=22,blank=25,
            createdAt=now-7L*86400000,examDate=now-7L*86400000,
            parts=emptyList(),
            topicOutcomes=listOf(repeated.copy(wrong=1,blank=0),
                TopicOutcome("tyt_mat","Matematik","Sayı Problemleri",0,1,1))
        )
        val first = latest.copy(
            id=99,name="TYT Genel-1",correct=76,wrong=20,blank=24,
            createdAt=now-14L*86400000,examDate=now-14L*86400000,
            parts=emptyList(),
            topicOutcomes=listOf(repeated.copy(wrong=1,blank=0))
        )
        val history = listOf(first,previous,latest)
        ActivityScenario.launch(PremiumActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        androidx.compose.foundation.layout.Box(
                            modifier=Modifier.fillMaxSize()
                        ) {
                            AnalysisDashboard(
                                exams=history,
                                category="Genel",
                                onCategory={},
                                onAdd={},
                                reduceMotion=true
                            )
                        }
                    }
                }
            }
            Thread.sleep(2000)
            capture(File(dir,"01-hero.png"))
            repeat(4) { index ->
                val swipe: ParcelFileDescriptor = instrumentation.uiAutomation
                    .executeShellCommand("input swipe 525 1930 525 530 410")
                swipe.close()
                Thread.sleep(850)
                capture(File(dir,"%02d-analysis.png".format(index+2)))
            }
        }
    }

    private fun capture(file: File) {
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.takeScreenshot()
            ?: throw AssertionError("Android screenshot returned null")
        try {
            assertTrue("Screenshot cannot be empty",bmp.width >= 320 && bmp.height >= 600)
            file.outputStream().use { out ->
                assertTrue(bmp.compress(Bitmap.CompressFormat.PNG,100,out))
            }
            assertTrue("Screenshot file missing",file.isFile && file.length() > 25000)
        } finally { bmp.recycle() }
    }
}
