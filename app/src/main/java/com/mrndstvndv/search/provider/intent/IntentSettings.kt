package com.mrndstvndv.search.provider.intent

import android.content.Context
import com.mrndstvndv.search.provider.settings.ProviderSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable settings for IntentProvider.
 */
data class IntentSettings(
    val configs: List<IntentConfig> = emptyList(),
) : ProviderSettings {

    override val providerId = PROVIDER_ID

    companion object {
        const val PROVIDER_ID = "intent"

        /**
         * Create default settings with an empty list of intent configurations.
         */
        fun default(): IntentSettings = IntentSettings(configs = emptyList())

        /**
         * Deserialize from JSON.
         * Returns null if parsing fails (caller should use default).
         */
        fun fromJson(json: JSONObject?): IntentSettings? {
            if (json == null) return null
            val configsArray = json.optJSONArray("configs") ?: JSONArray()
            val configs =
                buildList {
                    for (i in 0 until configsArray.length()) {
                        IntentConfig.fromJson(configsArray.optJSONObject(i))?.let { add(it) }
                    }
                }
            return IntentSettings(configs = configs)
        }

        /**
         * Parse from JSON string (for repository).
         */
        fun fromJsonString(jsonString: String): IntentSettings? =
            fromJson(JSONObject(jsonString))
    }

    /**
     * Serialize to JSON.
     */
    override fun toJson(): JSONObject =
        JSONObject().apply {
            val configsArray = JSONArray()
            configs.forEach { configsArray.put(it.toJson()) }
            put("configs", configsArray)
        }

    /**
     * Convenience method for repository.
     */
    fun toJsonString(): String = toJson().toString()
}

/**
 * Factory function to create repository.
 * Called from MainActivity.
 */
fun createIntentSettingsRepository(context: Context): SettingsRepository<IntentSettings> {
    return SettingsRepository(
        context = context,
        providerId = IntentSettings.PROVIDER_ID,
        default = { IntentSettings.default() },
        deserializer = { jsonString -> IntentSettings.fromJsonString(jsonString) },
        serializer = { settings -> settings.toJsonString() }
    )
}
