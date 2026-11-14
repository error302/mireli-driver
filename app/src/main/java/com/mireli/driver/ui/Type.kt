package com.mireli.driver.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mireli.driver.R

@OptIn(ExperimentalTextApi::class)
private val MireliFont = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(R.font.inter_variable, weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    }
)

private fun mireliType(
    size: Int,
    leading: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
    tabular: Boolean = false,
) = TextStyle(
    fontFamily = MireliFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = leading.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = if (tabular) MireliDesign.NumberFeatures else null,
)

internal val MireliTypography = Typography(
    displayLarge = mireliType(52, 57, FontWeight.Bold, -2.2, tabular = true),
    displayMedium = mireliType(44, 49, FontWeight.Bold, -1.8, tabular = true),
    displaySmall = mireliType(38, 43, FontWeight.Bold, -1.5, tabular = true),
    headlineLarge = mireliType(34, 39, FontWeight.Bold, -1.2),
    headlineMedium = mireliType(28, 34, FontWeight.Bold, -0.9),
    headlineSmall = mireliType(23, 29, FontWeight.SemiBold, -0.5),
    titleLarge = mireliType(20, 25, FontWeight.SemiBold, -0.4),
    titleMedium = mireliType(17, 22, FontWeight.SemiBold, -0.3),
    titleSmall = mireliType(15, 20, FontWeight.SemiBold, -0.1),
    bodyLarge = mireliType(17, 24, tracking = -0.2),
    bodyMedium = mireliType(15, 21, tracking = -0.1),
    bodySmall = mireliType(13, 18),
    labelLarge = mireliType(15, 20, FontWeight.SemiBold, -0.1, tabular = true),
    labelMedium = mireliType(12, 17, FontWeight.Medium, tabular = true),
    labelSmall = mireliType(11, 15, FontWeight.Medium, tabular = true),
)

internal val Typography.transferClock: TextStyle
    get() = mireliType(30, 36, FontWeight.SemiBold, -1.0, tabular = true)

internal val Typography.drivingMetric: TextStyle
    get() = titleLarge.copy(fontFeatureSettings = MireliDesign.NumberFeatures)

internal val Typography.timetableClock: TextStyle
    get() = titleMedium.copy(fontFeatureSettings = MireliDesign.NumberFeatures)

internal val Typography.numericDetail: TextStyle
    get() = bodyMedium.copy(fontFeatureSettings = MireliDesign.NumberFeatures)

internal val Typography.pageTitle: TextStyle
    get() = mireliType(24, 29, FontWeight.Bold, -0.55)

internal val Typography.earningsAmount: TextStyle
    get() = mireliType(26, 32, FontWeight.SemiBold, -0.8, tabular = true)
