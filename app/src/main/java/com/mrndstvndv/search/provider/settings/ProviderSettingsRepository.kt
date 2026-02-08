package com.mrndstvndv.search.provider.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Repository for provider settings.
 *
 * @param context Application context
 * @param scope CoroutineScope for async initialization. Pass null for synchronous initialization
 *              (useful for Workers that are already on IO thread).
 */
class ProviderSettingsRepository(
    context: Context,
    scope: CoroutineScope? = null,
) {
    companion object {
        private const val PREF_NAME = "provider_settings"
        private const val KEY_TRANSLUCENT_RESULTS = "translucent_results"
        private const val KEY_BACKGROUND_OPACITY = "background_opacity"
        private const val KEY_BACKGROUND_BLUR_STRENGTH = "background_blur_strength"
        private const val KEY_ACTIVITY_INDICATOR_DELAY_MS = "activity_indicator_delay_ms"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        private const val KEY_ENABLED_PROVIDERS = "enabled_providers"
        private const val KEY_SETTINGS_ICON_POSITION = "settings_icon_position"
        private const val KEY_SEARCH_BAR_POSITION = "search_bar_position"
        private const val DEFAULT_BACKGROUND_OPACITY = 0.35f
        private const val DEFAULT_BACKGROUND_BLUR_STRENGTH = 0.5f
        private const val DEFAULT_ACTIVITY_INDICATOR_DELAY_MS = 250
        private const val MAX_ACTIVITY_INDICATOR_DELAY_MS = 1000
        private const val DEFAULT_ANIMATIONS_ENABLED = true
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _translucentResultsEnabled = MutableStateFlow(true)
    val translucentResultsEnabled: StateFlow<Boolean> = _translucentResultsEnabled

    private val _backgroundOpacity = MutableStateFlow(DEFAULT_BACKGROUND_OPACITY)
    val backgroundOpacity: StateFlow<Float> = _backgroundOpacity

    private val _backgroundBlurStrength = MutableStateFlow(DEFAULT_BACKGROUND_BLUR_STRENGTH)
    val backgroundBlurStrength: StateFlow<Float> = _backgroundBlurStrength

    private val _activityIndicatorDelayMs = MutableStateFlow(DEFAULT_ACTIVITY_INDICATOR_DELAY_MS)
    val activityIndicatorDelayMs: StateFlow<Int> = _activityIndicatorDelayMs

    private val _motionPreferences = MutableStateFlow(MotionPreferences(animationsEnabled = DEFAULT_ANIMATIONS_ENABLED))
    val motionPreferences: StateFlow<MotionPreferences> = _motionPreferences

    private val _enabledProviders = MutableStateFlow(emptyMap<String, Boolean>())
    val enabledProviders: StateFlow<Map<String, Boolean>> = _enabledProviders

    private val _settingsIconPosition = MutableStateFlow(SettingsIconPosition.INSIDE)
    val settingsIconPosition: StateFlow<SettingsIconPosition> = _settingsIconPosition

    private val _searchBarPosition = MutableStateFlow(SearchBarPosition.BOTTOM)
    val searchBarPosition: StateFlow<SearchBarPosition> = _searchBarPosition

    init {
        if (scope != null) {
            scope.launch(Dispatchers.IO) {
                _translucentResultsEnabled.value = loadTranslucentResultsEnabled()
                _backgroundOpacity.value = loadBackgroundOpacity()
                _backgroundBlurStrength.value = loadBackgroundBlurStrength()
                _activityIndicatorDelayMs.value = loadActivityIndicatorDelayMs()
                _motionPreferences.value = loadMotionPreferences()
                _enabledProviders.value = loadEnabledProviders()
                _settingsIconPosition.value = loadSettingsIconPosition()
                _searchBarPosition.value = loadSearchBarPosition()
            }
        } else {
            _translucentResultsEnabled.value = loadTranslucentResultsEnabled()
            _backgroundOpacity.value = loadBackgroundOpacity()
            _backgroundBlurStrength.value = loadBackgroundBlurStrength()
            _activityIndicatorDelayMs.value = loadActivityIndicatorDelayMs()
            _motionPreferences.value = loadMotionPreferences()
            _enabledProviders.value = loadEnabledProviders()
            _settingsIconPosition.value = loadSettingsIconPosition()
            _searchBarPosition.value = loadSearchBarPosition()
        }
    }

    fun setTranslucentResultsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_TRANSLUCENT_RESULTS, enabled) }
        _translucentResultsEnabled.value = enabled
    }

    fun setBackgroundOpacity(alpha: Float) {
        val coercedAlpha = alpha.coerceIn(0f, 1f)
        preferences.edit { putFloat(KEY_BACKGROUND_OPACITY, coercedAlpha) }
        _backgroundOpacity.value = coercedAlpha
    }

    fun setBackgroundBlurStrength(strength: Float) {
        val coercedStrength = strength.coerceIn(0f, 1f)
        preferences.edit { putFloat(KEY_BACKGROUND_BLUR_STRENGTH, coercedStrength) }
        _backgroundBlurStrength.value = coercedStrength
    }

    fun setActivityIndicatorDelayMs(delayMs: Int) {
        val coercedDelay = delayMs.coerceIn(0, MAX_ACTIVITY_INDICATOR_DELAY_MS)
        preferences.edit { putInt(KEY_ACTIVITY_INDICATOR_DELAY_MS, coercedDelay) }
        _activityIndicatorDelayMs.value = coercedDelay
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ANIMATIONS_ENABLED, enabled) }
        _motionPreferences.value = _motionPreferences.value.copy(animationsEnabled = enabled)
    }

    fun setProviderEnabled(
        providerId: String,
        enabled: Boolean,
    ) {
        val current = _enabledProviders.value.toMutableMap()
        current[providerId] = enabled
        saveEnabledProviders(current)
    }

    private fun loadTranslucentResultsEnabled(): Boolean = preferences.getBoolean(KEY_TRANSLUCENT_RESULTS, true)

    private fun loadBackgroundOpacity(): Float = preferences.getFloat(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY)

    private fun loadBackgroundBlurStrength(): Float = preferences.getFloat(KEY_BACKGROUND_BLUR_STRENGTH, DEFAULT_BACKGROUND_BLUR_STRENGTH)

    private fun loadActivityIndicatorDelayMs(): Int {
        val stored = preferences.getInt(KEY_ACTIVITY_INDICATOR_DELAY_MS, DEFAULT_ACTIVITY_INDICATOR_DELAY_MS)
        return stored.coerceIn(0, MAX_ACTIVITY_INDICATOR_DELAY_MS)
    }

    private fun loadAnimationsEnabled(): Boolean = preferences.getBoolean(KEY_ANIMATIONS_ENABLED, DEFAULT_ANIMATIONS_ENABLED)

    private fun loadMotionPreferences(): MotionPreferences = MotionPreferences(animationsEnabled = loadAnimationsEnabled())

    private fun loadEnabledProviders(): Map<String, Boolean> {
        val json = preferences.getString(KEY_ENABLED_PROVIDERS, null) ?: return emptyMap()
        return try {
            val jsonObject = JSONObject(json)
            val map = mutableMapOf<String, Boolean>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.getBoolean(key)
            }
            map
        } catch (ignored: JSONException) {
            emptyMap()
        }
    }

    private fun saveEnabledProviders(providers: Map<String, Boolean>) {
        val jsonObject = JSONObject()
        providers.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        preferences.edit { putString(KEY_ENABLED_PROVIDERS, jsonObject.toString()) }
        _enabledProviders.value = providers
    }

    fun setSettingsIconPosition(position: SettingsIconPosition) {
        preferences.edit { putString(KEY_SETTINGS_ICON_POSITION, position.name) }
        _settingsIconPosition.value = position
    }

    private fun loadSettingsIconPosition(): SettingsIconPosition {
        val positionName = preferences.getString(KEY_SETTINGS_ICON_POSITION, null)
        return SettingsIconPosition.fromStorageValue(positionName)
    }

    fun setSearchBarPosition(position: SearchBarPosition) {
        preferences.edit { putString(KEY_SEARCH_BAR_POSITION, position.name) }
        _searchBarPosition.value = position
    }

    private fun loadSearchBarPosition(): SearchBarPosition {
        val positionName = preferences.getString(KEY_SEARCH_BAR_POSITION, null)
        return SearchBarPosition.fromStorageValue(positionName)
    }
}

