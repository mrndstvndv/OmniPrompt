package com.mrndstvndv.search.provider.settings

/**
 * Enum representing the position of the search bar in the main UI.
 */
enum class SearchBarPosition {
    TOP,
    BOTTOM;

    companion object {
        fun fromStorageValue(value: String?): SearchBarPosition {
            if (value.isNullOrBlank()) return BOTTOM
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BOTTOM
        }
    }
}
