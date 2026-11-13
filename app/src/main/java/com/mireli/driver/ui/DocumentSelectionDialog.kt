package com.mireli.driver.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun DocumentSelectionDialog(
    title: String,
    uri: Uri,
    expires: String,
    canUpload: Boolean,
    onRemove: () -> Unit,
    onUpload: () -> Unit,
) {
    val resolver = LocalContext.current.contentResolver
    val filename by produceState<String?>(null, uri, resolver) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.take(180)?.takeIf { it.isNotBlank() } ?: "Selected file"
        }
    }

    AlertDialog(
        onDismissRequest = onRemove,
        title = { Text("Review file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(filename ?: "Reading filename…")
                if (expires.isNotBlank()) Text("Expires $expires", style = MaterialTheme.typography.labelLarge)
            }
        },
        confirmButton = {
            TextButton(enabled = canUpload && filename != null, onClick = onUpload) { Text("Upload file") }
        },
        dismissButton = { TextButton(onClick = onRemove) { Text("Remove selected file") } },
    )
}
