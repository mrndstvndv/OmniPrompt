# Incremental Loading and Re-sorting of Search Results

Introduce progressive result loading and on-the-fly re-sorting in the search pipeline using Kotlin `Flow.merge()`. This ensures fast providers (like local apps/history) render results instantly, while slower providers (like contacts/files) append and sort their results dynamically as they complete, eliminating UI blocking.

## Proposed Changes

### Search UI Pipeline

#### [MODIFY] [MainActivity.kt](file:///data/data/com.termux/files/home/OmniPrompt/app/src/main/java/com/mrndstvndv/search/MainActivity.kt)
- Add `import kotlinx.coroutines.flow.flow` and `import kotlinx.coroutines.flow.flowOf`.
- Implement a helper `queryProvidersFlow(query, providersToQuery)` returning `Flow<List<ProviderResult>>` using `Flow.merge()` to run queries concurrently.
- In both trigger and non-trigger search pipelines inside the query `LaunchedEffect`:
  - Reset `providerResults` to empty (or just the active alias/trigger result) immediately after the 50ms debounce delay.
  - Collect results incrementally from `queryProvidersFlow`.
  - On each emission, merge new results with the loaded accumulator, deduplicate, re-sort via `sortResults()`, and update `providerResults`.

---

## Verification Plan

### Automated Tests
- Build and compile check: `./gradlew :app:compileDebugKotlin`

### Manual Verification
- Type queries where both fast providers (Apps/Calculator) and slow providers (mocked or heavy databases like Contacts/Files) are active.
- Verify that results appear incrementally (fast results show up first, followed by slow results).
- Verify that the list is re-sorted correctly after each provider loads.
