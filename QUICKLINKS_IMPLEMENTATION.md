# Quicklinks Feature Implementation Plan

## Overview

Add quicklinks (direct website bookmarks) to the Web Search provider. Quicklinks are fuzzy-matched against title and domain, prioritized over search engine results, and display favicons fetched from the web.

---

## Files to Create

| File | Purpose |
|------|---------|
| `app/src/main/java/com/mrndstvndv/search/util/FaviconLoader.kt` | Fetch favicons from URLs, save to internal storage |

## Files to Modify

| File | Changes |
|------|---------|
| `ProviderSettingsRepository.kt` | Add `Quicklink` data class, add `quicklinks` field to `WebSearchSettings` |
| `WebSearchProvider.kt` | Add quicklink matching logic, prioritize over search results |
| `WebSearchSettingsScreen.kt` | Add Quicklinks section, add/edit dialogs with favicon fetch |
| `AliasDefinition.kt` | Add `QuicklinkAliasTarget` for alias support |

---

## Detailed Implementation

### 1. FaviconLoader.kt (New File)

**Location:** `app/src/main/java/com/mrndstvndv/search/util/FaviconLoader.kt`

```kotlin
package com.mrndstvndv.search.util

object FaviconLoader {
    // Fetch favicon from Google's service
    // URL: https://www.google.com/s2/favicons?domain={domain}&sz=64
    
    suspend fun fetchFavicon(url: String, context: Context): Bitmap?
    
    // Save bitmap to internal storage: files/favicons/{id}.png
    fun saveFavicon(context: Context, id: String, bitmap: Bitmap): Boolean
    
    // Load favicon from storage
    suspend fun loadFavicon(context: Context, id: String): Bitmap?
    
    // Delete favicon file
    fun deleteFavicon(context: Context, id: String)
    
    // Extract domain from URL for favicon lookup
    fun extractDomain(url: String): String?
}
```

**Implementation details:**
- Use `HttpURLConnection` or `URL.openStream()` to fetch the favicon
- Save as PNG to `context.filesDir/favicons/{id}.png`
- Use `BitmapFactory.decodeFile()` to load
- Run network operations on `Dispatchers.IO`

---

### 2. ProviderSettingsRepository.kt Changes

**Add `Quicklink` data class (after `WebSearchSite`):**

```kotlin
data class Quicklink(
    val id: String,           // UUID
    val title: String,        // User-provided title
    val url: String,          // Full URL (always starts with http:// or https://)
    val hasFavicon: Boolean = false  // Whether favicon was successfully fetched
) {
    /**
     * Returns the URL without the protocol for display.
     * Example: "https://github.com/user/repo" -> "github.com/user/repo"
     */
    fun displayUrl(): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
    }

    /**
     * Extracts the domain from the URL for matching.
     * Example: "https://github.com/user/repo" -> "github.com"
     */
    fun domain(): String {
        val withoutProtocol = url
            .removePrefix("https://")
            .removePrefix("http://")
        return withoutProtocol.substringBefore("/").substringBefore("?")
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("url", url)
            put("hasFavicon", hasFavicon)
        }
    }

    companion object {
        fun fromJson(json: JSONObject?): Quicklink? {
            if (json == null) return null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
            val url = json.optString("url").takeIf { it.isNotBlank() } ?: return null
            val hasFavicon = json.optBoolean("hasFavicon", false)
            return Quicklink(id = id, title = title, url = url, hasFavicon = hasFavicon)
        }
    }
}
```

**Extend `WebSearchSettings`:**

Add `quicklinks` parameter:
```kotlin
data class WebSearchSettings(
    val defaultSiteId: String,
    val sites: List<WebSearchSite>,
    val quicklinks: List<Quicklink> = emptyList()  // NEW FIELD
)
```

Update `default()`:
```kotlin
fun default(): WebSearchSettings {
    return WebSearchSettings(
        defaultSiteId = DEFAULT_SITES.first().id,
        sites = DEFAULT_SITES,
        quicklinks = emptyList()
    )
}
```

Update `fromJson()`:
```kotlin
fun fromJson(json: JSONObject?): WebSearchSettings? {
    if (json == null) return null
    // ... existing sites parsing ...
    
    // Parse quicklinks
    val quicklinksArray = json.optJSONArray("quicklinks") ?: JSONArray()
    val quicklinks = mutableListOf<Quicklink>()
    for (i in 0 until quicklinksArray.length()) {
        Quicklink.fromJson(quicklinksArray.optJSONObject(i))?.let { quicklinks.add(it) }
    }
    
    return WebSearchSettings(
        defaultSiteId = defaultId,
        sites = sites,
        quicklinks = quicklinks
    )
}
```

