package com.mrndstvndv.search.provider.settings

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
        repositories.forEach { (id, repo) ->
            root.put(id, repo.toBackupJson())
        }
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
            json.optJSONObject(id)?.let { providerJson ->
                @Suppress("UNCHECKED_CAST")
                val typedRepo = repo as SettingsRepository<ProviderSettings>
                // Each provider handles their own migration in fromJson()
                // For now, mark as false - providers need to implement import logic
                results[id] = false
            } ?: run {
                results[id] = false
            }
        }
        return results
    }

    /**
     * Clear registry (useful for testing).
     */
    fun clear() {
        repositories.clear()
    }
}
