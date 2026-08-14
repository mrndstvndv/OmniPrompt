package com.mrndstvndv.search.provider.intent

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntentConfigJsonTest {
    @Test
    fun dataUriSurvivesJsonRoundTrip() {
        val original =
            IntentConfig(
                id = "test-id",
                title = "Search",
                packageName = "com.example",
                action = "android.intent.action.VIEW",
                type = "text/plain",
                className = ".MainActivity",
                payloadTemplate = "payload \$query",
                extras = listOf(IntentExtra("query", "\$query")),
                data = "example://search?q=\$query",
            )

        val restored = IntentConfig.fromJson(original.toJson())

        assertEquals(original, restored)
        assertTrue(restored!!.hasQuerySlot)
    }

    @Test
    fun legacyJsonWithoutDataStillLoads() {
        val legacy =
            JSONObject()
                .put("id", "legacy-id")
                .put("title", "Legacy")
                .put("packageName", "")
                .put("action", "android.intent.action.SEND")

        val restored = IntentConfig.fromJson(legacy)

        assertEquals("legacy-id", restored?.id)
        assertEquals("Legacy", restored?.title)
        assertNull(restored?.data)
        assertTrue(restored?.extras?.isEmpty() == true)
        assertFalse(restored!!.hasQuerySlot)
    }

    @Test
    fun removedRawFlagsAreIgnoredAndNotWrittenBack() {
        val json =
            JSONObject()
                .put("title", "Legacy flags")
                .put("packageName", "com.example")
                .put("action", "android.intent.action.VIEW")
                .put("extraFlags", 0x08000000)

        val restored = IntentConfig.fromJson(json)

        assertFalse(restored!!.toJson().has("extraFlags"))
    }
}
