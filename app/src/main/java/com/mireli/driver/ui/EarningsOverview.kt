package com.mireli.driver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun EarningsOverview(
    paid: String,
    hidden: Boolean,
    onToggleBalance: () -> Unit,
    onPayoutDetails: () -> Unit,
    onTripHistory: () -> Unit,
    onPayoutSupport: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(MireliDesign.Space.md),
            verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.sm),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Paid earnings", style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                    Text(paid, style = MaterialTheme.typography.displaySmall, color = colors.primary)
                }
                IconButton(onClick = onToggleBalance, modifier = Modifier.size(MireliDesign.TouchTarget)) {
                    Icon(if (hidden) MireliIcons.Visibility else MireliIcons.VisibilityOff,
                        contentDescription = if (hidden) "Show balances" else "Hide balances",
                        tint = colors.secondary)
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                EarningsShortcut("M-Pesa", MireliIcons.AccountBalanceWallet, onPayoutDetails)
                EarningsShortcut("Trip history", MireliIcons.CarFront, onTripHistory)
                EarningsShortcut("Payout help", MireliIcons.HeadsetMic, onPayoutSupport)
            }
        }
    }
}

@Composable
private fun EarningsShortcut(label: String, icon: ImageVector, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.secondary
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = MireliDesign.PrimaryActionHeight)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xxs)) {
            Icon(icon, null, Modifier.size(22.dp), tint = accent)
            Text(label, color = accent, style = MaterialTheme.typography.labelMedium)
        }
    }
}
