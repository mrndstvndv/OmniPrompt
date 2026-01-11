---
name: search-dataflow
description: Search app data flow - query flow diagram (user input → debounce → alias check → provider dispatch → aggregate → rank → UI), state management patterns (mutableStateOf, mutableStateListOf, StateFlow, SharedFlow), settings JSON persistence in SharedPreferences.
---

# Data Flow

## Query Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SEARCH QUERY FLOW                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌───────────────┐                                                         │
│   │  User Types   │                                                         │
│   │  in SearchBar │                                                         │
│   └───────┬───────┘                                                         │
│           │                                                                 │
│           ▼                                                                 │
│   ┌───────────────────────┐                                                 │
│   │  Query Object Created │  Query(text, timestamp)                         │
│   └───────────┬───────────┘                                                 │
│               │                                                             │
│               ▼                                                             │
│   ┌───────────────────────┐                                                 │
│   │   Debounce (50ms)     │  Prevents excessive queries while typing        │
│   └───────────┬───────────┘                                                 │
│               │                                                             │
│       ┌───────┴───────────────────────┐                                     │
│       │                               │                                     │
│       ▼                               ▼                                     │
│ ┌─────────────────┐         ┌─────────────────────────┐                     │
│ │  Alias Check    │         │  Provider Dispatch      │                     │
│ │                 │         │                         │                     │
│ │ Does query      │         │ For each enabled        │                     │
│ │ match an alias? │         │ provider where          │                     │
│ │                 │         │ canHandle(query) = true │                     │
│ └────────┬────────┘         └────────────┬────────────┘                     │
│          │                               │                                  │
│          │ yes                           │                                  │
│          ▼                               ▼                                  │
│ ┌─────────────────┐         ┌─────────────────────────┐                     │
│ │ buildAliasResult│         │ supervisorScope {       │                     │
│ │ ()              │         │   async { p.query(q) }  │  Parallel execution │
│ │                 │         │ }                       │                     │
│ └────────┬────────┘         └────────────┬────────────┘                     │
│          │                               │                                  │
│          └───────────┬───────────────────┘                                  │
│                      │                                                      │
│                      ▼                                                      │
│          ┌───────────────────────┐                                          │
│          │  Aggregate Results    │  Combine all provider results            │
│          └───────────┬───────────┘                                          │
│                      │                                                      │
│                      ▼                                                      │
│          ┌───────────────────────┐                                          │
│          │   Apply Ranking       │                                          │
│          │                       │                                          │
│          │ 1. Frequency-based    │  (if enabled)                            │
│          │ 2. Provider order     │  (fallback)                              │
│          └───────────┬───────────┘                                          │
│                      │                                                      │
│                      ▼                                                      │
│          ┌───────────────────────┐                                          │
│          │ Update StateList      │  providerResults.clear() + addAll()      │
│          └───────────┬───────────┘                                          │
│                      │                                                      │
│                      ▼                                                      │
│          ┌───────────────────────┐                                          │
│          │  ItemsList Recomposes │  UI updates with new results             │
│          └───────────────────────┘                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## State Management

| Type                  | Usage                              | Example                                    |
|-----------------------|------------------------------------|--------------------------------------------|
| `mutableStateOf`      | Simple reactive state              | `var query by mutableStateOf("")`          |
| `mutableStateListOf`  | Observable lists                   | `val results = mutableStateListOf<Result>()`|
| `StateFlow`           | Repository → UI reactive binding   | `val settings: StateFlow<Settings>`        |
| `SharedFlow`          | One-shot events (refresh signals)  | `val refreshSignal: SharedFlow<Unit>`      |

### State Management Guidelines

1. **Settings → StateFlow in Repository**:
   ```kotlin
   val setting: StateFlow<T> = repository.setting
   val value by setting.collectAsState()
   ```

2. **Local UI State → mutableStateOf**:
   ```kotlin
   var isExpanded by remember { mutableStateOf(false) }
   ```

3. **Observable Lists → mutableStateListOf**:
   ```kotlin
   val results = remember { mutableStateListOf<ProviderResult>() }
   ```

4. **One-shot Events → SharedFlow**:
   ```kotlin
   val refreshSignal = MutableSharedFlow<Unit>()
   ```

## Settings Persistence

Settings are stored as JSON in SharedPreferences:

```kotlin
// Example: WebSearchSettings storage

// Data class
data class WebSearchSettings(
    val defaultSiteId: String,
    val sites: List<WebSearchSite>,
    val quicklinks: List<Quicklink>
)

// Persistence in ProviderSettingsRepository
private val prefs = context.getSharedPreferences("provider_settings", MODE_PRIVATE)

fun saveWebSearchSettings(settings: WebSearchSettings) {
    prefs.edit().putString(KEY_WEB_SEARCH, Json.encodeToString(settings)).apply()
    _webSearchSettings.value = settings
}
```

## Key Files

| Purpose                    | Location                                |
|----------------------------|-----------------------------------------|
| Query model                | `provider/model/Query.kt`               |
| Provider interface         | `provider/Provider.kt`                  |
| Result model               | `provider/ProviderResult.kt`            |
| Settings persistence       | `settings/ProviderSettingsRepository.kt`|
| Ranking logic              | `settings/ProviderRankingRepository.kt` |

## Related Skills

- `search-provider` - Provider interface and lifecycle
- `search-repository` - Repository layer patterns
