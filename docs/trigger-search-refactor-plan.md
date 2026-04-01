# Trigger Search Refactor Plan

## Goal
Move trigger handling from the provider level to the item level so each trigger can decide how its active state should interact with normal provider query results.

## Architecture
- Introduce `SearchTrigger` as a trigger entry exposed by any `Provider`
- Introduce `TriggerResultPolicy` to control active trigger behavior:
  - `EXCLUSIVE`: only show trigger execution results
  - `INCLUDE_OWNER_RESULTS`: show trigger execution results plus the owning provider's normal query results for the payload
  - `INCLUDE_ALL_RESULTS`: show trigger execution results plus all normal provider query results for the payload
- Refine `TriggerState` to store the matched token alongside the active trigger and payload
- Suppress immediate re-triggering using both `triggerId` and `matchedToken`

## Checklist

### 1. Trigger model redesign
- [x] Add `SearchTrigger`, `TriggerMatch`, and `TriggerResultPolicy`
- [x] Add `Provider.triggers` with an empty default
- [x] Remove provider-level trigger abstraction usage

### 2. Provider migration
- [x] Migrate `WebSearchProvider` triggers to item-level `SearchTrigger`s
- [x] Migrate `TextUtilitiesProvider` triggers to item-level `SearchTrigger`s
- [x] Migrate `TermuxProvider` triggers to item-level `SearchTrigger`s
- [x] Migrate `IntentProvider` triggers to item-level `SearchTrigger`s
- [x] Assign the right `TriggerResultPolicy` per trigger source

### 3. UI and state flow
- [x] Update trigger matching helpers to work with `SearchTrigger`
- [x] Update `TriggerState` and trigger chip rendering
- [x] Remove `preTriggerText` restore logic in favor of `matchedToken`
- [x] Keep backspace-to-dismiss behavior
- [x] Keep suppression keyed by `triggerId + matchedToken`

### 4. Active trigger execution pipeline
- [x] Execute active trigger from the matched `SearchTrigger`
- [x] Support trigger-exclusive results
- [x] Support trigger plus owner-provider query results
- [x] Support trigger plus global query results
- [x] Deduplicate merged results safely

### 5. Validation
- [x] Run Kotlin compilation
- [x] Update this checklist to reflect completion
