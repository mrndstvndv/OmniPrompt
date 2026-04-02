package com.mrndstvndv.search.provider.settings

import androidx.annotation.StringRes
import com.mrndstvndv.search.R

enum class FirstResultHighlightMode(
    @StringRes val labelResId: Int,
) {
    SUBTLE(R.string.appearance_first_result_highlight_subtle),
    BALANCED(R.string.appearance_first_result_highlight_balanced),
    STRONG(R.string.appearance_first_result_highlight_strong),
    ;

    companion object {
        fun fromStorageValue(value: String?): FirstResultHighlightMode {
            if (value.isNullOrBlank()) return BALANCED
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: BALANCED
        }
    }
}
