package com.mrndstvndv.search.provider.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Generic repository for any ProviderSettings type.
 * Handles persistence, change notification, and auto-registration.
 *
 * @param S The settings type (must implement ProviderSettings)
 * @param context Application context
 * @param providerId Unique identifier for the provider (used for prefs file naming)
 * @param default Factory function to create default settings
 * @param deserializer Function to parse settings from JSON string
 * @param serializer Function to convert settings to JSON string
 */
class SettingsRepository<S : ProviderSettings>(
    context: Context,
    private val providerId: String,
    private val default: () -> S,
    private val deserializer: (String) -> S?,
    private val serializer: (S) -> String,
) {
    /**
     * SharedPreferences file name: "{providerId}_settings"
     */
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "${providerId}_settings",
        Context.MODE_PRIVATE
    )

    /**
     * Internal MutableStateFlow for reactive updates.
     */
    private val _flow: MutableStateFlow<S>

    /**
     * Public read-only StateFlow. UI observes this for changes.
     */
    val flow: StateFlow<S>

    /**
     * Current settings value (immutable snapshot).
     * Providers read this in their query() method.
     */
    val value: S
        get() = _flow.value

    init {
        // Load or create default settings
        val settings = load()
        _flow = MutableStateFlow(settings)
        flow = _flow.asStateFlow()

        // Auto-register for backup/restore
        SettingsRegistry.register(this)
    }

    /**
     * Update settings atomically.
     * Triggers StateFlow emission and persists to disk.
     *
     * Usage:
     *   repository.update { it.copy(defaultSiteId = "bing") }
     */
    fun update(transform: (S) -> S) {
        val newSettings = transform(value)
        _flow.value = newSettings
        persist(newSettings)
    }

    /**
     * Replace settings entirely (used by backup restore).
     */
    fun replace(settings: S) {
        _flow.value = settings
        persist(settings)
    }

    /**
     * Serialize to JSON for backup export.
     */
    fun toBackupJson(): JSONObject = value.toJson()

    private fun load(): S {
        val jsonString = prefs.getString("settings", null)
        return if (jsonString != null) {
            deserializer(jsonString) ?: default()
        } else {
            default()
        }
    }

    private fun persist(settings: S) {
        prefs.edit().putString("settings", serializer(settings)).apply()
    }
}