data class WebSearchSettings(
    val defaultSiteId: String,
    val sites: List<WebSearchSite>,
    val quicklinks: List<Quicklink> = emptyList(),
) : ProviderSettings {
    override val providerId: String = PROVIDER_ID

    companion object {
        const val PROVIDER_ID = "web-search"
        private val DEFAULT_SITES =
            listOf(
                WebSearchSite(
                    id = "bing",
                    displayName = "Bing",
                    urlTemplate = "https://www.bing.com/search?q={query}&form=QBLH",
                ),
                WebSearchSite(
                    id = "duckduckgo",
                    displayName = "DuckDuckGo",
                    urlTemplate = "https://duckduckgo.com/?q={query}",
                ),
                WebSearchSite(
                    id = "google",
                    displayName = "Google",
                    urlTemplate = "https://www.google.com/search?q={query}",
                ),
                WebSearchSite(
                    id = "youtube",
                    displayName = "YouTube",
                    urlTemplate = "https://m.youtube.com/results?search_query={query}",
                ),
                WebSearchSite(
                    id = "twitter",
                    displayName = "Twitter",
                    urlTemplate = "https://x.com/search?q={query}",
                ),
                WebSearchSite(
                    id = "playstore",
                    displayName = "Play Store",
                    urlTemplate = "https://play.google.com/store/search?q={query}&c=apps",
                ),
                WebSearchSite(
                    id = "github",
                    displayName = "GitHub",
                    urlTemplate = "https://github.com/search?q={query}",
                ),
            )
        const val QUERY_PLACEHOLDER = "{query}"

        fun default(): WebSearchSettings =
            WebSearchSettings(
                defaultSiteId = DEFAULT_SITES.first().id,
                sites = DEFAULT_SITES,
                quicklinks = emptyList(),
            )

        fun fromJson(json: JSONObject?): WebSearchSettings? {
            if (json == null) return null
            val array = json.optJSONArray("sites") ?: return null
            val sites = mutableListOf<WebSearchSite>()
            for (i in 0 until array.length()) {
                WebSearchSite.fromJson(array.optJSONObject(i))?.let { sites.add(it) }
            }
            if (sites.isEmpty()) return null
            val candidate = json.optString("defaultSiteId", sites.first().id)
            val defaultId = sites.firstOrNull { it.id == candidate }?.id ?: sites.first().id

            val quicklinksArray = json.optJSONArray("quicklinks") ?: JSONArray()
            val quicklinks = mutableListOf<Quicklink>()
            for (i in 0 until quicklinksArray.length()) {
                Quicklink.fromJson(quicklinksArray.optJSONObject(i))?.let { quicklinks.add(it) }
            }

            return WebSearchSettings(defaultSiteId = defaultId, sites = sites, quicklinks = quicklinks)
        }
    }

    fun siteForId(id: String?): WebSearchSite? = sites.firstOrNull { it.id == id }

    override fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("defaultSiteId", defaultSiteId)

        val sitesArray = JSONArray()
        sites.forEach { sitesArray.put(it.toJson()) }
        root.put("sites", sitesArray)

        val quicklinksArray = JSONArray()
        quicklinks.forEach { quicklinksArray.put(it.toJson()) }
        root.put("quicklinks", quicklinksArray)

        return root
    }

    fun toJsonString(): String = toJson().toString()
}

