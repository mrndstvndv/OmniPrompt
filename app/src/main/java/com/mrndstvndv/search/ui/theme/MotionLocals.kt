package com.mrndstvndv.search.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.mrndstvndv.search.provider.settings.MotionPreferences

val LocalMotionPreferences = staticCompositionLocalOf { MotionPreferences.Default }

@Composable
fun <T> motionAwareTween(
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing,
): FiniteAnimationSpec<T> {
    val motionPreferences = LocalMotionPreferences.current
    val resolvedDuration = motionPreferences.effectiveDurationMillis(durationMillis)
    val resolvedDelay = motionPreferences.effectiveDelayMillis(delayMillis)
    return remember(motionPreferences.animationsEnabled, resolvedDuration, resolvedDelay, easing) {
        if (motionPreferences.animationsEnabled) {
            tween(durationMillis = resolvedDuration, delayMillis = resolvedDelay, easing = easing)
        } else {
            tween(durationMillis = 0, delayMillis = 0, easing = LinearEasing)
        }
    }
}

@Composable
fun rememberMotionAwareFloat(
    targetValue: Float,
    durationMillis: Int,
    delayMillis: Int = 0,
    easing: Easing = FastOutSlowInEasing,
    label: String = "motionAwareFloat",
    finishedListener: ((Float) -> Unit)? = null,
): State<Float> {
    val spec =
        motionAwareTween<Float>(
            durationMillis = durationMillis,
            delayMillis = delayMillis,
            easing = easing,
        )
    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spec,
        label = label,
        finishedListener = finishedListener,
    )
}

@Composable
fun motionAwareVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val enabled = LocalMotionPreferences.current.animationsEnabled
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (enabled) enter else EnterTransition.None,
        exit = if (enabled) exit else ExitTransition.None,
        content = content,
    )
}
