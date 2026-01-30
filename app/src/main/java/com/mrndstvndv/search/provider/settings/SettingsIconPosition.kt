package com.mrndstvndv.search.provider.settings

enum class SettingsIconPosition {
    BELOW,
    INSIDE,
    OFF,
    ;

    companion object {
        fun fromStorageValue(value: String?): SettingsIconPosition {
            if (value.isNullOrBlank()) return INSIDE
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: INSIDE
        }
    }

    fun userFacingLabel(): String =
        when (this) {
            BELOW -> "Below"
            INSIDE -> "Inside"
            OFF -> "Hidden"
        }
}
