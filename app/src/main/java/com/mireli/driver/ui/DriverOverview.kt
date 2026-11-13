package com.mireli.driver.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
internal fun DriverOverviewSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(MireliDesign.Radius.hero), color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Box {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient(listOf(colors.secondaryContainer, colors.primaryContainer)))
                repeat(7) { index ->
                    val x = size.width * (.66f + index * .045f)
                    val path = Path().apply {
                        moveTo(x, -12f)
                        cubicTo(x - size.width * .19f, size.height * .31f, x + size.width * .38f, size.height * .52f,
                            x + size.width * .08f, size.height + 12f)
                    }
                    drawPath(path, colors.secondary.copy(alpha = .13f), style = Stroke(1.dp.toPx()))
                }
            }
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
internal fun DriverOverviewLink(title: String, detail: String, icon: ImageVector, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).pressScale(interaction)
        .clickable(interaction, ripple(), role = Role.Button, onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.secondary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            DepartureNote(detail)
        }
        Icon(MireliIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
