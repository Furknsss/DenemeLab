package com.denemelab.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Private photo assets for Soru Kumbaram.
 * QuestionRecord stores an opaque safe file name, never a temporary picker URI.
 * Files are written atomically and copied into DenemeLabBackup on every backup.
 */
object QuestionPhotoStore {
    val safeName = Regex("[0-9a-f-]{36}\\.jpg")
    const val FOLDER = "question-images"

    fun asset(context: Context, name: String): File? =
        if (safeName.matches(name))
            File(File(context.filesDir, FOLDER), name)
        else null

    fun imageUri(context: Context, name: String): Uri? =
        asset(context, name)?.takeIf { it.isFile }?.let { Uri.fromFile(it) }

    fun cameraTarget(context: Context): Pair<Uri, File> {
        val directory = File(context.cacheDir, "question-camera")
        if (!directory.exists() && !directory.mkdirs())
            throw IOException("Kamera fotoğrafı için geçici klasör açılamadı.")
        val file = File(directory, UUID.randomUUID().toString() + ".jpg")
        if (!file.createNewFile())
            throw IOException("Yeni kamera dosyası oluşturulamadı.")
        return FileProvider.getUriForFile(
            context, context.packageName + ".questionfiles", file) to file
    }

    /**
     * Bitmap preview includes source orientation and never decodes unlimited
     * full-resolution camera data into memory.
     */
    @Throws(IOException::class)
    fun decodePreview(context: Context, source: Uri, maxSide: Int = 2600): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IOException("Fotoğraf açılamadı.")
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IOException("Bu görüntü biçimi desteklenmiyor.")
        if (w.toLong() * h.toLong() > 150_000_000L)
            throw IOException("Fotoğraf çözünürlüğü güvenli sınırın üzerinde.")
        var sample = 1
        while (maxOf(w, h) / sample > maxSide * 1.4f) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("Fotoğraf belleğe alınamadı.")
        val degrees = runCatching {
            context.contentResolver.openInputStream(source)?.use {
                when (ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        val rotated = if (degrees != 0) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(degrees.toFloat()) }, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded
        val longest = maxOf(rotated.width, rotated.height)
        if (longest <= maxSide) return rotated
        val scale = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            rotated, (rotated.width * scale).toInt().coerceAtLeast(1),
            (rotated.height * scale).toInt().coerceAtLeast(1), true
        ).also { if (it !== rotated) rotated.recycle() }
    }

    fun rotateRight(bitmap: Bitmap): Bitmap = Bitmap.createBitmap(
        bitmap, 0, 0, bitmap.width, bitmap.height,
        Matrix().apply { postRotate(90f) }, true
    )

    @Throws(IOException::class)
    fun crop(bitmap: Bitmap, left: Float, top: Float, right: Float, bottom: Float): Bitmap {
        val x = (left.coerceIn(0f, 1f) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (top.coerceIn(0f, 1f) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val rx = (right.coerceIn(0f,1f) * bitmap.width).toInt().coerceIn(x + 1, bitmap.width)
        val by = (bottom.coerceIn(0f,1f) * bitmap.height).toInt().coerceIn(y + 1, bitmap.height)
        if ((rx-x) < 100 || (by-y) < 100)
            throw IOException("Soru kırpma alanı çok küçük.")
        return Bitmap.createBitmap(bitmap, x, y, rx-x, by-y)
    }

    @Throws(IOException::class)
    fun persist(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, FOLDER)
        if (!dir.isDirectory && !dir.mkdirs())
            throw IOException("Fotoğraf saklama alanı oluşturulamadı.")
        val name = UUID.randomUUID().toString() + ".jpg"
        val tmp = File(dir, "$name.pending")
        val destination = File(dir, name)
        try {
            tmp.outputStream().buffered().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 91, output))
                    throw IOException("Soru fotoğrafı sıkıştırılamadı.")
            }
            if (tmp.length() <= 0L || tmp.length() > 5L * 1024 * 1024)
                throw IOException("Fotoğraf 5 MB sınırını aşıyor. Daha dar bir alan seç.")
            if (!tmp.renameTo(destination))
                throw IOException("Soru fotoğrafı güvenli biçimde kaydedilemedi.")
            return name
        } finally { tmp.delete() }
    }

    fun discard(context: Context, name: String) {
        asset(context, name)?.delete()
    }
}
