package com.denemelab.preview

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException

/**
 * Native searchable PDF text has more faithful characters than OCR on a
 * rasterized copy. Never send user files to a server.
 *
 * Some PDFs only contain scanned pictures: in that case this produces an
 * empty result and the image OCR pipeline is used.
 */
object ReportPdfTextLayer {
    data class NativeText(val text: String, val pageCount: Int, val pagesWithText: Int)

    @Throws(IOException::class)
    fun extract(context: Context, file: File, maxPages: Int = 24): NativeText {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(file).use { document ->
            if (document.isEncrypted) {
                throw IOException("PDF şifreli; şifresiz bir belge seç.")
            }
            val count = document.numberOfPages
            if (count == 0) throw IOException("PDF'nin içinde sayfa yok.")
            val reader = PDFTextStripper().apply {
                sortByPosition = true
                lineSeparator = "\n"
            }
            val pages = mutableListOf<String>()
            var readable = 0
            for (page in 1..minOf(maxPages, count)) {
                reader.startPage = page
                reader.endPage = page
                val raw = reader.getText(document).trim()
                if (raw.count { it.isLetterOrDigit() } >= 24) {
                    pages.add(raw)
                    readable++
                }
            }
            return NativeText(pages.joinToString("\n"), count, readable)
        }
    }
}
