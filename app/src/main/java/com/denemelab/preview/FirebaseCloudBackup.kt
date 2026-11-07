package com.denemelab.preview

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class FirebaseBackupInfo(
    val id: String,
    val summary: BackupSummary,
    val byteSize: Long,
    val sha256: String
)

/**
 * Account-owned, manually recoverable cloud snapshots.
 * The backup is not a two-way live merge: another device must explicitly
 * inspect and confirm restore, preventing an empty device from erasing history.
 *
 * Uses Firestore instead of Cloud Storage to remain usable on Spark plans.
 * Each immutable generation consists of independent small Firestore documents;
 * the authoritative pointer moves only AFTER every part is uploaded.
 */
object FirebaseCloudBackup {
    private const val PREFS = "denemelab_cloud_settings_v1"
    private const val MAX_ARCHIVE_BYTES = 8 * 1024 * 1024
    private const val PART_BYTES = 160 * 1024

    fun configured(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun accountEmail(context: Context): String? =
        if (configured(context)) FirebaseAuth.getInstance().currentUser?.email else null

    private fun uid(context: Context): String {
        if (!configured(context)) throw IOException(
            "Firebase projesi bağlanmamış. google-services.json yapılandırması gerekiyor.")
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IOException("Bulut için önce DenemeLab hesabına giriş yap.")
    }

    fun autoEnabled(context: Context): Boolean {
        if (!configured(context)) return false
        val current = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("enabled", false) && prefs.getString("uid", "") == current
    }

    fun setAuto(context: Context, enabled: Boolean) {
        val current = uid(context)
        if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("uid", current).putBoolean("enabled", enabled).commit())
            throw IOException("Otomatik yedekleme tercihi kaydedilemedi.")
    }

    suspend fun signInWithGoogleToken(context: Context, idToken: String): String {
        if (!configured(context)) throw IOException("Firebase projesi henüz bağlı değil.")
        if (idToken.isBlank()) throw IOException("Google kimlik bilgisi boş.")
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = FirebaseAuth.getInstance().signInWithCredential(credential).awaitTask()
        return result.user?.email
            ?: throw IOException("Google hesabı Firebase'e bağlanamadı.")
    }

