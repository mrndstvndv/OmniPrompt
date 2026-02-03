package com.mrndstvndv.search.provider.web

import android.content.Context
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.WebSearchSettings

/**
 * Factory function to create WebSearch settings repository.
 * Uses existing WebSearchSettings from ProviderSettingsRepository.
 */
fun createWebSearchSettingsRepository(context: Context): SettingsRepository<WebSearchSettings> {
    return SettingsRepository(
        context = context,
        providerId = WebSearchSettings.PROVIDER_ID,
        default = { WebSearchSettings.default() },
        deserializer = { jsonString ->
            try {
                org.json.JSONObject(jsonString).let { WebSearchSettings.fromJson(it) }
            } catch (e: Exception) {
                null
            }
        },
        serializer = { settings -> settings.toJson().toString() }
    )
}
