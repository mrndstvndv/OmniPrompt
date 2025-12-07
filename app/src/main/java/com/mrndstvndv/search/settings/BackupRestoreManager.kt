package com.mrndstvndv.search.settings

import android.content.Context
import android.net.Uri
import com.mrndstvndv.search.alias.AliasEntry
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages backup and restore operations for app settings.
 * Exports/imports settings as JSON using Storage Access Framework (SAF).
 */
class BackupRestoreManager(private val context: Context) {

    companion object {
        const val CURRENT_VERSION = 1
        const val BACKUP_FILE_PREFIX = "search_backup_"
        const val BACKUP_FILE_EXTENSION = ".json"
        const val MIME_TYPE_JSON = "application/json"

        // JSON Keys
        private const val KEY_VERSION = "version"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_APP_VERSION_CODE = "appVersionCode"
        private const val KEY_APP_VERSION_NAME = "appVersionName"
        private const val KEY_PROVIDER_SETTINGS = "providerSettings"
        private const val KEY_PROVIDER_RANKINGS = "providerRankings"
        private const val KEY_ALIASES = "aliases"

        // Provider Settings Keys
        private const val KEY_WEB_SEARCH = "webSearch"
        private const val KEY_APP_SEARCH = "appSearch"
        private const val KEY_TEXT_UTILITIES = "textUtilities"
        private const val KEY_FILE_SEARCH = "fileSearch"
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_BEHAVIOR = "behavior"
        private const val KEY_SYSTEM_SETTINGS = "systemSettings"
        private const val KEY_CONTACTS = "contacts"
        private const val KEY_ENABLED_PROVIDERS = "enabledProviders"

        // Appearance Keys
        private const val KEY_TRANSLUCENT_RESULTS = "translucentResults"
        private const val KEY_BACKGROUND_OPACITY = "backgroundOpacity"
        private const val KEY_BACKGROUND_BLUR_STRENGTH = "backgroundBlurStrength"

        // Behavior Keys
        private const val KEY_ANIMATIONS_ENABLED = "animationsEnabled"
        private const val KEY_ACTIVITY_INDICATOR_DELAY_MS = "activityIndicatorDelayMs"

        // Ranking Keys
        private const val KEY_PROVIDER_ORDER = "providerOrder"
        private const val KEY_RESULT_FREQUENCY = "resultFrequency"
        private const val KEY_USE_FREQUENCY_RANKING = "useFrequencyRanking"
    }

    /**
     * Result of a backup operation.
     */
    sealed class BackupResult {
        data class Success(val uri: Uri, val sizeBytes: Long) : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    /**
     * Result of a restore operation.
     */
    sealed class RestoreResult {
        data class Success(
            val settingsRestored: Int,
            val aliasesRestored: Int,
            val warnings: List<String>
        ) : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }

    /**
     * Preview data for confirmation dialog before restore.
     */
    data class BackupPreview(
        val version: Int,
        val timestamp: Long,
        val webSearchSitesCount: Int,
        val quicklinksCount: Int,
        val aliasesCount: Int,
        val fileSearchRootsCount: Int,
        val enabledProvidersCount: Int,
        val hasAppearanceSettings: Boolean,
        val hasBehaviorSettings: Boolean
    ) {
        fun formattedTimestamp(): String {
            val date = Date(timestamp)
            val format = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            return format.format(date)
        }
    }

