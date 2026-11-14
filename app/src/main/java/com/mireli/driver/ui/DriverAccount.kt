package com.mireli.driver.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mireli.driver.DriverPhoto
import org.json.JSONObject

@Composable
internal fun MireliDriverCard(
    profile: JSONObject,
    applicationStatus: String,
    verified: Boolean,
    eligible: Boolean,
    photo: DriverPhoto,
    canChangePhoto: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val name = profile.optString("fullName").takeUnless { it.isBlank() || it == "New applicant" }
    val vehicle = listOfNotNull(
        (profile.opt("cabType") as? String)?.takeIf { it.isNotBlank() },
        (profile.opt("plate") as? String)?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val image = remember(photo.jpeg) {
        photo.jpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                if (image != null) {
                    Image(image, "Your profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    val initials = name?.trim()?.split(Regex("""\s+"""))?.take(2)
                        ?.joinToString("") { it.take(1).uppercase() } ?: "M"
                    Text(initials, color = colors.onSurface, style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name ?: "Your driver profile", Modifier.weight(1f, fill = false),
                        color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
                    if (verified) VerifiedBadge(verified = true, compact = true)
                }
                if (vehicle.isNotBlank()) {
                    Text(vehicle, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text(if (eligible) "Eligible for assignments" else when (applicationStatus) {
            "submitted" -> "Application in review"
            "changes_requested" -> "Changes requested"
            "approved" -> "Assignments unavailable"
            "draft" -> "Application draft"
            else -> "Approval required"
        }, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (photo.loading) LinearProgressIndicator(
            Modifier.fillMaxWidth(), color = colors.secondary, trackColor = colors.surfaceContainerHighest
        )
        photo.message?.let { message ->
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (photo.failed) colors.error else colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val actions = ButtonDefaults.textButtonColors(contentColor = colors.secondary,
                disabledContentColor = colors.onSurfaceVariant)
            TextButton(onClick = onPickPhoto, enabled = canChangePhoto && !photo.loading, colors = actions) {
                Text(if (image == null) "Add photo" else "Change photo", style = MaterialTheme.typography.titleSmall)
            }
            if (image != null) TextButton(
                onClick = { confirmRemove = true }, enabled = canChangePhoto && !photo.loading, colors = actions
            ) { Text("Remove photo", style = MaterialTheme.typography.titleSmall) }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("Remove profile photo?") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; onRemovePhoto() }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
    )
}

private val VehicleRequirements = setOf("insurance", "inspection", "speed_governor", "logbook", "vehicle_permit", "lease")

internal fun documentSection(requirement: JSONObject): String = when {
    !requirement.getBoolean("required") -> "Optional documents"
    requirement.getString("id") in VehicleRequirements -> "Vehicle documents"
    else -> "Driver credentials"
}


@Composable
internal fun AccountDocumentGroup(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().heightIn(min = MireliDesign.TouchTarget).pressScale(interaction)
            .clickable(interaction, ripple(), role = Role.Button,
                onClickLabel = if (expanded) "Collapse $title" else "Expand $title", onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MireliIcons.Badge, null, Modifier.size(18.dp), tint = colors.secondary)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Icon(if (expanded) MireliIcons.ExpandLess else MireliIcons.ExpandMore, null,
            Modifier.size(18.dp), tint = colors.secondary)
    }
}
