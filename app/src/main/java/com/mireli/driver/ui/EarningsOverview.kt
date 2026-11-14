package com.mireli.driver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun EarningsOverview(
    paid: String,
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
            Box {
                Canvas(Modifier.matchParentSize()) {
                    val rail = Path().apply {
                        moveTo(size.width * .55f, size.height)
                        cubicTo(size.width * .67f, size.height * .90f, size.width * .59f,
                            size.height * .58f, size.width * .78f, size.height * .53f)
                        cubicTo(size.width * .90f, size.height * .48f, size.width * .89f,
                            size.height * .22f, size.width, size.height * .11f)
                    }
                    drawPath(rail, StatementMuted.copy(alpha = .22f),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(StatementMuted.copy(alpha = .38f), 3.dp.toPx(),
                        Offset(size.width * .78f, size.height * .53f))
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
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
        Row(horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
            EarningsShortcut("M-Pesa", MireliIcons.AccountBalanceWallet, Modifier.weight(1f), onPayoutDetails)
            EarningsShortcut("Payout help", MireliIcons.HeadsetMic, Modifier.weight(1f), onPayoutSupport)
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
