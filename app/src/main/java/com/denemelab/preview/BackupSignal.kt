package com.denemelab.preview

import android.app.backup.BackupManager
import android.content.Context

/**
 * Advises Android's backup scheduler that local study data changed.
 * This is not an immediate cloud upload; transport, quota and restore policy
 * remain under Android and the student's device/account settings.
 */
object BackupSignal {
    fun changed(context: Context) {
        runCatching { BackupManager(context).dataChanged() }
        runCatching { FirebaseBackupScheduler.onLocalChange(context) }
    }
}
