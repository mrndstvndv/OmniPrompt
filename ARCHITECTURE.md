# Search App - Architecture Documentation

> **Purpose**: This document provides AI agents and developers with a comprehensive understanding of the project's architecture, patterns, and conventions to enable efficient contributions without starting from scratch.

---

## Table of Contents

1. [Overview](#1-overview)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Directory Structure](#3-directory-structure)
4. [Provider System](#4-provider-system)
5. [Data Flow](#5-data-flow)
6. [Repository Layer](#6-repository-layer)
7. [UI Architecture](#7-ui-architecture)
8. [Build Configuration](#8-build-configuration)
9. [Quick Reference Guides](#9-quick-reference-guides)
10. [Conventions & Patterns](#10-conventions--patterns)

---

## 1. Overview

### Project Purpose

**Search** is an Android native launcher-style search utility that integrates as the device's default assistant. It provides a unified search interface for:

- Installed applications (with fuzzy matching)
- Local files (with FTS4 indexing)
- Device contacts
- Web searches (multiple engines)
- System settings
- Text utilities (base64, URL encode/decode)
- Calculator expressions
- Termux command execution

### Technology Stack

| Technology          | Version/Details                              |
|---------------------|----------------------------------------------|
| **Language**        | Kotlin 2.0.21                                |
| **UI Framework**    | Jetpack Compose + Material 3 (1.5.0-alpha08) |
| **Android SDK**     | Min: 24, Target: 36, Compile: 36             |
| **Async**           | Kotlin Coroutines 1.7.3                      |
| **Database**        | Room 2.6.1 (FTS4 for file search)            |
| **Background Work** | WorkManager 2.9.1                            |
| **Elevated Perms**  | Shizuku 13.1.5                               |
| **Build System**    | Gradle Kotlin DSL + Version Catalog          |

### Quick Stats

```
Package:        com.mrndstvndv.search
Min SDK:        24 (Android 7.0)
Architecture:   Provider-Based Repository Pattern + Compose MVVM
Entry Points:   MainActivity (search), SettingsActivity (config)
```

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                             │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────────┐ │
│  │       MainActivity          │    │         SettingsActivity            │ │
│  │   (Search Overlay UI)       │    │    (Configuration Hub)              │ │
│  │                             │    │                                     │ │
│  │  ┌───────────────────────┐  │    │  ┌─────────────────────────────┐   │ │
│  │  │ SearchField           │  │    │  │ GeneralSettingsScreen       │   │ │
│  │  │ RecentAppsList        │  │    │  │ ProvidersSettingsScreen     │   │ │
│  │  │ ItemsList (results)   │  │    │  │ WebSearchSettingsScreen     │   │ │
│  │  │ ContactActionSheet    │  │    │  │ FileSearchSettingsScreen    │   │ │
│  │  └───────────────────────┘  │    │  │ ...other settings screens   │   │ │
│  └─────────────────────────────┘    │  └─────────────────────────────┘   │ │
│                                     └─────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                              PROVIDER LAYER                                 │
│                                                                             │
│   ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐  │
│   │ AppList     │ │ WebSearch   │ │ FileSearch  │ │ Contacts            │  │
│   │ Provider    │ │ Provider    │ │ Provider    │ │ Provider            │  │
│   └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └──────────┬──────────┘  │
│          │               │               │                   │             │
│   ┌──────┴──────┐ ┌──────┴──────┐ ┌──────┴──────┐ ┌─────────┴───────────┐  │
│   │ Calculator  │ │ Settings    │ │ TextUtils   │ │ Termux              │  │
│   │ Provider    │ │ Provider    │ │ Provider    │ │ Provider            │  │
│   └─────────────┘ └─────────────┘ └─────────────┘ └─────────────────────┘  │
│                                                                             │
│                    All implement: Provider interface                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                              REPOSITORY LAYER                               │
│                                                                             │
│   ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐   │
│   │ProviderSettings    │  │ProviderRanking     │  │ FileSearch         │   │
│   │Repository          │  │Repository          │  │ Repository         │   │
│   │                    │  │                    │  │                    │   │
│   │ - All settings     │  │ - Result ordering  │  │ - File indexing    │   │
│   │ - JSON persistence │  │ - Usage frequency  │  │ - FTS4 queries     │   │
│   └────────────────────┘  └────────────────────┘  └────────────────────┘   │
│                                                                             │
│   ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐   │
│   │ Alias              │  │ Contacts           │  │ RecentApps         │   │
│   │ Repository         │  │ Repository         │  │ Repository         │   │
│   └────────────────────┘  └────────────────────┘  └────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                DATA LAYER                                   │
│                                                                             │
│   ┌────────────────────┐  ┌────────────────────┐  ┌────────────────────┐   │
│   │  SharedPreferences │  │   Room Database    │  │   System APIs      │   │
│   │                    │  │                    │  │                    │   │
│   │  - User settings   │  │  - FileSearchDb    │  │  - PackageManager  │   │
│   │  - Provider config │  │  - FTS4 index      │  │  - ContactsProvider│   │
│   │  - Aliases         │  │  - File metadata   │  │  - UsageStats      │   │
│   └────────────────────┘  └────────────────────┘  └────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Directory Structure

```
/Volumes/realme/Dev/Search/
│
├── app/
│   ├── build.gradle.kts                    # App-level build config
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml         # Permissions, activities, intents
│       │   │
│       │   ├── aidl/                       # IPC interface definitions
│       │   │   └── com/mrndstvndv/search/
│       │   │       └── IUserService.aidl   # Shizuku service interface
│       │   │
│       │   ├── java/com/mrndstvndv/search/
│       │   │   │
│       │   │   ├── MainActivity.kt         # [ENTRY] Search overlay UI
│       │   │   ├── SettingsActivity.kt     # [ENTRY] Settings navigation hub
│       │   │   ├── UserService.kt          # Shizuku elevated permissions service
│       │   │   │
│       │   │   ├── alias/                  # Alias/shortcut system
│       │   │   │   ├── Alias.kt            # Alias data model
│       │   │   │   ├── AliasRepository.kt  # Alias persistence
│       │   │   │   └── AliasTarget.kt      # Sealed interface for targets
│       │   │   │
│       │   │   ├── provider/               # [CORE] Search providers
│       │   │   │   ├── Provider.kt         # Base provider interface
│       │   │   │   ├── ProviderResult.kt   # Result model
│       │   │   │   │
│       │   │   │   ├── apps/               # App search provider
│       │   │   │   │   ├── AppListProvider.kt
│       │   │   │   │   ├── FuzzyMatcher.kt
│       │   │   │   │   └── RecentAppsRepository.kt
│       │   │   │   │
│       │   │   │   ├── websearch/          # Web search provider
│       │   │   │   │   ├── WebSearchProvider.kt
│       │   │   │   │   └── WebSearchSite.kt
│       │   │   │   │
│       │   │   │   ├── files/              # File search provider
│       │   │   │   │   ├── FileSearchProvider.kt
│       │   │   │   │   ├── FileSearchRepository.kt
│       │   │   │   │   └── index/          # Room database & entities
│       │   │   │   │       ├── FileSearchDatabase.kt
│       │   │   │   │       ├── FileEntity.kt
│       │   │   │   │       └── FileDao.kt
│       │   │   │   │
│       │   │   │   ├── contacts/           # Contacts provider
│       │   │   │   │   ├── ContactsProvider.kt
│       │   │   │   │   └── ContactsRepository.kt
│       │   │   │   │
│       │   │   │   ├── calculator/         # Calculator provider
│       │   │   │   │   └── CalculatorProvider.kt
│       │   │   │   │
│       │   │   │   ├── settings/           # System settings provider
│       │   │   │   │   └── SettingsProvider.kt
│       │   │   │   │
│       │   │   │   ├── textutils/          # Text utilities provider
│       │   │   │   │   └── TextUtilitiesProvider.kt
│       │   │   │   │
│       │   │   │   ├── termux/             # Termux command provider
│       │   │   │   │   └── TermuxProvider.kt
│       │   │   │   │
│       │   │   │   └── model/              # Shared models
│       │   │   │       └── Query.kt
│       │   │   │
│       │   │   ├── settings/               # Settings utilities
│       │   │   │   ├── ProviderSettingsRepository.kt  # Central settings store
│       │   │   │   └── ProviderRankingRepository.kt   # Result ranking
│       │   │   │
│       │   │   ├── ui/                     # UI layer
│       │   │   │   ├── theme/              # Design system
│       │   │   │   │   ├── Theme.kt        # SearchTheme, dynamic colors
│       │   │   │   │   ├── Color.kt        # Color definitions
│       │   │   │   │   ├── Type.kt         # Typography
│       │   │   │   │   └── MotionLocals.kt # Animation preferences
│       │   │   │   │
│       │   │   │   ├── components/         # Reusable components
│       │   │   │   │   ├── ItemsList.kt    # Search results list
│       │   │   │   │   ├── SearchField.kt  # Search input
│       │   │   │   │   ├── RecentAppsList.kt
│       │   │   │   │   ├── ContactActionSheet.kt
│       │   │   │   │   ├── BottomSheet.kt
│       │   │   │   │   └── settings/       # Settings UI components
│       │   │   │   │       ├── SettingsGroup.kt
│       │   │   │   │       ├── SettingsSwitch.kt
│       │   │   │   │       ├── SettingsSliderRow.kt
│       │   │   │   │       └── SettingsNavigationRow.kt
│       │   │   │   │
│       │   │   │   └── settings/           # Settings screens
│       │   │   │       ├── GeneralSettingsScreen.kt
│       │   │   │       ├── ProvidersSettingsScreen.kt
│       │   │   │       ├── WebSearchSettingsScreen.kt
│       │   │   │       ├── FileSearchSettingsScreen.kt
│       │   │   │       ├── ContactsSettingsScreen.kt
│       │   │   │       ├── TermuxSettingsScreen.kt
│       │   │   │       └── ...
│       │   │   │
│       │   │   └── util/                   # Utility classes
│       │   │       └── ...
│       │   │
│       │   └── res/                        # Android resources
│       │       ├── values/
│       │       ├── drawable/
│       │       └── ...
│       │
│       ├── test/                           # Unit tests
│       │   └── java/com/mrndstvndv/search/
│       │
│       └── androidTest/                    # Instrumentation tests
│           └── java/com/mrndstvndv/search/
│
├── build.gradle.kts                        # Root build config
├── settings.gradle.kts                     # Module settings
├── gradle.properties                       # Version info (0.0.1)
├── gradle/
│   ├── libs.versions.toml                  # Dependency version catalog
│   └── wrapper/
│
├── AGENTS.md                               # Agent guidelines
└── ARCHITECTURE.md                         # This file
```

---

## 4. Provider System

The **Provider System** is the core abstraction that powers all search functionality. Each search source implements the `Provider` interface.

### Provider Interface

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

### Provider Lifecycle

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
│   │   ┌─────────────────┐                                     │     │
│   │   │  canHandle(q)?  │◄──────────────────────────┐        │     │
│   │   └────────┬────────┘                           │        │     │
│   │            │ yes                                │        │     │
│   │            ▼                                    │        │     │
│   │   ┌─────────────────┐                           │        │     │
│   │   │    query(q)     │  Returns List<Result>     │        │     │
│   │   └────────┬────────┘                           │        │     │
│   │            │                                    │        │     │
│   │            ▼                                    │        │     │
│   │   ┌─────────────────┐      new query           │        │     │
│   │   │  Return results │──────────────────────────┘        │     │
│   │   └─────────────────┘                                    │     │
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

### Available Providers

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

### Provider Registration

Providers are instantiated and registered in `MainActivity.kt`:

```kotlin
// Location: MainActivity.kt (around line 150)

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

---

## 5. Data Flow

### Query Flow Diagram

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

### State Management

| Type                  | Usage                              | Example                                    |
|-----------------------|------------------------------------|--------------------------------------------|
| `mutableStateOf`      | Simple reactive state              | `var query by mutableStateOf("")`          |
| `mutableStateListOf`  | Observable lists                   | `val results = mutableStateListOf<Result>()`|
| `StateFlow`           | Repository → UI reactive binding   | `val settings: StateFlow<Settings>`        |
| `SharedFlow`          | One-shot events (refresh signals)  | `val refreshSignal: SharedFlow<Unit>`      |

### Settings Persistence

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

---

## 6. Repository Layer

### Repository Overview

| Repository                  | Purpose                          | Storage Type          |
|-----------------------------|----------------------------------|-----------------------|
| `ProviderSettingsRepository`| All provider/app settings        | SharedPreferences (JSON) |
| `ProviderRankingRepository` | Result ordering & frequency      | SharedPreferences     |
| `AliasRepository`           | User-defined shortcuts           | SharedPreferences (JSON) |
| `FileSearchRepository`      | File indexing & FTS4 search      | Room Database         |
| `ContactsRepository`        | Contact data access              | System ContentProvider |
| `RecentAppsRepository`      | Recent app tracking              | UsageStatsManager     |
| `PinnedAppsRepository`      | Pinned apps list                 | SharedPreferences     |

### Singleton Pattern

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

### StateFlow Pattern for Reactive Settings

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

---

## 7. UI Architecture

### Component Hierarchy

```
┌─────────────────────────────────────────────────────────────────┐
│                        SearchTheme                              │
│   (Dynamic colors, typography, shapes)                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   MainActivity                    SettingsActivity              │
│   ┌─────────────────────┐        ┌─────────────────────────┐   │
│   │                     │        │                         │   │
│   │  ┌───────────────┐  │        │  AnimatedContent        │   │
│   │  │ SearchField   │  │        │  (screen transitions)   │   │
│   │  └───────────────┘  │        │                         │   │
│   │                     │        │  ┌───────────────────┐  │   │
│   │  ┌───────────────┐  │        │  │ GeneralSettings   │  │   │
│   │  │RecentAppsList │  │        │  │ Screen            │  │   │
│   │  └───────────────┘  │        │  └───────────────────┘  │   │
│   │                     │        │           or            │   │
│   │  ┌───────────────┐  │        │  ┌───────────────────┐  │   │
│   │  │  ItemsList    │  │        │  │ WebSearchSettings │  │   │
│   │  │  (results)    │  │        │  │ Screen            │  │   │
│   │  └───────────────┘  │        │  └───────────────────┘  │   │
│   │                     │        │           or            │   │
│   │  ┌───────────────┐  │        │  ┌───────────────────┐  │   │
│   │  │ContactAction  │  │        │  │ ...other screens  │  │   │
│   │  │Sheet          │  │        │  └───────────────────┘  │   │
│   │  └───────────────┘  │        │                         │   │
│   │                     │        │                         │   │
│   └─────────────────────┘        └─────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Navigation Pattern

Settings uses enum-based screen state navigation (no Navigation Component):

```kotlin
// Location: SettingsActivity.kt

private enum class Screen {
    Home, Providers, Appearance, Behavior, Aliases, Ranking,
    WebSearch, FileSearch, TextUtilities, AppSearch, ProviderList,
    SystemSettings, ContactsSettings, BackupRestore, TermuxSettings
}

@Composable
fun SettingsNavigation() {
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    
    AnimatedContent(targetState = currentScreen) { screen ->
        when (screen) {
            Screen.Home -> GeneralSettingsScreen(
                onNavigate = { currentScreen = it }
            )
            Screen.Providers -> ProvidersSettingsScreen(...)
            Screen.WebSearch -> WebSearchSettingsScreen(...)
            // ... other screens
        }
    }
}
```

### Theme System

```kotlin
// Location: ui/theme/Theme.kt

@Composable
fun SearchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### Reusable Settings Components

Located in `ui/components/settings/`:

| Component               | Purpose                                    |
|-------------------------|--------------------------------------------|
| `SettingsGroup`         | Groups related settings with a header      |
| `SettingsSwitch`        | Toggle switch with label and description   |
| `SettingsSliderRow`     | Slider for numeric values                  |
| `SettingsNavigationRow` | Clickable row that navigates to sub-screen |
| `SettingsTextField`     | Text input field for settings              |

---

## 8. Build Configuration

### Build Types

| Type      | Suffix    | Minification | App Name       |
|-----------|-----------|--------------|----------------|
| `debug`   | `.debug`  | No           | "Search Debug" |
| `release` | None      | Yes (R8)     | "Search"       |

### Key Build Features

```kotlin
// Location: app/build.gradle.kts

android {
    buildFeatures {
        compose = true      // Jetpack Compose
        buildConfig = true  // BuildConfig generation
        aidl = true         // AIDL for Shizuku IPC
    }
}
```

### Important Permissions

| Permission                        | Purpose                          |
|-----------------------------------|----------------------------------|
| `INTERNET`                        | Favicon loading, web features    |
| `READ_CONTACTS`                   | Contact search                   |
| `READ_PHONE_STATE`                | SIM number display               |
| `READ_PHONE_NUMBERS`              | Phone number access              |
| `PACKAGE_USAGE_STATS`             | Recent apps tracking             |
| `MANAGE_EXTERNAL_STORAGE`         | File search (all files)          |
| `WRITE_SECURE_SETTINGS`           | Dev options toggle (Shizuku)     |
| `com.termux.permission.RUN_COMMAND`| Termux integration              |

### Gradle Commands

```bash
# Compile Kotlin (required after any .kt file change)
./gradlew :app:compileDebugKotlin

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK
./gradlew :app:assembleRelease

# Run tests
./gradlew :app:testDebugUnitTest
```

---

## 9. Quick Reference Guides

### Adding a New Provider

1. **Create provider class**:
   ```
   app/src/main/java/com/mrndstvndv/search/provider/{type}/{Name}Provider.kt
   ```

2. **Implement Provider interface**:
   ```kotlin
   class NewProvider(
       private val context: Context,
       private val settingsRepository: ProviderSettingsRepository
   ) : Provider {
       
       override val id = "new-provider"
       override val displayName = "New Provider"
       override val refreshSignal = MutableSharedFlow<Unit>()
       
       override fun initialize() { /* Heavy setup */ }
       
       override fun canHandle(query: Query): Boolean {
           return query.text.isNotBlank()
       }
       
       override suspend fun query(query: Query): List<ProviderResult> {
           // Return search results
       }
       
       override fun dispose() { /* Cleanup */ }
   }
   ```

3. **Register in MainActivity** (~line 150):
   ```kotlin
   val providers = remember {
       listOf(
           // ... existing providers
           NewProvider(context, settingsRepository)
       )
   }
   ```

4. **Add toggle in ProvidersSettingsScreen**:
   ```kotlin
   SettingsSwitch(
       title = "New Provider",
       checked = isEnabled,
       onCheckedChange = { /* save state */ }
   )
   ```

5. **Add to DEFAULT_PROVIDER_ORDER** in `ProviderRankingRepository`:
   ```kotlin
   private val DEFAULT_PROVIDER_ORDER = listOf(
       // ... existing IDs
       "new-provider"
   )
   ```

### Adding a New Setting

1. **Add key constant** in `ProviderSettingsRepository`:
   ```kotlin
   private const val KEY_NEW_SETTING = "new_setting"
   ```

2. **Add StateFlow**:
   ```kotlin
   private val _newSetting = MutableStateFlow(loadNewSetting())
   val newSetting: StateFlow<Boolean> = _newSetting.asStateFlow()
   ```

3. **Add load/save methods**:
   ```kotlin
   private fun loadNewSetting(): Boolean {
       return prefs.getBoolean(KEY_NEW_SETTING, true)
   }
   
   fun setNewSetting(value: Boolean) {
       prefs.edit().putBoolean(KEY_NEW_SETTING, value).apply()
       _newSetting.value = value
   }
   ```

4. **Add UI** in appropriate settings screen:
   ```kotlin
   val newSetting by settingsRepository.newSetting.collectAsState()
   
   SettingsSwitch(
       title = "New Setting",
       description = "Description of what this does",
       checked = newSetting,
       onCheckedChange = { settingsRepository.setNewSetting(it) }
   )
   ```

### Adding a New Settings Screen

1. **Create screen file**:
   ```
   app/src/main/java/com/mrndstvndv/search/ui/settings/NewSettingsScreen.kt
   ```

2. **Implement Composable**:
   ```kotlin
   @Composable
   fun NewSettingsScreen(
       settingsRepository: ProviderSettingsRepository,
       onNavigateBack: () -> Unit
   ) {
       Column {
           // Back button header
           TopAppBar(
               title = { Text("New Settings") },
               navigationIcon = {
                   IconButton(onClick = onNavigateBack) {
                       Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                   }
               }
           )
           
           // Settings content
           SettingsGroup(title = "Section") {
               // Settings components...
           }
       }
   }
   ```

3. **Add to Screen enum** in `SettingsActivity.kt`:
   ```kotlin
   private enum class Screen {
       // ... existing screens
       NewScreen
   }
   ```

4. **Add navigation case**:
   ```kotlin
   Screen.NewScreen -> NewSettingsScreen(
       settingsRepository = settingsRepository,
       onNavigateBack = { currentScreen = Screen.Home }
   )
   ```

5. **Add navigation row** in parent screen:
   ```kotlin
   SettingsNavigationRow(
       title = "New Settings",
       onClick = { onNavigate(Screen.NewScreen) }
   )
   ```

### Database Migrations (Room)

1. **Update entity** in `provider/files/index/`:
   ```kotlin
   @Entity(tableName = "files")
   data class FileEntity(
       @PrimaryKey val path: String,
       val name: String,
       val newColumn: String = ""  // Add new field
   )
   ```

2. **Add migration** in `FileSearchDatabase`:
   ```kotlin
   val MIGRATION_1_2 = object : Migration(1, 2) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL(
               "ALTER TABLE files ADD COLUMN newColumn TEXT NOT NULL DEFAULT ''"
           )
       }
   }
   ```

3. **Increment version and add migration**:
   ```kotlin
   @Database(entities = [FileEntity::class], version = 2)
   abstract class FileSearchDatabase : RoomDatabase() {
       companion object {
           fun getInstance(context: Context) = Room.databaseBuilder(...)
               .addMigrations(MIGRATION_1_2)
               .build()
       }
   }
   ```

### Common File Locations

| Task                      | Location                                          |
|---------------------------|---------------------------------------------------|
| Add new provider          | `provider/{type}/{Name}Provider.kt`               |
| Add provider settings     | `settings/ProviderSettingsRepository.kt`          |
| Add settings UI           | `ui/settings/{Name}SettingsScreen.kt`             |
| Add reusable component    | `ui/components/{ComponentName}.kt`                |
| Modify theme              | `ui/theme/Theme.kt`, `Color.kt`, `Type.kt`        |
| Add permission            | `AndroidManifest.xml`                             |
| Add dependency            | `gradle/libs.versions.toml` + `app/build.gradle.kts` |
| Modify file search index  | `provider/files/index/`                           |

---

## 10. Conventions & Patterns

### Naming Conventions

| Type            | Convention               | Example                    |
|-----------------|--------------------------|----------------------------|
| Providers       | `*Provider.kt`           | `AppListProvider.kt`       |
| Repositories    | `*Repository.kt`         | `AliasRepository.kt`       |
| Settings Data   | `*Settings`              | `WebSearchSettings`        |
| Composables     | PascalCase functions     | `SearchField()`            |
| Screen files    | `*Screen.kt`             | `GeneralSettingsScreen.kt` |
| State variables | camelCase with prefix    | `isLoading`, `hasError`    |

### Package Organization

```
provider/
├── Provider.kt           # Base interface
├── ProviderResult.kt     # Result model
├── model/                # Shared models (Query, etc.)
├── {type}/               # Provider implementation
│   ├── {Type}Provider.kt
│   └── {Type}Repository.kt (if needed)

ui/
├── theme/                # Design system
├── components/           # Reusable components
│   └── settings/         # Settings-specific components
└── settings/             # Settings screens

settings/                 # Non-UI settings utilities
├── ProviderSettingsRepository.kt
└── ProviderRankingRepository.kt
```

### Design Patterns Used

| Pattern                | Usage                                  | Location                     |
|------------------------|----------------------------------------|------------------------------|
| **Provider Pattern**   | Unified search source abstraction      | `provider/Provider.kt`       |
| **Repository Pattern** | Data access abstraction                | `*Repository.kt` files       |
| **Singleton Pattern**  | Heavy shared resources                 | `FileSearchRepository`       |
| **Observer Pattern**   | Reactive settings via StateFlow        | All repositories             |
| **Sealed Classes**     | Type-safe result/target hierarchies    | `AliasTarget`, `ProviderResult` |
| **Fuzzy Matching**     | Raycast-style character matching       | `FuzzyMatcher.kt`            |

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

---

## Appendix: Entry Points Summary

| Entry Point        | Intent Filter                | Purpose                    |
|--------------------|------------------------------|----------------------------|
| `MainActivity`     | `ACTION_ASSIST`              | Search overlay (assistant) |
| `SettingsActivity` | `MAIN` + `LAUNCHER`          | App settings               |
| `UserService`      | Shizuku bind                 | Elevated permissions       |

---

*Last updated: January 2026*
