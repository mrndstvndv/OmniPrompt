package com.mrndstvndv.search.provider.apps

import android.content.Context
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository

/**
 * Factory function to create AppSearch settings repository.
 * Uses existing AppSearchSettings from SettingsRepository.
 */
fun createAppSearchSettingsRepository(context: Context): ProviderSettingsRepository<AppSearchSettings> {
    return ProviderSettingsRepository(
        context = context,
        providerId = AppSearchSettings.PROVIDER_ID,
        default = { AppSearchSettings.default() },
        deserializer = { jsonString ->
            try {
                org.json.JSONObject(jsonString).let { AppSearchSettings.fromJson(it) }
            } catch (e: Exception) {
                null
            }
        },
        serializer = { settings -> settings.toJson().toString() },
    )
}
