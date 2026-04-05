package com.mrndstvndv.search.provider.intent

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Single extra key/value pair with $query replacement support.
 */
data class IntentExtra(
    val key: String,           // e.g., "android.intent.extra.STREAM"
    val value: String,         // e.g., "$query" or "fixed value"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("key", key)
        put("value", value)
    }

    companion object {
        fun fromJson(json: JSONObject): IntentExtra? {
            return try {
                IntentExtra(
                    key = json.getString("key"),
                    value = json.getString("value"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Configuration for a single intent.
 */
data class IntentConfig(
    val id: String = UUID.randomUUID().toString(),
    val title: String,                      // e.g., "Instagram"
    val packageName: String,                 // Target app
    val action: String = Intent.ACTION_SEND, // SEND, VIEW, SENDTO, etc.
    val type: String? = null,                // MIME type (null = any/not set)
    
    // Payload customization
    val payloadTemplate: String? = null,     // "yabai $query" or null for raw
    
    // Custom extras (multiple supported)
    val extras: List<IntentExtra> = emptyList(),
) {
    /**
     * Whether this intent expects a query value (contains $query in payloadTemplate or any extra).
     */
    val hasQuerySlot: Boolean
        get() {
            if (payloadTemplate?.contains("\$query") == true) return true
            return extras.any { it.value.contains("\$query") }
        }

    companion object {
        fun fromJson(json: JSONObject): IntentConfig? {
            return try {
                val extrasArray = json.optJSONArray("extras") ?: JSONArray()
                val extras = buildList {
                    for (i in 0 until extrasArray.length()) {
                        extrasArray.optJSONObject(i)?.let { IntentExtra.fromJson(it) }?.let { add(it) }
                    }
                }

                IntentConfig(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    title = json.getString("title"),
                    packageName = json.optString("packageName", ""),
                    action = json.optString("action", Intent.ACTION_SEND),
                    type = json.optString("type").takeIf { it.isNotEmpty() },
                    payloadTemplate = json.optString("payloadTemplate").takeIf { it.isNotEmpty() },
                    extras = extras
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
        type?.let { put("type", it) }
        payloadTemplate?.let { put("payloadTemplate", it) }
        
        val extrasArray = JSONArray()
        extras.forEach { extrasArray.put(it.toJson()) }
        put("extras", extrasArray)
    }
}


