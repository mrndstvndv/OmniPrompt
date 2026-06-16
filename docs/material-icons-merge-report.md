# Material Icons Merge Report

Branch: `feat/material-icons-merged` (based on `dev`)
Date: 2026-06-16

Source branches:
- `material-icons` (PR #19) — "feat: Material-themed icons"
- `feat/app-icons` (current) — local branch with 9 commits

## Summary

Took the **simpler, cleaner implementations** from `material-icons` PR and the **better edge cases** from `feat/app-icons`. 7 modified files + 1 new file, ~590 lines total.

---

## File-by-file decisions

### `app/.../util/IconPackManager.kt` — **NEW FILE**

| Choice | Source | Reason |
|--------|--------|--------|
| Singleton `object` with `ConcurrentHashMap` cache | `material-icons` | Thread-safe, clean API |
| `cleanComponent()` parsing for `ComponentInfo{...}` | `material-icons` | Handles dotted class names and brace notation |
| `synchronized(appFilterLoadingMutex)` | `material-icons` | Prevents double-parsing on concurrent access |
| 6 intent actions/categories (4+2) | `feat/app-icons` | Detects more icon packs (solo.launcher, fede.launcher, anddoes.launcher) |

### `app/.../util/AppIconLoader.kt` — **REWRITTEN**

| Choice | Source | Reason |
|--------|--------|--------|
| Single overload `loadAppIconBitmap(context, ...)` with all themed params | `material-icons` | Cleaner than threading context as optional param |
| `createThemedAdaptiveIcon()` — canvas circle + monochrome tint | `material-icons` | Draws directly on Canvas, no `AdaptiveIconDrawable` wrapper |
| `createForcedThemedIcon()` — `extractAlpha()` + PorterDuff `SRC_IN` | `material-icons` | ~15 lines vs 60+ for per-pixel luminance analysis. Visually equivalent |
| No `ScaledDrawable` class | `material-icons` | Not needed — scaling via `RectF` dest rect |
| `DrawableCompat.setTint` for monochrome tinting | `material-icons` | Simpler than manual bitmap manipulation |
| Remaining utility functions (`getThemeColors`, `createBadgedIcon`, etc.) | `dev` base | Unchanged |
| `ponytail:` comment on forced theme | merged | Names the ceiling and upgrade path |

**Skip:** `feat/app-icons`'s per-pixel luminance analysis and `ScaledDrawable` — over-engineered for the result.

### `app/.../provider/apps/AppListRepository.kt` — **MERGED**

| Choice | Source | Reason |
|--------|--------|--------|
| `ConcurrentHashMap<String, Bitmap>` (no `Bitmap?` nullable values) | `material-icons` | Thread-safe, no mutex needed for reads |
| Settings watcher that clears icon cache on theme change | `material-icons` | Auto-invalidation without full refresh |
| Parameterless `getIcon(packageName)` reads settings internally | merged | Existing `iconLoader` lambdas in providers don't need changes |
| Composite cache keys (`$pkg:pack=$pack:themed:all`) | `feat/app-icons` | Different settings = different cache entry, no stale icons |
| No `CachedIcon` wrapper class | merged | `ConcurrentHashMap<String, Bitmap>` is sufficient with non-null values |
| Removed `cacheMutex` from `getIcon()` path | merged | `ConcurrentHashMap` handles this |
| Retained `cacheMutex` for `cachedApps` and `refresh()` | `dev` base | Still needed for app list cache consistency |
| Retained `SearchApplication.container` access | `material-icons` | Needed for settings repository |

**Skip:** `material-icons`'s `CachedIcon` wrapper (unnecessary), `feat/app-icons`'s manual cache key string composition (same result, no mutex needed with ConcurrentHashMap).

### `app/.../provider/settings/ProviderSettingsRepository.kt` — **ADDED FIELDS**

| Choice | Source | Reason |
|--------|--------|--------|
| `themedIconsEnabled: Boolean` | `material-icons` | Clearer intent than `useThemedIcons` |
| `themeAllIcons: Boolean` | `material-icons` | Clearer intent than `forceThemedIcons` |
| `iconPackPackageName: String` | `material-icons` | More explicit than `selectedIconPack` |

### `app/.../ui/components/ItemsList.kt` — **ADDED SETTINGS KEYS**

| Choice | Source | Reason |
|--------|--------|--------|
| Reads settings via `LocalContext` + `collectAsState` | `material-icons` | Local, no threading through function params |
| Settings as `produceState` keys (vararg) | `material-icons` | Triggers icon reload when theme changes |
| No explicit `useThemedIcons`/`forceThemedIcons` params on `ItemsList()` | `material-icons` | Keeps function signature clean |

**Skip:** `feat/app-icons`'s approach of threading settings through 5+ composable function signatures.

### `app/.../ui/components/RecentAppsList.kt` — **ADDED SETTINGS KEYS**

| Choice | Source | Reason |
|--------|--------|--------|
| Reads settings in `AppIconItem` composable | `material-icons` | Local, no param threading |
| Settings as `produceState` keys | `material-icons` | Triggers icon reload on theme change |
| No explicit settings params on `RecentAppsList()` | `material-icons` | Keeps public API clean (settings read internally by `getIcon()`) |
| Stable Box placeholder layout | `feat/app-icons` | Prevents sequential layout shift on cold starts |

**Skip:** `feat/app-icons`'s explicit `useThemedIcons`/`forceThemedIcons`/`selectedIconPack` params on `RecentAppsList`, `AppListSection`.

### `app/.../ui/settings/AppSearchSettingsScreen.kt` — **ADDED ICON THEME UI**

| Choice | Source | Reason |
|--------|--------|--------|
| `IconThemeSection` composable | `material-icons` | Clean UI with themed icons toggle + "theme all" sub-toggle |
| `IconPackSelectionDialog` with `RadioButton` | `material-icons` | Standard Material3 pattern |
| Settings threaded through `PinnedAppsSection` | `feat/app-icons` | Pinned apps also get themed icons (gap in PR) |
| Settings threaded through `AddPinnedAppDialog` | `feat/app-icons` | Add-pinned-app dialog also shows themed icons |
| `IconPackManager.getIconPackLabel()` helper | `material-icons` | Clean, no inline PackageManager calls |

**Skip:** `feat/app-icons`'s `IconPackChooserDialog` without radio buttons (less discoverable).

### `res/values/strings.xml` — **ADDED STRINGS**

| Choice | Source | Reason |
|--------|--------|--------|
| `settings_icon_theme_section_*` | `material-icons` | Consistent `settings_*` prefix |
| `settings_themed_icons_*` | `material-icons` | Matches field `themedIconsEnabled` |
| `settings_theme_all_icons_*` | `material-icons` | Matches field `themeAllIcons` |
| `settings_icon_pack_*` | `material-icons` | Matches field `iconPackPackageName` |

---

## What was NOT included and why

| Thing | Branch | Reason skipped |
|------|--------|----------------|
| `ScaledDrawable` class | `feat/app-icons` | Unnecessary — canvas-based drawing handles scaling via dest rect |
| Per-pixel luminance analysis in forced theme | `feat/app-icons` | 60+ lines for marginal visual improvement. `extractAlpha()` + PorterDuff is ~15 lines and visually equivalent for most icons |
| `CachedIcon` wrapper class | `material-icons` | `ConcurrentHashMap<String, Bitmap>` with non-null values is simpler and sufficient |
| `MutableMap` + `@Synchronized` for icon pack cache | `feat/app-icons` | `ConcurrentHashMap` is the standard Kotlin approach |
| Settings threaded through `RecentAppsList`/`AppListSection` as params | `feat/app-icons` | Unnecessary — `getIcon(packageName)` reads settings internally. `produceState` keys in `AppIconItem` handle reload |
| `forceThemedIcons`/`useThemedIcons`/`selectedIconPack` field names | `feat/app-icons` | `material-icons` names are more descriptive |

---

## Architecture notes

### How icon loading flows

```
User toggles "Themed icons" in settings
  → AppSearchSettings.themedIconsEnabled = true
  → Exposed via SettingsRepository.flow (StateFlow)

Settings watcher in AppListRepository:
  → detects change, calls iconCache.clear()

produceState in ItemsList / RecentAppsList / PinnedAppsSection:
  → keys include themedIconsEnabled/themeAllIcons/iconPackPackageName
  → keys change → produceState relaunches
  → calls iconLoader() → getIcon(packageName)
  → getIcon() reads current settings internally
  → calls loadAppIconBitmap(context, ...) with theme params
  → returns themed icon, caches under composite key ($pkg:themed:all)

Next call with same settings → cache hit
Toggling off → different cache key → fresh load with settings off
```

### Thread safety

- `IconPackManager.iconPackCache`: `ConcurrentHashMap` + `synchronized` loading mutex
- `AppListRepository.iconCache`: `ConcurrentHashMap` — no mutex for reads
- `AppListRepository.cachedApps`: still guarded by `cacheMutex` (app list on init)

### Edge cases

- **No icon packs installed:** Shows "Default", no crash
- **Icon pack removed after selection:** `getIconFromPack()` returns null gracefully, falls through to default icon
- **No monochrome layer:** Falls to "theme all icons" path, or returns original icon
- **Both "theme all" + icon pack selected:** Icon pack drawable is fetched first, then themed via forced algorithm