data class WebSearchSite(
    val id: String,
    val displayName: String,
    val urlTemplate: String,
) {
    fun buildUrl(query: String): String {
        val template = normalizedTemplate()
        val encoded = Uri.encode(query)
        return template.replace(WebSearchSettings.QUERY_PLACEHOLDER, encoded)
    }

    private fun normalizedTemplate(): String =
        if (urlTemplate.contains(WebSearchSettings.QUERY_PLACEHOLDER)) {
            urlTemplate
        } else if (urlTemplate.contains("?")) {
            "$urlTemplate&q=${WebSearchSettings.QUERY_PLACEHOLDER}"
        } else {
            "$urlTemplate?q=${WebSearchSettings.QUERY_PLACEHOLDER}"
        }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("displayName", displayName)
            put("urlTemplate", urlTemplate)
        }

    companion object {
        fun fromJson(json: JSONObject?): WebSearchSite? {
            if (json == null) return null
            val id = json.opt("id") as? String ?: return null
            val name = json.opt("displayName") as? String ?: return null
            val template = json.opt("urlTemplate") as? String ?: return null
            return WebSearchSite(id = id, displayName = name, urlTemplate = template)
        }
    }
}

data class Quicklink(
    val id: String,
    val title: String,
    val url: String,
    val hasFavicon: Boolean = false,
) {
    fun displayUrl(): String =
        url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")

    fun domain(): String {
        val withoutProtocol =
            url
                .removePrefix("https://")
                .removePrefix("http://")
        return withoutProtocol.substringBefore("/").substringBefore("?")
    }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("title", title)
            put("url", url)
            put("hasFavicon", hasFavicon)
        }

    companion object {
        fun fromJson(json: JSONObject?): Quicklink? {
            if (json == null) return null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
            val url = json.optString("url").takeIf { it.isNotBlank() } ?: return null
            val hasFavicon = json.optBoolean("hasFavicon", false)
            return Quicklink(id = id, title = title, url = url, hasFavicon = hasFavicon)
        }
    }
}

