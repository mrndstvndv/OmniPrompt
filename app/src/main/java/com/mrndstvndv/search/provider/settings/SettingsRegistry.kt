package com.mrndstvndv.search.provider.settings

import org.json.JSONArray
import org.json.JSONObject

/**
 * Registry for all provider settings repositories.
 * Used by BackupRestoreManager for auto-discovery.
 */
object SettingsRegistry {
    private val repositories = mutableMapOf<String, SettingsRepository<*>>()

    /**
     * Register a repository. Called automatically in SettingsRepository.init.
     */
    fun register(repository: SettingsRepository<*>) {
        // Get providerId from the current value
        repositories[repository.value.providerId] = repository
    }

    /**
     * Get all registered repositories.
     */
    fun getAll(): List<SettingsRepository<*>> = repositories.values.toList()

    /**
     * Get a specific repository by provider ID.
     */
    fun get(providerId: String): SettingsRepository<*>? = repositories[providerId]

    /**
     * Export all settings for backup.
     */
    fun exportAll(): JSONObject {
        val root = JSONObject()
        val debug = JSONArray()
        repositories.forEach { (id, repo) ->
            val json = repo.toBackupJson()
            root.put(id, json)
            debug.put(
                JSONObject().apply {
                    put("id", id)
                    put("json", json.toString())
                }
            )
        }
        root.put("_registry_dump", debug)
        return root
    }

    /**
     * Import settings from backup.
     * Returns map of providerId to success boolean.
     *
     * Note: Each provider must implement fromJson in their settings companion object.
     * The import logic should be implemented per-provider in their settings class.
     */
    fun importAll(json: JSONObject): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        repositories.forEach { (id, repo) ->
            val providerJson = getProviderJson(json, id)
            results[id] = providerJson?.let { repo.replaceFromJson(it) } ?: false
        }
        return results
    }

    fun getProviderJson(
        json: JSONObject,
        providerId: String,
    ): JSONObject? {
        json.optJSONObject(providerId)?.let { return it }
        val legacyKey =
            when (providerId) {
                "web-search" -> "webSearch"
                "app-list" -> "appSearch"
                "text-utilities" -> "textUtilities"
                "file-search" -> "fileSearch"
                "system-settings" -> "systemSettings"
                else -> providerId
            }
        return json.optJSONObject(legacyKey)
    }

    /**
     * Clear registry (useful for testing).
     */
    fun clear() {
        repositories.clear()
    }
}