    /**
     * Creates a backup JSON containing all app settings.
     */
    suspend fun createBackup(
        settingsRepository: ProviderSettingsRepository,
        rankingRepository: ProviderRankingRepository,
        aliasRepository: AliasRepository
    ): JSONObject = withContext(Dispatchers.IO) {
        val backup = JSONObject()

        // Metadata
        backup.put(KEY_VERSION, CURRENT_VERSION)
        backup.put(KEY_TIMESTAMP, System.currentTimeMillis())
        backup.put(KEY_APP_VERSION_CODE, getAppVersionCode())
        backup.put(KEY_APP_VERSION_NAME, getAppVersionName())

        // Provider Settings
        val providerSettings = JSONObject()
        providerSettings.put(KEY_WEB_SEARCH, settingsRepository.webSearchSettings.value.toJson())
        providerSettings.put(KEY_APP_SEARCH, settingsRepository.appSearchSettings.value.toJson())
        providerSettings.put(KEY_TEXT_UTILITIES, settingsRepository.textUtilitiesSettings.value.toJson())
        providerSettings.put(KEY_FILE_SEARCH, JSONObject(settingsRepository.fileSearchSettings.value.toJsonString()))
        providerSettings.put(KEY_SYSTEM_SETTINGS, settingsRepository.systemSettingsSettings.value.toJson())
        providerSettings.put(KEY_CONTACTS, settingsRepository.contactsSettings.value.toJson())

        // Appearance settings
        val appearance = JSONObject()
        appearance.put(KEY_TRANSLUCENT_RESULTS, settingsRepository.translucentResultsEnabled.value)
        appearance.put(KEY_BACKGROUND_OPACITY, settingsRepository.backgroundOpacity.value.toDouble())
        appearance.put(KEY_BACKGROUND_BLUR_STRENGTH, settingsRepository.backgroundBlurStrength.value.toDouble())
        providerSettings.put(KEY_APPEARANCE, appearance)

        // Behavior settings
        val behavior = JSONObject()
        behavior.put(KEY_ANIMATIONS_ENABLED, settingsRepository.motionPreferences.value.animationsEnabled)
        behavior.put(KEY_ACTIVITY_INDICATOR_DELAY_MS, settingsRepository.activityIndicatorDelayMs.value)
        providerSettings.put(KEY_BEHAVIOR, behavior)

        // Enabled providers
        val enabledProviders = JSONObject()
        settingsRepository.enabledProviders.value.forEach { (key, value) ->
            enabledProviders.put(key, value)
        }
        providerSettings.put(KEY_ENABLED_PROVIDERS, enabledProviders)

        backup.put(KEY_PROVIDER_SETTINGS, providerSettings)

        // Provider Rankings
        val rankings = JSONObject()
        rankings.put(KEY_PROVIDER_ORDER, JSONArray(rankingRepository.providerOrder.value))
        val resultFrequency = JSONObject()
        rankingRepository.resultFrequency.value.forEach { (key, value) ->
            resultFrequency.put(key, value)
        }
        rankings.put(KEY_RESULT_FREQUENCY, resultFrequency)
        rankings.put(KEY_USE_FREQUENCY_RANKING, rankingRepository.useFrequencyRanking.value)
        backup.put(KEY_PROVIDER_RANKINGS, rankings)

        // Aliases
        val aliasesArray = JSONArray()
        aliasRepository.aliases.value.forEach { entry ->
            aliasesArray.put(entry.toJson())
        }
        backup.put(KEY_ALIASES, aliasesArray)

        backup
    }

    /**
     * Writes a backup JSON to the specified URI.
     */
    suspend fun writeBackupToUri(uri: Uri, backupJson: JSONObject): BackupResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = backupJson.toString(2) // Pretty print with 2-space indent
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            } ?: return@withContext BackupResult.Error("Could not open file for writing")

