package com.mireli.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mireli.driver.DocumentUpload
import com.mireli.driver.domain.OnboardingPolicy
import org.json.JSONObject

@Composable
internal fun DriverDocumentRow(
    requirement: JSONObject,
    evidence: JSONObject?,
    graceUntil: String?,
    canEdit: Boolean,
    upload: DocumentUpload?,
    modifier: Modifier = Modifier,
    showRequirement: Boolean = true,
    onRemove: (() -> Unit)? = null,
    onUpload: (String) -> Unit
) {
    val title = requirement.getString("title")
    val expires = requirement.getBoolean("expires")
    val state = evidence?.optString("state")
    val storedExpiry = (evidence?.opt("expiresAt") as? String)?.take(10).orEmpty()
    var expiry by rememberSaveable(storedExpiry) { mutableStateOf(storedExpiry) }
    var editingExpiry by rememberSaveable { mutableStateOf(false) }
    val validExpiry = OnboardingPolicy.isCurrentDocumentDate(expiry)
    val approved = state == "approved" && (!expires || OnboardingPolicy.isCurrentDocumentDate(storedExpiry))
    val status = when {
        upload?.uploading == true -> "Uploading…"
        evidence == null -> "Upload needed"
        state == "rejected" -> "Needs replacement"
        state == "expired" || (expires && !OnboardingPolicy.isCurrentDocumentDate(storedExpiry)) -> "Expired"
        approved -> "Approved"
        else -> "Uploaded · awaiting review"
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                val requirementLabel = if (requirement.getBoolean("required")) "Required" else "Optional"
                Text(if (showRequirement) "$requirementLabel · $status" else status,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            if (canEdit) TextButton(
                onClick = {
                    if (expires) editingExpiry = !editingExpiry else onUpload("")
                },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary)
            ) {
                Text(if (editingExpiry) "Cancel" else if (evidence == null) "Upload" else "Replace")
            }
        }
        if (onRemove != null) {
            TextButton(onClick = onRemove) { Text("Remove document", color = colors.error) }
        }
        (evidence?.opt("reviewReason") as? String)?.takeIf { it.isNotBlank() }?.let { DepartureNote(it) }
        if (graceUntil != null && status == "Expired") DepartureNote("Replace by ${graceUntil.take(10)} to keep your verified status.")
        if (storedExpiry.isNotBlank()) DepartureNote("Expiry date $storedExpiry")
        upload?.message?.let { message ->
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (upload.uploaded) colors.primary else colors.error,
                style = MaterialTheme.typography.bodySmall)
        }
        DepartureReveal(editingExpiry && (canEdit || upload?.uploading == true)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField(expiry, { expiry = it.take(10) }, Modifier.fillMaxWidth(),
                    label = { Text("Expiry · YYYY-MM-DD") }, singleLine = true, enabled = canEdit,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), colors = departureFieldColors(),
                    isError = expiry.length == 10 && !validExpiry,
                    supportingText = if (expiry.length == 10 && !validExpiry) {
                        { Text("Enter a valid date that has not passed.") }
                    } else null)
                TextButton(enabled = canEdit && validExpiry, onClick = { onUpload(expiry) },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary)) {
                    Text("Choose file")
                }
            }
        }
        LaunchedEffect(upload?.uploaded) { if (upload?.uploaded == true) editingExpiry = false }
        if (upload?.uploading == true) LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.secondary)
    }
}
