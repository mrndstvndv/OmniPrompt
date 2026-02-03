# Provider Settings Refactoring Specification

## Overview

This document defines the architecture for migrating provider settings from a centralized repository to provider-owned settings using a generic, reusable repository pattern.

**Goals:**
- Co-locate settings with providers (same package)
- Minimize boilerplate (Pattern 4)
- Type-safe settings
- Easy backup/restore via auto-discovery
- Easy migration path to DataStore later

---

## Core Interfaces

### 1. ProviderSettings Interface

**Location:** `provider/settings/ProviderSettings.kt`

```kotlin
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
```

### 2. SettingsRepository Generic Class

**Location:** `provider/settings/SettingsRepository.kt`

```kotlin
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
 * @param default Factory function to create default settings
 * @param deserializer Function to parse settings from JSON string
 * @param serializer Function to convert settings to JSON string
 */
class SettingsRepository<S : ProviderSettings>(
    context: Context,
    private val default: () -> S,
    private val deserializer: (String) -> S?,
    private val serializer: (S) -> String,
) {
    /**
     * SharedPreferences file name: "{providerId}_settings"
     */
    private val prefs: SharedPreferences
    
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
        prefs = context.getSharedPreferences("${settings.providerId}_settings", Context.MODE_PRIVATE)
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
        // Subclasses will override this to load from their specific prefs
        return default()
    }
    
    private fun persist(settings: S) {
        prefs.edit().putString("settings", serializer(settings)).apply()
    }
}
```

### 3. SettingsRegistry

**Location:** `provider/settings/SettingsRegistry.kt`

```kotlin
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
        repositories[repository.value.providerId] = repository
    }
    
    /**
     * Get all registered repositories.
     */
    fun getAll(): List<SettingsRepository<*>> = repositories.values.toList()
    
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
     */
    fun importAll(json: JSONObject): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        repositories.forEach { (id, repo) ->
            json.optJSONObject(id)?.let { providerJson ->
                @Suppress("UNCHECKED_CAST")
                val typedRepo = repo as SettingsRepository<ProviderSettings>
                // Note: Provider needs to provide fromJson method
                results[id] = false // Will be implemented per-provider
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
```

---

## Provider Implementation Pattern

### File Structure per Provider

Each provider lives in its own package with 3 files:

```
provider/
└── web/
    ├── WebSearchProvider.kt          # Provider implementation
    ├── WebSearchSettings.kt          # Settings data class + repository factory
    └── WebSearchSettingsScreen.kt    # Settings UI (moved from ui/settings/)
```

### 1. Settings Data Class

**File:** `provider/{type}/{Type}Settings.kt`

```kotlin
package com.mrndstvndv.search.provider.web

import android.content.Context
import com.mrndstvndv.search.provider.settings.ProviderSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable settings for WebSearchProvider.
 */
data class WebSearchSettings(
    val defaultSiteId: String,
    val sites: List<WebSearchSite>,
    val quicklinks: List<Quicklink> = emptyList(),
) : ProviderSettings {
    
    override val providerId = PROVIDER_ID
    
    companion object {
        const val PROVIDER_ID = "web-search"
        
        /**
         * Create default settings.
         */
        fun default(): WebSearchSettings = WebSearchSettings(
            defaultSiteId = "google",
            sites = listOf(
                WebSearchSite("google", "Google", "https://www.google.com/search?q={query}"),
                WebSearchSite("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q={query}"),
                // ... more defaults
            ),
            quicklinks = emptyList(),
        )
        
        /**
         * Deserialize from JSON.
         * Returns null if parsing fails (caller should use default).
         */
        fun fromJson(json: JSONObject): WebSearchSettings? = try {
            val sitesArray = json.getJSONArray("sites")
            val sites = (0 until sitesArray.length()).mapNotNull { i ->
                WebSearchSite.fromJson(sitesArray.getJSONObject(i))
            }
            
            val quicklinksArray = json.optJSONArray("quicklinks")
            val quicklinks = quicklinksArray?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    Quicklink.fromJson(arr.getJSONObject(i))
                }
            } ?: emptyList()
            
            WebSearchSettings(
                defaultSiteId = json.getString("defaultSiteId"),
                sites = sites,
                quicklinks = quicklinks,
            )
        } catch (e: Exception) {
            null
        }
        
        /**
         * Parse from JSON string (for repository).
         */
        fun fromJsonString(jsonString: String): WebSearchSettings? = 
            fromJson(JSONObject(jsonString))
    }
    
    /**
     * Serialize to JSON.
     */
    override fun toJson(): JSONObject = JSONObject().apply {
        put("defaultSiteId", defaultSiteId)
        put("sites", JSONArray(sites.map { it.toJson() }))
        put("quicklinks", JSONArray(quicklinks.map { it.toJson() }))
    }
    
    /**
     * Convenience method for repository.
     */
    fun toJsonString(): String = toJson().toString()
}

/**
 * Factory function to create repository.
 * Called from MainActivity.
 */
fun createWebSearchSettingsRepository(context: Context): SettingsRepository<WebSearchSettings> {
    return SettingsRepository(
        context = context,
        default = { WebSearchSettings.default() },
        deserializer = { jsonString -> WebSearchSettings.fromJsonString(jsonString) },
        serializer = { settings -> settings.toJsonString() }
    )
}
```

