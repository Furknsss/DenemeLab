package com.denemelab.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlin.math.max

/**
 * Exam publisher records are not tied to one examination: choose a publisher
 * once, reuse the app-private logo for every later exam by that publisher.
 * No image URLs or external permissions are stored. Older exams remain valid.
 */
data class PublisherBrand(
    val key: String,
    val displayName: String,
    val logoFile: String = ""
)

object PublisherCatalog {
    private const val KEY = "publisher_catalog_v1"
    private const val LOGO_DIR = "publisher-logos"
    private const val MAX_BYTES = 8 * 1024 * 1024
    private val tr = Locale.forLanguageTag("tr-TR")

    fun normalize(raw: String): String {
        val withoutDots = raw.trim().lowercase(tr).replace('ı', 'i')
        val noAccents = Normalizer.normalize(withoutDots, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
        return noAccents.replace(Regex("[^a-z0-9]+"), " ").trim()
            .replace(Regex("\\s+"), " ")
    }

    fun decode(raw: String): List<PublisherBrand> = runCatching {
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { i ->
            val v = json.getJSONObject(i)
            val name = v.optString("name").trim()
            val key = normalize(name)
            if (key.isBlank()) null else PublisherBrand(
                key, name, safeFileName(v.optString("logoFile"))
            )
        }.distinctBy { it.key }.sortedBy { it.displayName.lowercase(tr) }
    }.getOrElse { emptyList() }

    fun encode(items: List<PublisherBrand>): String = JSONArray().apply {
        items.forEach { item ->
            put(JSONObject().put("name", item.displayName)
                .put("logoFile", safeFileName(item.logoFile)))
        }
    }.toString()

    fun list(context: Context): List<PublisherBrand> =
        decode(context.getSharedPreferences("denemelab_local_v1", Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]")

    fun find(context: Context, name: String): PublisherBrand? {
        val normalized = normalize(name)
        return if (normalized.isEmpty()) null else list(context).firstOrNull { it.key == normalized }
    }

    fun logoFile(context: Context, publisher: PublisherBrand): File? {
        val name = safeFileName(publisher.logoFile).takeIf { it.isNotBlank() } ?: return null
        val file = File(File(context.filesDir, LOGO_DIR), name)
        return file.takeIf { it.isFile }
    }

    /**
     * An image is always COPIED into DenemeLab's own files directory. Closing
     * the gallery, rebooting the phone or changing the original image cannot
     * break a saved publisher logo. Only this app can read the copied bitmap.
     *
     * Fails loudly if the image is invalid or if durable catalog write fails.
     * Does not claim success while the logo still lives in an external URI.
     */
    @Throws(IOException::class)
    fun upsert(context: Context, publisherName: String, imageUri: Uri?): PublisherBrand {
        val display = publisherName.trim().take(90)
        val key = normalize(display)
        if (key.isBlank()) throw IOException("Yayın adı boş olamaz.")
        val existing = find(context, display)
        var created: File? = null
        try {
            val fileName = if (imageUri != null) {
                created = writeLogo(context, imageUri)
                requireNotNull(created).name
            } else existing?.logoFile.orEmpty()

            val entry = PublisherBrand(key, display, fileName)
            val others = list(context).filterNot { it.key == key }
            val prefs = context.getSharedPreferences("denemelab_local_v1", Context.MODE_PRIVATE)
            if (!prefs.edit().putString(KEY, encode(others + entry)).commit())
                throw IOException("Yayın bilgileri kaydedilemedi.")
            // Only discard previous copied artwork AFTER the new record is safe.
            if (created != null && existing != null && existing.logoFile != fileName) {
                logoFile(context, existing)?.delete()
            }
            return entry
        } catch (e: Exception) {
            created?.delete()
            throw if (e is IOException) e else IOException("Yayın logosu okunamadı.", e)
        }
    }

    private fun safeFileName(value: String): String {
        val n = value.trim()
        return n.takeIf { it.matches(Regex("[a-f0-9-]{36}\\.png")) } ?: ""
    }

    @Throws(IOException::class)
    private fun writeLogo(context: Context, uri: Uri): File {
        val out = ByteArrayOutputStream()
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Seçilen görsel açılamadı.")
        stream.use { input ->
            val chunk = ByteArray(8192)
            var count = 0
            while (true) {
                val read = input.read(chunk)
                if (read == -1) break
                count += read
                if (count > MAX_BYTES)
                    throw IOException("Logo çok büyük. 8 MB altında bir görsel seç.")
                out.write(chunk, 0, read)
            }
        }
        val bytes = out.toByteArray()
        if (bytes.isEmpty()) throw IOException("Logo dosyası boş.")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
            throw IOException("Seçilen dosya geçerli bir görsel değil.")
        val opts = BitmapFactory.Options()
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 768) sample *= 2
        opts.inSampleSize = sample
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: throw IOException("Logo görseli çözümlenemedi.")
        val scale = minOf(1.0, 512.0 / max(bitmap.width, bitmap.height))
        val scaled = if (scale < 1) {
            Bitmap.createScaledBitmap(bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()), true)
        } else bitmap
        val folder = File(context.filesDir, LOGO_DIR)
        if (!folder.exists() && !folder.mkdirs()) throw IOException("Logo klasörü oluşturulamadı.")
        val destination = File(folder, UUID.randomUUID().toString() + ".png")
        try {
            destination.outputStream().use {
                if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, it))
                    throw IOException("Logo kaydedilemedi.")
            }
            if (!destination.isFile || destination.length() == 0L)
                throw IOException("Logo dosyası oluşturulamadı.")
        } catch (e: Exception) {
            destination.delete()
            throw e
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
        return destination
    }
}
