package com.denemelab.preview

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/** Copies a document-provider URI once to seekable, private temporary storage. */
object ReportDocumentSource {
    private const val MAX_REPORT_BYTES = 45L * 1024 * 1024

    @Throws(IOException::class)
    fun stage(context: Context, uri: Uri): File {
        val staged = File.createTempFile("denemelab-report-", ".temp", context.cacheDir)
        try {
            val resolver = context.contentResolver
            val source = try { resolver.openInputStream(uri) } catch (_: Exception) { null }
                ?: try {
                    resolver.openFileDescriptor(uri, "r")?.let {
                        ParcelFileDescriptor.AutoCloseInputStream(it)
                    }
                } catch (_: Exception) { null }
                ?: throw IOException("Dosya erişimi başarısız. Belgeyi telefona indirip yeniden seç.")
            var count = 0L
            source.use { input ->
                staged.outputStream().use { output ->
                    val bytes = ByteArray(32768)
                    while (true) {
                        val read = input.read(bytes)
                        if (read < 0) break
                        count += read
                        if (count > MAX_REPORT_BYTES)
                            throw IOException("Belge 45 MB sınırını aşıyor. Daha küçük PDF veya görsel seç.")
                        output.write(bytes, 0, read)
                    }
                }
            }
            if (count == 0L) throw IOException("Seçilen belge boş.")
            // Android PdfRenderer requires a seekable file descriptor. Preserve
            // the extension for UI preview even when the provider mislabels MIME.
            val targetExtension = when {
                hasPdfSignature(staged) -> ".pdf"
                resolver.getType(uri)?.contains("pdf", ignoreCase = true) == true -> ".pdf"
                uri.lastPathSegment?.endsWith(".pdf", ignoreCase = true) == true -> ".pdf"
                else -> ".img"
            }
            val target = File(staged.parentFile, staged.nameWithoutExtension + targetExtension)
            if (!staged.renameTo(target)) return staged
            return target
        } catch (e: Exception) {
            staged.delete()
            if (e is IOException) throw e
            throw IOException("Belge açılamadı. Dosyayı telefona indirip tekrar seç.", e)
        }
    }

    fun hasPdfSignature(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val header = ByteArray(5)
            input.read(header) == 5 && header.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))
        }
    } catch (_: IOException) { false }
}
