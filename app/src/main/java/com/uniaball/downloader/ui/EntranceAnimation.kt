package com.uniaball.downloader.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

fun Modifier.entranceAnimation(
    visible: Boolean,
    delayMillis: Int = 0,
    durationMillis: Int = 300
): Modifier = composed {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, delayMillis = delayMillis),
        label = "entrance-alpha"
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 24.dp,
        animationSpec = tween(durationMillis, delayMillis = delayMillis),
        label = "entrance-offset"
    )
    this.graphicsLayer {
        this.alpha = alpha
        this.translationY = offsetY.toPx()
    }
}