### 2. Supporting Data Classes

Include in same file or separate file if shared:

```kotlin
data class WebSearchSite(
    val id: String,
    val displayName: String,
    val urlTemplate: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("urlTemplate", urlTemplate)
    }
    
    companion object {
        fun fromJson(json: JSONObject): WebSearchSite? = try {
            WebSearchSite(
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                urlTemplate = json.getString("urlTemplate"),
            )
        } catch (e: Exception) { null }
    }
}

data class Quicklink(
    val id: String,
    val title: String,
    val url: String,
    val hasFavicon: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("url", url)
        put("hasFavicon", hasFavicon)
    }
    
    companion object {
        fun fromJson(json: JSONObject): Quicklink? = try {
            Quicklink(
                id = json.getString("id"),
                title = json.getString("title"),
                url = json.getString("url"),
                hasFavicon = json.optBoolean("hasFavicon", false),
            )
        } catch (e: Exception) { null }
    }
}
```

### 3. Provider Implementation

**File:** `provider/{type}/{Type}Provider.kt`

```kotlin
package com.mrndstvndv.search.provider.web

import androidx.activity.ComponentActivity
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

class WebSearchProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: SettingsRepository<WebSearchSettings>,
) : Provider {
    
    override val id = WebSearchSettings.PROVIDER_ID
    override val displayName = "Web Search"
    override val refreshSignal = MutableSharedFlow<Unit>()
    
    override suspend fun query(query: Query): List<ProviderResult> {
        val settings = settingsRepository.value  // Immutable snapshot
        
        // Use settings to build results
        val defaultSite = settings.sites.find { it.id == settings.defaultSiteId }
        val quicklinks = settings.quicklinks
        
        // ... query logic
        return emptyList()
    }
}
```

### 4. Settings Screen

**File:** `provider/{type}/{Type}SettingsScreen.kt`

```kotlin
package com.mrndstvndv.search.provider.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mrndstvndv.search.provider.settings.SettingsRepository

@Composable
fun WebSearchSettingsScreen(
    repository: SettingsRepository<WebSearchSettings>,
    onNavigateBack: () -> Unit,
) {
    val settings by repository.flow.collectAsState()
    
    // Build UI using settings value
    // Call repository.update { it.copy(...) } when user changes settings
}
```

---

## MainActivity Integration

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create all provider settings repositories (auto-register)
        val webSearchRepo = createWebSearchSettingsRepository(this)
        val appSearchRepo = createAppSearchSettingsRepository(this)
        val fileSearchRepo = createFileSearchSettingsRepository(this)
        // ... etc
        
        // Create providers with their repositories
        val providers = listOf(
            WebSearchProvider(this, webSearchRepo),
            AppListProvider(this, appSearchRepo),
            FileSearchProvider(this, fileSearchRepo),
            // ... etc
        )
        
        // Observe all settings changes for re-query
        val settingsFlows = providers.mapNotNull { 
            it.settingsRepository?.flow 
        }
        
        LaunchedEffect(Unit) {
            merge(*settingsFlows.toTypedArray()).collect {
                // Trigger re-query with new settings
            }
        }
    }
}
```

---

## Backup/Restore Integration

**File:** `settings/BackupRestoreManager.kt` (updated)

```kotlin
class BackupRestoreManager(private val context: Context) {
    
