package com.mrndstvndv.search.provider.intent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentConfigValidationTest {
    @Test
    fun `query slot includes payload data and extras`() {
        assertTrue(config(payloadTemplate = "share \$query").hasQuerySlot)
        assertTrue(config(data = "example://search/\$query").hasQuerySlot)
        assertTrue(config(extras = listOf(IntentExtra("key", "value \$query"))).hasQuerySlot)
        assertFalse(config(data = "example://fixed").hasQuerySlot)
    }

    @Test
    fun `system intent without package is valid`() {
        assertTrue(
            isIntentConfigValid(
                title = "Share",
                packageName = "",
                action = "android.intent.action.SEND",
                className = null,
            ),
        )
    }

    @Test
    fun `blank action is invalid`() {
        assertFalse(
            isIntentConfigValid(
                title = "Custom",
                packageName = "com.example",
                action = " ",
                className = null,
            ),
        )
    }

    @Test
    fun `class requires package`() {
        assertFalse(
            isIntentConfigValid(
                title = "Custom",
                packageName = "",
                action = "com.example.OPEN",
                className = ".MainActivity",
            ),
        )
    }

    @Test
    fun `component class expansion supports relative bare and qualified names`() {
        assertTrue(expandComponentClassName("com.example", ".MainActivity") == "com.example.MainActivity")
        assertTrue(expandComponentClassName("com.example", "MainActivity") == "com.example.MainActivity")
        assertTrue(expandComponentClassName("com.example", "other.app.MainActivity") == "other.app.MainActivity")
    }

    @Test
    fun `activity accessibility rejects protected unavailable components`() {
        assertTrue(
            isActivityAccessible(
                exported = true,
                enabled = true,
                applicationEnabled = true,
                requiresPermission = false,
                hasPermission = false,
            ),
        )
        assertFalse(
            isActivityAccessible(
                exported = true,
                enabled = true,
                applicationEnabled = true,
                requiresPermission = true,
                hasPermission = false,
            ),
        )
        assertTrue(
            isActivityAccessible(
                exported = true,
                enabled = true,
                applicationEnabled = true,
                requiresPermission = true,
                hasPermission = true,
            ),
        )
    }

    private fun config(
        payloadTemplate: String? = null,
        data: String? = null,
        extras: List<IntentExtra> = emptyList(),
    ) =
        IntentConfig(
            title = "Test",
            packageName = "",
            payloadTemplate = payloadTemplate,
            data = data,
            extras = extras,
        )
}
