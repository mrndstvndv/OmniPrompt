# Backup/Restore Refactor — Summary

## Motivation

The backup/restore system required every provider's settings repository to be manually wired through as a parameter across three layers:

- `BackupRestoreSettingsScreen` (Composable)
- `BackupRestoreManager.createBackup()` / `restoreFromBackup()`
- Individual per-provider serialization/deserialization calls

This meant adding a new provider required touching ~8 files. The refactor centralizes repo resolution and delegates serialization to `SettingsRegistry`.

---

## What Changed

### 1. `SearchApplication` + `AppContainer` (new files)

**`app/.../SearchApplication.kt`**
- Application subclass declared in manifest (`android:name=".SearchApplication"`)

**`app/.../di/AppContainer.kt`**
- Single DI container holding all repository instances as `lazy` vals
- Exposes: `settingsRepository`, `aliasRepository`, all `*SettingsRepo`, `fileSearchRepository`, `contactsRepository`, `rankingRepository`, `appListRepository`, `developerSettingsManager`, etc.
- All initialized lazily via existing factory methods / singletons

### 2. `SettingsActivity.kt`

**Before:** Each repository created inline with `remember { ... }` inside `setContent`:
```kotlin
val aliasRepository = remember { AliasRepository(this, coroutineScope) }
val webSearchSettingsRepo = remember { createWebSearchSettingsRepository(this) }
val fileSearchRepository = remember { FileSearchRepository.getInstance(this) }
// ... 15+ similar lines
```

**After:** All resolved from container:
```kotlin
val container = (application as SearchApplication).container
val aliasRepository = container.aliasRepository
val webSearchSettingsRepo = container.webSearchSettingsRepo
// ...
```

- Removed ~15 unused imports (repos, factory functions, data classes)
- Removed `rememberCoroutineScope` import (no longer needed)

### 3. `SettingsRepository.kt`

Added `replaceFromJson(json: JSONObject): Boolean` — deserializes JSON and applies it via `replace()`. This lets `SettingsRegistry` handle restore generically.

### 4. `SettingsRegistry.kt`

**`importAll()`** — previously a stub that always returned `false`. Now:
- Iterates registered repositories
- Calls `repo.replaceFromJson()` for each matching JSON entry
- Returns per-provider success/failure map

**`getProviderJson()`** — backward compatibility with old backup files that used camelCase keys (`webSearch` → `web-search`, `appSearch` → `app-list`, etc.)

### 5. `BackupRestoreManager.kt`

**Before:** `createBackup()` and `restoreFromBackup()` each took **8 individual repo parameters** and manually serialized/deserialized each one with try/catch blocks (~100 lines of boilerplate).

**After:**
- `createBackup()` calls `SettingsRegistry.exportAll()` — dynamically exports whatever's registered
- `restoreFromBackup()` calls `SettingsRegistry.importAll()` — dynamically imports, with legacy key fallback
- Individual repo params removed from both methods
- Added `hasLegacyKey()` helper for warning messages
- Removed 6 unused imports

### 6. `BackupRestoreSettingsScreen.kt`

Removed all 8 individual `SettingsRepository<*>` parameters from the Composable function signature and its call sites. Only `settingsRepository` and `fileSearchSettingsRepo` remain (the latter still needed for the file roots permission warning UI).

---

## Net Effect

```
 6 files changed, 80 insertions(+), 187 deletions(-)
```

- ~100 lines of repetitive per-provider restore boilerplate eliminated
- Adding a new provider = just register it in `SettingsRegistry` (already done via `*SettingsRepository` constructors)
- No more wiring repos through the Compose layer
- Legacy backup files with camelCase keys still import correctly
