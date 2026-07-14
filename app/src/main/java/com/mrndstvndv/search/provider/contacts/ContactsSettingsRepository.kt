package com.mrndstvndv.search.provider.contacts

import android.content.Context
import com.mrndstvndv.search.provider.settings.ContactsSettings
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository

/**
 * Factory function to create Contacts settings repository.
 * Uses existing ContactsSettings from SettingsRepository.
 */
fun createContactsSettingsRepository(context: Context): ProviderSettingsRepository<ContactsSettings> {
    return ProviderSettingsRepository(
        context = context,
        providerId = ContactsSettings.PROVIDER_ID,
        default = { ContactsSettings.default() },
        deserializer = { jsonString ->
            try {
                org.json.JSONObject(jsonString).let { ContactsSettings.fromJson(it) }
            } catch (e: Exception) {
                null
            }
        },
        serializer = { settings -> settings.toJson().toString() },
    )
}
