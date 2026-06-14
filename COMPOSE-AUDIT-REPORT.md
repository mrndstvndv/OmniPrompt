# Jetpack Compose Audit Report

Target: `/Volumes/realme/Dev/Search`
Date: 2026-06-14
Scope: `app/src/main/`
Excluded from scoring: `none`
Confidence: Medium (due to Compose Compiler diagnostics being unavailable in the current environment)
Overall Score: 76/100

## Scorecard

| Category | Score | Weight | Status | Notes |
|----------|-------|--------|--------|-------|
| Performance | 7/10 | 35% | solid | Capped at 7 (diagnostics unavailable); source quality is 8/10. |
| State management | 7/10 | 25% | solid | Unified UI state flow is missing; separate flows exposed directly. |
| Side effects | 10/10 | 20% | excellent | Proper keying, cleanup, and threading throughout. |
| Composable API quality | 7/10 | 20% | solid | Reusable dialogs/sheets missing modifiers; ItemsList has 20 params. |

---

## Critical Findings

1. **Performance: Redundant `derivedStateOf` wrapping plain values**
   - **Why it matters**: `derivedStateOf` is designed to cache and throttle rapidly-changing Compose State reads (like scroll offset). Using it to wrap plain variables (like strings, lists, or sets passed as keys to `remember`) adds unnecessary tracking overhead and allocation churn without any benefit.
   - **Evidence**: `app/src/main/java/com/mrndstvndv/search/ui/settings/AppSearchSettingsScreen.kt:603`
   - **Fix direction**: Remove the `derivedStateOf` block and perform the filter/sort logic directly inside the `remember` block.
   - **References**: <https://developer.android.com/develop/ui/compose/side-effects>

2. **State Management: Fragmented ViewModel flows instead of unified UI state**
   - **Why it matters**: Exposing multiple independent `StateFlow`s from a ViewModel allows the UI to collect them at different times, which can lead to visual inconsistencies (e.g., loading indicator hides before the result list updates). A single unified `StateFlow<SearchUiState>` ensures atomic updates and guarantees consistency.
   - **Evidence**: `app/src/main/java/com/mrndstvndv/search/ui/SearchViewModel.kt:59-78`
   - **Fix direction**: Define a sealed interface or data class `SearchUiState` and combine the internal flows into a single unified stream using `stateIn(...)`.
   - **References**: <https://developer.android.com/develop/ui/compose/architecture>

3. **Composable API Quality: Reusable dialogs/sheets missing `modifier` parameter**
   - **Why it matters**: All reusable components should accept `modifier: Modifier = Modifier` as their first optional parameter and apply it to their root layout. This allows parents to adjust margins, size, layout traits, or accessibility markers without editing the component's internals.
   - **Evidence**: `app/src/main/java/com/mrndstvndv/search/ui/components/BottomSheet.kt:55`, `app/src/main/java/com/mrndstvndv/search/ui/components/ContactActionSheet.kt:56`, `app/src/main/java/com/mrndstvndv/search/ui/components/TermuxPermissionDialog.kt:16`
   - **Fix direction**: Add `modifier: Modifier = Modifier` to the function signatures and chain it to the root composable.
   - **References**: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>

4. **Composable API Quality: Excessive parameter coupling on `ItemsList`**
   - **Why it matters**: Compositions with excessive parameter counts are difficult to maintain, verify, or reuse. `ItemsList` has 20 parameters, coupling it directly to various features like favorites, pinning, badges, bottom sheets, and scroll animations.
   - **Evidence**: `app/src/main/java/com/mrndstvndv/search/ui/components/ItemsList.kt:254`
   - **Fix direction**: Refactor `ItemsList` by extracting slot APIs for item layout or delegating specific sub-items to smaller, focused composables.
   - **References**: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>

---

## Adjacent Findings

### Android Launch UX