Update `toJson()`:
```kotlin
fun toJson(): JSONObject {
    val root = JSONObject()
    root.put("defaultSiteId", defaultSiteId)
    
    val sitesArray = JSONArray()
    sites.forEach { sitesArray.put(it.toJson()) }
    root.put("sites", sitesArray)
    
    val quicklinksArray = JSONArray()
    quicklinks.forEach { quicklinksArray.put(it.toJson()) }
    root.put("quicklinks", quicklinksArray)
    
    return root
}
```

**Add helper methods to `ProviderSettingsRepository`:**

```kotlin
fun addQuicklink(quicklink: Quicklink) {
    val current = _webSearchSettings.value
    val updated = current.copy(quicklinks = current.quicklinks + quicklink)
    saveWebSearchSettings(updated)
}

fun updateQuicklink(quicklink: Quicklink) {
    val current = _webSearchSettings.value
    val updatedQuicklinks = current.quicklinks.map {
        if (it.id == quicklink.id) quicklink else it
    }
    saveWebSearchSettings(current.copy(quicklinks = updatedQuicklinks))
}

fun removeQuicklink(quicklinkId: String) {
    val current = _webSearchSettings.value
    val updatedQuicklinks = current.quicklinks.filterNot { it.id == quicklinkId }
    saveWebSearchSettings(current.copy(quicklinks = updatedQuicklinks))
}
```

---

### 3. WebSearchProvider.kt Changes

**Add context reference for favicon loading:**

The provider already has `activity: ComponentActivity` which provides context.

**Update `query()` method:**

```kotlin
override suspend fun query(query: Query): List<ProviderResult> {
    val cleaned = query.trimmedText
    if (cleaned.isBlank()) return emptyList()

    val settings = settingsRepository.webSearchSettings.value
    
    // 1. Match quicklinks first (prioritized)
    val quicklinkResults = matchQuicklinks(cleaned, settings.quicklinks)
    
    // 2. Match search engines (existing logic, extract to separate function)
    val searchResults = matchSearchEngines(query, cleaned, settings)
    
    return quicklinkResults + searchResults
}
```

**Add `matchQuicklinks()` function:**

```kotlin
private suspend fun matchQuicklinks(
    query: String,
    quicklinks: List<Quicklink>
): List<ProviderResult> {
    if (quicklinks.isEmpty()) return emptyList()
    
    data class ScoredQuicklink(
        val quicklink: Quicklink,
        val score: Int,
        val matchedTitleIndices: List<Int>,
        val matchedSubtitleIndices: List<Int>
    )
    
    val scored = quicklinks.mapNotNull { quicklink ->
        val titleMatch = FuzzyMatcher.match(query, quicklink.title)
        val domainMatch = FuzzyMatcher.match(query, quicklink.domain())
        
        // Apply penalty to domain matches (like AppListProvider does for package names)
        val domainScoreWithPenalty = domainMatch?.let { it.score - DOMAIN_MATCH_PENALTY }
        
        val titleIsBest = when {
            titleMatch == null -> false
            domainScoreWithPenalty == null -> true
            else -> titleMatch.score >= domainScoreWithPenalty
        }
        
        when {
            titleIsBest && titleMatch != null -> ScoredQuicklink(
                quicklink = quicklink,
                score = titleMatch.score,
                matchedTitleIndices = titleMatch.matchedIndices,
                matchedSubtitleIndices = domainMatch?.matchedIndices ?: emptyList()
            )
            domainMatch != null -> ScoredQuicklink(
                quicklink = quicklink,
                score = domainScoreWithPenalty!!,
                matchedTitleIndices = emptyList(),
                matchedSubtitleIndices = domainMatch.matchedIndices
            )
            else -> null
        }
    }.sortedByDescending { it.score }
    
    return scored.map { (quicklink, _, matchedTitleIndices, matchedSubtitleIndices) ->
        val action: suspend () -> Unit = {
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW, quicklink.url.toUri())
                activity.startActivity(intent)
                activity.finish()
            }
        }
        
        ProviderResult(
            id = "$id:quicklink:${quicklink.id}",
            title = quicklink.title,
            subtitle = quicklink.displayUrl(),
            defaultVectorIcon = Icons.Outlined.Link,
            iconLoader = if (quicklink.hasFavicon) {
                { FaviconLoader.loadFavicon(activity, quicklink.id) }
            } else null,
            providerId = id,
            onSelect = action,
            aliasTarget = QuicklinkAliasTarget(quicklink.id, quicklink.title),
            keepOverlayUntilExit = true,
            matchedTitleIndices = matchedTitleIndices,
            matchedSubtitleIndices = matchedSubtitleIndices
        )
    }
}

private companion object {
    const val DOMAIN_MATCH_PENALTY = 10
}
```

