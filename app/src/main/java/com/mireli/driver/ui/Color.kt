package com.mireli.driver.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal const val RouteBlue = "#1565A8"
internal val StatementBlue = Color(0xFF123559)
internal val StatementInk = Color(0xFFF4F8FD)
internal val StatementMuted = Color(0xFFC3D5E8)

internal val LightColors=lightColorScheme(
    primary=Color(0xFF087460),onPrimary=Color.White,primaryContainer=Color(0xFFE0F3EC),onPrimaryContainer=Color(0xFF06302A),
    secondary=Color(0xFF1565A8),onSecondary=Color.White,secondaryContainer=Color(0xFFDFEDFA),onSecondaryContainer=Color(0xFF0B2E4E),
    tertiary=Color(0xFFAA4812),onTertiary=Color.White,tertiaryContainer=Color(0xFFFFEBDD),onTertiaryContainer=Color(0xFF7A2C00),
    background=Color(0xFFF7F8FA),onBackground=Color(0xFF101820),surface=Color.White,onSurface=Color(0xFF101820),
    surfaceVariant=Color(0xFFEFF1F4),onSurfaceVariant=Color(0xFF48525E),
    surfaceContainerLow=Color(0xFFF4F5F7),surfaceContainer=Color(0xFFEEF0F3),surfaceContainerHigh=Color(0xFFEFF1F4),surfaceContainerHighest=Color(0xFFE6EAF0),
    outline=Color(0xFF68737F),outlineVariant=Color(0xFFD9DEE5))

internal val DarkColors=darkColorScheme(
    primary=Color(0xFF4FD8B8),onPrimary=Color(0xFF00261F),primaryContainer=Color(0xFF0C3A31),onPrimaryContainer=Color(0xFFA9F2DF),
    secondary=Color(0xFF9CC8EF),onSecondary=Color(0xFF0A2338),secondaryContainer=Color(0xFF173A5A),onSecondaryContainer=Color(0xFFD6E9FB),
    tertiary=Color(0xFFFFB27A),onTertiary=Color(0xFF4A1E00),tertiaryContainer=Color(0xFF4A2A12),onTertiaryContainer=Color(0xFFFFD9BC),
    background=Color(0xFF0E1116),onBackground=Color(0xFFE8F0F2),surface=Color(0xFF101820),onSurface=Color(0xFFE8F0F2),
    surfaceVariant=Color(0xFF242A33),onSurfaceVariant=Color(0xFFB8C1CE),
    surfaceContainerLow=Color(0xFF181D25),surfaceContainer=Color(0xFF1B212A),surfaceContainerHigh=Color(0xFF20252D),surfaceContainerHighest=Color(0xFF282F39),
    outline=Color(0xFF7A8E97),outlineVariant=Color(0xFF3B4653))
