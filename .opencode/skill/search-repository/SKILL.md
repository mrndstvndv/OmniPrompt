---
name: search-repository
description: Search app repository layer - 7 repositories (ProviderSettings, ProviderRanking, Alias, FileSearch, Contacts, RecentApps, PinnedApps), singleton pattern with double-checked locking, StateFlow reactive settings pattern for UI binding.
---

# Repository Layer

## Repository Overview

| Repository                  | Purpose                          | Storage Type          |
|-----------------------------|----------------------------------|-----------------------|
| `ProviderSettingsRepository`| All provider/app settings        | SharedPreferences (JSON) |
| `ProviderRankingRepository` | Result ordering & frequency      | SharedPreferences     |
| `AliasRepository`           | User-defined shortcuts           | SharedPreferences (JSON) |
| `FileSearchRepository`      | File indexing & FTS4 search      | Room Database         |
| `ContactsRepository`        | Contact data access              | System ContentProvider |
| `RecentAppsRepository`      | Recent app tracking              | UsageStatsManager     |
| `PinnedAppsRepository`      | Pinned apps list                 | SharedPreferences     |

## Repository Locations

| Repository                  | Location                                    |
|-----------------------------|---------------------------------------------|
| `ProviderSettingsRepository`| `settings/ProviderSettingsRepository.kt`    |
| `ProviderRankingRepository` | `settings/ProviderRankingRepository.kt`     |
| `AliasRepository`           | `alias/AliasRepository.kt`                  |
| `FileSearchRepository`      | `provider/files/FileSearchRepository.kt`    |
| `ContactsRepository`        | `provider/contacts/ContactsRepository.kt`   |
| `RecentAppsRepository`      | `provider/apps/RecentAppsRepository.kt`     |

## Singleton Pattern

Heavy repositories use double-checked locking singletons:

```kotlin
// Location: FileSearchRepository.kt

class FileSearchRepository private constructor(context: Context) {
    
    companion object {
        @Volatile 
        private var INSTANCE: FileSearchRepository? = null
        
        fun getInstance(context: Context): FileSearchRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileSearchRepository(context.applicationContext)
                    .also { INSTANCE = it }
            }
    }
    
    // Repository implementation...
}

// Usage in Activity
val fileSearchRepository = remember { 
    FileSearchRepository.getInstance(applicationContext) 
}
```

## StateFlow Pattern for Reactive Settings

```kotlin
// Location: ProviderSettingsRepository.kt

class ProviderSettingsRepository(context: Context, scope: CoroutineScope) {
    
    // Private mutable state
    private val _webSearchSettings = MutableStateFlow(loadWebSearchSettings())
    
    // Public immutable exposure
    val webSearchSettings: StateFlow<WebSearchSettings> = _webSearchSettings.asStateFlow()
    
    // Update triggers StateFlow emission
    fun updateWebSearchSettings(settings: WebSearchSettings) {
        saveToPrefs(settings)
        _webSearchSettings.value = settings  // UI recomposes automatically
    }
}

// Usage in Composable
@Composable
fun WebSearchSettingsScreen(repository: ProviderSettingsRepository) {
    val settings by repository.webSearchSettings.collectAsState()
    
    // UI automatically updates when settings change
}
```

## Adding a New Repository

1. **Create repository class** with singleton pattern (if heavy):
   ```kotlin
   class NewRepository private constructor(context: Context) {
       companion object {
           @Volatile private var INSTANCE: NewRepository? = null
           fun getInstance(context: Context) = INSTANCE ?: synchronized(this) {
               INSTANCE ?: NewRepository(context.applicationContext).also { INSTANCE = it }
           }
       }
   }
   ```

2. **Add StateFlow for reactive data**:
   ```kotlin
   private val _data = MutableStateFlow(loadData())
   val data: StateFlow<DataType> = _data.asStateFlow()
   ```

3. **Use in Activity/Composable**:
   ```kotlin
   val repository = remember { NewRepository.getInstance(context) }
   val data by repository.data.collectAsState()
   ```

## Related Skills

- `search-dataflow` - How repositories fit into data flow
- `search-guides` - Adding settings to repositories