enum class TextUtilityDefaultMode {
    ENCODE,
    DECODE,
}

data class TextUtilitiesSettings(
    val openDecodedUrls: Boolean,
    val disabledUtilities: Set<String> = emptySet(),
    val disabledKeywords: Map<String, Set<String>> = emptyMap(),
    val utilityDefaultModes: Map<String, TextUtilityDefaultMode> = emptyMap(),
) : ProviderSettings {
    override val providerId: String = PROVIDER_ID

    companion object {
        const val PROVIDER_ID = "text-utilities"

        fun default(): TextUtilitiesSettings =
            TextUtilitiesSettings(
                openDecodedUrls = true,
                disabledUtilities = emptySet(),
                disabledKeywords = emptyMap(),
                utilityDefaultModes = emptyMap(),
            )

        fun fromJson(json: JSONObject?): TextUtilitiesSettings? {
            if (json == null) return null

            val disabledUtilitiesArray = json.optJSONArray("disabledUtilities")
            val disabledUtilities =
                buildSet {
                    if (disabledUtilitiesArray != null) {
                        for (i in 0 until disabledUtilitiesArray.length()) {
                            disabledUtilitiesArray.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }

            val disabledKeywordsObj = json.optJSONObject("disabledKeywords")
            val disabledKeywords =
                buildMap<String, Set<String>> {
                    if (disabledKeywordsObj != null) {
                        val keys = disabledKeywordsObj.keys()
                        while (keys.hasNext()) {
                            val utilityId = keys.next()
                            val keywordsArray = disabledKeywordsObj.optJSONArray(utilityId)
                            if (keywordsArray != null) {
                                val keywords =
                                    buildSet {
                                        for (i in 0 until keywordsArray.length()) {
                                            keywordsArray.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                                        }
                                    }
                                if (keywords.isNotEmpty()) put(utilityId, keywords)
                            }
                        }
                    }
                }

            val modesObj = json.optJSONObject("utilityDefaultModes")
            val utilityDefaultModes =
                buildMap<String, TextUtilityDefaultMode> {
                    if (modesObj != null) {
                        val keys = modesObj.keys()
                        while (keys.hasNext()) {
                            val utilityId = keys.next()
                            val modeStr = modesObj.optString(utilityId)
                            val mode =
                                try {
                                    TextUtilityDefaultMode.valueOf(modeStr)
                                } catch (e: Exception) {
                                    null
                                }
                            if (mode != null) put(utilityId, mode)
                        }
                    }
                }

            return TextUtilitiesSettings(
                openDecodedUrls = json.optBoolean("openDecodedUrls", true),
                disabledUtilities = disabledUtilities,
                disabledKeywords = disabledKeywords,
                utilityDefaultModes = utilityDefaultModes,
            )
        }
    }

    override fun toJson(): JSONObject =
        JSONObject().apply {
            put("openDecodedUrls", openDecodedUrls)
            put("disabledUtilities", JSONArray(disabledUtilities.toList()))
            put(
                "disabledKeywords",
                JSONObject().apply {
                    disabledKeywords.forEach { (utilityId, keywords) ->
                        put(utilityId, JSONArray(keywords.toList()))
                    }
                },
            )
            put(
                "utilityDefaultModes",
                JSONObject().apply {
                    utilityDefaultModes.forEach { (utilityId, mode) ->
                        put(utilityId, mode.name)
                    }
                },
            )
        }

    fun toJsonString(): String = toJson().toString()
}

data class FileSearchSettings(
    val roots: List<FileSearchRoot>,
    val scanMetadata: Map<String, FileSearchScanMetadata>,
    val includeDownloads: Boolean,
    val loadThumbnails: Boolean,
    val thumbnailCropMode: FileSearchThumbnailCropMode,
    val sortMode: FileSearchSortMode,
    val sortAscending: Boolean,
    val syncIntervalMinutes: Int,
    val syncOnAppOpen: Boolean,
    val lastSyncTimestamp: Long,
) : ProviderSettings {
    override val providerId: String = PROVIDER_ID

    override fun toJson(): JSONObject {
        val json = JSONObject()
        val rootsArray = JSONArray()
        roots.forEach { rootsArray.put(it.toJson()) }
        json.put("roots", rootsArray)
        val metadata = JSONObject()
        scanMetadata.forEach { (rootId, data) ->
            metadata.put(rootId, data.toJson())
        }
        json.put("metadata", metadata)
        json.put("includeDownloads", includeDownloads)
        json.put("loadThumbnails", loadThumbnails)
        json.put("thumbnailCropMode", thumbnailCropMode.name)
        json.put("sortMode", sortMode.name)
        json.put("sortAscending", sortAscending)
        json.put("syncIntervalMinutes", syncIntervalMinutes)
        json.put("syncOnAppOpen", syncOnAppOpen)
        json.put("lastSyncTimestamp", lastSyncTimestamp)
        return json
    }

    fun toJsonString(): String {
        val json = JSONObject()
        val rootsArray = JSONArray()
        roots.forEach { rootsArray.put(it.toJson()) }
        json.put("roots", rootsArray)
        val metadata = JSONObject()
        scanMetadata.forEach { (rootId, data) ->
            metadata.put(rootId, data.toJson())
        }
        json.put("metadata", metadata)
        json.put("includeDownloads", includeDownloads)
        json.put("loadThumbnails", loadThumbnails)
        json.put("thumbnailCropMode", thumbnailCropMode.name)
        json.put("sortMode", sortMode.name)
        json.put("sortAscending", sortAscending)
        json.put("syncIntervalMinutes", syncIntervalMinutes)
        json.put("syncOnAppOpen", syncOnAppOpen)
        json.put("lastSyncTimestamp", lastSyncTimestamp)
        return json.toString()
    }

    fun rootById(rootId: String): FileSearchRoot? = roots.firstOrNull { it.id == rootId }

    fun enabledRoots(): List<FileSearchRoot> = roots.filter { it.isEnabled }

    fun hasEnabledRoots(): Boolean = includeDownloads || roots.any { it.isEnabled }

    companion object {
        const val PROVIDER_ID = "file-search"
        const val DOWNLOADS_ROOT_ID = "downloads-root"
        const val DEFAULT_SYNC_INTERVAL_MINUTES = 30
        const val DEFAULT_SYNC_ON_APP_OPEN = true

        fun empty(): FileSearchSettings =
            FileSearchSettings(
                roots = emptyList(),
                scanMetadata = emptyMap(),
                includeDownloads = false,
                loadThumbnails = true,
                thumbnailCropMode = FileSearchThumbnailCropMode.CENTER_CROP,
                sortMode = FileSearchSortMode.NAME,
                sortAscending = true,
                syncIntervalMinutes = DEFAULT_SYNC_INTERVAL_MINUTES,
                syncOnAppOpen = DEFAULT_SYNC_ON_APP_OPEN,
                lastSyncTimestamp = 0L,
            )

        fun fromJson(json: JSONObject?): FileSearchSettings? {
            if (json == null) return empty()
            val rootsArray = json.optJSONArray("roots") ?: JSONArray()
            val roots =
                buildList {
                    for (i in 0 until rootsArray.length()) {
                        val parsed = FileSearchRoot.fromJson(rootsArray.optJSONObject(i))
                        if (parsed != null) add(parsed)
                    }
                }
            val metadataObject = json.optJSONObject("metadata") ?: JSONObject()
            val metadata = mutableMapOf<String, FileSearchScanMetadata>()
            val keys = metadataObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val parsed = FileSearchScanMetadata.fromJson(metadataObject.optJSONObject(key))
                if (parsed != null) metadata[key] = parsed
            }
            val includeDownloads = json.optBoolean("includeDownloads", false)
            val loadThumbnails = json.optBoolean("loadThumbnails", true)
            val cropRaw = json.optString("thumbnailCropMode", FileSearchThumbnailCropMode.FIT.name)
            val cropMode = FileSearchThumbnailCropMode.fromStorageValue(cropRaw)
            val sortRaw = json.optString("sortMode", FileSearchSortMode.NAME.name)
            val sortMode = FileSearchSortMode.fromStorageValue(sortRaw)
            val sortAscending = json.optBoolean("sortAscending", true)
            val syncIntervalMinutes = json.optInt("syncIntervalMinutes", DEFAULT_SYNC_INTERVAL_MINUTES)
            val syncOnAppOpen = json.optBoolean("syncOnAppOpen", DEFAULT_SYNC_ON_APP_OPEN)
            val lastSyncTimestamp = json.optLong("lastSyncTimestamp", 0L)
            return FileSearchSettings(
                roots = roots,
                scanMetadata = metadata,
                includeDownloads = includeDownloads,
                loadThumbnails = loadThumbnails,
                thumbnailCropMode = cropMode,
                sortMode = sortMode,
                sortAscending = sortAscending,
                syncIntervalMinutes = syncIntervalMinutes,
                syncOnAppOpen = syncOnAppOpen,
                lastSyncTimestamp = lastSyncTimestamp,
            )
        }
    }
}

data class FileSearchRoot(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val isEnabled: Boolean = true,
    val addedAtMillis: Long,
    val parentDisplayName: String? = null,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("uri", uri.toString())
            put("displayName", displayName)
            put("isEnabled", isEnabled)
            put("addedAtMillis", addedAtMillis)
            if (!parentDisplayName.isNullOrBlank()) {
                put("parentDisplayName", parentDisplayName)
            }
        }

    companion object {
        fun fromJson(json: JSONObject?): FileSearchRoot? {
            if (json == null) return null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val uriValue = json.optString("uri").takeIf { it.isNotBlank() } ?: return null
            val uri = Uri.parse(uriValue)
            val name = json.optString("displayName").ifBlank { uri.lastPathSegment ?: uriValue }
            val enabled = json.optBoolean("isEnabled", true)
            val addedAt = json.optLong("addedAtMillis", 0L)
            val parent = json.optString("parentDisplayName").takeIf { it.isNotBlank() }
            return FileSearchRoot(
                id = id,
                uri = uri,
                displayName = name,
                isEnabled = enabled,
                addedAtMillis = addedAt,
                parentDisplayName = parent,
            )
        }

        fun downloadsRoot(): FileSearchRoot? {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val uri = directory?.let { Uri.fromFile(it) } ?: return null
            val parentName = directory.parentFile?.name
            return FileSearchRoot(
                id = FileSearchSettings.DOWNLOADS_ROOT_ID,
                uri = uri,
                displayName = "Downloads",
                isEnabled = true,
                addedAtMillis = directory.lastModified(),
                parentDisplayName = parentName,
            )
        }
    }
}