            val size = jsonString.toByteArray(Charsets.UTF_8).size.toLong()
            BackupResult.Success(uri, size)
        } catch (e: IOException) {
            BackupResult.Error("Failed to save backup: ${e.message}")
        } catch (e: SecurityException) {
            BackupResult.Error("Permission denied: ${e.message}")
        }
    }

    /**
     * Reads and validates a backup JSON from the specified URI.
     */
    suspend fun readBackupFromUri(uri: Uri): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return@withContext Result.failure(IOException("Could not open file for reading"))

            val json = JSONObject(jsonString)

            // Basic validation
            if (!json.has(KEY_VERSION)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid backup file: missing version"))
            }

            val version = json.optInt(KEY_VERSION, -1)
            if (version < 1) {
                return@withContext Result.failure(IllegalArgumentException("Invalid backup file: invalid version"))
            }

            if (version > CURRENT_VERSION) {
                return@withContext Result.failure(IllegalArgumentException(
                    "Backup was created with a newer app version. Please update the app first."
                ))
            }

            Result.success(json)
        } catch (e: IOException) {
            Result.failure(IOException("Failed to read backup file: ${e.message}"))
        } catch (e: JSONException) {
            Result.failure(IllegalArgumentException("Invalid backup file: not a valid JSON"))
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Permission denied: ${e.message}"))
        }
    }

    /**
     * Parses a backup JSON to create a preview for the confirmation dialog.
     */
    fun parseBackupPreview(backupJson: JSONObject): BackupPreview? {
        return try {
            val version = backupJson.optInt(KEY_VERSION, -1)
            val timestamp = backupJson.optLong(KEY_TIMESTAMP, 0L)

            val providerSettings = backupJson.optJSONObject(KEY_PROVIDER_SETTINGS)

            // Web search
            val webSearch = providerSettings?.optJSONObject(KEY_WEB_SEARCH)
            val sitesCount = webSearch?.optJSONArray("sites")?.length() ?: 0
            val quicklinksCount = webSearch?.optJSONArray("quicklinks")?.length() ?: 0

            // File search
            val fileSearch = providerSettings?.optJSONObject(KEY_FILE_SEARCH)
            val rootsCount = fileSearch?.optJSONArray("roots")?.length() ?: 0

            // Enabled providers
            val enabledProviders = providerSettings?.optJSONObject(KEY_ENABLED_PROVIDERS)
            val enabledCount = enabledProviders?.length() ?: 0

            // Appearance and behavior
            val hasAppearance = providerSettings?.has(KEY_APPEARANCE) == true
            val hasBehavior = providerSettings?.has(KEY_BEHAVIOR) == true

            // Aliases
            val aliasesCount = backupJson.optJSONArray(KEY_ALIASES)?.length() ?: 0

            BackupPreview(
                version = version,
                timestamp = timestamp,
                webSearchSitesCount = sitesCount,
                quicklinksCount = quicklinksCount,
                aliasesCount = aliasesCount,
                fileSearchRootsCount = rootsCount,
                enabledProvidersCount = enabledCount,
                hasAppearanceSettings = hasAppearance,
                hasBehaviorSettings = hasBehavior
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Restores settings from a backup JSON.
     * Uses partial restore strategy: continues on individual failures and collects warnings.
     */
    suspend fun restoreFromBackup(
        backupJson: JSONObject,
        settingsRepository: ProviderSettingsRepository,
        rankingRepository: ProviderRankingRepository,
        aliasRepository: AliasRepository
    ): RestoreResult = withContext(Dispatchers.IO) {
        val warnings = mutableListOf<String>()
        var settingsRestored = 0
        var aliasesRestored = 0

        try {
            val providerSettings = backupJson.optJSONObject(KEY_PROVIDER_SETTINGS)

            // Restore Web Search Settings
            providerSettings?.optJSONObject(KEY_WEB_SEARCH)?.let { webSearchJson ->
                try {
                    val settings = com.mrndstvndv.search.provider.settings.WebSearchSettings.fromJson(webSearchJson)
                    if (settings != null) {
                        settingsRepository.saveWebSearchSettings(settings)
                        settingsRestored++
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to restore web search settings")
                }
            }

            // Restore App Search Settings
            providerSettings?.optJSONObject(KEY_APP_SEARCH)?.let { appSearchJson ->
                try {
                    val settings = com.mrndstvndv.search.provider.settings.AppSearchSettings.fromJson(appSearchJson)
                    if (settings != null) {
                        settingsRepository.saveAppSearchSettings(settings)
                        settingsRestored++
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to restore app search settings")
                }
            }

            // Restore Text Utilities Settings
            providerSettings?.optJSONObject(KEY_TEXT_UTILITIES)?.let { textUtilsJson ->
                try {
                    val settings = com.mrndstvndv.search.provider.settings.TextUtilitiesSettings.fromJson(textUtilsJson)
                    if (settings != null) {
                        // We need to use reflection or add a public save method
                        // For now, restore individual settings
                        settingsRepository.setOpenDecodedUrlsAutomatically(settings.openDecodedUrls)
                        settings.disabledUtilities.forEach { utilityId ->
                            settingsRepository.setUtilityEnabled(utilityId, false)
                        }
                        settingsRestored++
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to restore text utilities settings")
                }
            }

            // Restore File Search Settings
            providerSettings?.optJSONObject(KEY_FILE_SEARCH)?.let { fileSearchJson ->
                try {
                    val settings = com.mrndstvndv.search.provider.settings.FileSearchSettings.fromJson(fileSearchJson)
                    if (settings != null) {
                        // Restore basic settings
                        settingsRepository.setDownloadsIndexingEnabled(settings.includeDownloads)
                        settingsRepository.setFileSearchThumbnailsEnabled(settings.loadThumbnails)
                        settingsRepository.setFileSearchThumbnailCropMode(settings.thumbnailCropMode)
                        settingsRepository.setFileSearchSortMode(settings.sortMode)
                        settingsRepository.setFileSearchSortAscending(settings.sortAscending)
                        settingsRepository.setFileSearchSyncInterval(settings.syncIntervalMinutes)
                        settingsRepository.setFileSearchSyncOnAppOpen(settings.syncOnAppOpen)

                        // Restore roots (they may need permission re-grant)
                        var rootsNeedingPermission = 0
                        settings.roots.forEach { root ->
                            settingsRepository.addFileSearchRoot(root)
                            rootsNeedingPermission++
                        }
                        if (rootsNeedingPermission > 0) {
                            warnings.add("$rootsNeedingPermission file search folder(s) may need permission")
                        }
                        settingsRestored++
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to restore file search settings")
                }
            }

            // Restore Appearance Settings
            providerSettings?.optJSONObject(KEY_APPEARANCE)?.let { appearanceJson ->
                try {
                    settingsRepository.setTranslucentResultsEnabled(
                        appearanceJson.optBoolean(KEY_TRANSLUCENT_RESULTS, true)
                    )
                    settingsRepository.setBackgroundOpacity(
                        appearanceJson.optDouble(KEY_BACKGROUND_OPACITY, 0.35).toFloat()
                    )
                    settingsRepository.setBackgroundBlurStrength(
                        appearanceJson.optDouble(KEY_BACKGROUND_BLUR_STRENGTH, 0.5).toFloat()
                    )
                    settingsRestored++
                } catch (e: Exception) {
                    warnings.add("Failed to restore appearance settings")
                }
            }

            // Restore Behavior Settings
            providerSettings?.optJSONObject(KEY_BEHAVIOR)?.let { behaviorJson ->
                try {
                    settingsRepository.setAnimationsEnabled(
                        behaviorJson.optBoolean(KEY_ANIMATIONS_ENABLED, true)
                    )
                    settingsRepository.setActivityIndicatorDelayMs(
                        behaviorJson.optInt(KEY_ACTIVITY_INDICATOR_DELAY_MS, 250)
                    )
                    settingsRestored++
                } catch (e: Exception) {
                    warnings.add("Failed to restore behavior settings")
                }
            }

            // Restore System Settings
            providerSettings?.optJSONObject(KEY_SYSTEM_SETTINGS)?.let { systemJson ->
                try {
                    val settings = com.mrndstvndv.search.provider.settings.SystemSettingsSettings.fromJson(systemJson)
                    if (settings != null) {
                        settingsRepository.saveSystemSettingsSettings(settings)
                        settingsRestored++
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to restore system settings")
                }
            }

            // Restore Contacts Settings
            providerSettings?.optJSONObject(KEY_CONTACTS)?.let { contactsJson ->
                try {
                    val settings = com.mrndstvndv.search.provider.settings.ContactsSettings.fromJson(contactsJson)
                    if (settings != null) {
                        settingsRepository.saveContactsSettings(settings)
                        settingsRestored++
                    }
                } catch (e: Exception) {
                    warnings.add("Failed to restore contacts settings")
                }
            }

            // Restore Enabled Providers
            providerSettings?.optJSONObject(KEY_ENABLED_PROVIDERS)?.let { enabledJson ->
                try {
                    val keys = enabledJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        settingsRepository.setProviderEnabled(key, enabledJson.getBoolean(key))
                    }
                    settingsRestored++
                } catch (e: Exception) {
                    warnings.add("Failed to restore provider toggle states")
                }
            }

            // Restore Provider Rankings
            backupJson.optJSONObject(KEY_PROVIDER_RANKINGS)?.let { rankingsJson ->
                try {
                    // Provider order
                    rankingsJson.optJSONArray(KEY_PROVIDER_ORDER)?.let { orderArray ->
                        val order = (0 until orderArray.length()).map { orderArray.getString(it) }
                        rankingRepository.setProviderOrder(order)
                    }

                    // Use frequency ranking
                    if (rankingsJson.has(KEY_USE_FREQUENCY_RANKING)) {
                        rankingRepository.setUseFrequencyRanking(
                            rankingsJson.getBoolean(KEY_USE_FREQUENCY_RANKING)
                        )
                    }

                    // Note: Result frequency is intentionally not restored as it's usage-based data
                    settingsRestored++
                } catch (e: Exception) {
                    warnings.add("Failed to restore provider rankings")
                }
            }

            // Restore Aliases
            backupJson.optJSONArray(KEY_ALIASES)?.let { aliasesArray ->
                var duplicateCount = 0
                for (i in 0 until aliasesArray.length()) {
                    try {
                        val aliasJson = aliasesArray.optJSONObject(i)
                        val entry = AliasEntry.fromJson(aliasJson)
                        if (entry != null) {
                            val result = aliasRepository.addAlias(entry.alias, entry.target)
                            when (result) {
                                AliasRepository.SaveResult.SUCCESS -> aliasesRestored++
                                AliasRepository.SaveResult.DUPLICATE -> duplicateCount++
                                AliasRepository.SaveResult.INVALID_ALIAS -> { /* skip */ }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip invalid alias entry
                    }
                }
                if (duplicateCount > 0) {
                    warnings.add("$duplicateCount alias(es) skipped (already exist)")
                }
            }

            RestoreResult.Success(
                settingsRestored = settingsRestored,
                aliasesRestored = aliasesRestored,
                warnings = warnings
            )
        } catch (e: Exception) {
            RestoreResult.Error("Failed to restore backup: ${e.message}")
        }
    }

    /**
     * Generates a suggested filename for the backup.
     */
    fun generateBackupFilename(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "$BACKUP_FILE_PREFIX$timestamp$BACKUP_FILE_EXTENSION"
    }

    private fun getAppVersionCode(): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun getAppVersionName(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
