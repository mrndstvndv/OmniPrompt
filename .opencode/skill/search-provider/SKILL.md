---
name: search-provider
description: Search app provider system - interface definition (id, displayName, refreshSignal, initialize, canHandle, query, dispose), lifecycle diagram, 8 available providers (AppList, WebSearch, FileSearch, Contacts, Calculator, Settings, TextUtils, Termux), registration in MainActivity.
---

# Provider System

The **Provider System** is the core abstraction that powers all search functionality. Each search source implements the `Provider` interface.

## Provider Interface

```kotlin
// Location: app/src/main/java/com/mrndstvndv/search/provider/Provider.kt

interface Provider {
    val id: String                        // Unique identifier (e.g., "app-list")
    val displayName: String               // Human-readable name
    val refreshSignal: SharedFlow<Unit>   // Emits when provider needs refresh
    
    fun initialize()                      // Heavy setup (called once)
    fun canHandle(query: Query): Boolean  // Filter queries this provider handles
    suspend fun query(query: Query): List<ProviderResult>  // Execute search
    fun dispose()                         // Cleanup resources
}
```

## Provider Lifecycle

```
┌─────────────────────────────────────────────────────────────────────┐
│                        PROVIDER LIFECYCLE                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│   ┌──────────────┐                                                  │
│   │   Created    │  Provider instantiated in MainActivity          │
│   └──────┬───────┘                                                  │
│          │                                                          │
│          ▼                                                          │
│   ┌──────────────┐                                                  │
│   │ initialize() │  Heavy setup: load data, connect to repos       │
│   └──────┬───────┘                                                  │
│          │                                                          │
│          ▼                                                          │
│   ┌──────────────────────────────────────────────────────────┐     │
│   │                    QUERY LOOP                             │     │
│   │                                                           │     │
│   │   canHandle(query)? ──yes──► query(q) ──► Return results  │     │
│   │         ▲                                      │          │     │
│   │         └──────────── new query ───────────────┘          │     │
│   │                                                           │     │
│   └──────────────────────────────────────────────────────────┘     │
│          │                                                          │
│          │ Activity destroyed                                       │
│          ▼                                                          │
│   ┌──────────────┐                                                  │
│   │   dispose()  │  Cleanup: cancel jobs, release resources        │
│   └──────────────┘                                                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Available Providers

| Provider               | ID                | Description                          | Key Features                        |
|------------------------|-------------------|--------------------------------------|-------------------------------------|
| `AppListProvider`      | `app-list`        | Installed applications               | Fuzzy matching, AI queries          |
| `WebSearchProvider`    | `web-search`      | Web search engines                   | Multiple engines, quicklinks        |
| `FileSearchProvider`   | `file-search`     | Local file search                    | FTS4 index, thumbnails              |
| `ContactsProvider`     | `contacts`        | Device contacts                      | Phone/SIM numbers, actions          |
| `CalculatorProvider`   | `calculator`      | Math expressions                     | Expression parsing                  |
| `SettingsProvider`     | `system-settings` | System settings shortcuts            | Dev options toggle (Shizuku)        |
| `TextUtilitiesProvider`| `text-utilities`  | Text transformations                 | Base64, URL encode/decode           |
| `TermuxProvider`       | `termux`          | Terminal commands                    | Custom Termux execution             |

## Provider File Locations

| Provider               | Location                                              |
|------------------------|-------------------------------------------------------|
| `AppListProvider`      | `provider/apps/AppListProvider.kt`                    |
| `WebSearchProvider`    | `provider/websearch/WebSearchProvider.kt`             |
| `FileSearchProvider`   | `provider/files/FileSearchProvider.kt`                |
| `ContactsProvider`     | `provider/contacts/ContactsProvider.kt`               |
| `CalculatorProvider`   | `provider/calculator/CalculatorProvider.kt`           |
| `SettingsProvider`     | `provider/settings/SettingsProvider.kt`               |
| `TextUtilitiesProvider`| `provider/textutils/TextUtilitiesProvider.kt`         |
| `TermuxProvider`       | `provider/termux/TermuxProvider.kt`                   |

## Provider Registration

Providers are instantiated and registered in `MainActivity.kt` (around line 150):

```kotlin
val providers = remember {
    listOf(
        AppListProvider(context, settingsRepository, ...),
        WebSearchProvider(context, settingsRepository, ...),
        FileSearchProvider(context, fileSearchRepository, ...),
        ContactsProvider(context, ...),
        CalculatorProvider(),
        SettingsProvider(context, ...),
        TextUtilitiesProvider(settingsRepository),
        TermuxProvider(context, settingsRepository)
    )
}
```

## Adding a New Provider

See the `search-guides` skill for step-by-step instructions on implementing and registering a new provider.

## Related Skills

- `search-overview` - Project overview and architecture
- `search-dataflow` - How queries flow through providers
- `search-guides` - Step-by-step guide for adding providers
