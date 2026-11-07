package com.denemelab.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real Android decoding + bundled ML Kit PDF recognition, not mock OCR. */
@RunWith(AndroidJUnit4::class)
class ReportImportDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun mismatchedPdfExtensionWithPngContentStaysImage() {
        val original = File.createTempFile("mislabelled-photo-", ".pdf", context.cacheDir)
        val bitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).drawColor(Color.WHITE)
            original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val copy = ReportDocumentSource.stage(context, Uri.fromFile(original))
            try {
                assertTrue(ReportDocumentSource.hasImageSignature(copy))
                assertFalse(ReportDocumentSource.isPdf(copy))
            } finally { copy.delete() }
        } finally { bitmap.recycle(); original.delete() }
    }

    @Test fun searchablePdfTextIsExtractedWithoutRasterOcr() {
        // A synthetic PDF with an actual font/text layer, not an image.
        val file = File.createTempFile("selectable-karne-", ".pdf", context.cacheDir)
        val document = com.tom_roush.pdfbox.pdmodel.PDDocument()
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            val page = com.tom_roush.pdfbox.pdmodel.PDPage()
            document.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(60f, 700f)
                stream.showText("Turkce 40 32 6 2 30,50")
                stream.endText()
            }
            document.save(file)
        } finally { document.close() }
        try {
            val text = ReportPdfTextLayer.extract(context, file)
            assertEquals(1, text.pageCount)
            assertEquals(1, text.pagesWithText)
            assertTrue(text.text.contains("Turkce 40 32 6 2 30,50"))
        } finally { file.delete() }
    }

    @Test fun resultOnSecondPdfPageIsRecognized(): Unit = runBlocking {
        val file = File.createTempFile("two-page-karne-", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 23f
            isFakeBoldText = true
        }
        try {
            val cover = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            cover.canvas.drawColor(Color.WHITE)
            cover.canvas.drawText("DENEME SONUC KARNESI", 70f, 140f, ink)
            pdf.finishPage(cover)
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
            page.canvas.drawColor(Color.WHITE)
            listOf(
                "TYT DENEME SONUCLARI",
                "Turkce 40 32 6 2 30,50",
                "Tarih-1 5 4 1 0 3,75",
                "Cografya-1 5 4 1 0 3,75",
                "Felsefe 5 4 1 0 3,75",
                "Din Kulturu 5 4 1 0 3,75"
            ).forEachIndexed { i, row -> page.canvas.drawText(row, 50f, 70f + i * 65, ink) }
            pdf.finishPage(page)
            file.outputStream().use { pdf.writeTo(it) }
            val result = withTimeout(120_000) {
                HighResolutionReportOcr.recognize(context, Uri.fromFile(file))
            }
            assertEquals(32 to 6, result.counts["tyt_turkce"])
        } finally { pdf.close(); file.delete() }
    }

    @Test fun completeTwoColumn120QuestionPdfIsReadAs120TopicQuestions(): Unit = runBlocking {
        // GENERATED test PDF, not a user's report.
        // Checks complete PDF text-layer extraction and table reconciliation.
        val file = File.createTempFile("synthetic-full-120-", ".pdf", context.cacheDir)
        val pdf = com.tom_roush.pdfbox.pdmodel.PDDocument()
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            val page = com.tom_roush.pdfbox.pdmodel.PDPage()
            pdf.addPage(page)
            val scores = listOf(
                Triple("Turkce",30,7),
                Triple("Tarih-1",4,1),
                Triple("Cografya-1",4,1),
                Triple("Felsefe",4,1),
                Triple("Din Kulturu",4,0),
                Triple("Matematik-1",22,5),
                Triple("Geometri",6,2),
                Triple("Fizik",5,1),
                Triple("Kimya",5,1),
                Triple("Biyoloji",4,1)
            )
            val counts = listOf(40,5,5,5,5,30,10,7,7,6)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pdf,page).use { writer ->
                fun line(text:String, x:Float,y:Float,size:Float) {
                    writer.beginText()
                    writer.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA,size)
                    writer.newLineAtOffset(x,y)
                    writer.showText(text)
                    writer.endText()
                }
                line("BILGI SARMAL TYT TG 1",40f,800f,13f)
                scores.forEachIndexed { i,(name,c,w) ->
                    line(name+" "+counts[i]+" "+c+" "+w,40f,765f-i*17f,9f)
                }
                line("Toplam: 120 88 20 83,00",40f,520f,10f)
                var y = 785f
                scores.forEachIndexed { i,(name,c,w) ->
                    line(name+" S D Y B%",320f,y,6f)
                    y-=5.45f
                    repeat(counts[i]) { j ->
                        val right = if (j < c) 1 else 0
                        val wrong = if (j >= c && j < c+w) 1 else 0
                        val pct = if (right == 1) 100 else 0
                        line("Konu_"+(i+1)+"_"+(j+1)+" 1 "+right+" "+wrong+" "+pct,
                            320f,y,6f)
                        y-=5.45f
                    }
                }
            }
            pdf.save(file)
            val extracted = ReportPdfTextLayer.extract(context,file)
            assertEquals(1,extracted.pagesWithText)
            assertTrue("Positioned right column must contain real rows",
                extracted.rightRows.size >= 120)
            val result=withTimeout(180_000L) {
                HighResolutionReportOcr.recognize(context,Uri.fromFile(file))
            }
            val parts=ExamStructure.tytDetailed.map { lesson ->
                val pair=result.counts[lesson.key]
                    ?: throw AssertionError("Missing subject: "+lesson.key)
                SubjectScore(lesson.key,lesson.title,lesson.questions,pair.first,pair.second)
            }
            val audit=ReportCoverage.inspect(parts,result.outcomes)
            assertEquals("Ten scored subtests",10,result.foundCount)
            assertEquals("Every printed question's topic and D/Y/B",120,audit.observed)
            assertTrue("Verified all 120, no omissions",audit.complete)
        } finally {
            pdf.close()
            file.delete()
        }
    }

}
