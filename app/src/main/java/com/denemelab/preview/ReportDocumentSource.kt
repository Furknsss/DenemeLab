package com.denemelab.preview

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.FileOutputStream

/** Copies a document-provider URI once to seekable, private temporary storage. */
object ReportDocumentSource {
    private const val MAX_REPORT_BYTES = 45L * 1024 * 1024

    @Throws(IOException::class)
    fun stage(context: Context, uri: Uri): File {
        // SAF/gallery providers can be backed by cloud files and one of their
        // open methods may fail. Retry a fresh descriptor without retaining
        // a half-copied file or corrupting the user's original document.
        val resolver = context.contentResolver
        val staged = File.createTempFile("denemelab-report-", ".temp", context.cacheDir)
        val factories: List<() -> java.io.InputStream?> = listOf(
            { resolver.openInputStream(uri) },
            {
                resolver.openFileDescriptor(uri, "r")?.let {
                    ParcelFileDescriptor.AutoCloseInputStream(it)
                }
            }
        )
        var firstFailure: Exception? = null
        var copied = false
        try {
            for (factory in factories) {
                try {
                    val source = factory() ?: throw IOException("Belge sağlayıcısı veri döndürmedi.")
                    var copiedBytes = 0L
                    source.use { input ->
                        FileOutputStream(staged, false).use { output ->
                            val bytes = ByteArray(32768)
                            var zeroReads = 0
                            while (true) {
                                val read = input.read(bytes)
                                if (read < 0) break
                                if (read == 0) {
                                    if (++zeroReads > 4) throw IOException("Dosya akışı okunamıyor.")
                                    continue
                                }
                                zeroReads = 0
                                copiedBytes += read
                                if (copiedBytes > MAX_REPORT_BYTES)
                                    throw IOException("Belge 45 MB sınırını aşıyor.")
                                output.write(bytes, 0, read)
                            }
                        }
                    }
                    if (copiedBytes == 0L) throw IOException("Seçilen belge boş.")
                    copied = true
                    break
                } catch (e: Exception) {
                    if (firstFailure == null) firstFailure = e
                }
            }
            if (!copied)
                throw IOException(
                    "Seçilen görsele erişilemedi. Belgeyi cihazına indirip yeniden seç.",
                    firstFailure
                )
            val hasPdfHeader = hasPdfSignature(staged)
            val isImage = hasImageSignature(staged)
            val claimsPdf = runCatching {
                resolver.getType(uri)?.contains("pdf", ignoreCase = true) == true
            }.getOrDefault(false) ||
                uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true
            val suffix = if (hasPdfHeader || (claimsPdf && !isImage)) ".pdf" else ".img"
            val target = File(staged.parentFile, staged.nameWithoutExtension + suffix)
            return if (staged.renameTo(target)) target else staged
        } catch (e: Exception) {
            staged.delete()
            if (e is IOException) throw e
            throw IOException("Belge açılamadı. Tekrar seç veya cihazına indir.", e)
        }
    }

    /** PDF magic can legally appear after a small preamble. */
    fun hasPdfSignature(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val header = ByteArray(1024)
            val size = input.read(header)
            if (size <= 0) false else
                String(header, 0, size, Charsets.ISO_8859_1).contains("%PDF-")
        }
    } catch (_: IOException) { false }

    fun hasImageSignature(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val header = ByteArray(16)
            val size = input.read(header)
            if (size < 4) false else {
                val b = header.map { it.toInt() and 0xff }
                (b[0] == 0xff && b[1] == 0xd8) ||                  // JPEG
                (b[0] == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71) || // PNG
                String(header, 0, 3, Charsets.US_ASCII) == "GIF" ||
                (String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    size >= 12 && String(header, 8, 4, Charsets.US_ASCII) == "WEBP") ||
                (size >= 12 && String(header, 4, 4, Charsets.US_ASCII) == "ftyp") // HEIF
            }
        }
    } catch (_: IOException) { false }

    fun isPdf(file: File): Boolean =
        hasPdfSignature(file) ||
            (file.extension.equals("pdf", ignoreCase = true) && !hasImageSignature(file))
}

}
