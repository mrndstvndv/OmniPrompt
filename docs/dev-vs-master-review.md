# dev vs master review

Review target: `master..dev` (through `a2e314d`)

Baseline:
- `./gradlew :app:compileDebugKotlin` passes on current `dev`
- Use `./gradlew :app:compileDebugKotlin` as the required validation after fixes

## Summary

This review found:
- 3 confirmed bugs/regressions
- 4 simplification / line-reduction opportunities

## Fix checklist

### Bugs / regressions
- [x] Fix trigger-mode ranking so it learns by trigger identity/token, not by payload text
- [x] Fix text-utility suggestion/prefill behavior while a trigger chip is active
- [x] Fix web-search default engine state so a disabled site cannot remain the effective default

### Simplification / cleanup
- [x] Either remove `TriggerParser` or use it consistently for first-token + payload parsing
- [x] Extract the duplicated `SearchField(...)` setup in `MainActivity` into a shared composable/helper
- [x] Reduce duplication in trigger result builders so ranking metadata is applied consistently
- [x] Simplify `sortResults()` by computing frequency metadata once per result

### Validation
- [x] Run `./gradlew :app:compileDebugKotlin`
- [ ] Manual test: trigger chips for web search, text utilities, termux, and intents
- [ ] Manual test: disable/enable web search engines and confirm default behavior stays correct

## Findings

## 1) Bug: trigger-mode ranking currently learns the payload instead of the trigger

### Affected areas
- `app/src/main/java/com/mrndstvndv/search/MainActivity.kt`
  - trigger query pipeline in the main `LaunchedEffect`
  - `startPendingAction(...)`
  - contact selection frequency tracking in `handleResultSelection(...)`
- Trigger execution result builders:
  - `app/src/main/java/com/mrndstvndv/search/provider/web/WebSearchProvider.kt`
  - `app/src/main/java/com/mrndstvndv/search/provider/termux/TermuxProvider.kt`
  - `app/src/main/java/com/mrndstvndv/search/provider/intent/IntentProvider.kt`
  - `app/src/main/java/com/mrndstvndv/search/provider/text/TextUtilitiesProvider.kt`

### Problem
When trigger mode is active, `currentNormalizedQuery` is set to the trigger payload.

That means selection/ranking falls back to the payload when `frequencyQuery` is missing:
- `gh kotlin`
- `gh android`
- `base64 hello`
- `base64 world`

These end up training different query buckets even though they represent the same trigger intent.

The older non-trigger code paths still provide stable `frequencyQuery` values in several providers, so trigger mode is now inconsistent with normal query mode.

### Recommended fix
Use a stable trigger-level query key for trigger results. Any of these approaches is fine:
- set `frequencyQuery` from `matchedToken`
- add a canonical ranking/query field to `SearchTrigger`
- explicitly set stable `frequencyQuery` values in each `execute*Trigger(...)` result builder

### Acceptance criteria
- Selecting the same trigger with different payloads improves ranking in one shared bucket
- Trigger-mode ranking behavior matches the non-trigger provider path
- Web search / termux / intent / text utility trigger results all use the same rule

## 2) Bug: text-utility suggestion/prefill results can desync trigger chip state

### Affected areas
- `app/src/main/java/com/mrndstvndv/search/provider/text/TextUtilitiesProvider.kt`
  - `executeUtilityTrigger(...)`
  - `buildSuggestionResult(...)`
- `app/src/main/java/com/mrndstvndv/search/MainActivity.kt`
  - `handleResultSelection(...)`

### Problem
When a text utility trigger has no payload, `executeUtilityTrigger(...)` returns a suggestion result built by `buildSuggestionResult(...)`.

That suggestion carries `PREFILL_QUERY_EXTRA`, and `handleResultSelection(...)` blindly inserts that prefill into `textState`.

This causes bad state interactions:
- Outside chip mode, tapping the suggestion inserts something like `base64 ` as plain text without necessarily activating trigger mode through the normal path
- Inside chip mode, tapping the suggestion can write the trigger keyword into the payload text itself, resulting in payloads like `base64 foo` instead of just `foo`

