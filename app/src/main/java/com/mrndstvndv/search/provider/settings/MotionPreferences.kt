package com.mrndstvndv.search.provider.settings

data class MotionPreferences(
    val animationsEnabled: Boolean = true,
) {
    companion object {
        val Default = MotionPreferences()
    }

    fun effectiveDurationMillis(requested: Int): Int {
        if (!animationsEnabled) return 0
        return requested.coerceAtLeast(0)
    }

    fun effectiveDelayMillis(requested: Int): Int {
        if (!animationsEnabled) return 0
        return requested.coerceAtLeast(0)
    }
}
