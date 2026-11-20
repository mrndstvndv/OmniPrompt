package com.mrndstvndv.search.provider.settings

import androidx.annotation.StringRes
import com.mrndstvndv.search.R

/**
 * Light/dark selection for the app theme.
 */
enum class ThemeMode(
    @StringRes val labelResId: Int,
) {
    SYSTEM(R.string.appearance_theme_mode_system),
    LIGHT(R.string.appearance_theme_mode_light),
    DARK(R.string.appearance_theme_mode_dark),
    ;

    companion object {
        fun fromStorageValue(value: String?): ThemeMode {
            if (value.isNullOrBlank()) return SYSTEM
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Color palette for the app theme.
 *
 * [DYNAMIC] uses the system wallpaper colors (Android 12+). Every other palette is generated
 * from a single seed color. [seedArgb] is null only for [DYNAMIC].
 */
enum class ColorPalette(
    @StringRes val labelResId: Int,
    val seedArgb: Long?,
    val monochrome: Boolean = false,
) {
    DYNAMIC(R.string.appearance_palette_dynamic, null),
    DEFAULT(R.string.appearance_palette_default, 0xFF6750A4),
    OCEAN(R.string.appearance_palette_ocean, 0xFF1565C0),
    FOREST(R.string.appearance_palette_forest, 0xFF2E7D32),
    SUNSET(R.string.appearance_palette_sunset, 0xFFE65100),
    ROSE(R.string.appearance_palette_rose, 0xFFC2185B),
    MONOCHROME(R.string.appearance_palette_monochrome, 0xFF808080, monochrome = true),
    ;

    companion object {
        fun fromStorageValue(value: String?): ColorPalette {
            if (value.isNullOrBlank()) return DYNAMIC
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DYNAMIC
        }
    }
}

data class ThemePreferences(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ColorPalette = ColorPalette.DYNAMIC,
    val pureBlack: Boolean = false,
) {
    companion object {
        val Default = ThemePreferences()
    }
}
