package com.mrndstvndv.search.provider.settings

import org.json.JSONObject

/**
 * Marker interface for all provider settings data classes.
 * Implementations must be immutable data classes.
 */
interface ProviderSettings {
    /**
     * Unique provider identifier (same as Provider.id).
     * Used for SharedPreferences file naming and backup identification.
     */
    val providerId: String

    /**
     * Serialize settings to JSON for persistence.
     */
    fun toJson(): JSONObject
}
