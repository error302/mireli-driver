package com.mireli.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private enum class VehicleOption(val label: String, val detail: String, val symbol: () -> ImageVector) {
    MOTORCYCLE("Motorcycle", "Boda boda", { MireliIcons.Motorcycle }),
    TUK_TUK("Tuk-tuk", "Passenger three-wheeler", { MireliIcons.TukTuk }),
    CAR("Car", "Sedan, hatchback or SUV", { MireliIcons.DirectionsCar }),
    VAN("Van", "Passenger van or shuttle", { MireliIcons.Van }),
    BUS("Bus or minibus", "Larger passenger vehicle", { MireliIcons.Bus }),
    OTHER("Other vehicle", "Enter the type on your documents", { MireliIcons.Badge });

    companion object {
        fun forValue(value: String): VehicleOption? = when (value.trim().lowercase()) {
            "" -> null
            "motorcycle", "motorbike", "boda boda", "boda-boda" -> MOTORCYCLE
            "tuk-tuk", "tuk tuk", "tuktuk" -> TUK_TUK
            "car" -> CAR
            "van" -> VAN
            "bus", "minibus", "bus or minibus" -> BUS
            else -> OTHER
        }
    }
}

@Composable
internal fun VehicleTypePicker(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    var choosingOther by rememberSaveable { mutableStateOf(false) }
    val selected = if (choosingOther) VehicleOption.OTHER else VehicleOption.forValue(value)
    val colors = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("What do you drive?", style = MaterialTheme.typography.titleMedium)
        Text("Choose the vehicle you’ll register with Mireli.", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
        Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainer) {
            Column(Modifier.selectableGroup()) {
                VehicleOption.entries.forEachIndexed { index, option ->
                    val active = option == selected
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    Surface(
                        color = if (active) colors.secondaryContainer else colors.surfaceContainer,
                        contentColor = if (active) colors.onSecondaryContainer else colors.onSurface
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                                .selectable(selected = active, enabled = enabled, role = Role.RadioButton) {
                                    choosingOther = option == VehicleOption.OTHER
                                    if (!active) onChange(if (choosingOther) "" else option.label)
                                }.padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(option.symbol(), contentDescription = null, Modifier.size(30.dp), tint = colors.primary)
                            Column(Modifier.weight(1f)) {
                                Text(option.label, style = MaterialTheme.typography.titleSmall)
                                Text(option.detail, style = MaterialTheme.typography.bodySmall)
                            }
                            RadioButton(selected = active, onClick = null, enabled = enabled)
                        }
                    }
                }
            }
        }
        if (selected == VehicleOption.OTHER) {
            OutlinedTextField(value, { choosingOther = true; onChange(it.take(80)) },
                label = { Text("Describe your vehicle type") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled)
        }
    }
}
