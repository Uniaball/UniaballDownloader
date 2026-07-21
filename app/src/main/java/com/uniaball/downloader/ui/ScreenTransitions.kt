package com.uniaball.downloader.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

fun <S> AnimatedContentTransitionScope<S>.screenTransitionSpec() =
    fadeIn(animationSpec = tween(220)) + slideInVertically(
        animationSpec = tween(220),
        initialOffsetY = { it / 8 }
    ) togetherWith fadeOut(animationSpec = tween(180)) + slideOutVertically(
        animationSpec = tween(180),
        targetOffsetY = { -it / 8 }
    )
