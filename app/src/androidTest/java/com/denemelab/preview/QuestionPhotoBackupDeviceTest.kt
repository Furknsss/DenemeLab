package com.denemelab.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class QuestionPhotoBackupDeviceTest {
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
