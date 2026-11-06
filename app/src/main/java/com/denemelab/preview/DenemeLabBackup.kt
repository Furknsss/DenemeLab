package com.denemelab.preview

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupSummary(
    val createdAt: Long,
    val examCount: Int,
    val questionCount: Int,
    val publisherLogoCount: Int
)

/**
 * Portable, user-chosen Drive/file backup. No account or server secret required.
 * Import never silently runs and the existing SharedPreferences are only
 * replaced after the entire archive and every referenced image is validated.
 */
object DenemeLabBackup {
    private const val PREFS = "denemelab_local_v1"
    private const val MAX_ENTRY = 16 * 1024 * 1024
    private const val MAX_TOTAL = 48L * 1024 * 1024
    private const val MAX_ENTRIES = 180
    private val safeLogo = Regex("[a-f0-9-]{36}\\.png")

    private fun snapshot(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val values = JSONObject()
        prefs.all.forEach { (key, value) ->
            val type = when (value) {
                is String -> "string"
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                else -> null
            }
            if (type != null) values.put(key, JSONObject()
                .put("type", type).put("value", value))
        }
        return JSONObject().put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("preferences", values)
    }

    fun createArchive(context: Context): File {
        val file = File.createTempFile("denemelab-backup-", ".zip", context.cacheDir)
        try {
            val data = snapshot(context)
            ZipOutputStream(file.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(data.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                PublisherCatalog.decode(prefs.getString("publisher_catalog_v1", "[]") ?: "[]")
                    .filter { it.logoFile.isNotEmpty() }.distinctBy { it.logoFile }
                    .forEach { brand ->
                        if (!safeLogo.matches(brand.logoFile))
                            throw IOException("Logo dosyası güvenli değil: " + brand.displayName)
                        val logo = File(File(context.filesDir, "publisher-logos"), brand.logoFile)
                        if (!logo.isFile || logo.length() <= 0L || logo.length() > MAX_ENTRY)
                            throw IOException("Yayın logosu arşivlenemedi: " + brand.displayName)
                        zip.putNextEntry(ZipEntry("logos/" + brand.logoFile))
                        logo.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }

                val pictureUri = prefs.getString("studentPictureUri", "").orEmpty()
                if (pictureUri.isNotBlank()) {
                    val photo = try { context.contentResolver.openInputStream(Uri.parse(pictureUri)) }
                    catch (e: Exception) { null }
                    if (photo == null) throw IOException(
                        "Profil fotoğrafına ulaşılamıyor. Önce Profilim'den fotoğrafı yeniden seç.")
                    zip.putNextEntry(ZipEntry("profile/picture.png"))
                    photo.use { input ->
                        val buffer = ByteArray(8192)
                        var copied = 0
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            copied += n
                            if (copied > MAX_ENTRY) throw IOException("Profil görseli çok büyük.")
                            zip.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry()
                }
            }
            if (file.length() <= 0 || file.length() > MAX_TOTAL)
                throw IOException("Yedek 48 MB sınırını aşıyor.")
            return file
        } catch (e: Exception) {
            file.delete()
            if (e is IOException) throw e
            throw IOException("Yedek oluşturulamadı.", e)
        }
    }

    @Throws(IOException::class)
    fun exportTo(context: Context, destination: Uri): BackupSummary {
        val file = createArchive(context)
        try {
            val summary = inspect(file)
            val sink = context.contentResolver.openOutputStream(destination, "wt")
                ?: throw IOException("Seçilen konuma yazma izni verilmedi.")
            sink.use { output -> file.inputStream().use { it.copyTo(output) } }
            return summary
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw IOException("Yedek konuma yazılamadı.", e)
        } finally { file.delete() }
    }

    @Throws(IOException::class)
    fun stageImport(context: Context, source: Uri): File {
        val file = File.createTempFile("denemelab-restore-", ".zip", context.cacheDir)
        try {
            val stream = context.contentResolver.openInputStream(source)
                ?: throw IOException("Yedek dosyası açılamadı.")
            stream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(16384)
                    var size = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        size += n
                        if (size > MAX_TOTAL) throw IOException("Yedek dosyası çok büyük.")
                        output.write(buffer, 0, n)
                    }
                }
            }
            inspect(file)
            return file
        } catch (e: Exception) {
            file.delete()
            if (e is IOException) throw e
            throw IOException("Yedek okunamadı.", e)
        }
    }

    private data class ReadResult(val preferences: JSONObject, val assets: Map<String, ByteArray>,
                                  val summary: BackupSummary)

    fun inspect(file: File): BackupSummary = readArchive(file).summary

    private fun readArchive(file: File): ReadResult {
        val assets = mutableMapOf<String, ByteArray>()
        var manifest: JSONObject? = null
        var total = 0L
        var count = 0
        try {
            ZipInputStream(file.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    if (count > MAX_ENTRIES) throw IOException("Yedekte çok fazla dosya var.")
                    val name = entry.name
                    if (entry.isDirectory ||
                        !(name == "data.json" || name == "profile/picture.png" ||
                            (name.startsWith("logos/") && safeLogo.matches(name.removePrefix("logos/"))))
                    ) throw IOException("Yedekte beklenmeyen dosya var.")
                    if (assets.containsKey(name)) throw IOException("Yinelenen yedek dosyası.")
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        if (out.size() + n > MAX_ENTRY || total > MAX_TOTAL)
                            throw IOException("Yedek boyutu güvenlik sınırını aşıyor.")
                        out.write(buffer, 0, n)
                    }
                    if (name == "data.json") {
                        if (manifest != null) throw IOException("Yinelenen manifest.")
                        manifest = JSONObject(out.toString("UTF-8"))
                    } else assets[name] = out.toByteArray()
                    zip.closeEntry()
                }
            }
            val metadata = manifest ?: throw IOException("Yedekte veri bilgisi yok.")
            if (metadata.optInt("version") != 1)
                throw IOException("Bu yedek sürümü desteklenmiyor.")
            val prefs = metadata.getJSONObject("preferences")
            fun checkedArray(key: String): Int {
                val value = prefs.optJSONObject(key)?.optString("value", "[]") ?: "[]"
                return JSONArray(value).length()
            }
            val examCount = checkedArray("exams")
            val questionCount = checkedArray("questions")
            checkedArray("focus_completions_v1")
            checkedArray("publisher_catalog_v1")
            val brands = PublisherCatalog.decode(
                prefs.optJSONObject("publisher_catalog_v1")?.optString("value", "[]") ?: "[]")
            brands.filter { it.logoFile.isNotBlank() }.forEach {
                if (!assets.containsKey("logos/" + it.logoFile))
                    throw IOException("Yedek eksik logo içeriyor: " + it.displayName)
            }
            val avatar = prefs.optJSONObject("studentPictureUri")?.optString("value").orEmpty()
            if (avatar.isNotBlank() && !assets.containsKey("profile/picture.png"))
                throw IOException("Yedekte profil fotoğrafı eksik.")
            assets.forEach { (key, bytes) ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (bytes.isEmpty() || options.outWidth <= 0 || options.outHeight <= 0)
                    throw IOException("Yedekte bozuk görsel var: " + key)
            }
            return ReadResult(prefs, assets,
                BackupSummary(metadata.getLong("createdAt"), examCount, questionCount,
                    brands.count { it.logoFile.isNotBlank() }))
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw IOException("Yedek dosyası bozuk veya DenemeLab biçiminde değil.", e)
        }
    }

    /**
     * Caller has already displayed the summary and obtained confirmation.
     * Source validation finishes before writing the next local preference set.
     */
    @Throws(IOException::class)
    fun restore(context: Context, file: File): BackupSummary {
        val archive = readArchive(file)
        val written = mutableListOf<File>()
        try {
            fun writeAsset(folder: String, bytes: ByteArray): File {
                val directory = File(context.filesDir, folder)
                if (!directory.exists() && !directory.mkdirs())
                    throw IOException("Yedek görseli için klasör oluşturulamadı.")
                val destination = File(directory, UUID.randomUUID().toString() + ".png")
                destination.outputStream().use { it.write(bytes) }
                written.add(destination)
                return destination
            }
            val restoredValues = mutableMapOf<String, Any>()
            val prefKeys = archive.preferences.keys()
            while (prefKeys.hasNext()) {
                val key = prefKeys.next()
                val record = archive.preferences.getJSONObject(key)
                restoredValues[key] = when (record.getString("type")) {
                    "string" -> record.getString("value")
                    "boolean" -> record.getBoolean("value")
                    "int" -> record.getInt("value")
                    "long" -> record.getLong("value")
                    "float" -> record.getDouble("value").toFloat()
                    else -> throw IOException("Bilinmeyen yedek alan tipi.")
                }
            }

            val brands = PublisherCatalog.decode(
                restoredValues["publisher_catalog_v1"] as? String ?: "[]")
            val newNames = mutableMapOf<String, String>()
            brands.filter { it.logoFile.isNotBlank() }.forEach { brand ->
                if (!newNames.containsKey(brand.logoFile)) {
                    val data = archive.assets["logos/" + brand.logoFile]
                        ?: throw IOException("Logo görseli eksik.")
                    newNames[brand.logoFile] = writeAsset("publisher-logos", data).name
                }
            }
            restoredValues["publisher_catalog_v1"] = PublisherCatalog.encode(
                brands.map { it.copy(logoFile = newNames[it.logoFile] ?: "") })
            val picture = archive.assets["profile/picture.png"]
            if (picture != null) {
                restoredValues["studentPictureUri"] =
                    Uri.fromFile(writeAsset("profile-pictures", picture)).toString()
            }

            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = preferences.edit().clear()
            restoredValues.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            if (!editor.commit()) throw IOException("Geri yükleme diske yazılamadı.")
            return archive.summary
        } catch (e: Exception) {
            written.forEach { it.delete() }
            if (e is IOException) throw e
            throw IOException("Veriler geri yüklenemedi. Mevcut kayıtlar korundu.", e)
        }
    }
}
