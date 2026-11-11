package com.mireli.driver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mireli.driver.R
import org.json.JSONObject

/**
 * Premium blue-green surface. The sign-in hero established it in 0.9.2; from 0.9.3 the signed-in
 * Home, Trips and Earnings headers use the same gradient and mint accent so the first screen and
 * the app behind it read as one product.
 */
internal val PremiumGradient = listOf(Color(0xFF063555), Color(0xFF007F79))

@Composable internal fun PremiumHero(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MireliDesign.Radius.hero))
            .background(Brush.linearGradient(PremiumGradient))
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (eyebrow != null) Text(
            eyebrow.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = StationMint
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = StationMint)
        if (content != null) {
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable internal fun MireliPrimaryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) .97f else 1f,
        animationSpec = tween(if (pressed) 0 else 150), label = "button release")
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(MireliDesign.ControlRadius), interactionSource = interaction,
        content = content)
}

@Composable fun DriverBrandHeader(subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // The original logo has a white canvas. Keep that canvas in both themes.
        Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
            Image(painterResource(R.drawable.mireli_driver_logo), "Mireli original logo", Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Mireli Driver", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppearanceButton()
    }
}

@Composable fun DriverHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable fun DriverMetric(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = MireliDesign.NumberFeatures))
        }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun DriverEarningsHero(label: String, amount: Long, detail: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(MireliDesign.Radius.hero))
            .background(Brush.linearGradient(PremiumGradient))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(MireliIcons.AccountBalanceWallet, null, tint = StationMint)
            Text(label, color = StationMint, style = MaterialTheme.typography.labelLarge)
        }
        Text(money(amount), color = Color.White, fontSize = 34.sp, lineHeight = 40.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = MireliDesign.NumberFeatures))
        Text(detail, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun DriverActionTile(title: String, detail: String, icon: ImageVector, action: () -> Unit) {
    Surface(onClick = action, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun DriverStepProgress(current: Int, labels: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = if(index <= current) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant) {
                    Text("${index + 1}", Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        color = if(index <= current) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = if(index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable fun DriverHomeOverview(profile: JSONObject?, application: JSONObject?, eligible: Boolean,
    trips: List<JSONObject>?, busy: Boolean, activeNavigation: (@Composable () -> Unit)? = null, open: (Int) -> Unit) {
    val name = profile?.optString("fullName")?.takeIf { it.isNotBlank() && it != "New applicant" }
    val active = trips?.filter { it.isActiveTransfer() }
    val next = active?.minWithOrNull(transferOrder())
    val nextName = next?.optString("routeName")?.takeIf { it.isNotBlank() }
    val phase = next?.optString("phase")
    val hasJourney = phase in listOf("accepted", "at_pickup", "in_progress") || eligible && next != null
    val title = when {
        phase == "in_progress" -> "Current journey"
        hasJourney -> "Next transfer"
        eligible -> "Scheduled transfers"
        else -> "Driver approval needed"
    }
    DriverHeading("Your schedule", name?.let { "${it.substringBefore(' ')} · Mombasa SGR transfers" } ?: "Mombasa SGR transfers")
    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)) {
        Surface(color = StationNavy) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (eligible || hasJourney) MireliIcons.Train else MireliIcons.VerifiedUser, null,
                    tint = StationMint, modifier = Modifier.size(22.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasJourney && next != null && nextName != null) {
                TransferStatus(next.optString("phase"))
                Text(nextName, style = MaterialTheme.typography.titleMedium)
                TransferSchedule(next)
                TransferEndpoints(next)
            } else {
                val detail = when {
                    !eligible -> "Complete your profile and upload your driver and vehicle documents for review."
                    active == null -> "Refresh your account to check your assignments."
                    active.isEmpty() -> "No transfers are assigned. Dispatch will send your next scheduled trip here."
                    else -> "Open Trips to see the pickup, drop-off and departure for your assignment."
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MireliPrimaryButton(onClick = { open(if (eligible || hasJourney) 3 else 0) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Icon(if (eligible || hasJourney) MireliIcons.Calendar else MireliIcons.Badge, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (eligible || hasJourney) "View assignments" else "Continue onboarding")
            }
        }
    }
    activeNavigation?.invoke()
    if (trips != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DriverMetric("Active assignments", active.orEmpty().size.toString(), MireliIcons.Calendar, Modifier.weight(1f))
            DriverMetric("Recently completed", trips.count { it.optString("phase") == "completed" }.toString(),
                MireliIcons.CheckCircleOutline, Modifier.weight(1f))
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            DriverActionTile("Driver account", application?.optString("status")?.takeIf { it.isNotBlank() }
                ?.replace('_', ' ')?.let { "Application status: $it" }
                ?: "Profile, vehicle and documents", MireliIcons.PersonOutline) { open(0) }
            HorizontalDivider()
            DriverActionTile("Earnings & payouts", "Settlements and M-Pesa beneficiary", MireliIcons.AccountBalanceWallet) { open(1) }
            HorizontalDivider()
            DriverActionTile("Help & support", "Account, document and payout cases", MireliIcons.HeadsetMic) { open(2) }
        }
    }
}
