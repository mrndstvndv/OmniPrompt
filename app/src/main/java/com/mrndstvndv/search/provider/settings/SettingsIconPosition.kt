package com.mrndstvndv.search.provider.settings

enum class SettingsIconPosition {
    BELOW,
    INSIDE,
    OFF,
    ;

    companion object {
        fun fromStorageValue(value: String?): SettingsIconPosition {
            if (value.isNullOrBlank()) return BELOW
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BELOW
        }
    }

    fun userFacingLabel(): String =
        when (this) {
            BELOW -> "Below"
            INSIDE -> "Inside"
            OFF -> "Hidden"
        }
}
