package com.mrndstvndv.search.provider.intent

import android.content.Intent
import org.json.JSONObject
import java.util.UUID

/**
 * Configuration for a single intent.
 */
data class IntentConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String,             // e.g., "Download Video" - used for fuzzy matching
    val packageName: String,       // Target app package (empty for system share/view)
    val action: String = Intent.ACTION_SEND,  // SEND, VIEW, etc.
    val type: String = "text/plain",         // MIME type
    val extraKey: String? = null,   // Optional: custom extra key
    val extraValue: String? = null, // Optional: custom extra value ($query replaced)
) {
    companion object {
        fun fromJson(json: JSONObject): IntentConfig? {
            return try {
                IntentConfig(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    title = json.getString("title"),
                    packageName = json.optString("packageName", ""),
                    action = json.optString("action", Intent.ACTION_SEND),
                    type = json.optString("type", "text/plain"),
                    extraKey = json.optString("extraKey").takeIf { it.isNotEmpty() },
                    extraValue = json.optString("extraValue").takeIf { it.isNotEmpty() },
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("packageName", packageName)
        put("action", action)
        put("type", type)
        extraKey?.let { put("extraKey", it) }
        extraValue?.let { put("extraValue", it) }
    }
}

/**
 * Default intent configurations pre-loaded with common intents.
 */
val defaultIntentConfigs = listOf(
    // Generic
    IntentConfig("share", "Share", "", action = Intent.ACTION_SEND),
    IntentConfig("open_url", "Open URL", "", action = Intent.ACTION_VIEW, type = "*/*"),
)
