---
name: search-overview
description: Search app project overview - Android launcher-style search utility with Kotlin/Compose, provider-based architecture, unified interface for apps, files, contacts, web, settings, calculator, and Termux. Includes tech stack, package info, and directory structure.
---

# Search App - Overview

## Project Purpose

**Search** is an Android native launcher-style search utility that integrates as the device's default assistant. It provides a unified search interface for:

- Installed applications (with fuzzy matching)
- Local files (with FTS4 indexing)
- Device contacts
- Web searches (multiple engines)
- System settings
- Text utilities (base64, URL encode/decode)
- Calculator expressions
- Termux command execution

## Technology Stack

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

## Quick Stats

```
Package:        com.mrndstvndv.search
Min SDK:        24 (Android 7.0)
Architecture:   Provider-Based Repository Pattern + Compose MVVM
Entry Points:   MainActivity (search), SettingsActivity (config)
```

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                             │
│  ┌─────────────────────────────┐    ┌─────────────────────────────────────┐ │
│  │       MainActivity          │    │         SettingsActivity            │ │
│  │   (Search Overlay UI)       │    │    (Configuration Hub)              │ │
│  └─────────────────────────────┘    └─────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────────────────────────┤
│                              PROVIDER LAYER                                 │
│   AppList │ WebSearch │ FileSearch │ Contacts │ Calculator │ Settings │... │
│                    All implement: Provider interface                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                              REPOSITORY LAYER                               │
│   ProviderSettings │ ProviderRanking │ FileSearch │ Alias │ Contacts │ ... │
├─────────────────────────────────────────────────────────────────────────────┤
│                                DATA LAYER                                   │
│   SharedPreferences │ Room Database │ System APIs (PackageManager, etc.)   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Directory Structure

```
app/src/main/java/com/mrndstvndv/search/
│
├── MainActivity.kt         # [ENTRY] Search overlay UI
├── SettingsActivity.kt     # [ENTRY] Settings navigation hub
├── UserService.kt          # Shizuku elevated permissions service
│
├── alias/                  # Alias/shortcut system
│   ├── Alias.kt            # Alias data model
│   ├── AliasRepository.kt  # Alias persistence
│   └── AliasTarget.kt      # Sealed interface for targets
│
├── provider/               # [CORE] Search providers
│   ├── Provider.kt         # Base provider interface
│   ├── ProviderResult.kt   # Result model
│   ├── model/              # Shared models (Query, etc.)
│   ├── apps/               # App search provider
│   ├── websearch/          # Web search provider
│   ├── files/              # File search provider (with Room DB)
│   ├── contacts/           # Contacts provider
│   ├── calculator/         # Calculator provider
│   ├── settings/           # System settings provider
│   ├── textutils/          # Text utilities provider
│   └── termux/             # Termux command provider
│
├── settings/               # Settings utilities
│   ├── ProviderSettingsRepository.kt  # Central settings store
│   └── ProviderRankingRepository.kt   # Result ranking
│
├── ui/                     # UI layer
│   ├── theme/              # Design system (Theme, Color, Type)
│   ├── components/         # Reusable components
│   └── settings/           # Settings screens
│
└── util/                   # Utility classes
```

## Entry Points Summary

| Entry Point        | Intent Filter                | Purpose                    |
|--------------------|------------------------------|----------------------------|
| `MainActivity`     | `ACTION_ASSIST`              | Search overlay (assistant) |
| `SettingsActivity` | `MAIN` + `LAUNCHER`          | App settings               |
| `UserService`      | Shizuku bind                 | Elevated permissions       |

## Related Skills

- `search-provider` - Provider system details
- `search-dataflow` - Query flow and state management
- `search-repository` - Repository layer patterns
- `search-ui` - UI architecture and components
- `search-build` - Build configuration and permissions
- `search-guides` - Step-by-step implementation guides