### Recommended fix
Choose one consistent behavior:
- when a trigger chip is already active, do not emit prefill-style suggestion results, or
- route prefill selection through the same trigger activation logic as typing

The first option is simpler and less error-prone.

### Acceptance criteria
- With an active text-utility chip and empty payload, tapping the top suggestion does not insert the trigger keyword into the payload field
- Selecting a text-utility helper result never leaves `textState` and `triggerState` out of sync
- Normal non-trigger suggestion behavior still works while typing utility keywords

## 3) Bug: a disabled web-search site can remain the stored default

### Affected areas
- `app/src/main/java/com/mrndstvndv/search/ui/settings/WebSearchSettingsScreen.kt`
- `app/src/main/java/com/mrndstvndv/search/provider/web/WebSearchProvider.kt`
- `app/src/main/java/com/mrndstvndv/search/provider/settings/ProviderSettingsRepository.kt`

### Problem
The settings UI allows disabling the currently selected default search engine.

`WebSearchProvider` then silently falls back to the first enabled site at runtime. That creates a mismatch:
- settings can show one site as default
- actual search execution can use another enabled site

It also makes trigger behavior inconsistent because trigger generation excludes `defaultSiteId`, while runtime search uses the effective enabled default.

### Recommended fix
Pick one rule and enforce it consistently:
- prevent disabling the current default site, or
- auto-select a new enabled default when the current default is disabled, or
- auto-enable any site chosen as default

The key requirement is: stored default and effective default must always match.

### Acceptance criteria
- There is never a disabled default site in saved settings
- The default shown in settings is the same default used by runtime search
- Trigger generation and runtime default-site behavior stay aligned

## 4) Simplification: `TriggerParser` is currently dead code

### Affected areas
- `app/src/main/java/com/mrndstvndv/search/provider/model/TriggerParser.kt`
- repeated token parsing in:
  - `MainActivity.kt`
  - `WebSearchProvider.kt`
  - `IntentProvider.kt`
  - `TermuxProvider.kt`

### Problem
The same first-token / payload split logic now exists in multiple places while `TriggerParser` is unused.

### Recommended fix
Either:
- delete `TriggerParser`, or
- standardize on it everywhere this parsing is needed

Using one parser is cleaner if behavior must stay consistent across providers.

## 5) Simplification: duplicate `SearchField(...)` setup in `MainActivity`

### Affected areas
- `app/src/main/java/com/mrndstvndv/search/MainActivity.kt`

### Problem
The search bar block is duplicated in both top and bottom search-bar layout branches. This increases line count and makes future changes to trigger chip behavior easier to miss in one branch.

### Recommended fix
Extract a shared local composable/helper for the full search bar configuration, including:
- `onValueChange = ::onSearchChange`
- trigger chip rendering
- settings icon behavior
- IME submit behavior
- backspace-at-start behavior

## 6) Simplification: trigger result builders duplicate the same pattern

### Affected areas
- `WebSearchProvider.executeSiteTrigger(...)`
- `TermuxProvider.executeCommandTrigger(...)`
- `IntentProvider.executeIntentTrigger(...)`
- `TextUtilitiesProvider.executeUtilityTrigger(...)`

### Problem
Each provider rebuilds nearly the same one-result trigger path. This duplication made it easy for stable ranking metadata to drift out of sync.

### Recommended fix
Introduce a small shared helper/pattern for trigger results, or at least a consistent convention for:
- stable `frequencyKey`
- stable `frequencyQuery`
- `keepOverlayUntilExit`
- icon setup

This reduces lines and makes ranking bugs less likely to reappear.

## 7) Simplification: `sortResults()` computes frequency score more than once

### Affected areas
- `app/src/main/java/com/mrndstvndv/search/MainActivity.kt`

### Problem
The comparator recomputes `getResultFrequency(...)` multiple times per result during sorting.

### Recommended fix
Precompute per-result sort metadata once, then sort on that structure. This makes the code easier to read and avoids repeated repository lookups.

## Suggested implementation order

1. Fix trigger-mode ranking metadata
2. Fix text-utility prefill behavior in chip mode
3. Fix web-search default/disabled state consistency
4. Refactor duplicate parsing / duplicate UI / duplicate trigger result builders
