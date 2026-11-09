package com.denemelab.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.view.PixelCopy
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.MaterialTheme
import java.io.File
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class QuestionPhotoBackupDeviceTest {

    @Test fun questionVaultPhotoCardRendersInRealAndroidActivity() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val bitmap=Bitmap.createBitmap(1000,650,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.BLACK
            textSize=66f
            isFakeBoldText=true
        }
        canvas.drawText("Soru 12",70f,120f,ink)
        ink.textSize=48f
        ink.isFakeBoldText=false
        canvas.drawText("2x + 3 = 17",70f,270f,ink)
        canvas.drawText("x = ?",70f,350f,ink)
        val fileName=QuestionPhotoStore.persist(ctx,bitmap)
        try {
            val record=QuestionRecord(
                System.currentTimeMillis(),"Soru 12","TYT Matematik",
                "Problemler","QA örnek yayın","Yanlış",0,System.currentTimeMillis(),fileName
            )
            ActivityScenario.launch(PremiumActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        MaterialTheme {
                            QuestionVaultV24(
                                questions=listOf(record),
                                onAdd={},
                                onUpdate={ _,_ -> },
                                onDelete={ false }
                            )
                        }
                    }
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Thread.sleep(1000)
                val done=CountDownLatch(1)
                val copyResult=AtomicInteger(-1)
                val captureError=AtomicReference<Throwable?>(null)
                scenario.onActivity { activity ->
                    val root=activity.window.decorView.rootView
                    assertTrue(root.width>0 && root.height>0)
                    val screenshot=Bitmap.createBitmap(
                        root.width,root.height,Bitmap.Config.ARGB_8888
                    )
                    // Coil can load hardware bitmaps. Drawing a View into a
                    // software Canvas crashes; copy the actual GPU window instead.
                    PixelCopy.request(activity.window,screenshot,{ result ->
                        copyResult.set(result)
                        try {
                            if(result==PixelCopy.SUCCESS) {
                                val output=File(activity.filesDir,"qa-question-vault-v024.png")
                                output.outputStream().use {
                                    if(!screenshot.compress(Bitmap.CompressFormat.PNG,100,it))
                                        throw IllegalStateException("Screenshot could not be compressed.")
                                }
                                if(output.length()<=4096L)
                                    throw IllegalStateException("Screenshot file was too small.")
                            }
                        } catch(t:Throwable) { captureError.set(t) }
                        finally {
                            screenshot.recycle()
                            done.countDown()
                        }
                    },Handler(Looper.getMainLooper()))
                }
                assertTrue("PixelCopy capture timed out.",done.await(12,TimeUnit.SECONDS))
                assertEquals("Screenshot capture failed.",PixelCopy.SUCCESS,copyResult.get())
                captureError.get()?.let { throw it }
            }
        } finally {
            QuestionPhotoStore.discard(ctx,fileName)
            bitmap.recycle()
        }
    }

    @Test fun questionPhotoRoundTripKeepsCropAndNeverLosesTheImage() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val original=DenemeLabBackup.createArchive(ctx)
        val bitmap=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val cropped=QuestionPhotoStore.crop(bitmap,.1f,.1f,.9f,.9f)
        assertEquals(640,cropped.width)
        assertEquals(480,cropped.height)
        val fileName=QuestionPhotoStore.persist(ctx,cropped)
        val saved=QuestionRecord(System.currentTimeMillis(),"Soru 12","TYT Matematik",
            "Problemler","Örnek kaynak","Yanlış",0,System.currentTimeMillis(),fileName)
        lateinit var archive:java.io.File
        try {
            assertTrue(LocalData.saveQuestions(ctx,listOf(saved)))
            archive=DenemeLabBackup.createArchive(ctx)
            try {
                val imageNames=mutableListOf<String>()
                ZipInputStream(archive.inputStream()).use { zip ->
                    while(true){
                        val entry=zip.nextEntry?:break
                        if(entry.name.startsWith("questions/"))imageNames.add(entry.name)
                        zip.closeEntry()
                    }
                }
                assertEquals(listOf("questions/"+fileName),imageNames)
                assertEquals(1,DenemeLabBackup.inspect(archive).questionCount)
                DenemeLabBackup.restore(ctx,archive)
                val loaded=LocalData.loadQuestions(ctx).single()
                assertEquals(saved.title,loaded.title)
                assertNotEquals(fileName,loaded.imageFile)
                assertTrue(QuestionPhotoStore.asset(ctx,loaded.imageFile)?.isFile==true)
                assertNotNull(QuestionPhotoStore.imageUri(ctx,loaded.imageFile))
                QuestionPhotoStore.discard(ctx,loaded.imageFile)
            } finally {
                archive.delete()
            }
        } finally {
            // Test fixture must not overwrite or delete user data.
            DenemeLabBackup.restore(ctx,original)
            original.delete()
            QuestionPhotoStore.discard(ctx,fileName)
            bitmap.recycle()
            cropped.recycle()
        }
    }
}
