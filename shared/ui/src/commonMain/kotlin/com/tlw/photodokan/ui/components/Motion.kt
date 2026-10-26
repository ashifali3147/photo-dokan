package com.tlw.photodokan.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/** Shrinks a little while it is pressed, so a tap is felt even before anything else changes. */
internal fun Modifier.pressScale(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, tween(PRESS_MILLIS), label = "press")
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private const val PRESSED_SCALE = 0.97f
private const val PRESS_MILLIS = 120

/** How long things fade or slide in, and the pause between neighbours in a grid. */
object Motion {
    const val ENTER_MILLIS = 350
    const val STAGGER_MILLIS = 35
}