**Extract existing search engine logic to `matchSearchEngines()`:**

Move the existing search engine matching code from `query()` into a separate private function called `matchSearchEngines()` that returns `List<ProviderResult>`.

**Add required imports:**
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import com.mrndstvndv.search.alias.QuicklinkAliasTarget
```

---

### 4. WebSearchSettingsScreen.kt Changes

**Add Quicklinks section in `WebSearchSettingsScreen` composable:**

Insert a new section BEFORE the "Search Engines" section:

```kotlin
item {
    SettingsSection(
        title = "Quicklinks",
        subtitle = "Direct links to your favorite sites."
    ) {
        SettingsCardGroup {
            if (quicklinks.isEmpty()) {
                // Empty state
                Text(
                    text = "No quicklinks yet. Add your favorite sites for quick access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                quicklinks.forEachIndexed { index, quicklink ->
                    QuicklinkRow(
                        quicklink = quicklink,
                        onClick = { editingQuicklink = index to quicklink }
                    )
                    if (index < quicklinks.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            TextButton(
                onClick = { isAddQuicklinkDialogOpen = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "Add Quicklink")
            }
        }
    }
}
```

**Add state variables:**

```kotlin
var quicklinks by remember { mutableStateOf(initialSettings.quicklinks) }
var editingQuicklink by remember { mutableStateOf<Pair<Int, Quicklink>?>(null) }
var isAddQuicklinkDialogOpen by remember { mutableStateOf(false) }
```

Update `LaunchedEffect` to include quicklinks:
```kotlin
LaunchedEffect(initialSettings) {
    sites = initialSettings.sites
    defaultSiteId = initialSettings.defaultSiteId
    quicklinks = initialSettings.quicklinks
}
```

Update `saveSettings()` to include quicklinks:
```kotlin
fun saveSettings() {
    // ... existing validation ...
    if (resolvedDefault.isNotBlank() && allTemplatesValid) {
        onSave(WebSearchSettings(resolvedDefault, sites, quicklinks))
    }
}
```

**Add `QuicklinkRow` composable:**

```kotlin
@Composable
private fun QuicklinkRow(
    quicklink: Quicklink,
    onClick: () -> Unit
) {
    var favicon by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    
    LaunchedEffect(quicklink.id, quicklink.hasFavicon) {
        if (quicklink.hasFavicon) {
            favicon = withContext(Dispatchers.IO) {
                FaviconLoader.loadFavicon(context, quicklink.id)
            }
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favicon or fallback icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (favicon != null) {
                Image(
                    bitmap = favicon!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quicklink.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = quicklink.displayUrl(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
```

**Add `QuicklinkAddDialog` composable:**

```kotlin
@Composable
private fun QuicklinkAddDialog(
    onDismiss: () -> Unit,
    onAdd: (Quicklink) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFetchingFavicon by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val isValidUrl = remember(url) {
        val trimmed = url.trim()
        trimmed.isNotBlank() && (
            Patterns.WEB_URL.matcher(trimmed).matches() ||
            Patterns.WEB_URL.matcher("https://$trimmed").matches()
        )
    }
    val canSave = title.isNotBlank() && isValidUrl
    
    // Normalize URL (prepend https:// if needed)
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }
    
    fun save() {
        if (!canSave) return
        
        val normalizedUrl = normalizeUrl(url)
        val quicklinkId = java.util.UUID.randomUUID().toString()
        
        isFetchingFavicon = true
        errorMessage = null
        
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                FaviconLoader.fetchFavicon(normalizedUrl, context)
            }
            
            val hasFavicon = if (bitmap != null) {
                withContext(Dispatchers.IO) {
                    FaviconLoader.saveFavicon(context, quicklinkId, bitmap)
                }
            } else {
                false
            }
            
            isFetchingFavicon = false
            
            val quicklink = Quicklink(
                id = quicklinkId,
                title = title.trim(),
                url = normalizedUrl,
                hasFavicon = hasFavicon
            )
            onAdd(quicklink)
        }
    }
    
    // Cancelable loading dialog for favicon fetch
    if (isFetchingFavicon) {
        AlertDialog(
            onDismissRequest = { isFetchingFavicon = false },
            title = { Text("Fetching Favicon") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Loading favicon...")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    isFetchingFavicon = false
                    // Save without favicon
                    val normalizedUrl = normalizeUrl(url)
                    val quicklinkId = java.util.UUID.randomUUID().toString()
                    val quicklink = Quicklink(
                        id = quicklinkId,
                        title = title.trim(),
                        url = normalizedUrl,
                        hasFavicon = false
                    )
                    onAdd(quicklink)
                }) {
                    Text("Skip")
                }
            }
        )
        return
    }
    
    ScrimDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Add Quicklink",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                placeholder = { Text("example.com") },
                singleLine = true,
                isError = url.isNotBlank() && !isValidUrl,
                supportingText = if (url.isNotBlank() && !isValidUrl) {
                    { Text("Please enter a valid URL") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { save() },
                    enabled = canSave
                ) {
                    Text("Save")
                }
            }
        }
    }
}
```

**Add `QuicklinkEditDialog` composable:**

```kotlin
@Composable
private fun QuicklinkEditDialog(
    quicklink: Quicklink,
    onDismiss: () -> Unit,
    onSave: (Quicklink) -> Unit,
    onRemove: () -> Unit
) {
    var title by remember { mutableStateOf(quicklink.title) }
    var url by remember { mutableStateOf(quicklink.url) }
    var hasFavicon by remember { mutableStateOf(quicklink.hasFavicon) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFetchingFavicon by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val isValidUrl = remember(url) {
        val trimmed = url.trim()
        trimmed.isNotBlank() && (
            Patterns.WEB_URL.matcher(trimmed).matches() ||
            Patterns.WEB_URL.matcher("https://$trimmed").matches()
        )
    }
    val canSave = title.isNotBlank() && isValidUrl
    
    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }
    
    fun save() {
        if (!canSave) return
        
        val normalizedUrl = normalizeUrl(url)
        val urlChanged = normalizedUrl != quicklink.url
        
        // If URL changed, refetch favicon
        if (urlChanged) {
            isFetchingFavicon = true
            errorMessage = null
            
            scope.launch {
                // Delete old favicon
                withContext(Dispatchers.IO) {
                    FaviconLoader.deleteFavicon(context, quicklink.id)
                }
                
                val bitmap = withContext(Dispatchers.IO) {
                    FaviconLoader.fetchFavicon(normalizedUrl, context)
                }
                
                val newHasFavicon = if (bitmap != null) {
                    withContext(Dispatchers.IO) {
                        FaviconLoader.saveFavicon(context, quicklink.id, bitmap)
                    }
                } else {
                    false
                }
                
                isFetchingFavicon = false
                
                onSave(quicklink.copy(
                    title = title.trim(),
                    url = normalizedUrl,
                    hasFavicon = newHasFavicon
                ))
            }
        } else {
            onSave(quicklink.copy(
                title = title.trim(),
                url = normalizedUrl
            ))
        }
    }
    
    // Cancelable loading dialog for favicon fetch
    if (isFetchingFavicon) {
        AlertDialog(
            onDismissRequest = { isFetchingFavicon = false },
            title = { Text("Fetching Favicon") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Loading favicon...")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    isFetchingFavicon = false
                    // Save without favicon
                    val normalizedUrl = normalizeUrl(url)
                    onSave(quicklink.copy(
                        title = title.trim(),
                        url = normalizedUrl,
                        hasFavicon = false
                    ))
                }) {
                    Text("Skip")
                }
            }
        )
        return
    }
    
    ScrimDialog(onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Edit Quicklink",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            TextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            TextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL") },
                singleLine = true,
                isError = url.isNotBlank() && !isValidUrl,
                supportingText = if (url.isNotBlank() && !isValidUrl) {
                    { Text("Please enter a valid URL") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRemove) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { save() },
                        enabled = canSave
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
```

**Add dialog handlers in `WebSearchSettingsScreen`:**

```kotlin
// Add quicklink dialog
if (isAddQuicklinkDialogOpen) {
    QuicklinkAddDialog(
        onDismiss = { isAddQuicklinkDialogOpen = false },
        onAdd = { quicklink ->
            quicklinks = quicklinks + quicklink
            saveSettings()
            isAddQuicklinkDialogOpen = false
        }
    )
}

// Edit quicklink dialog
editingQuicklink?.let { (index, quicklink) ->
    QuicklinkEditDialog(
        quicklink = quicklink,
        onDismiss = { editingQuicklink = null },
        onSave = { updated ->
            val mutable = quicklinks.toMutableList()
            mutable[index] = updated
            quicklinks = mutable
            saveSettings()
            editingQuicklink = null
        },
        onRemove = {
            // Delete favicon file
            FaviconLoader.deleteFavicon(context, quicklink.id)
            val mutable = quicklinks.toMutableList()
            mutable.removeAt(index)
            quicklinks = mutable
            saveSettings()
            editingQuicklink = null
        }
    )
}
```

**Additional imports needed:**
```kotlin
import android.graphics.Bitmap
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.mrndstvndv.search.provider.settings.Quicklink
import com.mrndstvndv.search.util.FaviconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
```

---

### 5. AliasDefinition.kt Changes

**Add `QuicklinkAliasTarget` data class:**

```kotlin
data class QuicklinkAliasTarget(
    val quicklinkId: String,
    val title: String
) : AliasTarget {
    override val providerId: String = "web-search"
    override val summary: String
        get() = title

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", TYPE)
            put("quicklinkId", quicklinkId)
            put("title", title)
        }
    }

    companion object {
        private const val TYPE = "quicklink"

        fun fromJson(json: JSONObject?): QuicklinkAliasTarget? {
            if (json == null) return null
            val quicklinkId = json.optString("quicklinkId").takeIf { it.isNotBlank() } ?: return null
            val title = json.optString("title").takeIf { it.isNotBlank() } ?: return null
            return QuicklinkAliasTarget(quicklinkId, title)
        }
    }
}
```

**Update `AliasTarget.Companion.fromJson()`:**

```kotlin
companion object {
    private const val KEY_TYPE = "type"
    private const val TYPE_WEB_SEARCH = "web-search"
    private const val TYPE_APP_LAUNCH = "app-launch"
    private const val TYPE_QUICKLINK = "quicklink"  // NEW

    fun fromJson(json: JSONObject?): AliasTarget? {
        if (json == null) return null
        val type = json.optString(KEY_TYPE).takeIf { it.isNotBlank() } ?: return null
        return when (type) {
            TYPE_WEB_SEARCH -> WebSearchAliasTarget.fromJson(json)
            TYPE_APP_LAUNCH -> AppLaunchAliasTarget.fromJson(json)
            TYPE_QUICKLINK -> QuicklinkAliasTarget.fromJson(json)  // NEW
            else -> null
        }
    }
}
```

---

## Implementation Order

1. **Create `FaviconLoader.kt`** - New utility file for favicon operations
2. **Update `ProviderSettingsRepository.kt`** - Add `Quicklink` data class and settings
3. **Update `AliasDefinition.kt`** - Add `QuicklinkAliasTarget`
4. **Update `WebSearchProvider.kt`** - Add quicklink matching logic
5. **Update `WebSearchSettingsScreen.kt`** - Add UI for quicklinks management
6. **Compile & test** - Run `./gradlew :app:compileDebugKotlin`

---

## Key Behaviors

### URL Validation & Normalization
- Validate URLs using `Patterns.WEB_URL`
- Auto-prepend `https://` if user enters bare domain (e.g., `github.com` → `https://github.com`)
- Display cleaned URL without protocol in results and settings

### Favicon Fetching
- Use Google's favicon service: `https://www.google.com/s2/favicons?domain={domain}&sz=64`
- Show cancelable loading dialog when saving (user can skip to save without favicon)
- Store favicons as PNG files in `files/favicons/{id}.png`
- Fallback to `Icons.Outlined.Link` if no favicon

### Search Result Priority
- Quicklinks appear BEFORE search engine results
- Fuzzy match against title (full score) and domain (with -10 penalty)
- Support highlighting of matched characters in both title and subtitle

### Alias Support
- Users can long-press quicklink results to create aliases
- Aliases stored with `QuicklinkAliasTarget` type