    suspend fun signInWithEmail(context: Context, email: String, password: String): String {
        if (!configured(context)) throw IOException("Firebase yapılandırması eksik.")
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank() && password.isNotBlank()) { "E-posta ve şifre gerekli." }
        val result = FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(cleanEmail, password).awaitTask()
        return result.user?.uid ?: throw IOException("Oturum açılamadı.")
    }

    suspend fun registerWithEmail(context: Context, email: String, password: String): String {
        if (!configured(context)) throw IOException("Firebase yapılandırması eksik.")
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank() && password.length >= 8) {
            "Geçerli bir e-posta ve en az 8 karakterlik şifre gerekli."
        }
        val result = FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(cleanEmail, password).awaitTask()
        return result.user?.uid ?: throw IOException("Kayıt oluşturulamadı.")
    }

    suspend fun sendPasswordReset(context: Context, email: String) {
        if (!configured(context)) throw IOException("Firebase yapılandırması eksik.")
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())
            throw IllegalArgumentException("Geçerli bir e-posta gir.")
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.trim()).awaitTask()
    }

    fun accountUid(context: Context): String? =
        if (configured(context)) FirebaseAuth.getInstance().currentUser?.uid else null

    fun signOut(context: Context) {
        if (configured(context)) FirebaseAuth.getInstance().signOut()
    }

    private fun userRoot(context: Context) =
        FirebaseFirestore.getInstance().collection("users").document(uid(context))

    suspend fun current(context: Context): FirebaseBackupInfo? = withContext(Dispatchers.IO) {
        val root = userRoot(context)
        val pointer = root.collection("metadata").document("current")
            .get(Source.SERVER).awaitTask()
        val id = pointer.getString("id") ?: return@withContext null
        val doc = root.collection("backups").document(id)
            .get(Source.SERVER).awaitTask()
        if (!doc.exists()) throw IOException("Bulut yedeğinin bilgi kaydı bulunamadı.")
        val summary = BackupSummary(
            createdAt = doc.getLong("createdAt") ?: 0L,
            examCount = (doc.getLong("exams") ?: 0L).toInt(),
            questionCount = (doc.getLong("questions") ?: 0L).toInt(),
            publisherLogoCount = (doc.getLong("logos") ?: 0L).toInt()
        )
        FirebaseBackupInfo(id, summary, doc.getLong("byteSize") ?: 0L,
            doc.getString("sha256") ?: "")
    }

    suspend fun upload(context: Context): FirebaseBackupInfo = withContext(Dispatchers.IO) {
        val root = userRoot(context)
        val archive = DenemeLabBackup.createArchive(context)
        try {
            if (archive.length() !in 1L..MAX_ARCHIVE_BYTES.toLong())
                throw IOException(
                    "Bulut yedeği 8 MB sınırını aşıyor. Logo sayısını veya görsel boyutlarını azalt.")
            val summary = DenemeLabBackup.inspect(archive)
            val bytes = archive.readBytes()
            val hash = sha256(bytes)
            val parts = bytes.asList().chunked(PART_BYTES).map { it.toByteArray() }
            val pointer = root.collection("metadata").document("current")
            val before = pointer.get(Source.SERVER).awaitTask()
            val previous = before.getString("id").orEmpty()
            val oldest = before.getString("previous").orEmpty()
            if (previous.isNotBlank()) {
                val existing = root.collection("backups").document(previous)
                    .get(Source.SERVER).awaitTask()
                if (!existing.exists())
                    throw IOException("Bulut yedeğinin önceki sürümü eksik. Üzerine yazılmadı.")
                val oldExams = (existing.getLong("exams") ?: 0L).toInt()
                val oldQuestions = (existing.getLong("questions") ?: 0L).toInt()
                val oldLogos = (existing.getLong("logos") ?: 0L).toInt()
                if (summary.examCount < oldExams ||
                    summary.questionCount < oldQuestions ||
                    summary.publisherLogoCount < oldLogos) {
                    throw IOException(
                        "Buluttaki yedek bu telefondakinden daha fazla kayıt içeriyor. " +
                        "Önce 'Buluttan yedeği incele ve geri yükle' seçeneğini kullan. " +
                        "Güvenlik için mevcut yedek değiştirilmedi.")
                }
            }
            val id = UUID.randomUUID().toString()
            val backup = root.collection("backups").document(id)

            parts.forEachIndexed { index, part ->
                backup.collection("parts").document(index.toString().padStart(3, '0'))
                    .set(mapOf(
                        "index" to index,
                        "base64" to Base64.encodeToString(part, Base64.NO_WRAP)
                    )).awaitTask()
            }
            backup.set(mapOf(
                "createdAt" to summary.createdAt,
                "exams" to summary.examCount,
                "questions" to summary.questionCount,
                "logos" to summary.publisherLogoCount,
                "byteSize" to bytes.size,
                "sha256" to hash,
                "partCount" to parts.size
            )).awaitTask()
            // This is the only operation that promotes a new backup to current.
            pointer.set(mapOf(
                "id" to id,
                "previous" to previous,
                "uploadedAt" to System.currentTimeMillis()
            )).awaitTask()
            // Retain the immediately previous complete generation as fallback.
            // Cleanup is best effort; a cleanup error must not mark upload failed.
            if (oldest.isNotBlank() && oldest != previous && oldest != id) {
                runCatching {
                    val old = root.collection("backups").document(oldest)
                    val oldMeta = old.get(Source.SERVER).awaitTask()
                    val count = (oldMeta.getLong("partCount") ?: 0).toInt().coerceIn(0, 100)
                    repeat(count) { index ->
                        old.collection("parts").document(index.toString().padStart(3, '0'))
                            .delete().awaitTask()
                    }
                    old.delete().awaitTask()
                }
            }
            FirebaseBackupInfo(id, summary, bytes.size.toLong(), hash)
        } finally { archive.delete() }
    }

    suspend fun download(context: Context): Pair<File, FirebaseBackupInfo> =
        withContext(Dispatchers.IO) {
            val info = current(context)
                ?: throw IOException("Bu hesapta henüz bir bulut yedeği yok.")
            val root = userRoot(context)
            val meta = root.collection("backups").document(info.id)
                .get(Source.SERVER).awaitTask()
            val count = (meta.getLong("partCount") ?: 0).toInt()
            if (count !in 1..64 || info.byteSize !in 1..MAX_ARCHIVE_BYTES.toLong())
                throw IOException("Bulut yedeğinin boyut bilgileri geçersiz.")
            val file = File.createTempFile("denemelab-cloud-restore-", ".zip", context.cacheDir)
            try {
                file.outputStream().use { output ->
                    repeat(count) { index ->
                        val doc = root.collection("backups").document(info.id)
                            .collection("parts").document(index.toString().padStart(3, '0'))
                            .get(Source.SERVER).awaitTask()
                        val data = doc.getString("base64")
                            ?: throw IOException("Bulut yedeğinde eksik bölüm: ${index + 1}")
                        val part = Base64.decode(data, Base64.NO_WRAP)
                        if (part.size > PART_BYTES || file.length() + part.size > MAX_ARCHIVE_BYTES)
                            throw IOException("Bulut yedeği güvenlik sınırını aşıyor.")
                        output.write(part)
                    }
                }
                if (file.length() != info.byteSize || sha256(file.readBytes()) != info.sha256)
                    throw IOException("Bulut yedeğinin bütünlük kontrolü başarısız.")
                val checked = DenemeLabBackup.inspect(file)
                if (checked.examCount != info.summary.examCount ||
                    checked.questionCount != info.summary.questionCount)
                    throw IOException("Bulut yedeği sayıları uyuşmuyor.")
                file to info
            } catch (e: Exception) {
                file.delete()
                throw e
            }
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
            addOnCanceledListener {
                if (continuation.isActive) continuation.cancel()
            }
        }
}
