package com.mrndstvndv.search.provider.contacts

import android.content.Context
import com.mrndstvndv.search.provider.settings.ContactsSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository

/**
 * Factory function to create Contacts settings repository.
 * Uses existing ContactsSettings from ProviderSettingsRepository.
 */
fun createContactsSettingsRepository(context: Context): SettingsRepository<ContactsSettings> {
    return SettingsRepository(
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
        serializer = { settings -> settings.toJson().toString() }
    )
}
