package com.mireli.driver.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mireli.driver.R
import org.json.JSONObject

/** Reference-inspired surfaces retain Mireli's original blue/teal identity. */
@Composable fun DriverBrandHeader(subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // The original logo has a white canvas. Keep that canvas in both themes.
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Image(painterResource(R.drawable.mireli_driver_logo), "Mireli original logo", Modifier.size(56.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Mireli", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
    OutlinedCard(modifier, colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun DriverEarningsHero(label: String, amount: Long, detail: String) {
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF063555), Color(0xFF007F79))),
        RoundedCornerShape(24.dp)).padding(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(MireliIcons.AccountBalanceWallet, null, tint = Color.White)
                Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Text(money(amount), color = Color.White, fontSize = 34.sp, lineHeight = 40.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Text(detail, color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable fun DriverActionTile(title: String, detail: String, icon: ImageVector, action: () -> Unit) {
    OutlinedCard(onClick = action, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(24.dp))
            }
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
    trips: List<JSONObject>?, busy: Boolean, open: (Int) -> Unit) {
    val name = profile?.optString("fullName")?.takeIf { it.isNotBlank() && it != "New applicant" }
    DriverHeading(name?.let { "Welcome, ${it.substringBefore(' ')}" } ?: "Your day with Mireli",
        "SGR transfers to and from Mombasa.")
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if(eligible) MireliIcons.Train else MireliIcons.VerifiedUser, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text(if(eligible) "Ready for your next journey" else "Let's get you ready to drive",
                style = MaterialTheme.typography.titleLarge)
            Text(if(eligible) "Your account is approved for assignments. Open Trips to view your assigned route and passenger details."
                else "Complete your driver profile and upload your documents. Mireli reviews your account before you can accept journeys.",
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Button(onClick = { open(if(eligible) 3 else 0) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                Text(if(eligible) "View assignments" else "Continue onboarding")
                Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DriverMetric("Active assignments", trips?.count { it.optString("phase") !in listOf("completed", "cancelled", "declined") }?.toString() ?: "—",
            MireliIcons.Route, Modifier.weight(1f))
        DriverMetric("Recent completed", trips?.count { it.optString("phase") == "completed" }?.toString() ?: "—",
            MireliIcons.CheckCircleOutline, Modifier.weight(1f))
    }
    DriverActionTile("Driver account", application?.optString("status")?.replace('_', ' ')?.let { "Application: $it · profile & documents" }
        ?: "Profile, vehicle and required documents", MireliIcons.PersonOutline) { open(0) }
    DriverActionTile("Earnings & payouts", "Confirmed settlements and your M-Pesa beneficiary", MireliIcons.AccountBalanceWallet) { open(1) }
    DriverActionTile("Help & support", "Track account, document and payout cases", MireliIcons.HeadsetMic) { open(2) }
}
