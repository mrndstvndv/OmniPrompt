package com.mrndstvndv.search.provider.settings

import androidx.annotation.StringRes
import com.mrndstvndv.search.R

enum class SettingsIconPosition(
    @StringRes val labelResId: Int,
) {
    BELOW(R.string.appearance_settings_icon_below),
    INSIDE(R.string.appearance_settings_icon_inside),
    OFF(R.string.appearance_settings_icon_hidden),
    ;

    companion object {
        fun fromStorageValue(value: String?): SettingsIconPosition {
            if (value.isNullOrBlank()) return INSIDE
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INSIDE
        }
    }
}
