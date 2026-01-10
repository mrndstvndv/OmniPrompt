package com.mrndstvndv.search.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mrndstvndv.search.provider.files.FileSearchRepository
import com.mrndstvndv.search.provider.settings.FileSearchRoot
import com.mrndstvndv.search.provider.settings.FileSearchScanMetadata
import com.mrndstvndv.search.provider.settings.FileSearchScanState
import com.mrndstvndv.search.provider.settings.FileSearchSettings
import com.mrndstvndv.search.provider.settings.FileSearchSortMode
import com.mrndstvndv.search.provider.settings.FileSearchThumbnailCropMode
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun FileSearchSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    fileSearchRepository: FileSearchRepository,
    onBack: () -> Unit,
) {
    val fileSearchSettings by settingsRepository.fileSearchSettings.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val addFileRootLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                handleFolderSelection(
                    uri = uri,
                    context = context,
                    settingsRepository = settingsRepository,
                    fileSearchRepository = fileSearchRepository,
                )
            }
        }
    val downloadsMetadata = fileSearchSettings.scanMetadata[FileSearchSettings.DOWNLOADS_ROOT_ID]
    var downloadsPermissionGranted by remember { mutableStateOf(hasAllFilesAccess()) }
    var showDownloadsPermissionDialog by remember { mutableStateOf(false) }
    var pendingEnableDownloads by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun enableDownloadsIndexing() {
        coroutineScope.launch(Dispatchers.Default) {
            settingsRepository.setDownloadsIndexingEnabled(true)
            FileSearchRoot.downloadsRoot()?.let { root ->
                settingsRepository.updateFileSearchScanState(
                    rootId = root.id,
                    state = FileSearchScanState.INDEXING,
                    itemCount = 0,
                    errorMessage = null,
                )
                fileSearchRepository.scheduleFullIndex(root)
            }
        }
    }

    fun disableDownloadsIndexing() {
        coroutineScope.launch(Dispatchers.Default) {
            settingsRepository.setDownloadsIndexingEnabled(false)
            fileSearchRepository.deleteRootEntries(FileSearchSettings.DOWNLOADS_ROOT_ID)
        }
    }

    fun rescanDownloads() {
        coroutineScope.launch(Dispatchers.Default) {
            FileSearchRoot.downloadsRoot()?.let { root ->
                settingsRepository.updateFileSearchScanState(
                    rootId = root.id,
                    state = FileSearchScanState.INDEXING,
                    itemCount = 0,
                    errorMessage = null,
                )
                fileSearchRepository.scheduleFullIndex(root)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val granted = hasAllFilesAccess()
                    val previouslyPending = pendingEnableDownloads
                    downloadsPermissionGranted = granted
                    if (granted && previouslyPending) {
                        pendingEnableDownloads = false
                        enableDownloadsIndexing()
                    }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onToggleDownloads: (Boolean) -> Unit = { enabled ->
        if (enabled) {
            if (downloadsPermissionGranted || hasAllFilesAccess()) {
                downloadsPermissionGranted = true
                pendingEnableDownloads = false
                enableDownloadsIndexing()
            } else {
                pendingEnableDownloads = true
                showDownloadsPermissionDialog = true
            }
        } else {
            pendingEnableDownloads = false
            disableDownloadsIndexing()
        }
    }

    val manageAllFilesLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val granted = hasAllFilesAccess()
            downloadsPermissionGranted = granted
            if (granted && pendingEnableDownloads) {
                pendingEnableDownloads = false
                enableDownloadsIndexing()
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item {
                SettingsHeader(title = "Files & folders", subtitle = "Pick which directories are indexed.", onBack = onBack)
            }

            item {
                SettingsGroup {
                    SettingsSwitch(
                        title = "Load thumbnails",
                        subtitle = "Show previews for images, videos, and audio files in search results.",
                        checked = fileSearchSettings.loadThumbnails,
                        onCheckedChange = { settingsRepository.setFileSearchThumbnailsEnabled(it) },
                    )
                    SettingsDivider()
                    ThumbnailCropModeRow(
                        selectedMode = fileSearchSettings.thumbnailCropMode,
                        enabled = fileSearchSettings.loadThumbnails,
                        onModeSelected = { settingsRepository.setFileSearchThumbnailCropMode(it) },
                    )
                    SettingsDivider()
                    FileSearchSortRow(
                        sortMode = fileSearchSettings.sortMode,
                        sortAscending = fileSearchSettings.sortAscending,
                        onModeSelected = { settingsRepository.setFileSearchSortMode(it) },
                        onToggleAscending = { settingsRepository.setFileSearchSortAscending(it) },
                    )
                    SettingsDivider()
                    SyncIntervalRow(
                        selectedInterval = fileSearchSettings.syncIntervalMinutes,
                        onIntervalSelected = {
                            settingsRepository.setFileSearchSyncInterval(it)
                            fileSearchRepository.schedulePeriodicSync(it)
                        },
                    )
                    SettingsDivider()
                    SettingsSwitch(
                        title = "Sync on app open",
                        subtitle = "Check for file changes when Search opens.",
                        checked = fileSearchSettings.syncOnAppOpen,
                        onCheckedChange = { settingsRepository.setFileSearchSyncOnAppOpen(it) },
                    )
                    SettingsDivider()
                    FileSearchRootsCard(
                        settings = fileSearchSettings,
                        scanMetadata = fileSearchSettings.scanMetadata,
                        downloadsEnabled = fileSearchSettings.includeDownloads,
                        downloadsPermissionGranted = downloadsPermissionGranted,
                        downloadsMetadata = downloadsMetadata,
                        onToggleDownloads = onToggleDownloads,
                        onRescanDownloads = { rescanDownloads() },
                        onAddRoot = { addFileRootLauncher.launch(null) },
                        onToggleRoot = { root, enabled ->
                            settingsRepository.setFileSearchRootEnabled(root.id, enabled)
                        },
                        onRescanRoot = { root ->
                            settingsRepository.updateFileSearchScanState(root.id, FileSearchScanState.INDEXING)
                            fileSearchRepository.scheduleFullIndex(root)
                        },
                        onRemoveRoot = { root ->
                            settingsRepository.removeFileSearchRoot(root.id)
                            coroutineScope.launch(Dispatchers.IO) {
                                fileSearchRepository.deleteRootEntries(root.id)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showDownloadsPermissionDialog) {
        DownloadsPermissionDialog(
            onDismiss = {
                showDownloadsPermissionDialog = false
                pendingEnableDownloads = false
            },
            onOpenSettings = {
                showDownloadsPermissionDialog = false
                manageAllFilesLauncher.launch(buildManageAllFilesIntent(context))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailCropModeRow(
    selectedMode: FileSearchThumbnailCropMode,
    enabled: Boolean,
    onModeSelected: (FileSearchThumbnailCropMode) -> Unit,
) {
    val subtitle =
        if (enabled) {
            "Choose how previews fill the square icon."
        } else {
            "Turn on thumbnails to change how previews are cropped."
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = "Thumbnail crop",
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val options = listOf(FileSearchThumbnailCropMode.CENTER_CROP, FileSearchThumbnailCropMode.FIT)
        SingleChoiceSegmentedButtonRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        ) {
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selectedMode,
                    onClick = {
                        if (enabled && mode != selectedMode) {
                            onModeSelected(mode)
                        }
                    },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(text = mode.userFacingLabel())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSearchSortRow(
    sortMode: FileSearchSortMode,
    sortAscending: Boolean,
    onModeSelected: (FileSearchSortMode) -> Unit,
    onToggleAscending: (Boolean) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = "Sort order",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Choose how file results are ordered.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val options = listOf(FileSearchSortMode.NAME, FileSearchSortMode.DATE)
        SingleChoiceSegmentedButtonRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        ) {
            options.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == sortMode,
                    onClick = {
                        if (mode != sortMode) {
                            onModeSelected(mode)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(text = mode.userFacingLabel())
                }
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ascending order",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Off lists newest or Z–A first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = sortAscending,
                onCheckedChange = onToggleAscending,
            )
        }
    }
}

private fun FileSearchThumbnailCropMode.userFacingLabel(): String =
    when (this) {
        FileSearchThumbnailCropMode.FIT -> "Fit"
        FileSearchThumbnailCropMode.CENTER_CROP -> "Center crop"
    }

private fun FileSearchSortMode.userFacingLabel(): String =
    when (this) {
        FileSearchSortMode.DATE -> "Date modified"
        FileSearchSortMode.NAME -> "Name"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncIntervalRow(
    selectedInterval: Int,
    onIntervalSelected: (Int) -> Unit,
) {
    val options =
        listOf(
            0 to "Off",
            15 to "15m",
            30 to "30m",
            60 to "1h",
            120 to "2h",
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = "Background sync",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Automatically check for file changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        ) {
            options.forEachIndexed { index, (intervalMinutes, label) ->
                SegmentedButton(
                    selected = intervalMinutes == selectedInterval,
                    onClick = {
                        if (intervalMinutes != selectedInterval) {
                            onIntervalSelected(intervalMinutes)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(text = label)
                }
            }
        }
    }
}

@Composable
private fun FileSearchRootsCard(
    settings: FileSearchSettings,
    scanMetadata: Map<String, FileSearchScanMetadata>,
    downloadsEnabled: Boolean,
    downloadsPermissionGranted: Boolean,
    downloadsMetadata: FileSearchScanMetadata?,
    onToggleDownloads: (Boolean) -> Unit,
    onRescanDownloads: () -> Unit,
    onAddRoot: () -> Unit,
    onToggleRoot: (FileSearchRoot, Boolean) -> Unit,
    onRescanRoot: (FileSearchRoot) -> Unit,
    onRemoveRoot: (FileSearchRoot) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DownloadsIndexRow(
            enabled = downloadsEnabled,
            permissionGranted = downloadsPermissionGranted,
            metadata = downloadsMetadata,
            onToggle = onToggleDownloads,
            onRescan = onRescanDownloads,
        )
        val firstErroredRoot =
            settings.roots.firstNotNullOfOrNull { root ->
                val metadata = scanMetadata[root.id]
                if (metadata?.state == FileSearchScanState.ERROR) root to metadata else null
            }
        if (firstErroredRoot != null) {
            SettingsDivider()
            FileSearchErrorBanner(
                rootName = firstErroredRoot.first.displayName,
                metadata = firstErroredRoot.second,
            )
        }
        val duplicateNameIds =
            settings.roots
                .groupBy { it.displayName }
                .filterValues { it.size > 1 }
                .flatMap { entry -> entry.value.map(FileSearchRoot::id) }
                .toSet()
        if (settings.roots.isEmpty()) {
            SettingsDivider()
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                text = "No folders have been indexed yet. Tap Add folder to pick a directory.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SettingsDivider()
            settings.roots.forEachIndexed { index, root ->
                val displayName = formatRootDisplayName(root, duplicateNameIds.contains(root.id))
                FileSearchRootRow(
                    root = root,
                    displayName = displayName,
                    metadata = scanMetadata[root.id],
                    onToggle = { enabled -> onToggleRoot(root, enabled) },
                    onRescan = { onRescanRoot(root) },
                    onRemove = { onRemoveRoot(root) },
                )
                if (index != settings.roots.lastIndex) {
                    SettingsDivider()
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            onClick = onAddRoot,
        ) {
            Text(text = "Add folder")
        }
    }
}

private fun formatRootDisplayName(
    root: FileSearchRoot,
    requireParentLabel: Boolean,
): String {
    if (!requireParentLabel) return root.displayName
    val parent = root.parentDisplayName?.takeIf { it.isNotBlank() } ?: root.uri.deriveParentDisplayName()
    return if (parent.isNullOrBlank()) root.displayName else "${root.displayName} ($parent)"
}

@Composable
private fun FileSearchRootRow(
    root: FileSearchRoot,
    displayName: String,
    metadata: FileSearchScanMetadata?,
    onToggle: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val (status, detail) = resolveFileSearchStatus(root, metadata)
                val statusColor =
                    if (metadata?.state == FileSearchScanState.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
                if (metadata?.state == FileSearchScanState.INDEXING) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    )
                }
            }
            Switch(
                checked = root.isEnabled,
                onCheckedChange = onToggle,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onRescan,
                enabled = root.isEnabled,
            ) {
                Text(text = "Rescan")
            }
            TextButton(onClick = onRemove) {
                Text(text = "Remove")
            }
        }
    }
}

@Composable
private fun DownloadsIndexRow(
    enabled: Boolean,
    permissionGranted: Boolean,
    metadata: FileSearchScanMetadata?,
    onToggle: (Boolean) -> Unit,
    onRescan: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Downloads folder",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val (status, detail) = resolveDownloadsStatusText(enabled, permissionGranted, metadata)
                val statusColor: Color =
                    when {
                        !permissionGranted -> MaterialTheme.colorScheme.error
                        metadata?.state == FileSearchScanState.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor,
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
                val showProgress = permissionGranted && enabled && metadata?.state == FileSearchScanState.INDEXING
                if (showProgress) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = onRescan,
                enabled = enabled && permissionGranted,
            ) {
                Text(text = "Rescan")
            }
        }
    }
}

private fun resolveFileSearchStatus(
    root: FileSearchRoot,
    metadata: FileSearchScanMetadata?,
): Pair<String, String?> {
    if (!root.isEnabled) {
        return "Disabled" to "Enable to surface matches"
    }
    if (metadata == null) {
        return "Pending scan" to "Tap Rescan after granting storage access"
    }
    return when (metadata.state) {
        FileSearchScanState.INDEXING -> {
            val detail =
                if (metadata.indexedItemCount > 0) {
                    "${metadata.indexedItemCount} items scanned so far"
                } else {
                    "This may take a minute"
                }
            "Indexing…" to detail
        }

        FileSearchScanState.ERROR -> {
            "Index failed" to (metadata.errorMessage ?: "Check folder permissions")
        }

        FileSearchScanState.SUCCESS -> {
            val detail =
                if (metadata.updatedAtMillis > 0L) {
                    "Updated ${formatRelativeTime(metadata.updatedAtMillis)}"
                } else {
                    null
                }
            "Indexed ${metadata.indexedItemCount} items" to detail
        }

        FileSearchScanState.IDLE -> {
            val detail =
                if (metadata.updatedAtMillis > 0L) {
                    "Updated ${formatRelativeTime(metadata.updatedAtMillis)}"
                } else {
                    null
                }
            "Idle" to detail
        }
    }
}

private fun resolveDownloadsStatusText(
    enabled: Boolean,
    permissionGranted: Boolean,
    metadata: FileSearchScanMetadata?,
): Pair<String, String?> {
    if (!permissionGranted) {
        return "Permission required" to "Allow \"All files access\" to index Downloads."
    }
    if (!enabled) {
        return "Disabled" to "Turn on to include Downloads in search results."
    }
    val placeholderRoot =
        FileSearchRoot(
            id = FileSearchSettings.DOWNLOADS_ROOT_ID,
            uri = Uri.EMPTY,
            displayName = "Downloads",
            isEnabled = true,
            addedAtMillis = 0L,
            parentDisplayName = null,
        )
    return resolveFileSearchStatus(placeholderRoot, metadata)
}

@Composable
private fun DownloadsPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Allow Downloads access") },
        text = {
            Text(
                text = "Search needs Android's \"All files access\" permission to index the Downloads folder.",
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = "Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        },
    )
}

private fun buildManageAllFilesIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

private fun hasAllFilesAccess(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }

@Composable
private fun FileSearchErrorBanner(
    rootName: String,
    metadata: FileSearchScanMetadata,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Can't index $rootName",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = metadata.errorMessage ?: "Re-grant storage permission or try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = "Fix the issue, then tap Rescan below.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "just now"
    val relative =
        DateUtils.getRelativeTimeSpanString(
            timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
    return relative.toString()
}

private fun handleFolderSelection(
    uri: Uri,
    context: android.content.Context,
    settingsRepository: ProviderSettingsRepository,
    fileSearchRepository: FileSearchRepository,
) {
    val existingRoot =
        settingsRepository.fileSearchSettings.value.roots
            .firstOrNull { it.uri == uri }
    if (existingRoot != null) {
        val folderName =
            existingRoot.displayName.ifBlank {
                existingRoot.uri.lastPathSegment ?: "Folder"
            }
        Toast
            .makeText(
                context,
                "\"$folderName\" is already indexed",
                Toast.LENGTH_SHORT,
            ).show()
        return
    }

    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }.onFailure {
        Log.w(FILE_SEARCH_LOG_TAG, "Unable to persist URI permission", it)
    }
    val document = DocumentFile.fromTreeUri(context, uri)
    if (document == null) {
        Log.w(FILE_SEARCH_LOG_TAG, "Document tree unavailable for $uri")
        return
    }
    val parentDisplayName = document.parentDisplayNameOrNull()
    val root =
        FileSearchRoot(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = document.name ?: document.uri.lastPathSegment ?: "Folder",
            isEnabled = true,
            addedAtMillis = System.currentTimeMillis(),
            parentDisplayName = parentDisplayName,
        )
    settingsRepository.addFileSearchRoot(root)
    settingsRepository.updateFileSearchScanState(root.id, FileSearchScanState.INDEXING)
    fileSearchRepository.scheduleFullIndex(root)
}

private fun DocumentFile.parentDisplayNameOrNull(): String? {
    parentFile?.name?.takeIf { it.isNotBlank() }?.let { return it }
    return uri?.deriveParentDisplayName()
}

private fun Uri?.deriveParentDisplayName(): String? {
    if (this == null) return null
    val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull()
    val candidate = treeDocId ?: path
    return extractParentSegment(candidate)
}

private fun extractParentSegment(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val decoded = Uri.decode(raw)
    val relative = decoded.substringAfter(':', decoded)
    val parentPath = relative.substringBeforeLast('/', "")
    if (parentPath.isBlank()) return null
    val parent = parentPath.substringAfterLast('/')
    return parent.takeIf { it.isNotBlank() }
}

private const val FILE_SEARCH_LOG_TAG = "FileSearchSettings"
