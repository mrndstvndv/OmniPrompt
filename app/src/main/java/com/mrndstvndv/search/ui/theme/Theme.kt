package com.mrndstvndv.search.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.mrndstvndv.search.provider.settings.ColorPalette
import com.mrndstvndv.search.provider.settings.MotionPreferences
import com.mrndstvndv.search.provider.settings.ThemeMode
import com.mrndstvndv.search.provider.settings.ThemePreferences

/** Whether the resolved app theme is dark (honors the user's theme mode, not just the system). */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** Whether the resolved app theme is dark with pure black backgrounds. */
val LocalIsPureBlack = staticCompositionLocalOf { false }

@Composable
fun ThemeMode.isDark(): Boolean =
    when (this) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

/** Palettes selectable on this device (Dynamic requires Android 12+). */
val availableColorPalettes: List<ColorPalette>
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ColorPalette.entries
        } else {
            ColorPalette.entries.filter { it != ColorPalette.DYNAMIC }
        }

/** The palette actually rendered for [palette] on this device. */
fun effectiveColorPalette(palette: ColorPalette): ColorPalette =
    if (palette == ColorPalette.DYNAMIC && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        ColorPalette.DEFAULT
    } else {
        palette
    }

fun resolveColorScheme(
    context: Context,
    palette: ColorPalette,
    darkTheme: Boolean,
    pureBlack: Boolean,
): ColorScheme {
    val effective = effectiveColorPalette(palette)
    val base =
        if (effective == ColorPalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            dynamicColorScheme(
                seedColor = Color(effective.seedArgb ?: ColorPalette.DEFAULT.seedArgb!!),
                isDark = darkTheme,
                style = if (effective.monochrome) PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
            )
        }
    return if (darkTheme && pureBlack) base.toPureBlack() else base
}

/**
 * Pure black base surfaces for AMOLED screens. Containers are darkened rather than flattened so
 * cards and the search bar keep a visible (tinted) edge against the black background.
 */
private fun ColorScheme.toPureBlack(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = lerp(surfaceContainerLow, Color.Black, 0.6f),
        surfaceContainer = lerp(surfaceContainer, Color.Black, 0.5f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, Color.Black, 0.4f),
        surfaceContainerHighest = lerp(surfaceContainerHighest, Color.Black, 0.3f),
        surfaceBright = lerp(surfaceBright, Color.Black, 0.3f),
    )

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun SearchTheme(
    themePreferences: ThemePreferences = ThemePreferences.Default,
    motionPreferences: MotionPreferences = MotionPreferences.Default,
    content: @Composable () -> Unit,
) {
    val darkTheme = themePreferences.mode.isDark()
    val pureBlack = darkTheme && themePreferences.pureBlack
    val context = LocalContext.current
    val colorScheme =
        remember(context, themePreferences.palette, darkTheme, pureBlack) {
            resolveColorScheme(context, themePreferences.palette, darkTheme, pureBlack)
        }

    val motionScheme =
        remember(motionPreferences.animationsEnabled) { motionSchemeFor(motionPreferences) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalMotionPreferences provides motionPreferences,
        LocalIsDarkTheme provides darkTheme,
        LocalIsPureBlack provides pureBlack,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            motionScheme = motionScheme,
            typography = Typography,
            content = content,
        )
    }
}
