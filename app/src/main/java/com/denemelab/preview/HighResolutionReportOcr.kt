package com.denemelab.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
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

    suspend fun recognize(context: Context, uri: Uri): ReportResult =
        withContext(Dispatchers.Default) {
            val isPdf = context.contentResolver.getType(uri)
                ?.contains("pdf", ignoreCase = true) == true ||
                uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true
            val bitmap = if (isPdf) renderFirstPdfPage(context, uri)
                         else decodeImage(context, uri)
            val warnings = mutableListOf<String>()
            if (isPdf) {
                val pages = runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                        PdfRenderer(descriptor).use { it.pageCount }
                    } ?: 0
                }.getOrElse { 0 }
                if (pages > 1) warnings.add(
                    "Bu PDF $pages sayfalı. Şimdilik yalnızca ilk sonuç sayfası tarandı; diğer sayfalardaki kazanımları ayrıca kontrol et."
                )
            }
            if (!isPdf && bitmap.width < 1800) {
                warnings.add(
                    "Görselin yatay çözünürlüğü düşük. Çok küçük konu yazıları için orijinal PDF veya yüksek çözünürlüklü belge kullan."
                )
            }
            if (bitmap.width < 600 || bitmap.height < 700)
                warnings.add("Belge küçük; okunamayan kazanımlar olabilir.")
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                // 1600px overview quickly finds the exam heading. Subtables
                // are analysed separately at a much higher text scale.
                val preview = resizedPreview(bitmap, 1600)
                val fullText = try {
                    read(recognizer, preview).text
                } finally {
                    if (preview !== bitmap) preview.recycle()
                }

                val width = bitmap.width
                val height = bitmap.height
                val xSplit = (width * 0.502f).roundToInt()
                val regions = listOf(
                    Region("left", Rect(0, (height * .10f).roundToInt(),
                        (width * .51f).roundToInt(), (height * .67f).roundToInt())),
                    Region("right1", Rect(xSplit, (height * .035f).roundToInt(),
                        width, (height * .31f).roundToInt())),
                    Region("right2", Rect(xSplit, (height * .285f).roundToInt(),
                        width, (height * .56f).roundToInt())),
                    Region("right3", Rect(xSplit, (height * .535f).roundToInt(),
                        width, (height * .80f).roundToInt())),
                    Region("right4", Rect(xSplit, (height * .775f).roundToInt(),
                        width, height))
                )
                val left = mutableListOf<Line>()
                val right = mutableListOf<Line>()
                for (region in regions) {
                    val box = region.bounds
                    if (box.width() < 64 || box.height() < 64) continue
                    val scale = min(3.1f,
                        min(2100f / box.width(), 2200f / box.height()))
                        .coerceAtLeast(1.0f)
                    val source = Bitmap.createBitmap(bitmap,
                        box.left, box.top, box.width(), box.height())
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
                        if (region.name == "left") left.addAll(lines)
                        else right.addAll(lines)
                    } finally {
                        enhanced.recycle()
                        scaled.recycle()
                    }
                }
                val result = DetailedReportReconciliation.reconcile(
                    fullText, deduplicate(left).map { it.text },
                    deduplicate(right).map { it.text }
                )
                result.copy(warnings = (warnings + result.warnings).distinct())
            } finally {
                recognizer.close()
                bitmap.recycle()
            }
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
    private fun renderFirstPdfPage(context: Context, uri: Uri): Bitmap {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IOException("PDF dosyası açılamadı.")
        descriptor.use { fd ->
            PdfRenderer(fd).use { pdf ->
                if (pdf.pageCount < 1) throw IOException("PDF boş.")
                pdf.openPage(0).use { page ->
                    val scale = min(4.5f,
                        min(3400f / page.width, 4700f / page.height))
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
            }
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IOException("Görsel açılamadı.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw IOException("Geçersiz görüntü.")
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
