package com.mireli.driver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

/** The verification blue. Deliberately distinct from the teal brand accent so a
 *  blue check never reads as decoration: it is only ever shown for a driver the
 *  server has evaluated as fully verified. */
internal val VerifiedBlue = Color(0xFF1D7AF3)

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

/**
 * A coach mark shown once per installation. Long instruction blocks make screens feel
 * bureaucratic; a single dismissible tip teaches the same thing the first time it matters.
 */
@Composable fun DriverTip(key: String, icon: ImageVector, title: String, body: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val seen = remember(key) {
        context.getSharedPreferences("driver_tips", android.content.Context.MODE_PRIVATE)
            .getBoolean(key, false)
    }
    if (seen) return
    var visible by remember(key) { androidx.compose.runtime.mutableStateOf(true) }
    if (!visible) return
    MireliPanel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MireliDesign.Emerald, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    context.getSharedPreferences("driver_tips", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean(key, true).apply()
                    visible = false
                }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Got it")
                }
            }
        }
    }
}

/**
 * A quiet disclosure row: the summary is always visible, the detail opens on demand.
 * Use it wherever a card previously carried several lines of standing instructions.
 */
@Composable fun CollapsibleCard(icon: ImageVector, title: String, summary: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    MireliPanel(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) MireliIcons.ExpandLess else MireliIcons.ExpandMore, title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        if (summary != null && !open) Text(summary, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) content()
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

@Composable fun DriverEarningsHero(label: String, amount: Long, detail: String, hidden: Boolean = false) {
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
        Text(if (hidden) "KSh •••••" else money(amount), color = Color.White, fontSize = 34.sp, lineHeight = 40.sp,
            fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = MireliDesign.NumberFeatures))
        Text(detail, color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * The binding in-app privacy summary. It is honest about what the app does and does not
 * do, and carries the review promise. The signed documents stay linked from Account when
 * the service provides their HTTPS URLs.
 */
@Composable fun DriverPrivacyNotice(onboarding: JSONObject?, supportEmail: String) {
    CollapsibleCard(MireliIcons.PrivacyTip, "Privacy notice") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Mireli Driver stores your profile and vehicle details, the documents you upload for verification, your assignments, and your settlement statement. Documents live in Mireli's private review service and are used only to verify you — never shown to passengers or the public.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Your device position is used only while the app is open and is sent to Mireli's routing service only when you ask for directions. There is no background tracking between trips.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("You can request deletion of your account from Account → Request account deletion, or write to $supportEmail. Completed applications are reviewed within one day of enrollment.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            onboarding?.optString("policyVersion")?.takeIf { it.isNotBlank() && it != "null" }?.let {
                Text("Notice version: $it", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable fun DriverActionTile(title: String, detail: String, icon: ImageVector, action: () -> Unit) {
    Surface(onClick = action, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * The blue check. A driver earns it only when every required document is
 * approved, scanned clean and unexpired, the application is approved and the
 * account is active. It is derived from the server's eligibility evaluation on
 * every refresh, so it drops the moment a document expires or evidence is
 * replaced. Nothing here grants approval; it only shows it.
 */
@Composable
internal fun VerifiedBadge(
    verified: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    approvedDocuments: Int = 0,
    requiredDocuments: Int = 0
) {
    if (verified && compact) {
        androidx.compose.foundation.Canvas(
            modifier.size(18.dp).semantics { contentDescription = "Verified driver" }
        ) {
            val center = size.width / 2f
            val edge = androidx.compose.ui.graphics.Path()
            repeat(20) { index ->
                val angle = index * kotlin.math.PI / 10 - kotlin.math.PI / 2
                val radius = center * if (index % 2 == 0) 1f else .84f
                val x = center + (kotlin.math.cos(angle) * radius).toFloat()
                val y = center + (kotlin.math.sin(angle) * radius).toFloat()
                if (index == 0) edge.moveTo(x, y) else edge.lineTo(x, y)
            }
            edge.close()
            drawPath(edge, Color(0xFF0079CE))
            val check = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * .28f, size.height * .51f)
                lineTo(size.width * .44f, size.height * .66f)
                lineTo(size.width * .72f, size.height * .36f)
            }
            drawPath(check, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = size.width * .105f, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        return
    }
    val tint = if (verified) VerifiedBlue else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (verified) VerifiedBlue.copy(alpha = .12f) else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            if (verified) MireliIcons.VerifiedUser else MireliIcons.CheckCircleOutline,
            contentDescription = if (verified) "Verified driver" else "Verification pending",
            tint = tint,
            modifier = Modifier.size(if (compact) 13.dp else 16.dp)
        )
        Text(
            when {
                verified -> "Verified driver"
                requiredDocuments > 0 -> "Verification $approvedDocuments/$requiredDocuments"
                else -> "Not verified"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = tint
        )
    }
}
