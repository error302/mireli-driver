package com.mireli.driver.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val mint = Color(0xFFB2F1DE)
    val paleBlue = Color(0xFFD1E6FF)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF102F54), Color(0xFF10495B), Color(0xFF0D574C))))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mireli Driver", color = mint, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(66.dp).clip(CircleShape).background(paleBlue), contentAlignment = Alignment.Center) {
                if (image != null) {
                    Image(image, "Your profile photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    val initials = name?.trim()?.split(Regex("""\s+"""))?.take(2)
                        ?.joinToString("") { it.take(1).uppercase() } ?: "M"
                    Text(initials, color = Color(0xFF102F54), style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name ?: "Your driver profile", Modifier.weight(1f, fill = false),
                        color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    if (verified) VerifiedBadge(verified = true, compact = true)
                }
                if (vehicle.isNotBlank()) {
                    Text(vehicle, color = paleBlue, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = .24f))
        Text(if (eligible) "Eligible for new assignments" else when (applicationStatus) {
            "submitted" -> "Application in review"
            "changes_requested" -> "Changes requested"
            "approved" -> "Assignments currently unavailable"
            "draft" -> "Application draft"
            else -> "Approval required"
        }, color = if (eligible) mint else paleBlue, style = MaterialTheme.typography.labelLarge)
        if (photo.loading) LinearProgressIndicator(
            Modifier.fillMaxWidth(), color = mint, trackColor = Color.White.copy(alpha = .2f)
        )
        photo.message?.let { message ->
            Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = if (photo.failed) Color(0xFFFFD7CB) else mint, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val actions = ButtonDefaults.textButtonColors(contentColor = Color.White,
                disabledContentColor = Color.White.copy(alpha = .55f))
            TextButton(onClick = onPickPhoto, enabled = canChangePhoto && !photo.loading, colors = actions) {
                Text(if (image == null) "Add profile photo" else "Change photo")
            }
            if (image != null) TextButton(
                onClick = { confirmRemove = true }, enabled = canChangePhoto && !photo.loading, colors = actions
            ) { Text("Remove photo") }
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
