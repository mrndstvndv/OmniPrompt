package com.mrndstvndv.search.provider.system

import android.content.Context
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.SystemSettingsSettings

/**
 * Factory function to create SystemSettings settings repository.
 * Uses existing SystemSettingsSettings from ProviderSettingsRepository.
 */
fun createSystemSettingsSettingsRepository(context: Context): SettingsRepository<SystemSettingsSettings> {
    return SettingsRepository(
        context = context,
        providerId = SystemSettingsSettings.PROVIDER_ID,
        default = { SystemSettingsSettings.default() },
        deserializer = { jsonString ->
            try {
                org.json.JSONObject(jsonString).let { SystemSettingsSettings.fromJson(it) }
            } catch (e: Exception) {
                null
            }
        },
        serializer = { settings -> settings.toJson().toString() }
    )
}
