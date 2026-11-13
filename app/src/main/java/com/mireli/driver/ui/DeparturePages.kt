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

@Composable
internal fun DeparturePageTitle(title: String, detail: String? = null) {
    Column(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        detail?.let { DepartureNote(it) }
    }
}

@Composable
internal fun DepartureNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun DepartureSection(title: String) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun DepartureDisclosure(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(initiallyExpanded) { mutableStateOf(initiallyExpanded) }
    val interaction = remember { MutableInteractionSource() }
    Column {
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
                summary?.let { DepartureNote(it) }
            }
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
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        listOf("Assignments", "History").forEachIndexed { index, title ->
            val selected = (index == 1) == history
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier.weight(1f).pressScale(interaction).selectable(
                    selected = selected, interactionSource = interaction, indication = ripple(), role = Role.Tab,
                    onClick = { onSelect(index == 1) }),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.heightIn(min = MireliDesign.TouchTarget).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(title, style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.secondary else colors.onSurfaceVariant)
                }
                HorizontalDivider(thickness = 2.dp, color = if (selected) colors.secondary else colors.outlineVariant)
            }
        }
    }
}

@Composable
internal fun DepartureAmount(label: String, amount: String) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DepartureNote(label)
        Text(amount, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
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
    Column(modifier.fillMaxWidth()) {
        DepartureDisclosure(line.getString("routeName"), line.getString("serviceType").readableStatus()) {
            Column {
                DepartureValueRow("Gross", shown("grossMinor"))
                DepartureValueRow("Commission", shown("commissionMinor"))
                DepartureValueRow("Home surcharge", shown("surchargeMinor"))
                line.optString("failureReason").takeIf { it.isNotBlank() && it != "null" }?.let { DepartureNote(it) }
            }
        }
        DepartureValueRow(status.settlementLabel(), shown("netMinor"))
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