data class FileSearchScanMetadata(
    val state: FileSearchScanState,
    val indexedItemCount: Int,
    val updatedAtMillis: Long,
    val errorMessage: String? = null,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("state", state.name)
            put("indexedItemCount", indexedItemCount)
            put("updatedAtMillis", updatedAtMillis)
            if (errorMessage != null) {
                put("errorMessage", errorMessage)
            }
        }

    companion object {
        fun fromJson(json: JSONObject?): FileSearchScanMetadata? {
            if (json == null) return null
            val stateName = json.optString("state", FileSearchScanState.IDLE.name)
            val state =
                runCatching { FileSearchScanState.valueOf(stateName) }
                    .getOrDefault(FileSearchScanState.IDLE)
            val itemCount = json.optInt("indexedItemCount", 0)
            val updatedAt = json.optLong("updatedAtMillis", 0L)
            val rawError = json.optString("errorMessage")
            val error = rawError.takeIf { it.isNotBlank() }
            return FileSearchScanMetadata(
                state = state,
                indexedItemCount = itemCount,
                updatedAtMillis = updatedAt,
                errorMessage = error,
            )
        }
    }
}

enum class FileSearchScanState {
    IDLE,
    INDEXING,
    SUCCESS,
    ERROR,
}

enum class FileSearchThumbnailCropMode {
    FIT,
    CENTER_CROP,
    ;

