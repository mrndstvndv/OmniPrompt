package com.mrndstvndv.search.provider.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPreferencesTest {
    @Test
    fun `default preferences enable animations`() {
        val preferences = MotionPreferences.Default

        assertEquals(true, preferences.animationsEnabled)
        assertEquals(250, preferences.effectiveDurationMillis(250))
        assertEquals(120, preferences.effectiveDelayMillis(120))
    }

    @Test
    fun `disabled preferences zero out duration and delay`() {
        val preferences = MotionPreferences(animationsEnabled = false)

        assertEquals(0, preferences.effectiveDurationMillis(300))
        assertEquals(0, preferences.effectiveDelayMillis(150))
    }

    @Test
    fun `requested negative values are coerced to zero`() {
        val preferences = MotionPreferences(animationsEnabled = true)

        assertEquals(0, preferences.effectiveDurationMillis(-10))
        assertEquals(0, preferences.effectiveDelayMillis(-5))
    }
}
