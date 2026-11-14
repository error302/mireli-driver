package com.mireli.driver.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SettlementDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("en-KE"))
    .withZone(ZoneId.of("Africa/Nairobi"))

@Composable
internal fun DeparturePageTitle(title: String, detail: String? = null) {
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.pageTitle)
        detail?.let { DepartureNote(it) }
    }
}

@Composable
internal fun DepartureNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun DepartureSection(title: String) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
}

@Composable
internal fun DepartureDisclosure(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val interaction = remember { MutableInteractionSource() }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).pressScale(interaction)
                .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button,
                    onClickLabel = if (expanded) "Collapse $title" else "Expand $title") { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
            Icon(if (expanded) MireliIcons.ExpandLess else MireliIcons.ExpandMore, null,
                tint = MaterialTheme.colorScheme.secondary)
        }
        DepartureReveal(expanded) { Box(Modifier.padding(top = 4.dp, bottom = 12.dp)) { content() } }
    }
}

@Composable
internal fun DepartureReveal(visible: Boolean, content: @Composable () -> Unit) {
    val duration = MireliDesign.DetailDurationMs
    val exitDuration = MireliMotion.exit(duration)
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(duration, easing = MireliMotion.Enter), expandFrom = Alignment.Top) +
            fadeIn(tween(duration, easing = MireliMotion.Enter)),
        exit = shrinkVertically(tween(exitDuration, easing = MireliMotion.MoveEase), shrinkTowards = Alignment.Top) +
            fadeOut(tween(exitDuration)),
        content = { content() }
    )
}

@Composable
internal fun DepartureTripTabs(history: Boolean, onSelect: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(3.dp).selectableGroup()) {
            listOf("Assignments", "History").forEachIndexed { index, title ->
                val selected = (index == 1) == history
                val interaction = remember { MutableInteractionSource() }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (selected) colors.secondaryContainer else Color.Transparent,
                ) {
                    Box(
                        Modifier.heightIn(min = MireliDesign.TouchTarget).pressScale(interaction)
                            .selectable(selected, interactionSource = interaction, indication = ripple(),
                                role = Role.Tab, onClick = { onSelect(index == 1) })
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(title, style = MaterialTheme.typography.titleSmall,
                            color = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DepartureValueRow(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
    }
}

@Composable
internal fun DepartureSettlement(line: JSONObject, hideBalance: Boolean, modifier: Modifier = Modifier) {
    val status = line.getString("status")
    fun shown(field: String) = if (hideBalance) "KSh ••••" else money(line.getLong(field))
    val stamp = (line.opt("completedAt") as? String) ?: (line.opt("initiatedAt") as? String)
    val date = stamp?.let { runCatching { SettlementDate.format(Instant.parse(it)) }.getOrNull() }
    val summary = listOfNotNull(status.settlementLabel(), date).joinToString(" · ")
    DepartureDisclosure(
        line.getString("routeName"),
        summary = summary,
        modifier = modifier,
        trailing = {
            Text(shown("netMinor"), style = MaterialTheme.typography.numericDetail,
                color = MaterialTheme.colorScheme.onSurface)
        },
    ) {
        Column {
            DepartureValueRow("Gross", shown("grossMinor"))
            DepartureValueRow("Commission", shown("commissionMinor"))
            DepartureValueRow("Home surcharge", shown("surchargeMinor"))
            if (line.optLong("platformFeeMinor") != 0L) DepartureValueRow("Platform fee", shown("platformFeeMinor"))
            line.optString("failureReason").takeIf { it.isNotBlank() && it != "null" }?.let { DepartureNote(it) }
        }
    }
}

internal fun String.readableStatus(): String = replace('_', ' ').replaceFirstChar { it.uppercaseChar() }

internal fun String.settlementLabel(): String = if (this == "completed") "Paid" else readableStatus()

@Composable
internal fun departureFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.secondary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
)