- **Android 12+ splash icon status**: Clean / Workaround Implemented
- **Evidence**: `app/src/main/res/values-v31/themes.xml:11` sets `windowSplashScreenAnimatedIcon` to `@drawable/ic_splash`, which resolves on API 31+ to an empty `<animated-vector>` wrapper in `app/src/main/res/drawable-v31/ic_splash.xml`.
- **Finding**: The project successfully implements the empty `<animated-vector>` workaround. This switches the platform's render path from `ImmobileIconDrawable` (which pre-renders at 108 dp and upscales, producing blurriness on XHDPI+ devices) to native resolution drawing. No blurry splash screen icon risk is present.
- **References**: <https://developer.android.com/develop/ui/views/launch/splash-screen>, <https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen>, <https://issuetracker.google.com/issues/520672537>

---

## Category Details

### Performance — [7/10]

**Ceiling check**
- Strong Skipping: ON (Kotlin 2.0.21)
- Ceiling table applied: Fallback applied (Diagnostics unavailable -> Capped at 7)
- Qualitative score: 8/10
- Ceiling: Cap at 7
- Applied score: 7/10

**What is working**
- Lazy list `key` provided in results list (`ItemsList.kt:351`) and rank details.
- Deferring animated offsets to the layout phase via lambda `.offset { ... }` in `BottomSheet.kt:113`.
- Lambda form of `graphicsLayer` used for scale animations (`ItemsList.kt:324-326`), deferring reads to draw phase.
- Regex compiled once as a class member in `SearchViewModel.kt:123`, not inside composition.
- No primitive autoboxing (`mutableStateOf<Int>`) or backwards writes found.
- R8 enabled in release builds.

**What is hurting the score**
- Redundant `derivedStateOf` inside `AppSearchSettingsScreen.kt:603` (reads plain values instead of Compose State).
- Missing `contentType` on `ItemsList.kt:349` and `ProviderRankingSection.kt:241` to optimize reuse.
- `BottomSheet.kt:99` reads `scrimAlpha` in composition for `.background(Color.Black.copy(alpha = scrimAlpha))` instead of using `drawBehind`.
- Missing baseline profiles to optimize startup performance.
- `isShrinkResources` not set to `true` alongside `isMinifyEnabled = true` in `build.gradle.kts`.

**Evidence**
- `app/src/main/java/com/mrndstvndv/search/ui/settings/AppSearchSettingsScreen.kt:603` — Redundant `derivedStateOf` · References: <https://developer.android.com/develop/ui/compose/side-effects>
- `app/src/main/java/com/mrndstvndv/search/ui/components/ItemsList.kt:349` — Missing `contentType` · References: <https://developer.android.com/develop/ui/compose/lists>
- `app/src/main/java/com/mrndstvndv/search/ui/components/BottomSheet.kt:99` — Composition read of animated float for background alpha · References: <https://developer.android.com/develop/ui/compose/performance/phases>
- `app/build.gradle.kts:90` — Missing `isShrinkResources = true` · References: <https://developer.android.com/develop/ui/compose/performance/baseline-profiles>

---

### State Management — [7/10]

**What is working**
- Lifecycle-aware observable collection is used consistently via `collectAsStateWithLifecycle()`.
- Screen-level ViewModels created at root entry point (`MainActivity.kt:50-51`).
- `rememberSaveable` used correctly for dialog visibility states.
- No ViewModels in `CompositionLocal` or non-observable mutable collections used as UI state.

**What is hurting the score**
- Fragmented independent `MutableStateFlow` streams exposed directly instead of a single consolidated `UiState`.
- `MutableStateFlow` exposed directly or without `.asStateFlow()`.
- No `SharingStarted.WhileSubscribed(5_000)` sharing pattern on the flows in the ViewModel.

**Evidence**
- `app/src/main/java/com/mrndstvndv/search/ui/SearchViewModel.kt:59-78` — Fragmented StateFlows · References: <https://developer.android.com/develop/ui/compose/architecture>
- `app/src/main/java/com/mrndstvndv/search/ui/SearchViewModel.kt:59` — Flow not narrowed via `.asStateFlow()` · References: <https://developer.android.com/develop/ui/compose/architecture>

