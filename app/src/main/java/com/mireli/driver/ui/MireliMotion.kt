package com.mireli.driver.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One motion vocabulary for the whole app.
 *
 * Entrances decelerate, moves ease in and out, exits are 20% shorter than the entrance that
 * preceded them, and list rows arrive 30ms apart. Everything is driven through
 * [androidx.compose.ui.graphics.GraphicsLayerScope], so an animation invalidates the layer and
 * never recomposes the content it is animating.
 */
internal object MireliMotion {
    /** Small state change, such as a chip settling. */
    const val Fast = 160

    /** Default entrance for content that appears in place. */
    const val Base = 220

    /** Content moving between two positions keeps its identity. */
    const val Move = 240

    /** Gap between consecutive rows of a list. */
    const val Stagger = 30

    /** How far a pressed surface shrinks. Small enough to feel like weight, not a bounce. */
    const val PressScale = 0.97f

    val Enter = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val MoveEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Exits leave faster than they arrived so the next state never waits on them. */
    fun exit(duration: Int) = (duration * 0.8f).toInt()
}

/**
 * 0.97 of the original size while held. The shrink snaps so the finger never chases a moving
 * target; only the release is eased.
 */
@Composable
internal fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) MireliMotion.PressScale else 1f,
        animationSpec = if (pressed) snap() else tween(MireliMotion.Fast, easing = MireliMotion.Enter),
        label = "press"
    )
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * Enters a row with a 10dp rise and a fade, delayed by [MireliMotion.Stagger] per index so a group
 * settles as a sequence instead of a flash.
 */
@Composable
internal fun Modifier.staggerIn(index: Int): Modifier {
    val density = LocalDensity.current
    val rise = remember(density) { with(density) { 10.dp.toPx() } }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        if (index > 0) delay(index * MireliMotion.Stagger.toLong())
        progress.animateTo(1f, tween(MireliMotion.Base, easing = MireliMotion.Enter))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}
