package com.denemelab.preview

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class WorkspaceResult(
    val ready: Boolean,
    val needsChoice: Boolean = false,
    val message: String = ""
)

/**
 * User-owned local workspace plus private verified Firestore snapshots.
 *
 * Identity and user preferences are separate: cloud backup only captures the
 * current user's local data, and switching accounts preserves the former
 * user's local archive BEFORE any restore/clear operation.
 */
object AccountWorkspace {
    private const val GUARD = "denemelab_account_workspace_v1"
    private const val DATA = "denemelab_local_v1"

    private fun checkSignedIn(uid: String) {
        if (FirebaseAuth.getInstance().currentUser?.uid != uid)
            throw IOException("Oturum değişti. Hesabına tekrar giriş yap.")
    }

    private fun guard(context: Context) =
        context.getSharedPreferences(GUARD, Context.MODE_PRIVATE)

    fun owner(context: Context): String? =
        guard(context).getString("ownerUid", null)?.takeIf { it.isNotBlank() }

    private fun pendingReview(context: Context, uid: String): Boolean =
        guard(context).getString("reviewUid", "") == uid

    private fun markReview(context: Context, uid: String, needsReview: Boolean) {
        val saved = guard(context).edit()
            .putString("reviewUid", if (needsReview) uid else "")
            .commit()
        if (!saved) throw IOException("Bulut çakışması güvenlik durumu kaydedilemedi.")
    }

    fun hasLocalData(context: Context): Boolean =
        context.getSharedPreferences(DATA, Context.MODE_PRIVATE).all.isNotEmpty()

    private fun accountFile(context: Context, label: String): File {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(label.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val directory = File(context.filesDir, "denemelab-account-workspaces")
        if (!directory.isDirectory && !directory.mkdirs())
            throw IOException("Hesap verileri için güvenli klasör oluşturulamadı.")
        return File(directory, hash + ".zip")
    }

    private fun saveCurrent(context: Context, label: String) {
        val archived = DenemeLabBackup.createArchive(context)
        try {
            DenemeLabBackup.inspect(archived)
            val destination = accountFile(context, label)
            val staged = File(destination.parentFile, destination.name + ".tmp")
            archived.copyTo(staged, overwrite = true)
            DenemeLabBackup.inspect(staged)
            if (!staged.renameTo(destination)) {
                staged.delete()
                throw IOException("Önceki hesap verileri korunamadı. Hesap değiştirilmedi.")
            }
        } finally { archived.delete() }
    }

    private fun emptyArchive(context: Context): File {
        val file = File.createTempFile("denemelab-empty-workspace-", ".zip", context.cacheDir)
        try {
            val json = JSONObject().put("version", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("preferences", JSONObject()).toString()
            ZipOutputStream(file.outputStream()).use {
                it.putNextEntry(ZipEntry("data.json"))
                it.write(json.toByteArray(Charsets.UTF_8))
                it.closeEntry()
            }
            DenemeLabBackup.inspect(file)
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    private fun restoreSafely(context: Context, source: File) {
        DenemeLabBackup.inspect(source)
        DenemeLabBackup.restore(context, source)
    }

    private fun fillMissingName(context: Context) {
        val userName = FirebaseAuth.getInstance().currentUser?.displayName
            ?.trim()?.split(Regex("\\s+"))?.firstOrNull().orEmpty()
        if (userName.isBlank()) return
        val profile = LocalData.loadProfile(context)
        if (profile.firstName.isBlank()) {
            LocalData.saveProfile(context, profile.copy(firstName = userName))
        }
    }

    private fun assign(context: Context, uid: String) {
        if (!guard(context).edit().putString("ownerUid", uid).commit())
            throw IOException("Hesap sahipliği kaydedilemedi.")
    }

    /**
     * null choice means safe automatic behaviour. If legacy local data and
     * remote data BOTH exist, request an explicit choice instead of wiping
     * either. preferCloud=true archives legacy and restores remote.
     */
    suspend fun prepare(
        context: Context, uid: String, preferCloud: Boolean? = null
    ): WorkspaceResult = withContext(Dispatchers.IO) {
        checkSignedIn(uid)
        val previousOwner = owner(context)

        if (previousOwner == uid) {
            if (pendingReview(context, uid) && !FirebaseCloudBackup.autoEnabled(context)) {
                return@withContext WorkspaceResult(true,
                    message = "Yerel ve bulut verileri ayrı korunuyor. Bulut yedeğini incele.")
            }
            if (pendingReview(context, uid)) markReview(context, uid, false)
            if (!hasLocalData(context)) {
                val cloud = FirebaseCloudBackup.current(context)
                if (cloud != null) {
                    val (file, _) = FirebaseCloudBackup.download(context)
                    try { restoreSafely(context, file) } finally { file.delete() }
                } else FirebaseCloudBackup.upload(context)
            }
            FirebaseCloudBackup.setAuto(context, true)
            fillMissingName(context)
            return@withContext WorkspaceResult(true)
        }

        // Read remote availability BEFORE changing local state.
        val cloud = FirebaseCloudBackup.current(context)
        val localExists = hasLocalData(context)

        if (previousOwner == null && localExists && cloud != null && preferCloud == null)
            return@withContext WorkspaceResult(false, needsChoice = true,
                message = "Bu cihazda eski kayıtlar ve hesabında bulut yedeği var.")

        if (previousOwner == null && cloud == null) {
            // First account on a legacy installation: upload all old records
            // before assigning ownership. Nothing is silently discarded.
            FirebaseCloudBackup.upload(context)
            checkSignedIn(uid)
            assign(context, uid)
            FirebaseCloudBackup.setAuto(context, true)
            fillMissingName(context)
            return@withContext WorkspaceResult(true,
                message = if (localExists) "Eski kayıtların buluta yedeklendi."
                    else "Hesabın hazır.")
        }

        if (previousOwner == null && localExists && preferCloud == false) {
            // The remote archive is untouched; avoid syncing this divergent
            // local workspace until user consciously reconciles it.
            checkSignedIn(uid)
            assign(context, uid)
            FirebaseCloudBackup.setAuto(context, false)
            markReview(context, uid, true)
            fillMissingName(context)
            return@withContext WorkspaceResult(true,
                message = "Cihaz kayıtların korundu. Bulut yedeği değiştirilmedi.")
        }

        if (previousOwner != null) {
            // Never leave another student's data visible after a login.
            saveCurrent(context, previousOwner)
        } else if (localExists) {
            saveCurrent(context, "legacy-before-" + uid)
        }
        val cached = if (previousOwner != null) accountFile(context, uid) else null
        if (cached?.isFile == true) {
            restoreSafely(context, cached)
        } else if (cloud != null) {
            val (file, _) = FirebaseCloudBackup.download(context)
            try { restoreSafely(context, file) } finally { file.delete() }
        } else {
            val file = emptyArchive(context)
            try { restoreSafely(context, file) } finally { file.delete() }
        }

        checkSignedIn(uid)
        if (cloud == null) FirebaseCloudBackup.upload(context)
        assign(context, uid)
        // With both a cached device archive and a remote snapshot, keep both
        // intact until the student checks them in cloud settings.
        val needsReconcile = cached?.isFile == true && cloud != null
        FirebaseCloudBackup.setAuto(context, !needsReconcile)
        markReview(context, uid, needsReconcile)
        fillMissingName(context)
        WorkspaceResult(true, message = if (needsReconcile)
            "Bu cihazdaki hesap verilerin açıldı. Bulut sürümünü ayarlardan incele."
            else if (cloud != null) "Bulut kayıtların hesabına geri yüklendi."
            else "Hesabın hazır.")
    }
}
