package com.denemelab.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Cloud-only settings. The legacy ZIP archive encoder is an internal format
 * for Firestore snapshots, not a second Google Drive upload workflow.
 */
@Composable
fun BackupSettingsDialog(onDismiss: () -> Unit, onRestored: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(0.95f).heightIn(max = 680.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 13.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Kapat",
                            tint = Color(0xFF181818))
                    }
                }
                Column(
                    modifier = Modifier.heightIn(max = 606.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                ) {
                    FirebaseCloudSection(onRestored = onRestored)
                    Spacer(Modifier.height(10.dp))
                    LegacyBackupMigration(onRestored = onRestored)
                }
            }
        }
    }
}
