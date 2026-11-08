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
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.mrndstvndv.search.provider.settings.MotionPreferences

val LocalMotionPreferences = staticCompositionLocalOf { MotionPreferences.Default }

/*
 * Motion system
 *
 * All in-app motion is physics based and comes from the Material 3 Expressive [MotionScheme]
 * installed by [SearchTheme]. Pick a spec by *role*, not by duration:
 *
 * - Spatial: anything that moves or changes size/scale (offsets, slides, expand/shrink, scale).
 *   Expressive spatial springs are under-damped, so they overshoot and settle with a small bounce.
 * - Effects: anything that does not move (alpha, color, blur). Effects springs are critically
 *   damped and never overshoot, so values stay in range.
 *
 * Then pick a [MotionSpeed]: Fast for small components (icons, badges, cues), Default for
 * mid-size elements (lists, sheets, bars), Slow for full-screen transitions.
 *
 * When the user disables animations, [SearchTheme] installs [NoMotionScheme], so every spec read
 * through these helpers (and every Material component that reads MaterialTheme.motionScheme)
 * snaps instantly. Call sites never need to branch on [MotionPreferences] themselves.
 */

enum class MotionSpeed { Fast, Default, Slow }

/** Motion scheme used when animations are disabled: every spec snaps to its target. */
object NoMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

fun motionSchemeFor(motionPreferences: MotionPreferences): MotionScheme =
    if (motionPreferences.animationsEnabled) MotionScheme.expressive() else NoMotionScheme

/** Spring for position/size/scale. May overshoot. Usable outside composition (e.g. transitionSpec). */
fun <T> MotionScheme.spatial(speed: MotionSpeed = MotionSpeed.Default): FiniteAnimationSpec<T> =
    when (speed) {
        MotionSpeed.Fast -> fastSpatialSpec()
        MotionSpeed.Default -> defaultSpatialSpec()
        MotionSpeed.Slow -> slowSpatialSpec()
    }

/** Spring for alpha/color/blur. Never overshoots. Usable outside composition. */
fun <T> MotionScheme.effects(speed: MotionSpeed = MotionSpeed.Default): FiniteAnimationSpec<T> =
    when (speed) {
        MotionSpeed.Fast -> fastEffectsSpec()
        MotionSpeed.Default -> defaultEffectsSpec()
        MotionSpeed.Slow -> slowEffectsSpec()
    }

/** Spatial spring from the current theme's motion scheme. */
@Composable
fun <T> spatialSpec(speed: MotionSpeed = MotionSpeed.Default): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.spatial(speed)

/** Effects spring from the current theme's motion scheme. */
@Composable
fun <T> effectsSpec(speed: MotionSpeed = MotionSpeed.Default): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.effects(speed)

/**
 * Duration-based tween that honors [LocalMotionPreferences]. Only for the few animations that
 * need an explicit delay or must run outside [SearchTheme] (e.g. the window background reveal);
 * prefer [spatialSpec]/[effectsSpec] everywhere else.
 */
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

/** See [motionAwareTween]: only for delayed / out-of-theme animations. */
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
