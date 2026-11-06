package com.denemelab.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Report images are scanned locally. No user name, school name or paper is
 * uploaded to a server. OCR is deliberately separated into the numeric
 * results table and four overlapping strips of small-print curriculum rows.
 *
 * Upscaling helps OCR segmentation but cannot reconstruct characters that
 * were destroyed by a low-resolution screenshot. Native PDF rendering is
 * preferable and supported via Android PdfRenderer.
 */
object HighResolutionReportOcr {
    private data class Region(val name: String, val bounds: Rect)
    private data class Line(val text: String, val y: Float)

    private data class Fragments(
        val overview: String,
        val left: List<String>,
        val right: List<String>,
        val warnings: List<String>
    )

    /**
     * Work on a staged local file, not a transient gallery/Drive content URI.
     * PDFs may have the score table on later pages; inspect up to four pages
     * without retaining large bitmaps across pages.
     */
    suspend fun recognize(context: Context, uri: Uri): ReportResult =
        withContext(Dispatchers.IO) {
            val isTemporary = uri.scheme != "file"
            val staged = if (isTemporary) ReportDocumentSource.stage(context, uri)
                else File(uri.path ?: throw IOException("Belge yolu bulunamadı."))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val overviews = mutableListOf<String>()
                val allLeft = mutableListOf<String>()
                val allRight = mutableListOf<String>()
                val warnings = mutableListOf<String>()
                var scannedPages = 0
                if (ReportDocumentSource.hasPdfSignature(staged)) {
                    try {
                        ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            PdfRenderer(fd).use { pdf ->
                                if (pdf.pageCount == 0) throw IOException("PDF'de sayfa yok.")
                                val limit = min(pdf.pageCount, 4)
                                for (pageNumber in 0 until limit) {
                                    pdf.openPage(pageNumber).use { page ->
                                        val image = renderPdfPage(page)
                                        try {
                                            val fragment = scanBitmap(recognizer, image)
                                            overviews.add(fragment.overview)
                                            allLeft.addAll(fragment.left)
                                            allRight.addAll(fragment.right)
                                            warnings.addAll(fragment.warnings.map {
                                                "Sayfa ${pageNumber + 1}: $it"
                                            })
                                        } finally {
                                            image.recycle()
                                        }
                                    }
                                    scannedPages++
                                    val candidate = DetailedReportReconciliation.reconcile(
                                        overviews.joinToString("\n"),
                                        allLeft, allRight
                                    )
                                    // Keep scanning when page 1 has only a summary
                                    // and the following pages may have topic rows.
                                    if (candidate.foundCount >= 10 &&
                                        candidate.validatedTopicQuestions >= 80) break
                                }
                                if (scannedPages < pdf.pageCount) {
                                    warnings.add(
                                        "PDF ${pdf.pageCount} sayfa; ${scannedPages} sayfa tarandı. " +
                                        "Diğer sayfalarda konu verisi varsa belgeni kontrol et."
                                    )
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (e: Exception) {
                        throw IOException(
                            "PDF açılamadı. Dosya şifreliyse şifresiz bir kopya seç. " +
                            (e.localizedMessage ?: "PDF okuma hatası."), e
                        )
                    }
                } else {
                    val image = decodeImage(context, Uri.fromFile(staged))
                    try {
                        if (min(image.width, image.height) < 850)
                            warnings.add("Görsel çözünürlüğü düşük; küçük rakamlar hatalı okunabilir.")
                        val fragment = scanBitmap(recognizer, image)
                        overviews.add(fragment.overview)
                        allLeft.addAll(fragment.left)
                        allRight.addAll(fragment.right)
                        warnings.addAll(fragment.warnings)
                    } finally {
                        image.recycle()
                    }
                }
                val result = DetailedReportReconciliation.reconcile(
                    overviews.joinToString("\n"), allLeft, allRight
                )
                val moreWarnings = if (result.foundCount == 0) {
                    listOf(
                        "Bu karnenin ders tablosu otomatik çözümlenemedi. " +
                        "Görüntü açıldı; kaydetmeden önce ders rakamlarını elle doğrula."
                    )
                } else emptyList()
                result.copy(warnings =
                    (warnings + result.warnings + moreWarnings).distinct())
            } finally {
                recognizer.close()
                if (isTemporary) staged.delete()
            }
        }

    /** Fast summary pass, then small-print tables at independent resolutions. */
    private suspend fun scanBitmap(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): Fragments {
        val warnings = mutableListOf<String>()
        val preview = resizedPreview(bitmap, 1850)
        val overview = try {
            read(recognizer, preview).text
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            warnings.add("Genel OCR denemesi başarısız; tablo bölgeleri ayrı tarandı.")
            ""
        } finally {
            if (preview !== bitmap) preview.recycle()
        }
        val width = bitmap.width
        val height = bitmap.height
        fun region(name: String, x1: Float, y1: Float, x2: Float, y2: Float) =
            Region(name, Rect(
                (width * x1).roundToInt().coerceIn(0,width-1),
                (height * y1).roundToInt().coerceIn(0,height-1),
                (width * x2).roundToInt().coerceIn(1,width),
                (height * y2).roundToInt().coerceIn(1,height)
            ))
        val left = mutableListOf<Line>()
        val right = mutableListOf<Line>()
        suspend fun scan(regions: List<Region>) {
            for (tile in regions) {
                val box = tile.bounds
                if (box.width() < 64 || box.height() < 64) continue
                val scale = min(3.2f,
                    min(2000f / box.width(), 2400f / box.height()))
                    .coerceAtLeast(1.0f)
                val source = Bitmap.createBitmap(bitmap, box.left, box.top,
                    box.width(), box.height())
                val scaled = if (scale > 1.02f) {
                    Bitmap.createScaledBitmap(source,
                        (source.width * scale).roundToInt(),
                        (source.height * scale).roundToInt(), true)
                } else source
                if (scaled !== source) source.recycle()
                val enhanced = enhanceDocument(scaled)
                try {
                    val text = read(recognizer, enhanced)
                    val lines = spatialRows(text, box, scale)
                    if (tile.name.startsWith("left")) left.addAll(lines)
                    else right.addAll(lines)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    warnings.add("Belgenin bir bölümündeki metin okunamadı.")
                } finally {
                    enhanced.recycle()
                    scaled.recycle()
                }
            }
        }
        scan(listOf(
            region("left-summary", 0f,.02f,.55f,.73f),
            region("right-top",.46f,0f,1f,.30f),
            region("right-mid1",.46f,.275f,1f,.55f),
            region("right-mid2",.46f,.525f,1f,.79f),
            region("right-bottom",.46f,.765f,1f,1f)
        ))
        val initial = DetailedReportReconciliation.reconcile(
            overview, deduplicate(left).map { it.text },
            deduplicate(right).map { it.text }
        )
        // Publisher layouts vary: a 3D-style half-page split cannot read
        // a full-width table or a course list near the foot of the page.
        if (initial.foundCount < 10 || initial.validatedTopicQuestions < 6) {
            scan(listOf(
                region("left-footer",0f,.67f,.55f,1f),
                region("right-overview",0f,0f,1f,.40f),
                region("right-middle",0f,.38f,1f,.75f),
                region("right-footer",0f,.73f,1f,1f)
            ))
        }
        return Fragments(
            overview,
            deduplicate(left).map { it.text },
            deduplicate(right).map { it.text },
            warnings.distinct()
        )
    }

    private suspend fun read(
        recognizer: com.google.mlkit.vision.text.TextRecognizer, bitmap: Bitmap
    ): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener {
                if (continuation.isActive) continuation.resume(it)
            }
            .addOnFailureListener {
                if (continuation.isActive)
                    continuation.resumeWith(Result.failure(it))
            }
    }

    /**
     * Native PDFs render sharp glyphs at ~4x their PDF point size. Raster
     * photos can never gain real details by digital upscaling.
     */
    @Throws(IOException::class)
    private fun renderPdfPage(page: PdfRenderer.Page): Bitmap {
        val scale = min(4.0f,
            min(3000f / page.width, 4200f / page.height))
        val width = max(1, (page.width * scale).roundToInt())
        val height = max(1, (page.height * scale).roundToInt())
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            Canvas(result).drawColor(Color.WHITE)
            page.render(result, null,
                Matrix().apply { setScale(scale, scale) },
                PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            return result
        } catch (e: Exception) {
            result.recycle()
            throw e
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IOException("Görsel açılamadı.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // Newer devices may recognize HEIC/AVIF through ImageDecoder even
            // when BitmapFactory's old header decoder does not.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val fallback = runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) {
                            decoder, info, _ ->
                        val maximum = max(info.size.width, info.size.height)
                        var sample = 1
                        while (maximum / sample > 5100) sample *= 2
                        decoder.setTargetSampleSize(sample)
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                    }
                }.getOrNull()
                if (fallback != null) return fallback
            }
            throw IOException("Görsel biçimi okunamadı. Orijinal JPG, PNG, WEBP ya da PDF seç.")
        }
        val options = BitmapFactory.Options()
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 5100) sample *= 2
        options.inSampleSize = sample
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Görsel çözümlenemedi.")
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrElse { 0f }
        if (rotation == 0f) return source
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height,
                Matrix().apply { postRotate(rotation) }, true)
        } finally {
            source.recycle()
        }
    }

    private fun resizedPreview(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val ratio = maxWidth.toFloat() / source.width
        return Bitmap.createScaledBitmap(source,
            maxWidth, max(1, (source.height * ratio).roundToInt()), true)
    }

    /** Moderate contrast; avoid thresholding away thin fonts/diacritics. */
    private fun enhanceDocument(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(
            source.width, source.height, Bitmap.Config.ARGB_8888)
        val saturation = ColorMatrix().apply { setSaturation(0f) }
        val contrast = ColorMatrix(floatArrayOf(
            1.20f,0f,0f,0f,-20f,
            0f,1.20f,0f,0f,-20f,
            0f,0f,1.20f,0f,-20f,
            0f,0f,0f,1f,0f
        ))
        saturation.postConcat(contrast)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(saturation)
        }
        Canvas(result).drawColor(Color.WHITE)
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** Uses actual word bounds, not a fixed page-width Y tolerance. */
    private fun spatialRows(text: Text, box: Rect, scale: Float): List<Line> {
        data class Word(val text: String, val x: Float, val y: Float, val height: Float)
        val words = text.textBlocks.flatMap { it.lines }.flatMap { line ->
            line.elements.mapNotNull { element ->
                val bounds = element.boundingBox ?: return@mapNotNull null
                Word(element.text,
                    box.left + bounds.exactCenterX() / scale,
                    box.top + bounds.exactCenterY() / scale,
                    bounds.height().toFloat() / scale)
            }
        }
        if (words.isEmpty()) return emptyList()
        val heights = words.map { it.height }.filter { it > 0 }.sorted()
        val median = heights[heights.size / 2]
        val tolerance = (median * 0.40f).coerceIn(1.6f, 4.2f)
        val groups = mutableListOf<MutableList<Word>>()
        words.sortedBy { it.y }.forEach { word ->
            val current = groups.lastOrNull()
            val average = current?.map { it.y }?.average()?.toFloat()
            if (current != null && average != null && abs(average-word.y) <= tolerance)
                current.add(word)
            else groups.add(mutableListOf(word))
        }
        return groups.map { group ->
            Line(
                group.sortedBy { it.x }.joinToString(" ") { it.text }.trim(),
                group.map { it.y }.average().toFloat()
            )
        }.filter { it.text.isNotBlank() }
    }

    /** OCR strips overlap: never count the same printed topic twice. */
    private fun deduplicate(lines: List<Line>): List<Line> {
        val sorted = lines.sortedBy { it.y }
        val result = mutableListOf<Line>()
        for (candidate in sorted) {
            val index = result.indexOfLast { abs(it.y-candidate.y) < 4f }
            if (index < 0) result.add(candidate)
            else {
                // Prefer the result that includes more actual visible text.
                // This also recovers the number columns split across tiles.
                val old = result[index]
                if (candidate.text.length > old.text.length) result[index] = candidate
            }
        }
        return result.sortedBy { it.y }
    }
}
