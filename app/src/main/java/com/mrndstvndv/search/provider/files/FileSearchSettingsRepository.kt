package com.mrndstvndv.search.provider.files

import android.content.Context
import com.mrndstvndv.search.provider.settings.FileSearchSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository

/**
 * Factory function to create FileSearch settings repository.
 * Uses existing FileSearchSettings from ProviderSettingsRepository.
 */
fun createFileSearchSettingsRepository(context: Context): SettingsRepository<FileSearchSettings> {
    return SettingsRepository(
        context = context,
        providerId = FileSearchSettings.PROVIDER_ID,
        default = { FileSearchSettings.empty() },
        deserializer = { jsonString ->
            try {
                org.json.JSONObject(jsonString).let { FileSearchSettings.fromJson(it) }
            } catch (e: Exception) {
                null
            }
        },
        serializer = { settings -> settings.toJson().toString() }
    )
}
