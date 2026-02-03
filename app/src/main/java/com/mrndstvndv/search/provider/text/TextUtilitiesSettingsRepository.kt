package com.mrndstvndv.search.provider.text

import android.content.Context
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.TextUtilitiesSettings

/**
 * Factory function to create TextUtilities settings repository.
 * Uses existing TextUtilitiesSettings from ProviderSettingsRepository.
 */
fun createTextUtilitiesSettingsRepository(context: Context): SettingsRepository<TextUtilitiesSettings> {
    return SettingsRepository(
        context = context,
        providerId = TextUtilitiesSettings.PROVIDER_ID,
        default = { TextUtilitiesSettings.default() },
        deserializer = { jsonString ->
            try {
                org.json.JSONObject(jsonString).let { TextUtilitiesSettings.fromJson(it) }
            } catch (e: Exception) {
                null
            }
        },
        serializer = { settings -> settings.toJson().toString() }
    )
}
