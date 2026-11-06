package com.denemelab.preview

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Opt-in only. The first backup is always initiated by the user; subsequent
 * local saves coalesce into one network-constrained background upload.
 * WorkManager persists pending uploads across ordinary process death.
 */
object FirebaseBackupScheduler {
    fun onLocalChange(context: Context) {
        if (!FirebaseCloudBackup.autoEnabled(context)) return
        val task = OneTimeWorkRequestBuilder<FirebaseUploadWorker>()
            .setInitialDelay(45, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "denemelab-firebase-cloud-snapshot",
            ExistingWorkPolicy.REPLACE,
            task
        )
    }
}

class FirebaseUploadWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    override suspend fun doWork(): Result {
        if (!FirebaseCloudBackup.autoEnabled(applicationContext)) return Result.success()
        return try {
            FirebaseCloudBackup.upload(applicationContext)
            Result.success()
        } catch (e: IOException) {
            // A size or configuration problem cannot be solved with network retries.
            if (e.message?.contains("8 MB") == true ||
                e.message?.contains("Buluttaki yedek") == true ||
                e.message?.contains("önceki sürümü eksik") == true ||
                runAttemptCount >= 5)
                Result.failure()
            else Result.retry()
        } catch (_: Exception) {
            if (runAttemptCount >= 5) Result.failure() else Result.retry()
        }
    }
}