    suspend fun createBackup(): JSONObject {
        return SettingsRegistry.exportAll()
    }
    
    suspend fun restoreFromBackup(json: JSONObject): RestoreResult {
        // Each provider handles its own migration in fromJson()
        val results = SettingsRegistry.importAll(json)
        
        return RestoreResult.Success(
            settingsRestored = results.count { it.value },
            warnings = results.filter { !it.value }.keys.toList()
        )
    }
}
```

---

## Migration Guide for Subagents

### Step 1: Identify Current Settings

Find the settings data class in `ProviderSettingsRepository.kt`:
- Look for `data class XxxSettings`
- Note all fields and their types
- Find the `toJson()` and `fromJson()` methods

### Step 2: Create New Files

Create 3 files in `provider/{type}/`:
1. `{Type}Settings.kt` - Move data class here
2. Update `{Type}Provider.kt` - Inject repository
3. Move settings screen from `ui/settings/`

### Step 3: Update Provider

Change constructor from:
```kotlin
class XxxProvider(
    activity: ComponentActivity,
    settingsRepository: ProviderSettingsRepository,  // OLD
)
```

To:
```kotlin
class XxxProvider(
    activity: ComponentActivity,
    settingsRepository: SettingsRepository<XxxSettings>,  // NEW
)
```

Update query method:
```kotlin
// OLD
val settings = settingsRepository.xxxSettings.value

// NEW
val settings = settingsRepository.value
```

### Step 4: Update MainActivity

Change from:
```kotlin
val providers = listOf(
    XxxProvider(this, providerSettingsRepository)  // OLD
)
```

To:
```kotlin
val xxxRepo = createXxxSettingsRepository(this)  // NEW
val providers = listOf(
    XxxProvider(this, xxxRepo)
)
```

### Step 5: Test

- Verify settings persist across app restarts
- Verify backup/restore works
- Verify UI updates when settings change

---

## DataStore Migration Path (Future)

When ready to migrate to DataStore:

1. Create new repository implementation:
```kotlin
class DataStoreSettingsRepository<S : ProviderSettings>(...)
```

2. Swap in factory function:
```kotlin
fun createWebSearchSettingsRepository(context: Context): SettingsRepository<WebSearchSettings> {
    return DataStoreSettingsRepository(...)  // Instead of SettingsRepository
}
```

3. Provider code **doesn't change** - same interface!

---

## Common Pitfalls

1. **Don't forget `providerId` in settings** - Must match Provider.id
2. **Always handle null in fromJson** - Return null on parse error, don't throw
3. **Use immutable data classes** - No var fields, use copy() for updates
4. **Auto-registration happens in init** - Make sure to hold reference to repository
5. **Separate prefs files per provider** - Don't share SharedPreferences instances

---

## Files to Delete After Migration

After all providers are migrated:
- `provider/settings/ProviderSettingsRepository.kt` (old 1200-line file)
- `ui/settings/WebSearchSettingsScreen.kt` (moved to provider)
- `ui/settings/AppSearchSettingsScreen.kt` (moved to provider)
- etc.

---

## Checklist for Each Provider

- [ ] Settings data class created
- [ ] `providerId` property matches Provider.id
- [ ] `toJson()` method implemented
- [ ] `fromJson()` companion method implemented
- [ ] Factory function `createXxxSettingsRepository()` created
- [ ] Provider constructor updated to accept new repository
- [ ] Provider query method updated to use `repository.value`
- [ ] Settings screen moved to provider package
- [ ] MainActivity updated to create repository and pass to provider
- [ ] Settings persist after app restart
- [ ] Settings appear in backup/export

---

## Questions?

Refer to `WebSearchSettings.kt` as the reference implementation.