    companion object {
        fun fromStorageValue(value: String?): FileSearchThumbnailCropMode {
            if (value.isNullOrBlank()) return CENTER_CROP
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CENTER_CROP
        }
    }
}

enum class FileSearchSortMode {
    DATE,
    NAME,
    ;

    companion object {
        fun fromStorageValue(value: String?): FileSearchSortMode {
            if (value.isNullOrBlank()) return NAME
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NAME
        }
    }
}

enum class AppListType {
    RECENT,
    PINNED,
    BOTH,
    ;

    companion object {
        fun fromStorageValue(value: String?): AppListType {
            if (value.isNullOrBlank()) return RECENT
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RECENT
        }
    }

    fun userFacingLabel(): String =
        when (this) {
            RECENT -> "Recent Apps"
            PINNED -> "Pinned Apps"
            BOTH -> "Both"
        }
}

data class AppSearchSettings(
    val includePackageName: Boolean,
    val aiAssistantQueriesEnabled: Boolean = true,
    val appListEnabled: Boolean = false,
    val appListType: AppListType = AppListType.RECENT,
    val reverseRecentAppsOrder: Boolean = false,
    val reversePinnedAppsOrder: Boolean = false,
    val bothLayoutPinnedOnLeft: Boolean = false,
    val filterPinnedFromRecentsInBoth: Boolean = true,
    val centerAppList: Boolean = false,
    val pinnedApps: List<String> = emptyList(),
    val hideAppListWhenResultsVisible: Boolean = true,
) : ProviderSettings {
    override val providerId: String = PROVIDER_ID

    companion object {
        const val PROVIDER_ID = "app-list"
        fun default(): AppSearchSettings =
            AppSearchSettings(
                includePackageName = false,
                aiAssistantQueriesEnabled = true,
                appListEnabled = false,
                appListType = AppListType.RECENT,
                reverseRecentAppsOrder = false,
                reversePinnedAppsOrder = false,
                bothLayoutPinnedOnLeft = false,
                filterPinnedFromRecentsInBoth = true,
                centerAppList = false,
                pinnedApps = emptyList(),
                hideAppListWhenResultsVisible = true,
            )

        fun fromJson(json: JSONObject?): AppSearchSettings? {
            if (json == null) return null
            val pinnedAppsArray = json.optJSONArray("pinnedApps")
            val pinnedApps =
                if (pinnedAppsArray != null) {
                    (0 until pinnedAppsArray.length()).mapNotNull { pinnedAppsArray.optString(it) }
                } else {
                    emptyList()
                }
            return AppSearchSettings(
                includePackageName = json.optBoolean("includePackageName", false),
                aiAssistantQueriesEnabled = json.optBoolean("aiAssistantQueriesEnabled", true),
                appListEnabled = json.optBoolean("appListEnabled", json.optBoolean("showRecentApps", false)),
                appListType = AppListType.fromStorageValue(json.optString("appListType")),
                reverseRecentAppsOrder = json.optBoolean("reverseRecentAppsOrder", false),
                reversePinnedAppsOrder = json.optBoolean("reversePinnedAppsOrder", false),
                bothLayoutPinnedOnLeft = json.optBoolean("bothLayoutPinnedOnLeft", false),
                filterPinnedFromRecentsInBoth = json.optBoolean("filterPinnedFromRecentsInBoth", true),
                centerAppList = json.optBoolean("centerAppList", false),
                pinnedApps = pinnedApps,
                hideAppListWhenResultsVisible = json.optBoolean("hideAppListWhenResultsVisible", true),
            )
        }
    }

    override fun toJson(): JSONObject =
        JSONObject().apply {
            put("includePackageName", includePackageName)
            put("aiAssistantQueriesEnabled", aiAssistantQueriesEnabled)
            put("appListEnabled", appListEnabled)
            put("appListType", appListType.name)
            put("reverseRecentAppsOrder", reverseRecentAppsOrder)
            put("reversePinnedAppsOrder", reversePinnedAppsOrder)
            put("bothLayoutPinnedOnLeft", bothLayoutPinnedOnLeft)
            put("filterPinnedFromRecentsInBoth", filterPinnedFromRecentsInBoth)
            put("centerAppList", centerAppList)
            put("pinnedApps", JSONArray(pinnedApps))
            put("hideAppListWhenResultsVisible", hideAppListWhenResultsVisible)
        }

    fun toJsonString(): String = toJson().toString()
}

