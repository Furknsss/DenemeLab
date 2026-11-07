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
    private data class Line(val text: String, val y: Float, val x: Float)

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
                var nativeResult: ReportResult? = null
                if (ReportDocumentSource.isPdf(staged)) {
                    // A real PDF text layer is preferable to OCR of tiny
                    // rasterized characters. No network or cloud model needed.
                    val native = runCatching {
                        ReportPdfTextLayer.extract(context, staged)
                    }.getOrNull()
                    if (native != null && native.pagesWithText > 0) {
                        nativeResult = DetailedReportReconciliation.reconcile(
                            native.text, native.text.lines(), native.text.lines()
                        )
                        val nativeParts = ExamStructure.tytDetailed.map { lesson ->
                            val pair = nativeResult.counts[lesson.key] ?: (0 to 0)
                            SubjectScore(lesson.key, lesson.title,
                                lesson.questions, pair.first, pair.second)
                        }
                        if (nativeResult.foundCount == 10 &&
                            ReportCoverage.inspect(nativeParts, nativeResult.outcomes).complete) {
                            return@withContext nativeResult.copy(warnings =
                                (nativeResult.warnings +
                                    "PDF'nin seçilebilir metni doğrudan okundu. Kaydetmeden önce kontrol et.")
                                    .distinct())
                        }
                    }
                    try {
                        ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            PdfRenderer(fd).use { pdf ->
                                if (pdf.pageCount == 0) throw IOException("PDF'de sayfa yok.")
                                val limit = min(pdf.pageCount, 12)
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
                                    val candidateParts = ExamStructure.tytDetailed.map { lesson ->
                                        val totals = candidate.counts[lesson.key] ?: (0 to 0)
                                        SubjectScore(lesson.key, lesson.title, lesson.questions,
                                            totals.first, totals.second)
                                    }
                                    if (candidate.foundCount == 10 &&
                                        ReportCoverage.inspect(candidateParts, candidate.outcomes).complete)
                                        break
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
                    val image = decodeImage(staged)
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
                val rasterResult = DetailedReportReconciliation.reconcile(
                    overviews.joinToString("\n"), allLeft, allRight
                )
                val native = nativeResult
                val result = if (native == null) rasterResult
                    else ReportEvidenceMerger.merge(rasterResult, native)
                val moreWarnings = if (result.foundCount == 0) {
                    listOf(
                        "Bu karnenin ders tablosu otomatik çözümlenemedi. " +
                        "Görüntü açıldı; kaydetmeden önce ders rakamlarını elle doğrula."
                    )
                } else emptyList()
                result.copy(
                    warnings = (warnings + result.warnings + moreWarnings).distinct()
                )
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
        val preview = resizedPreview(bitmap, 2400)
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
        suspend fun scan(regions: List<Region>, originalPixels: Boolean = false) {
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
                // A second, unfiltered pass is essential for thin 3D
                // curriculum glyphs: contrast filters can erase a 1px digit.
                val enhanced = if (originalPixels) scaled else enhanceDocument(scaled)
                try {
                    val text = read(recognizer, enhanced)
                    // Even "full-width" fallback crops must never concatenate
                    // left summary cells with right curriculum cells on the
                    // same horizontal line: keep their page-column identities.
                    val lines = spatialRows(text, box, scale, width)
                    val cut = width * 0.49f
                    left.addAll(lines.filter { it.x < cut })
                    right.addAll(lines.filter { it.x >= cut })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    warnings.add("Belgenin bir bölümündeki metin okunamadı.")
                } finally {
                    if (enhanced !== scaled) enhanced.recycle()
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
        val initialParts = ExamStructure.tytDetailed.map { lesson ->
            val pair = initial.counts[lesson.key] ?: (0 to 0)
            SubjectScore(lesson.key, lesson.title, lesson.questions,
                pair.first, pair.second)
        }
        if (initial.foundCount < 10 ||
            !ReportCoverage.inspect(initialParts, initial.outcomes).complete) {
            // On Bilgi Sarmal's two-column sheet the final Biology line is
            // near 40% of page height. A large left-half scan reduces tiny
            // characters, so reread the actual SCORE TABLE in tight bands.
            // The right-hand topic list may be cropped; never extrapolate it.
            scan(listOf(
                region("score-table-original",.005f,.145f,.485f,.445f),
                region("score-upper-original",.005f,.16f,.485f,.305f),
                region("score-lower-original",.005f,.285f,.485f,.445f)
            ), originalPixels = true)
            scan(listOf(
                region("left-footer",0f,.67f,.55f,1f),
                region("right-overview",0f,0f,1f,.40f),
                region("right-middle",0f,.38f,1f,.75f),
                region("right-footer",0f,.73f,1f,1f)
            ))
            // Independent narrow, overlapping bands preserve tiny row spacing
            // and allow higher effective OCR scale than full-page passes.
            // Use original pixels to avoid destroying small digits/percentages.
            scan(listOf(
                region("summary-top-original",0f,0f,.52f,.38f),
                region("summary-bottom-original",0f,.35f,.52f,.78f),
                region("curriculum-1-original",.47f,0f,1f,.20f),
                region("curriculum-2-original",.47f,.18f,1f,.40f),
                region("curriculum-3-original",.47f,.38f,1f,.60f),
                region("curriculum-4-original",.47f,.58f,1f,.80f),
                region("curriculum-5-original",.47f,.78f,1f,1f)
            ), originalPixels = true)
        }
        // Extra resolution only when completeness is still missing.
        // On a tall 1200x10000 screenshot the original 20%-height tile
        // scales at ~1.2x, whereas 8%-height overlapping micro-strips
        // can scale above 2x. This prevents losing small S/D/Y/B% digits.
        val afterCoarse = DetailedReportReconciliation.reconcile(
            overview, deduplicate(left).map { it.text },
            deduplicate(right).map { it.text })
        val afterParts = ExamStructure.tytDetailed.map { lesson ->
            val pair = afterCoarse.counts[lesson.key] ?: (0 to 0)
            SubjectScore(lesson.key, lesson.title, lesson.questions,
                pair.first, pair.second)
        }
        if (!ReportCoverage.inspect(afterParts, afterCoarse.outcomes).complete) {
            val micro = (0 until 12).map { index ->
                val start = (index / 12f - .012f).coerceAtLeast(0f)
                val end = ((index+1) / 12f + .012f).coerceAtMost(1f)
                region("curriculum-micro-$index", .455f, start, 1f, end)
            }
            scan(micro, originalPixels = true)
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
        val scale = min(5.0f,
            min(3400f / page.width, 4800f / page.height))
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

    /**
     * The photo is already copied to cache. Decode it directly by FILE path,
     * never by reopening a potentially unavailable content:// provider. PNG,
     * JPEG, WebP, HEIC and their MIME-less cache extensions are supported
     * wherever Android's decoders support the actual file signature.
     */
    private fun decodeImage(file: File): Bitmap {
        val path = file.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val fallback = runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) {
                            decoder, info, _ ->
                        // Long report screenshots must keep the tiny
                        // printed digits unless the actual pixel-memory
                        // budget requires resampling.
                        val sample = ReportImageSampling.sampleSize(
                            info.size.width, info.size.height)
                        decoder.setTargetSampleSize(sample)
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                    }
                }.getOrNull()
                if (fallback != null) return fallback
            }
            throw IOException(
                "Seçilen dosya desteklenen bir görsel olarak çözümlenemedi. " +
                "Orijinal JPG, PNG, WebP veya şifresiz PDF seç."
            )
        }
        val options = BitmapFactory.Options()
        val sample = ReportImageSampling.sampleSize(
            bounds.outWidth, bounds.outHeight)
        options.inSampleSize = sample
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        val source = BitmapFactory.decodeFile(path, options)
            ?: throw IOException("Görsel belleğe açılamadı. Daha küçük bir kopya seç.")
        val rotation = runCatching {
            when (ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrElse { 0f }
        if (rotation == 0f) return source
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height,
                Matrix().apply { postRotate(rotation) }, true)
        } finally { source.recycle() }
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

    /**
     * OCR word coordinates are in the rendered page's pixel space. Large
     * overlaps are intentional, but must not fuse two distinct rows simply
     * because they happen to share a y coordinate.
     */
    private fun spatialRows(
        text: Text, box: Rect, scale: Float, pageWidth: Int
    ): List<Line> {
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
        val tolerance = (median * 0.45f).coerceIn(0.8f, 6.5f)
        val wide = box.width() > pageWidth * 0.57f
        val fragments = if (wide) words.groupBy {
            if (it.x < pageWidth * 0.49f) 0 else 1
        }.values.toList() else listOf(words)
        val results = mutableListOf<Line>()
        for (column in fragments) {
            val groups = mutableListOf<MutableList<Word>>()
            column.sortedBy { it.y }.forEach { word ->
                val last = groups.lastOrNull()
                val averageY = last?.map { it.y }?.average()?.toFloat()
                if (last != null && averageY != null &&
                    abs(averageY - word.y) <= tolerance) last.add(word)
                else groups.add(mutableListOf(word))
            }
            groups.forEach { group ->
                val joined = group.sortedBy { it.x }.joinToString(" ") { it.text }.trim()
                if (joined.isNotBlank()) results.add(Line(
                    joined,
                    group.map { it.y }.average().toFloat(),
                    group.minOf { it.x }
                ))
            }
        }
        // ML Kit often already knows row boundaries. Preserve those
        // original lines as a second candidate; our spatial word regrouping
        // is useful when the numeric columns are detected separately, but
        // can accidentally join two physically adjacent tiny rows.
        text.textBlocks.flatMap { it.lines }.forEach { line ->
            val bounds = line.boundingBox ?: return@forEach
            val leftEdge = box.left + bounds.left / scale
            val rightEdge = box.left + bounds.right / scale
            val pageMidpoint = pageWidth * 0.49f
            if (leftEdge < pageMidpoint && rightEdge > pageMidpoint) return@forEach
            if (line.text.isNotBlank()) results.add(Line(
                line.text.trim(), box.top + bounds.exactCenterY()/scale, leftEdge))
        }
        return results.sortedWith(compareBy<Line> { it.y }.thenBy { it.x })
    }

    /** Remove the same printed row across overlapping OCR tiles, not different
     * topic rows that happen to have close y coordinates (tiny text = 5px).
     * Keep competing OCR variants with different labels for later checking.
     */
    private fun deduplicate(lines: List<Line>): List<Line> {
        val result = mutableListOf<Line>()
        for (candidate in lines.sortedWith(compareBy<Line> { it.y }.thenBy { it.x })) {
            val candidateKey = TopicAnalyticsV3.normalize(candidate.text)
            val matchAt = result.indexOfLast { older ->
                val priorKey = TopicAnalyticsV3.normalize(older.text)
                val common = min(15, min(candidateKey.length, priorKey.length))
                common >= 6 && candidateKey.take(common) == priorKey.take(common) &&
                    abs(older.y - candidate.y) <= 2.0f &&
                    abs(older.x - candidate.x) <= 22f
            }
            if (matchAt < 0) result.add(candidate) else {
                val old = result[matchAt]
                // Numerically complete S/D/Y/B% is more reliable than
                // a longer string containing stray OCR noise.
                val oldScore = ReportOcrTextQualityV22.score(old.text)
                val newScore = ReportOcrTextQualityV22.score(candidate.text)
                if (newScore > oldScore) result[matchAt] = candidate
            }
        }
        return result.sortedWith(compareBy<Line> { it.y }.thenBy { it.x })
    }
}
