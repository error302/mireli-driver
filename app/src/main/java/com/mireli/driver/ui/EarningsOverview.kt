package com.mireli.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.StatementPeriod

@Composable
internal fun EarningsOverview(
    paid: String,
    period: StatementPeriod,
    onPeriodChange: (StatementPeriod) -> Unit,
    hidden: Boolean,
    onToggleBalance: () -> Unit,
    onPayoutDetails: () -> Unit,
    onPayoutSupport: () -> Unit,
    payoutStatus: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
        Surface(shape = MaterialTheme.shapes.medium, color = StatementBlue,
            contentColor = StatementInk, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(MireliDesign.Space.md),
                verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Paid earnings", style = MaterialTheme.typography.labelMedium, color = StatementMuted)
                        Text(paid, style = MaterialTheme.typography.earningsAmount)
                    }
                    IconButton(onClick = onToggleBalance, modifier = Modifier.size(MireliDesign.TouchTarget)) {
                        Icon(if (hidden) MireliIcons.Visibility else MireliIcons.VisibilityOff,
                            contentDescription = if (hidden) "Show balances" else "Hide balances",
                            tint = StatementInk, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StatementPeriod.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = period == option,
                    onClick = { onPeriodChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, StatementPeriod.entries.size),
                    modifier = Modifier.heightIn(min = MireliDesign.TouchTarget),
                    icon = {},
                ) { Text(option.label, style = MaterialTheme.typography.labelMedium) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
            EarningsShortcut("M-Pesa", MireliIcons.AccountBalanceWallet, Modifier, onPayoutDetails)
            EarningsShortcut("Payout help", MireliIcons.HeadsetMic, Modifier, onPayoutSupport)
        }
        payoutStatus?.let {
            Text(it, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun EarningsShortcut(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerLow) {
        Row(Modifier.heightIn(min = MireliDesign.TouchTarget).padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = colors.secondary)
            Spacer(Modifier.width(6.dp))
            Text(label, color = colors.secondary, style = MaterialTheme.typography.titleSmall)
        }
    }
}