---

### Side Effects — [10/10]

**What is working**
- All `LaunchedEffect` and `DisposableEffect` are correctly keyed.
- Clean resource cleanup in `onDispose` (such as unregistering broadcast receivers).
- Zero IO or network queries inside composables. Threading managed in ViewModel coroutine scope.

**What is hurting the score**
- None. Side effects design is highly clean and idiomatic.

**Evidence**
- `app/src/main/java/com/mrndstvndv/search/ui/components/SearchField.kt:130-143` — BroadcastReceiver properly registered and unregistered on dispose · References: <https://developer.android.com/develop/ui/compose/side-effects>

---

### Composable API Quality — [7/10]

**What is working**
- **Strong string resource hygiene**: Consistently uses `stringResource` for all user-facing strings across settings screens and dialogs.
- Material 3 theme compliance via color tokens.
- Parameter order is clean (required first, modifier, optional, trailing lambda).
- Correct use of value + callback instead of `MutableState` params in reusable APIs.

**What is hurting the score**
- `ItemsList` composable holds 20 parameters. Excessive coupling.
- Missing `modifier` parameter on dialogs/sheets.
- Low preview coverage (missing for main screens like `RecentAppsList`, `SearchField`, `BottomSheet`, and all main settings screens).

**Evidence**
- `app/src/main/java/com/mrndstvndv/search/ui/components/ItemsList.kt:254` — Excessive parameter count · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `app/src/main/java/com/mrndstvndv/search/ui/components/BottomSheet.kt:55` — Missing `modifier` parameter · References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>
- `app/src/main/java/com/mrndstvndv/search/ui/components/RecentAppsList.kt` — Extracted component missing `@Preview` annotation · References: <https://developer.android.com/develop/ui/compose/tooling/previews>

---

## Prioritized Fixes

1. **Remove redundant `derivedStateOf`** in `AppSearchSettingsScreen.kt:603`
   - Change: Remove `derivedStateOf { ... }` wrapper and execute the `.filter` directly inside the `remember(searchQuery, existingPinnedApps, allApps)` block.
   - Expected Impact: Removes wasted allocation tracking overhead on query changes.
   - References: <https://developer.android.com/develop/ui/compose/side-effects>

2. **Unify State Flows** in `SearchViewModel.kt:59-78`
   - Change: Define a consolidated `SearchUiState` data class and combine individual state variables into a single unified Flow exposed using `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`.
   - Expected Impact: Prevents state fragmentation and ensures visual consistency in UI updates.
   - References: <https://developer.android.com/develop/ui/compose/architecture>

3. **Hoist `modifier` parameters** to dialogs and bottom sheets
   - Change: Add `modifier: Modifier = Modifier` to `BottomSheet.kt:55`, `ContactActionSheet.kt:56`, and `TermuxPermissionDialog.kt:16` and apply it to their root-most layouts.
   - Expected Impact: Allows parent views to customize sizes, paddings, and alignment without altering component internals.
   - References: <https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md>

4. **Add `isShrinkResources = true`** in `build.gradle.kts:90`
   - Change: Add `isShrinkResources = true` alongside `isMinifyEnabled = true` in the release build block.
   - Expected Impact: Optimizes release build sizes by cleaning up unused resources.
   - References: <https://developer.android.com/develop/ui/compose/performance/baseline-profiles>

---

## Notes And Limits

- **Audited surface**: Entire production Jetpack Compose surface in the main app module.
- **Compiler diagnostics used**: `no` (stability claims are inferred from source scans, not compiler reports).
- **Strong Skipping mode**: ON (Kotlin 2.0.21 default).
- **Weight choice**: Default 35/25/20/20.

## Suggested Follow-Up

- Run `compose-agent focus on focus` if D-pad navigation, custom focus traversal, or keyboard restoration policies require deep evaluation.
- Run `compose-agent focus on testing` to write previews and verification rules for reusable components.
