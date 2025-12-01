package com.mrndstvndv.search.ui.settings

import android.graphics.Bitmap
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.settings.Quicklink
import com.mrndstvndv.search.provider.settings.WebSearchSettings
import com.mrndstvndv.search.provider.settings.WebSearchSite
import com.mrndstvndv.search.ui.components.ScrimDialog
import com.mrndstvndv.search.util.FaviconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun WebSearchSettingsScreen(
    initialSettings: WebSearchSettings,
    onBack: () -> Unit,
    onSave: (WebSearchSettings) -> Unit
) {
    var sites by remember { mutableStateOf(initialSettings.sites) }
    var defaultSiteId by remember { mutableStateOf(initialSettings.defaultSiteId) }
    var quicklinks by remember { mutableStateOf(initialSettings.quicklinks) }
    var editingSite by remember { mutableStateOf<Pair<Int, WebSearchSite>?>(null) }
    var editingQuicklink by remember { mutableStateOf<Pair<Int, Quicklink>?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }
    var isAddQuicklinkDialogOpen by remember { mutableStateOf(false) }
    val placeholder = WebSearchSettings.QUERY_PLACEHOLDER
    val context = LocalContext.current

    LaunchedEffect(initialSettings) {
        sites = initialSettings.sites
        defaultSiteId = initialSettings.defaultSiteId
        quicklinks = initialSettings.quicklinks
    }

    val allTemplatesValid = sites.all { it.urlTemplate.contains(placeholder) }

    fun saveSettings() {
        val resolvedDefault = sites.firstOrNull { it.id == defaultSiteId }?.id
            ?: sites.firstOrNull()?.id
            ?: ""
        if (resolvedDefault.isNotBlank() && allTemplatesValid) {
            onSave(WebSearchSettings(resolvedDefault, sites, quicklinks))
        }
    }

    fun updateSite(index: Int, updater: (WebSearchSite) -> WebSearchSite) {
        val mutable = sites.toMutableList()
        mutable[index] = updater(mutable[index])
        sites = mutable
        saveSettings()
    }

    fun removeSite(index: Int) {
        if (sites.size <= 1) return
        val removed = sites[index]
        val mutable = sites.toMutableList().also { it.removeAt(index) }
        sites = mutable
        if (defaultSiteId == removed.id) {
            defaultSiteId = mutable.firstOrNull()?.id.orEmpty()
        }
        saveSettings()
    }

    fun addCustomSite(name: String, template: String, onError: (String) -> Unit): Boolean {
        val trimmedName = name.trim()
        val trimmedTemplate = template.trim()
        if (trimmedName.isBlank() || trimmedTemplate.isBlank()) {
            onError("Name and template are required")
            return false
        }
        if (!trimmedTemplate.contains(placeholder)) {
            onError("Template must include $placeholder")
            return false
        }
        val candidateId = trimmedName.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .ifBlank { trimmedName.lowercase() }
        if (sites.any { it.id == candidateId }) {
            onError("A site with that name already exists")
            return false
        }
        val newSite = WebSearchSite(id = candidateId, displayName = trimmedName, urlTemplate = trimmedTemplate)
        sites = sites + newSite
        saveSettings()
        return true
    }

    fun addQuicklink(quicklink: Quicklink) {
        quicklinks = quicklinks + quicklink
        saveSettings()
    }

    fun updateQuicklink(index: Int, quicklink: Quicklink) {
        val mutable = quicklinks.toMutableList()
        mutable[index] = quicklink
        quicklinks = mutable
        saveSettings()
    }

    fun removeQuicklink(index: Int) {
        val removed = quicklinks[index]
        FaviconLoader.deleteFavicon(context, removed.id)
        val mutable = quicklinks.toMutableList().also { it.removeAt(index) }
        quicklinks = mutable
        saveSettings()
    }

    // Edit site dialog
    editingSite?.let { (index, site) ->
        WebSearchSiteEditDialog(
            site = site,
            canRemove = sites.size > 1,
            onDismiss = { editingSite = null },
            onSave = { updatedSite ->
                updateSite(index) { updatedSite }
                editingSite = null
            },
            onRemove = {
                removeSite(index)
                editingSite = null
            }
        )
    }

    // Add site dialog
    if (isAddDialogOpen) {
        WebSearchSiteAddDialog(
            placeholder = placeholder,
            onDismiss = { isAddDialogOpen = false },
            onAdd = { name, template, onError ->
                if (addCustomSite(name, template, onError)) {
                    isAddDialogOpen = false
                }
            }
        )
    }

    // Add quicklink dialog
    if (isAddQuicklinkDialogOpen) {
        QuicklinkAddDialog(
            onDismiss = { isAddQuicklinkDialogOpen = false },
            onAdd = { quicklink ->
                addQuicklink(quicklink)
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
                updateQuicklink(index, updated)
                editingQuicklink = null
            },
            onRemove = {
                removeQuicklink(index)
                editingQuicklink = null
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                WebSearchHeader(onBack = onBack)
            }

            // Quicklinks section
            item {
                SettingsSection(
                    title = "Quicklinks",
                    subtitle = "Direct links to your favorite sites."
                ) {
                    SettingsCardGroup {
                        if (quicklinks.isEmpty()) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "Add Quicklink")
                        }
                    }
                }
            }

            // Search Engines section
            item {
                SettingsSection(
                    title = "Search Engines",
                    subtitle = "Manage your search providers. Use $placeholder as the query placeholder."
                ) {
                    SettingsCardGroup {
                        sites.forEachIndexed { index, site ->
                            WebSearchSiteRow(
                                site = site,
                                isDefault = defaultSiteId == site.id,
                                onSetDefault = {
                                    defaultSiteId = site.id
                                    saveSettings()
                                },
                                onEdit = { editingSite = index to site }
                            )
                            if (index < sites.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        TextButton(
                            onClick = { isAddDialogOpen = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(text = "Add Search Engine")
                        }
                    }
                }
            }

            if (!allTemplatesValid) {
                item {
                    Text(
                        text = "Every template needs $placeholder",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WebSearchHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Web Search",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Configure search providers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

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
        } else {
            favicon = null
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

@Composable
private fun QuicklinkAddDialog(
    onDismiss: () -> Unit,
    onAdd: (Quicklink) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFetchingFavicon by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var fetchedFavicon by remember { mutableStateOf<Bitmap?>(null) }
    var quicklinkId by remember { mutableStateOf(UUID.randomUUID().toString()) }
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
    val canFetchFavicon = isValidUrl

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun fetchFavicon() {
        if (!canFetchFavicon) return

        val normalizedUrl = normalizeUrl(url)
        isFetchingFavicon = true
        errorMessage = null

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                FaviconLoader.fetchFavicon(normalizedUrl, context)
            }

            if (bitmap != null) {
                val saved = withContext(Dispatchers.IO) {
                    FaviconLoader.saveFavicon(context, quicklinkId, bitmap)
                }
                if (saved) {
                    fetchedFavicon = bitmap
                }
            }

            isFetchingFavicon = false
        }
    }

    fun cancelFaviconFetch() {
        isFetchingFavicon = false
    }

    fun save() {
        if (!canSave) return

        val normalizedUrl = normalizeUrl(url)

        // If favicon not yet fetched, fetch it first
        if (fetchedFavicon == null) {
            isSaving = true
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
                isSaving = false

                val quicklink = Quicklink(
                    id = quicklinkId,
                    title = title.trim(),
                    url = normalizedUrl,
                    hasFavicon = hasFavicon
                )
                onAdd(quicklink)
            }
        } else {
            // Favicon already fetched
            val quicklink = Quicklink(
                id = quicklinkId,
                title = title.trim(),
                url = normalizedUrl,
                hasFavicon = true
            )
            onAdd(quicklink)
        }
    }

    fun skipAndSave() {
        val normalizedUrl = normalizeUrl(url)
        // Delete any previously fetched favicon
        FaviconLoader.deleteFavicon(context, quicklinkId)
        val quicklink = Quicklink(
            id = quicklinkId,
            title = title.trim(),
            url = normalizedUrl,
            hasFavicon = false
        )
        onAdd(quicklink)
    }

    // Cancelable loading dialog for favicon fetch
    if (isFetchingFavicon) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss by tapping outside */ },
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
                    if (isSaving) {
                        // If we were saving, skip and save without favicon
                        isFetchingFavicon = false
                        isSaving = false
                        skipAndSave()
                    } else {
                        // Just cancel the fetch preview
                        cancelFaviconFetch()
                    }
                }) {
                    Text(if (isSaving) "Skip" else "Cancel")
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
                onValueChange = { 
                    url = it
                    // Reset favicon if URL changes
                    if (fetchedFavicon != null) {
                        FaviconLoader.deleteFavicon(context, quicklinkId)
                        fetchedFavicon = null
                        quicklinkId = UUID.randomUUID().toString()
                    }
                },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Favicon section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favicon preview
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (fetchedFavicon != null) {
                        Image(
                            bitmap = fetchedFavicon!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (fetchedFavicon != null) "Favicon loaded" else "No favicon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (fetchedFavicon != null) "Tap to refresh" else "Tap to fetch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { fetchFavicon() },
                    enabled = canFetchFavicon
                ) {
                    Text(if (fetchedFavicon != null) "Refresh" else "Fetch")
                }
            }

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

@Composable
private fun QuicklinkEditDialog(
    quicklink: Quicklink,
    onDismiss: () -> Unit,
    onSave: (Quicklink) -> Unit,
    onRemove: () -> Unit
) {
    var title by remember { mutableStateOf(quicklink.title) }
    var url by remember { mutableStateOf(quicklink.url) }
    var isFetchingFavicon by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var currentHasFavicon by remember { mutableStateOf(quicklink.hasFavicon) }
    var fetchedFavicon by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load existing favicon on first composition
    LaunchedEffect(quicklink.id, quicklink.hasFavicon) {
        if (quicklink.hasFavicon) {
            fetchedFavicon = withContext(Dispatchers.IO) {
                FaviconLoader.loadFavicon(context, quicklink.id)
            }
        }
    }

    val isValidUrl = remember(url) {
        val trimmed = url.trim()
        trimmed.isNotBlank() && (
            Patterns.WEB_URL.matcher(trimmed).matches() ||
            Patterns.WEB_URL.matcher("https://$trimmed").matches()
        )
    }
    val canSave = title.isNotBlank() && isValidUrl
    val canFetchFavicon = isValidUrl

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    fun fetchFavicon() {
        if (!canFetchFavicon) return

        val normalizedUrl = normalizeUrl(url)
        isFetchingFavicon = true

        scope.launch {
            // Delete old favicon first
            withContext(Dispatchers.IO) {
                FaviconLoader.deleteFavicon(context, quicklink.id)
            }

            val bitmap = withContext(Dispatchers.IO) {
                FaviconLoader.fetchFavicon(normalizedUrl, context)
            }

            if (bitmap != null) {
                val saved = withContext(Dispatchers.IO) {
                    FaviconLoader.saveFavicon(context, quicklink.id, bitmap)
                }
                if (saved) {
                    fetchedFavicon = bitmap
                    currentHasFavicon = true
                }
            } else {
                fetchedFavicon = null
                currentHasFavicon = false
            }

            isFetchingFavicon = false
        }
    }

    fun cancelFaviconFetch() {
        isFetchingFavicon = false
    }

    fun save() {
        if (!canSave) return

        val normalizedUrl = normalizeUrl(url)
        val urlChanged = normalizedUrl != quicklink.url

        // If URL changed and no favicon fetched yet, fetch it
        if (urlChanged && fetchedFavicon == null) {
            isSaving = true
            isFetchingFavicon = true

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
                isSaving = false

                onSave(quicklink.copy(
                    title = title.trim(),
                    url = normalizedUrl,
                    hasFavicon = newHasFavicon
                ))
            }
        } else {
            onSave(quicklink.copy(
                title = title.trim(),
                url = normalizedUrl,
                hasFavicon = currentHasFavicon
            ))
        }
    }

    fun skipAndSave() {
        val normalizedUrl = normalizeUrl(url)
        // Delete favicon if URL changed
        if (normalizedUrl != quicklink.url) {
            FaviconLoader.deleteFavicon(context, quicklink.id)
        }
        onSave(quicklink.copy(
            title = title.trim(),
            url = normalizedUrl,
            hasFavicon = false
        ))
    }

    // Cancelable loading dialog for favicon fetch
    if (isFetchingFavicon) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismiss by tapping outside */ },
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
                    if (isSaving) {
                        isFetchingFavicon = false
                        isSaving = false
                        skipAndSave()
                    } else {
                        cancelFaviconFetch()
                    }
                }) {
                    Text(if (isSaving) "Skip" else "Cancel")
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
                onValueChange = { newUrl ->
                    url = newUrl
                    // Reset favicon state if URL changes
                    val normalizedNew = normalizeUrl(newUrl)
                    if (normalizedNew != quicklink.url) {
                        fetchedFavicon = null
                        currentHasFavicon = false
                    }
                },
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

            Spacer(modifier = Modifier.height(16.dp))

            // Favicon section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Favicon preview
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (fetchedFavicon != null) {
                        Image(
                            bitmap = fetchedFavicon!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (fetchedFavicon != null) "Favicon loaded" else "No favicon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (fetchedFavicon != null) "Tap to refresh" else "Tap to fetch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = { fetchFavicon() },
                    enabled = canFetchFavicon
                ) {
                    Text(if (fetchedFavicon != null) "Refresh" else "Fetch")
                }
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

@Composable
private fun WebSearchSiteRow(
    site: WebSearchSite,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tap area with display name and URL template
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = site.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = site.urlTemplate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Vertical divider
        VerticalDivider(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // RadioButton for setting default
        RadioButton(
            selected = isDefault,
            onClick = onSetDefault,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).padding(end = 12.dp)
        )
    }
}

@Composable
private fun WebSearchSiteEditDialog(
    site: WebSearchSite,
    canRemove: Boolean,
    onDismiss: () -> Unit,
    onSave: (WebSearchSite) -> Unit,
    onRemove: () -> Unit
) {
    var displayName by remember { mutableStateOf(site.displayName) }
    var urlTemplate by remember { mutableStateOf(site.urlTemplate) }
    val placeholder = WebSearchSettings.QUERY_PLACEHOLDER
    val isValid = displayName.isNotBlank() && urlTemplate.contains(placeholder)

    ScrimDialog(
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Edit Search Engine",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = urlTemplate,
                onValueChange = { urlTemplate = it },
                label = { Text("URL template") },
                supportingText = { Text(text = "Include $placeholder") },
                isError = !urlTemplate.contains(placeholder),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            if (!urlTemplate.contains(placeholder)) {
                Text(
                    text = "Missing $placeholder",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canRemove) {
                    TextButton(
                        onClick = onRemove
                    ) {
                        Text(
                            text = "Remove",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(site.copy(displayName = displayName.trim(), urlTemplate = urlTemplate.trim()))
                        },
                        enabled = isValid
                    ) {
                        Text(text = "Save")
                    }
                }
            }
        }
    }
}


@Composable
private fun WebSearchSiteAddDialog(
    placeholder: String,
    onDismiss: () -> Unit,
    onAdd: (name: String, template: String, onError: (String) -> Unit) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var urlTemplate by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val isValid = displayName.isNotBlank() && urlTemplate.contains(placeholder)

    ScrimDialog(
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Add Search Engine",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.height(12.dp))

            TextField(
                value = urlTemplate,
                onValueChange = { urlTemplate = it },
                label = { Text("URL template") },
                supportingText = { Text(text = "Include $placeholder") },
                isError = urlTemplate.isNotBlank() && !urlTemplate.contains(placeholder),
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )

            if (urlTemplate.isNotBlank() && !urlTemplate.contains(placeholder)) {
                Text(
                    text = "Missing $placeholder",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

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
                    Text(text = "Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onAdd(displayName, urlTemplate) { error ->
                            errorMessage = error
                        }
                    },
                    enabled = isValid
                ) {
                    Text(text = "Add")
                }
            }
        }
    }
}


@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(content = content)
    }
}
