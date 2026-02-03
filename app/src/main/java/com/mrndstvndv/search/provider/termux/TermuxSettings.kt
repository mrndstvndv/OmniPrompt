package com.mrndstvndv.search.provider.termux

import android.content.Context
import com.mrndstvndv.search.provider.settings.ProviderSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable settings for TermuxProvider.
 */
data class TermuxSettings(
    val commands: List<TermuxCommand>,
) : ProviderSettings {

    override val providerId = PROVIDER_ID

    companion object {
        const val PROVIDER_ID = "termux"

        /**
         * Create default settings.
         */
        fun default(): TermuxSettings = TermuxSettings(commands = emptyList())

        /**
         * Deserialize from JSON.
         * Returns null if parsing fails (caller should use default).
         */
        fun fromJson(json: JSONObject?): TermuxSettings? {
            if (json == null) return null
            val commandsArray = json.optJSONArray("commands") ?: JSONArray()
            val commands =
                buildList {
                    for (i in 0 until commandsArray.length()) {
                        TermuxCommand.fromJson(commandsArray.optJSONObject(i))?.let { add(it) }
                    }
                }
            return TermuxSettings(commands = commands)
        }

        /**
         * Parse from JSON string (for repository).
         */
        fun fromJsonString(jsonString: String): TermuxSettings? =
            fromJson(JSONObject(jsonString))
    }

    /**
     * Serialize to JSON.
     */
    override fun toJson(): JSONObject =
        JSONObject().apply {
            val commandsArray = JSONArray()
            commands.forEach { commandsArray.put(it.toJson()) }
            put("commands", commandsArray)
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
fun createTermuxSettingsRepository(context: Context): SettingsRepository<TermuxSettings> {
    return SettingsRepository(
        context = context,
        providerId = TermuxSettings.PROVIDER_ID,
        default = { TermuxSettings.default() },
        deserializer = { jsonString -> TermuxSettings.fromJsonString(jsonString) },
        serializer = { settings -> settings.toJsonString() }
    )
}
