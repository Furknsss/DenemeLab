package com.denemelab.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.IOException
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Copies the avatar into app storage so platform backup can recover it. */
object ProfilePictureStore {
    @Throws(IOException::class)
    fun persist(context: Context, rawUri: String): String {
        if (rawUri.isBlank()) return ""
        val uri = Uri.parse(rawUri)
        val original = uri.path?.let { File(it) }
        if (uri.scheme == "file" &&
            original?.parentFile?.canonicalPath ==
                File(context.filesDir, "profile-pictures").canonicalPath &&
            original.isFile) return rawUri

        val resolver = context.contentResolver
        val bytes = try {
            resolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (out.size() + read > 12 * 1024 * 1024)
                        throw IOException("Profil fotoğrafı çok büyük (12 MB üstü).")
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
        } catch (e: Exception) {
            throw IOException("Profil fotoğrafı cihaz belleğine kopyalanamadı.", e)
        } ?: throw IOException("Profil fotoğrafı açılamadı.")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw IOException("Profil fotoğrafı geçerli bir görsel değil.")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Profil fotoğrafı çözümlenemedi.")
        val dir = File(context.filesDir, "profile-pictures")
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Profil fotoğrafı klasörü oluşturulamadı.")
        val file = File(dir, UUID.randomUUID().toString() + ".png")
        try {
            file.outputStream().use {
                if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it))
                    throw IOException("Profil fotoğrafı kaydedilemedi.")
            }
        } finally { bmp.recycle() }
        return Uri.fromFile(file).toString()
    }
}