data class SystemSettingsSettings(
    val developerToggleEnabled: Boolean,
) : ProviderSettings {
    override val providerId: String = PROVIDER_ID

    companion object {
        const val PROVIDER_ID = "system-settings"

        fun default(): SystemSettingsSettings = SystemSettingsSettings(developerToggleEnabled = false)

        fun fromJson(json: JSONObject?): SystemSettingsSettings? {
            if (json == null) return null
            return SystemSettingsSettings(
                developerToggleEnabled = json.optBoolean("developerToggleEnabled", false),
            )
        }
    }

    override fun toJson(): JSONObject =
        JSONObject().apply {
            put("developerToggleEnabled", developerToggleEnabled)
        }

    fun toJsonString(): String = toJson().toString()
}

data class ContactsSettings(
    val includePhoneNumbers: Boolean,
    val showSimNumbers: Boolean,
) : ProviderSettings {
    override val providerId: String = PROVIDER_ID

    companion object {
        const val PROVIDER_ID = "contacts"

        fun default(): ContactsSettings =
            ContactsSettings(
                includePhoneNumbers = true,
                showSimNumbers = false,
            )

        fun fromJson(json: JSONObject?): ContactsSettings? {
            if (json == null) return null
            return ContactsSettings(
                includePhoneNumbers = json.optBoolean("includePhoneNumbers", true),
                showSimNumbers = json.optBoolean("showSimNumbers", false),
            )
        }
    }

    override fun toJson(): JSONObject =
        JSONObject().apply {
            put("includePhoneNumbers", includePhoneNumbers)
            put("showSimNumbers", showSimNumbers)
        }

    fun toJsonString(): String = toJson().toString()
}
