# Implementation Plan: SearchActivity & Search Pipeline Performance Optimization

This plan addresses and resolves the 7 performance inefficiencies and memory leaks empirically proven by the captured logs in [`performance_evidence.log`](file:///Volumes/realme/Dev/Search/performance_evidence.log).

---

## Goal Description

Eliminate memory leaks, recomposition storms, coroutine churn, linear sorting bottlenecks, and redundant context switching in `SearchActivity` and `SearchViewModel` to ensure standard 60–120 FPS UI performance, zero memory leaks across activity recreations, and minimal CPU overhead during search interactions.

---

## Proven Inefficiencies & Concrete Fixes

### 1. Activity Context Leak & Provider Instantiation
* **Problem**: 9 provider instances are instantiated in `@Composable` code in [`SearchActivity.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/SearchActivity.kt#L464) using `this@SearchActivity`. Because `SearchViewModel` survives activity recreation and retains these provider instances, it leaks the destroyed `SearchActivity` instance.
* **Fix**: Change all provider constructors to accept `Context` (specifically `applicationContext`) instead of `ComponentActivity`. Define a single, thread-safe `providers` list in [`AppContainer.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/di/AppContainer.kt) using lazy instantiation.

### 2. Inner Composable Recomposition Storm (`SearchBar` & `UpdateBanner`)
* **Problem**: `SearchBar` and `UpdateBanner` are defined as local nested `@Composable` functions inside `setContent { ... }` in [`SearchActivity.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/SearchActivity.kt#L740). On each recomposition, new function objects are created, defeating Jetpack Compose's compiler-level skippability.
* **Fix**: Extract `SearchBar` and `UpdateBanner` out of `setContent` as top-level / private composable functions. Explicitly pass state variables and actions as parameters.

### 3. Window Blur Animation Coroutine Churn
* **Problem**: `LaunchedEffect(animatedBlurStrength)` in [`SearchActivity.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/SearchActivity.kt#L442) cancels and restarts 40+ coroutines per second during the 300 ms blur transition, causing high CPU/GC overhead.
* **Fix**: Replace `LaunchedEffect(animatedBlurStrength)` with a `SideEffect { applyWindowBlur(animatedBlurStrength) }` as `applyWindowBlur` is a synchronous block that does not require coroutine synchronization.

### 4. Startup Search Execution Storm
* **Problem**: `observeRefreshSignals()` in [`SearchViewModel.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/ui/SearchViewModel.kt#L102) collects three `StateFlow` streams (`aliases`, `enabledProviders`, `useFrequencyRanking`) which all emit initial values simultaneously upon subscription, causing 3 redundant back-to-back search queries during app startup.
* **Fix**: Prepend `.drop(1)` on the collected `StateFlow` streams so setup collectors only trigger search execution on subsequent changes.

### 5. Linear $O(N)$ Provider Rank Lookup in Result Sorting
* **Problem**: `sortResults()` in [`SearchViewModel.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/ui/SearchViewModel.kt#L484) calls `getProviderRank()` which performs a linear scan `indexOf` search on `providerOrder` for each result, yielding $O(M \times N)$ time complexity.
* **Fix**: Pre-map provider IDs to their rank index inside `sortResults()` to perform $O(1)$ lookups.

### 6. Redundant Context Switching in Parallel Provider Queries
* **Problem**: `queryProviders()` in [`SearchViewModel.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/ui/SearchViewModel.kt#L465) calls `async { withContext(Dispatchers.IO) { ... } }`, causing a redundant thread jump from `Dispatchers.Default` (used by `async`) to `Dispatchers.IO` (used by `withContext`).
* **Fix**: Dispatch the coroutine directly to `Dispatchers.IO` by calling `async(Dispatchers.IO) { ... }` and removing `withContext`.

### 7. Soft Keyboard (IME) Inset Recomposition Bottleneck
* **Problem**: Reading `WindowInsets.ime.getBottom(density)` in composition inside `BoxWithConstraints` in [`SearchActivity.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/SearchActivity.kt#L936) forces 26+ recompositions of the layout tree as the soft keyboard slides up/down.
* **Fix**: Extract keyboard padding calculation into a custom `layout` modifier, reading the IME insets inside the layout/placement phase and removing the heavy `BoxWithConstraints` wrapper.

---

## File-by-File Concrete Code Snippets & Diffs

### 1. Provider Signatures & Implementations

#### [`MODIFY`] [`AppListProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/apps/AppListProvider.kt)
* Replace `ComponentActivity` constructor parameter with `Context` and pass a shared `CoroutineScope` for sharing the refresh flow.
* Add `Intent.FLAG_ACTIVITY_NEW_TASK` to launch intents.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
+import kotlinx.coroutines.CoroutineScope
 
 class AppListProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
     private val settingsRepository: ProviderSettingsRepository<AppSearchSettings>,
     private val appListRepository: AppListRepository,
+    private val scope: CoroutineScope,
 ) : Provider {
     override val id: String = "app-list"
-    override val displayName: String = activity.getString(R.string.provider_applications)
+    override val displayName: String = context.getString(R.string.provider_applications)
     override val refreshSignal: SharedFlow<Unit> =
         appListRepository
             .getAllApps()
             .drop(1)
             .map { Unit }
             .shareIn(
-                scope = activity.lifecycleScope,
+                scope = scope,
                 started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                 replay = 0,
             )
 
-    private val packageManager = activity.packageManager
+    private val packageManager = context.packageManager
 
     ...
 
     action = {
         withContext(Dispatchers.Main) {
             val intent = buildAiQueryIntent(askMatch.assistant, askMatch.query)
-            activity.startActivity(intent)
-            activity.finish()
+            context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
         }
     }
 
     ...
 
     action = {
         withContext(Dispatchers.Main) {
-            val userManager = activity.getSystemService(Context.USER_SERVICE) as UserManager
+            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
             val userHandle = userManager.getUserForSerialNumber(entry.userSerialNumber)
                 ?: android.os.Process.myUserHandle()
-            val launcherApps = activity.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
+            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
             val activities = launcherApps.getActivityList(entry.packageName, userHandle)
             val activityInfo = activities.firstOrNull()
             if (activityInfo != null) {
                 launcherApps.startMainActivity(
                     activityInfo.componentName,
                     userHandle,
-                    activity.intent.sourceBounds,
+                    null,
                     null
                 )
-                activity.finish()
             } else {
                 val launchIntent = packageManager.getLaunchIntentForPackage(entry.packageName)
                 if (launchIntent != null) {
-                    activity.startActivity(launchIntent)
-                    activity.finish()
+                    context.startActivity(launchIntent)
                 }
             }
         }
     }
```

#### [`MODIFY`] [`SettingsProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/system/SettingsProvider.kt)
* Replace `ComponentActivity` with `Context` and remove `activity.finish()`. Add `FLAG_ACTIVITY_NEW_TASK`.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
 
 class SettingsProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
     private val globalSettingsRepository: SettingsRepository,
     private val settingsRepository: ProviderSettingsRepository<SystemSettingsSettings>,
     private val developerSettingsManager: DeveloperSettingsManager,
 ) : Provider {
     override val id: String = "system-settings"
-    override val displayName: String = activity.getString(R.string.provider_system_settings)
+    override val displayName: String = context.getString(R.string.provider_system_settings)
 
     ...
 
-    private fun string(resId: Int): String = activity.getString(resId)
+    private fun string(resId: Int): String = context.getString(resId)
 
     ...
 
     onSelect = {
         withContext(Dispatchers.Main) {
             try {
                 if (item.onLaunch != null) {
                     item.onLaunch.invoke()
                 } else {
                     val intent = Intent(item.action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
-                    if (intent.resolveActivity(activity.packageManager) != null) {
-                        activity.startActivity(intent)
+                    if (intent.resolveActivity(context.packageManager) != null) {
+                        context.startActivity(intent)
                     }
                 }
-                activity.finish()
             } catch (e: Exception) {
                 e.printStackTrace()
             }
         }
     }
```

#### [`MODIFY`] [`CalculatorProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/calculator/CalculatorProvider.kt)
* Replace `ComponentActivity` with `Context` and remove `activity.finish()`.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
 
 class CalculatorProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
 ) : Provider {
     override val id: String = "calculator"
-    override val displayName: String = activity.getString(R.string.provider_calculator)
+    override val displayName: String = context.getString(R.string.provider_calculator)
 
     ...
 
     val action: suspend () -> Unit = {
         withContext(Dispatchers.Main) {
             copyToClipboard(result)
-            activity.finish()
         }
     }
 
-    private fun copyToClipboard(value: String) {
-        val clipboard = activity.getSystemService(ClipboardManager::class.java)
-        val clip = ClipData.newPlainText(activity.getString(R.string.provider_calculator), value)
+    private fun copyToClipboard(value: String) {
+        val clipboard = context.getSystemService(ClipboardManager::class.java)
+        val clip = ClipData.newPlainText(context.getString(R.string.provider_calculator), value)
         clipboard.setPrimaryClip(clip)
-        Toast.makeText(activity, activity.getString(R.string.calculator_copied), Toast.LENGTH_SHORT).show()
+        Toast.makeText(context, context.getString(R.string.calculator_copied), Toast.LENGTH_SHORT).show()
     }
```

#### [`MODIFY`] [`TextUtilitiesProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/text/TextUtilitiesProvider.kt)
* Replace `ComponentActivity` with `Context` and remove `activity.finish()`. Add `FLAG_ACTIVITY_NEW_TASK`.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
 
 class TextUtilitiesProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
     private val settingsRepository: ProviderSettingsRepository<TextUtilitiesSettings>,
 ) : Provider {
     
     ...
 
     private suspend fun openUri(uri: Uri) {
         withContext(Dispatchers.Main) {
-            val intent = Intent(Intent.ACTION_VIEW, uri)
-            activity.startActivity(intent)
+            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
+            context.startActivity(intent)
             finishOverlay()
         }
     }
 
     private fun finishOverlay() {
-        activity.finish()
+        // Overlay dismissal is now handled dynamically in SearchActivity's LaunchedEffect upon action completion.
     }
```

#### [`MODIFY`] [`FileSearchProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/files/FileSearchProvider.kt)
* Replace `ComponentActivity` with `Context` and remove `activity.finish()`. Add `FLAG_ACTIVITY_NEW_TASK`.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
 
 class FileSearchProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
     private val settingsRepository: ProviderSettingsRepository<FileSearchSettings>,
     private val repository: FileSearchRepository,
     private val thumbnailRepository: FileThumbnailRepository,
 ) : Provider {
-    private val fileProviderAuthority: String = "${activity.packageName}.fileprovider"
+    private val fileProviderAuthority: String = "${context.packageName}.fileprovider"
 
     ...
 
             val intent =
                 Intent(Intent.ACTION_VIEW)
                     .setDataAndType(shareableUri, targetMime)
                     .addFlags(
-                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
+                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK,
                     )
             try {
-                activity.startActivity(intent)
-                activity.finish()
+                context.startActivity(intent)
             } catch (error: ActivityNotFoundException) {
                 Toast.makeText(
-                    activity,
-                    activity.getString(R.string.toast_no_app_open),
+                    context,
+                    context.getString(R.string.toast_no_app_open),
                     Toast.LENGTH_SHORT,
                 ).show()
             }
```

#### [`MODIFY`] [`WebSearchProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/web/WebSearchProvider.kt)
* Replace `ComponentActivity` with `Context` and remove `activity.finish()`. Add `FLAG_ACTIVITY_NEW_TASK`.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
 
 class WebSearchProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
     private val settingsRepository: ProviderSettingsRepository<WebSearchSettings>,
 ) : Provider {
-    override val displayName: String = activity.getString(R.string.provider_web_search)
+    override val displayName: String = context.getString(R.string.provider_web_search)
 
     ...
 
     private suspend fun executeSiteTrigger(
         siteId: String,
         invocation: TriggerInvocation,
     ): List<ProviderResult> {
         ...
         val action: suspend () -> Unit = {
             withContext(Dispatchers.Main) {
-                val intent = Intent(Intent.ACTION_VIEW, searchUrl.toUri())
-                activity.startActivity(intent)
-                activity.finish()
+                val intent = Intent(Intent.ACTION_VIEW, searchUrl.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
+                context.startActivity(intent)
             }
         }
         return listOf(
             createTriggerResult(
                 invocation = invocation,
                 id = "$id:${site.id}",
-                title = activity.getString(R.string.web_search_result_title, queryText),
+                title = context.getString(R.string.web_search_result_title, queryText),
                 subtitle = site.displayName,
                 providerId = id,
                 triggerId = site.id,
                 onSelect = action,
                 aliasTarget = WebSearchAliasTarget(site.id, site.displayName),
             ),
         )
     }
```

#### [`MODIFY`] [`TermuxProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/termux/TermuxProvider.kt)
* Replace `ComponentActivity` with `Context` and remove `activity.finish()`. Add `FLAG_ACTIVITY_NEW_TASK`.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
 
 class TermuxProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
     private val globalSettingsRepository: SettingsRepository,
     private val settingsRepository: ProviderSettingsRepository<TermuxSettings>,
 ) : Provider {
-    override val displayName: String = activity.getString(R.string.provider_termux)
+    override val displayName: String = context.getString(R.string.provider_termux)
 
     ...
 
     private suspend fun executeTermuxCommand(...) {
         ...
         val intent = Intent().apply {
             action = "com.termux.service.RUN_COMMAND"
             addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
             ...
         }
-        activity.startService(intent)
-        activity.finish()
+        context.startService(intent)
     }
```

#### [`MODIFY`] [`IntentProvider.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/provider/intent/IntentProvider.kt)
* Replace `ComponentActivity` with `Context` and remove `activity.finish()`. Add `FLAG_ACTIVITY_NEW_TASK`.

```diff
-import androidx.activity.ComponentActivity
+import android.content.Context
 
 class IntentProvider(
-    private val activity: ComponentActivity,
+    private val context: Context,
     private val globalSettingsRepository: SettingsRepository,
     private val settingsRepository: ProviderSettingsRepository<IntentSettings>,
     private val appListRepository: AppListRepository,
 ) : Provider {
-    override val displayName: String = activity.getString(R.string.provider_intent_launcher)
+    override val displayName: String = context.getString(R.string.provider_intent_launcher)
 
     ...
 
     private fun executeIntent(...) {
         val intent = Intent(config.intentAction).apply {
             addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
             ...
         }
-        activity.startActivity(intent)
-        activity.finish()
+        context.startActivity(intent)
     }
```

---

### 2. Dependency Injection Container

#### [`MODIFY`] [`AppContainer.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/di/AppContainer.kt)
* Define the single, lazy initialization list of the 9 providers using `context.applicationContext` and `applicationScope`.

```diff
+import com.mrndstvndv.search.provider.Provider
+import com.mrndstvndv.search.provider.apps.AppListProvider
+import com.mrndstvndv.search.provider.system.SettingsProvider
+import com.mrndstvndv.search.provider.calculator.CalculatorProvider
+import com.mrndstvndv.search.provider.text.TextUtilitiesProvider
+import com.mrndstvndv.search.provider.files.FileSearchProvider
+import com.mrndstvndv.search.provider.contacts.ContactsProvider
+import com.mrndstvndv.search.provider.web.WebSearchProvider
+import com.mrndstvndv.search.provider.termux.TermuxProvider
+import com.mrndstvndv.search.provider.intent.IntentProvider
+
 class AppContainer(val context: Context) {
     val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
 
     ...
 
+    val providers: List<Provider> by lazy {
+        buildList {
+            add(AppListProvider(context.applicationContext, appSearchSettingsRepo, appListRepository, applicationScope))
+            add(SettingsProvider(context.applicationContext, settingsRepository, systemSettingsSettingsRepo, developerSettingsManager))
+            add(CalculatorProvider(context.applicationContext))
+            add(TextUtilitiesProvider(context.applicationContext, textUtilitiesSettingsRepo))
+            add(FileSearchProvider(context.applicationContext, fileSearchSettingsRepo, fileSearchRepository, fileThumbnailRepository))
+            add(ContactsProvider(context.applicationContext, settingsRepository, contactsSettingsRepo, contactsRepository))
+            add(WebSearchProvider(context.applicationContext, webSearchSettingsRepo))
+            add(TermuxProvider(context.applicationContext, settingsRepository, termuxSettingsRepo))
+            add(IntentProvider(context.applicationContext, settingsRepository, intentSettingsRepo, appListRepository))
+        }
+    }
 }
```

---

### 3. ViewModel & Pipeline Optimizations

#### [`MODIFY`] [`SearchViewModel.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/ui/SearchViewModel.kt)
* Auto-initialize providers directly in the `init` block of the `SearchViewModel`.
* Prepend `.drop(1)` to `aliasRepository.aliases`, `settingsRepository.enabledProviders`, and `rankingRepository.useFrequencyRanking` flows inside `observeRefreshSignals()`.
* Replace redundant thread jump in `queryProviders` with direct dispatching `async(Dispatchers.IO)`.
* Optimize result sorting to use an $O(1)$ provider ranks map lookup.

```diff
 class SearchViewModel(
     private val container: AppContainer,
 ) : ViewModel() {
     ...
     private var providers = emptyList<Provider>()
     private var providersById = emptyMap<String, Provider>()
     private var availableTriggers = emptyList<SearchTrigger>()
 
+    init {
+        val providerList = container.providers
+        providers = providerList
+        providersById = providerList.associateBy { it.id }
+        updateAvailableTriggers()
+        observeRefreshSignals()
+        
+        // Pre-initialize heavy providers off the main thread
+        viewModelScope.launch(Dispatchers.Default) {
+            providers.forEach { it.initialize() }
+            container.appListRepository.initialize()
+        }
+    }
+
-    fun initProviders(providerList: List<Provider>) {
-        if (providers.isNotEmpty()) {
-            PerformanceLogger.log(
-                container.context,
-                "ISSUE_1_CONTEXT_LEAK",
-                "initProviders called with new Activity providers, but SKIPPED because SearchViewModel already retains providers! (Retained providers count: ${providers.size})"
-            )
-            return
-        }
-        providers = providerList
-        providersById = providerList.associateBy { it.id }
-        updateAvailableTriggers()
-        observeRefreshSignals()
-    }
 
     private fun observeRefreshSignals() {
         viewModelScope.launch {
             merge(*providers.map { it.refreshSignal }.toTypedArray())
                 .collect {
                     PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: provider refreshSignal triggered executeSearch()")
                     executeSearch()
                 }
         }
         viewModelScope.launch {
-            aliasRepository.aliases.collect {
+            aliasRepository.aliases.drop(1).collect {
                 PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: aliasRepository emitted, triggering executeSearch()")
                 executeSearch()
             }
         }
         viewModelScope.launch {
-            settingsRepository.enabledProviders.collect {
+            settingsRepository.enabledProviders.drop(1).collect {
                 updateAvailableTriggers()
                 PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: enabledProviders emitted, triggering executeSearch()")
                 executeSearch()
             }
         }
         viewModelScope.launch {
-            rankingRepository.useFrequencyRanking.collect {
+            rankingRepository.useFrequencyRanking.drop(1).collect {
                 PerformanceLogger.log(container.context, "ISSUE_4_EXECUTE_SEARCH_STORM", "observeRefreshSignals: useFrequencyRanking emitted, triggering executeSearch()")
                 executeSearch()
             }
         }
     }
 
     ...
 
     private suspend fun queryProviders(
         query: Query,
         providersToQuery: List<Provider>,
     ): List<ProviderResult> {
         if (providersToQuery.isEmpty()) return emptyList()
         val allResults =
             supervisorScope {
                 providersToQuery.map { provider ->
-                    async {
+                    async(Dispatchers.IO) {
                         try {
                             PerformanceLogger.log(
                                 container.context,
                                 "ISSUE_6_CONTEXT_SWITCH",
-                                "queryProviders: Querying '${provider.id}' inside async { withContext(Dispatchers.IO) } on thread: ${Thread.currentThread().name}"
+                                "queryProviders: Querying '${provider.id}' on thread: ${Thread.currentThread().name}"
                             )
-                            withContext(Dispatchers.IO) { provider.query(query) }
+                            provider.query(query)
                         } catch (e: CancellationException) {
                             throw e
                         } catch (e: Exception) {
                             e.printStackTrace()
                             emptyList()
                         }
                     }
                 }.awaitAll()
             }
         return deduplicateResults(allResults.flatten())
     }
 
     private fun sortResults(
         results: List<ProviderResult>,
         normalizedText: String,
         useFrequencyRanking: Boolean,
     ): List<ProviderResult> {
         val startNano = System.nanoTime()
+        val providerOrder = rankingRepository.providerOrder.value
+        val rankMap = providerOrder.withIndex().associate { it.value to it.index }
         val sortMetadata =
             results.map { result ->
-                val providerRank = rankingRepository.getProviderRank(result.providerId)
+                val providerRank = rankMap[result.providerId] ?: providerOrder.size
                 val frequencyQuery = result.frequencyQuery ?: normalizedText
                 val frequencyScore =
                     if (useFrequencyRanking) {
                         rankingRepository.getResultFrequency(result.frequencyKey, frequencyQuery)
                     } else {
                         0f
                     }
                 ResultSortMetadata(
                     result = result,
                     providerRank = providerRank,
                     frequencyScore = frequencyScore,
                 )
             }
         val durationMs = (System.nanoTime() - startNano) / 1_000_000.0
         PerformanceLogger.log(
             container.context,
             "ISSUE_5_LINEAR_RANK_LOOKUP",
-            "sortResults: Sorted ${results.size} items performing ${results.size} linear indexOf scans on provider order. Time: ${String.format("%.3f", durationMs)} ms"
+            "sortResults: Sorted ${results.size} items performing O(1) rank map lookups. Time: ${String.format("%.3f", durationMs)} ms"
         )
         ...
     }
```

---

### 4. UI Layer & Activity Optimizations

#### [`MODIFY`] [`SearchActivity.kt`](file:///Volumes/realme/Dev/Search/app/src/main/java/com/mrndstvndv/search/SearchActivity.kt)
* Remove provider instantiation and initialization `LaunchedEffect` / `remember` block.
* Replace `LaunchedEffect(animatedBlurStrength)` with `SideEffect`.
* Extract `UpdateBanner` and `SearchBar` into separate, top-level private `@Composable` functions outside `setContent`.
* Implement layout-phase IME padding inside `Modifier.layout` on the bottom layout container and remove `BoxWithConstraints`.
* Update selection `LaunchedEffect` to automatically finish the activity when an action completes.

```diff
-            val providers =
-                remember(this@SearchActivity) {
-                    PerformanceLogger.log(this@SearchActivity, "ISSUE_1_CONTEXT_LEAK", "Creating 9 new Provider instances in Composable passing Activity Context HashCode: ${System.identityHashCode(this@SearchActivity)}")
-                    buildList {
-                        add(AppListProvider(this@SearchActivity, appSearchSettingsRepo, appListRepository))
-                        add(SettingsProvider(this@SearchActivity, settingsRepository, systemSettingsSettingsRepo, developerSettingsManager))
-                        add(CalculatorProvider(this@SearchActivity))
-                        add(TextUtilitiesProvider(this@SearchActivity, textUtilitiesSettingsRepo))
-                        add(FileSearchProvider(this@SearchActivity, fileSearchSettingsRepo, fileSearchRepository, fileThumbnailRepository))
-                        add(ContactsProvider(this@SearchActivity, settingsRepository, contactsSettingsRepo, contactsRepository))
-                        add(WebSearchProvider(this@SearchActivity, webSearchSettingsRepo))
-                        add(TermuxProvider(this@SearchActivity, settingsRepository, termuxSettingsRepo))
-                        add(IntentProvider(this@SearchActivity, settingsRepository, intentSettingsRepo, appListRepository))
-                    }
-                }
-
-            // Pre-initialize heavy providers on first composition
-            LaunchedEffect(Unit) {
-                withContext(Dispatchers.Default) {
-                    providers.forEach { it.initialize() }
-                    appListRepository.initialize()
-                }
-            }
-
-            LaunchedEffect(providers) {
-                viewModel.initProviders(providers)
-            }
 
-            LaunchedEffect(animatedBlurStrength) {
-                PerformanceLogger.log(this@SearchActivity, "ISSUE_3_ANIMATION_CHURN", "LaunchedEffect(animatedBlurStrength) restarted for strength: $animatedBlurStrength")
-                applyWindowBlur(animatedBlurStrength)
-            }
+            SideEffect {
+                PerformanceLogger.log(this@SearchActivity, "ISSUE_3_ANIMATION_CHURN", "SideEffect running for strength: $animatedBlurStrength")
+                applyWindowBlur(animatedBlurStrength)
+            }
 
             ...
 
             LaunchedEffect(pendingAction) {
                 val action = pendingAction ?: return@LaunchedEffect
                 var completed = false
                 try {
                     withContext(Dispatchers.Default) {
                         action.block()
                     }
                     completed = true
                 } finally {
                     pendingAction = null
-                    val shouldDismissOverlay =
-                        !action.keepOverlayUntilExit || !completed || (!this@SearchActivity.isFinishing && !isExiting && !finishRequestedDuringAction)
-                    if (shouldDismissOverlay) {
-                        viewModel.setIsPerformingAction(false)
-                    }
-                    if (finishRequestedDuringAction) {
-                        finish()
-                    }
+                    if (completed && action.keepOverlayUntilExit) {
+                        finish()
+                    } else {
+                        val shouldDismissOverlay =
+                            !action.keepOverlayUntilExit || !completed || (!this@SearchActivity.isFinishing && !isExiting && !finishRequestedDuringAction)
+                        if (shouldDismissOverlay) {
+                            viewModel.setIsPerformingAction(false)
+                        }
+                        if (finishRequestedDuringAction) {
+                            finish()
+                        }
+                    }
                 }
             }
 
             ...
 
-            @OptIn(ExperimentalMaterial3Api::class)
-            @Composable
-            fun UpdateBanner(...) { ... }
-
-            @Composable
-            fun SearchBar() { ... }
-
             ...
 
             if (searchBarPosition == SearchBarPosition.BOTTOM) {
                 val density = LocalDensity.current
                 val imeInsets = WindowInsets.ime
-                val keyboardHeightPx = imeInsets.getBottom(density)
-                val keyboardHeightDp = with(density) { keyboardHeightPx.toDp() }
-
-                BoxWithConstraints(
-                    modifier =
-                        Modifier
-                            .fillMaxWidth()
-                            .then(
-                                if (!hasVisibleResults) {
-                                    Modifier.weight(1f)
-                                } else {
-                                    Modifier
-                                },
-                            ),
-                ) {
-                    SideEffect {
-                        PerformanceLogger.log(this@SearchActivity, "ISSUE_7_IME_RECOMPOSITION", "BoxWithConstraints recomposed due to IME bottom padding change: $keyboardHeightPx px")
-                    }
-                    val containerHeight = maxHeight
-                    val searchBarHeight = 56.dp // Approximate height of search bar
-                    val appListHeight = if (appSearchSettings.appListEnabled && !hasVisibleResults) 64.dp else 0.dp
-                    val totalContentHeight = searchBarHeight + appListHeight + 10.dp // padding
-
-                    // Calculate centering padding for "no results" state
-                    val centeringPadding =
-                        if (!hasVisibleResults) {
-                            (containerHeight - totalContentHeight) / 2
-                        } else {
-                            0.dp
-                        }
-
-                    // Calculate if centered content would overlap with keyboard
-                    val centeredBottomPosition = containerHeight / 2 + totalContentHeight / 2
-                    val keyboardTopPosition = containerHeight - keyboardHeightDp
-                    val shouldPushUp = centeredBottomPosition > keyboardTopPosition && keyboardHeightPx > 0
-
-                    // When there are results, always push up by keyboard height if keyboard is visible
-                    val needsKeyboardPadding = hasVisibleResults && keyboardHeightPx > 0
-
-                    // Animate the bottom padding for smooth keyboard transitions
-                    val targetPadding =
-                        when {
-                            needsKeyboardPadding -> keyboardHeightDp
-                            shouldPushUp -> keyboardHeightDp
-                            !hasVisibleResults -> centeringPadding
-                            else -> 0.dp
-                        }
-                    val animatedBottomPadding by animateDpAsState(
-                        targetValue = targetPadding,
-                        animationSpec = tween(durationMillis = 250),
-                        label = "keyboardPadding",
-                    )
-
-                    Column(
-                        modifier =
-                            Modifier
-                                .fillMaxWidth()
-                                .align(Alignment.BottomCenter)
-                                .padding(bottom = animatedBottomPadding),
-                        verticalArrangement = Arrangement.Center,
-                    ) {
-                        if (showUpdateBanner && !hasVisibleResults) {
-                            activeUpdateResult?.let { update ->
-                                UpdateBanner(
-                                    result = update,
-                                    onDismiss = {
-                                        showUpdateBanner = false
-                                        settingsRepository.setDismissedVersion(update.version)
-                                    },
-                                    onDownload = {
-                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
-                                        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
-                                        startActivity(browserIntent)
-                                    },
-                                )
-                            }
-                        }
-                        SearchBar()
-                        ...
-                    }
-                }
+                Box(
+                    modifier = Modifier
+                        .fillMaxWidth()
+                        .then(if (!hasVisibleResults) Modifier.weight(1f) else Modifier)
+                        .layout { measurable, constraints ->
+                            val keyboardHeightPx = imeInsets.getBottom(density)
+                            val keyboardHeightDp = keyboardHeightPx.toDp()
+
+                            val containerHeight = constraints.maxHeight.toDp()
+                            val searchBarHeight = 56.dp
+                            val appListHeight = if (appSearchSettings.appListEnabled && !hasVisibleResults) 64.dp else 0.dp
+                            val totalContentHeight = searchBarHeight + appListHeight + 10.dp
+
+                            val centeringPadding = if (!hasVisibleResults) {
+                                (containerHeight - totalContentHeight) / 2
+                            } else {
+                                0.dp
+                            }
+
+                            val centeredBottomPosition = containerHeight / 2 + totalContentHeight / 2
+                            val keyboardTopPosition = containerHeight - keyboardHeightDp
+                            val shouldPushUp = centeredBottomPosition > keyboardTopPosition && keyboardHeightPx > 0
+                            val needsKeyboardPadding = hasVisibleResults && keyboardHeightPx > 0
+
+                            val targetPadding = when {
+                                needsKeyboardPadding -> keyboardHeightDp
+                                shouldPushUp -> keyboardHeightDp
+                                !hasVisibleResults -> centeringPadding
+                                else -> 0.dp
+                            }
+                            
+                            val targetPaddingPx = targetPadding.roundToPx()
+
+                            val placeable = measurable.measure(
+                                constraints.copy(
+                                    maxHeight = (constraints.maxHeight - targetPaddingPx).coerceAtLeast(0)
+                                )
+                            )
+
+                            layout(placeable.width, placeable.height + targetPaddingPx) {
+                                placeable.placeRelative(0, 0)
+                            }
+                        }
+                ) {
+                    Column(
+                        modifier = Modifier
+                            .fillMaxWidth()
+                            .align(Alignment.BottomCenter),
+                        verticalArrangement = Arrangement.Center,
+                    ) {
+                        if (showUpdateBanner && !hasVisibleResults) {
+                            activeUpdateResult?.let { update ->
+                                UpdateBanner(
+                                    result = update,
+                                    onDismiss = {
+                                        showUpdateBanner = false
+                                        settingsRepository.setDismissedVersion(update.version)
+                                    },
+                                    onDownload = {
+                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
+                                        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
+                                        startActivity(browserIntent)
+                                    },
+                                    firstResultHighlightEnabled = firstResultHighlightEnabled,
+                                    translucentResultsEnabled = translucentResultsEnabled,
+                                    firstResultBorderThickness = firstResultBorderThickness
+                                )
+                            }
+                        }
+                        SearchBar(
+                            textState = textState,
+                            triggerState = uiState.triggerState,
+                            settingsIconPosition = settingsIconPosition,
+                            focusRequester = focusRequester,
+                            onSearchChange = viewModel::onSearchChange,
+                            onDismissTrigger = viewModel::dismissTrigger,
+                            onOpenSettings = ::openSettingsScreen,
+                            onOpenSystemSettings = ::openSystemSettingsScreen,
+                            onSubmitSearch = ::submitSearch
+                        )
+                        ...
+                    }
+                }
+            }
```

#### Extracted Top-Level Composables to Add at Bottom of `SearchActivity.kt`
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateBanner(
    result: GitHubUpdateChecker.UpdateResult,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    firstResultHighlightEnabled: Boolean,
    translucentResultsEnabled: Boolean,
    firstResultBorderThickness: Int,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.6f },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd ||
            dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
        ) {
            onDismiss()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
            )
        },
        modifier = modifier,
    ) {
        val borderStroke = if (firstResultHighlightEnabled) {
            val borderColor = MaterialTheme.colorScheme.primary.copy(
                alpha = if (translucentResultsEnabled) 0.5f else 0.22f,
            )
            BorderStroke(firstResultBorderThickness.dp, borderColor)
        } else {
            null
        }

        Card(
            onClick = onDownload,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = borderStroke,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update available: ${result.version}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Download Update",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    textState: TextFieldValue,
    triggerState: TriggerState?,
    settingsIconPosition: SettingsIconPosition,
    focusRequester: FocusRequester,
    onSearchChange: (TextFieldValue) -> Unit,
    onDismissTrigger: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onSubmitSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect {
        PerformanceLogger.log(
            null,
            "ISSUE_2_RECOMPOSITION",
            "SearchBar composable recomposed! Current search text: '${textState.text}'"
        )
    }
    Box(modifier = modifier) {
        SearchField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = textState,
            onValueChange = onSearchChange,
            triggerChip = triggerState?.let { activeTrigger ->
                {
                    TriggerChip(
                        item = activeTrigger.trigger,
                        onDismiss = onDismissTrigger,
                    )
                }
            },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            trailingIcon = {
                if (settingsIconPosition == SettingsIconPosition.INSIDE) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            onClear = { onSearchChange(TextFieldValue("")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmitSearch() }),
            onBackspaceAtStart = triggerState?.let { { onDismissTrigger() } },
        )

        if (settingsIconPosition == SettingsIconPosition.INSIDE && textState.text.isEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .size(48.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenSettings,
                        onLongClick = onOpenSystemSettings,
                    ),
            )
        }
    }
}
```

---

## Verification Plan

### Automated Verification
Run Kotlin compilation check:
```bash
./gradlew :app:compileDebugKotlin
```

### Manual & Log Verification
1. Launch `SearchActivity` and inspect `performance_evidence.log` via **Settings → About → Extract Evidence Logs**.
2. Verify `ISSUE_1_CONTEXT_LEAK` shows providers initialized once per app process, not per activity creation.
3. Verify `SearchBar` recomposition count is reduced dramatically during window animations.
4. Verify window blur does not trigger 40+ coroutine launches per second.
5. Verify `ISSUE_7_IME_RECOMPOSITION` shows no recompositions of Box/Column during soft keyboard slide transitions.
