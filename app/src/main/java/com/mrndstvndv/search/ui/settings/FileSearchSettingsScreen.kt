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
import androidx.compose.ui.res.stringResource
import com.mrndstvndv.search.R
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
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun FileSearchSettingsScreen(
    repository: SettingsRepository<FileSearchSettings>,
    fileSearchRepository: FileSearchRepository,
    onBack: () -> Unit,
) {
    val fileSearchSettings by repository.flow.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val addFileRootLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                handleFolderSelection(
                    uri = uri,
                    context = context,
                    repository = repository,
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
            repository.update { it.copy(includeDownloads = true) }
            FileSearchRoot.downloadsRoot()?.let { root ->
                repository.update { settings ->
                    settings.copy(scanMetadata = settings.scanMetadata.toMutableMap().apply {
                        set(root.id, FileSearchScanMetadata(state = FileSearchScanState.INDEXING, indexedItemCount = 0, errorMessage = null, updatedAtMillis = System.currentTimeMillis()))
                    })
                }
                fileSearchRepository.scheduleFullIndex(root)
            }
        }
    }

    fun disableDownloadsIndexing() {
        coroutineScope.launch(Dispatchers.Default) {
            repository.update { it.copy(includeDownloads = false) }
            fileSearchRepository.deleteRootEntries(FileSearchSettings.DOWNLOADS_ROOT_ID)
        }
    }

    fun rescanDownloads() {
        coroutineScope.launch(Dispatchers.Default) {
            FileSearchRoot.downloadsRoot()?.let { root ->
                repository.update { settings ->
                    settings.copy(scanMetadata = settings.scanMetadata.toMutableMap().apply {
                        set(root.id, FileSearchScanMetadata(state = FileSearchScanState.INDEXING, indexedItemCount = 0, errorMessage = null, updatedAtMillis = System.currentTimeMillis()))
                    })
                }
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
                SettingsHeader(
                    title = stringResource(R.string.file_search_header),
                    subtitle = stringResource(R.string.file_search_header_subtitle),
                    onBack = onBack,
                )
            }

            item {
                SettingsGroup {
                    SettingsSwitch(
                        title = stringResource(R.string.file_search_load_thumbnails),
                        subtitle = stringResource(R.string.file_search_load_thumbnails_subtitle),
                        checked = fileSearchSettings.loadThumbnails,
                        onCheckedChange = { enabled -> repository.update { it.copy(loadThumbnails = enabled) } },
                    )
                    SettingsDivider()
                    ThumbnailCropModeRow(
                        selectedMode = fileSearchSettings.thumbnailCropMode,
                        enabled = fileSearchSettings.loadThumbnails,
                        onModeSelected = { mode -> repository.update { settings -> settings.copy(thumbnailCropMode = mode) } },
                    )
                    SettingsDivider()
                    FileSearchSortRow(
                        sortMode = fileSearchSettings.sortMode,
                        sortAscending = fileSearchSettings.sortAscending,
                        onModeSelected = { mode -> repository.update { settings -> settings.copy(sortMode = mode) } },
                        onToggleAscending = { ascending -> repository.update { it.copy(sortAscending = ascending) } },
                    )
                    SettingsDivider()
                    SyncIntervalRow(
                        selectedInterval = fileSearchSettings.syncIntervalMinutes,
                        onIntervalSelected = { minutes ->
                            repository.update { it.copy(syncIntervalMinutes = minutes) }
                            fileSearchRepository.schedulePeriodicSync(minutes)
                        },
                    )
                    SettingsDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.file_search_sync_on_app_open),
                        subtitle = stringResource(R.string.file_search_sync_on_app_open_subtitle),
                        checked = fileSearchSettings.syncOnAppOpen,
                        onCheckedChange = { enabled -> repository.update { it.copy(syncOnAppOpen = enabled) } },
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
                            repository.update { settings ->
                                settings.copy(roots = settings.roots.map { if (it.id == root.id) it.copy(isEnabled = enabled) else it })
                            }
                        },
                        onRescanRoot = { root ->
                            repository.update { settings ->
                                settings.copy(scanMetadata = settings.scanMetadata.toMutableMap().apply {
                                    set(root.id, FileSearchScanMetadata(state = FileSearchScanState.INDEXING, indexedItemCount = 0, errorMessage = null, updatedAtMillis = System.currentTimeMillis()))
                                })
                            }
                            fileSearchRepository.scheduleFullIndex(root)
                        },
                        onRemoveRoot = { root ->
                            repository.update { it.copy(roots = it.roots.filter { r -> r.id != root.id }) }
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
            stringResource(R.string.file_search_thumbnail_crop_enabled_subtitle)
        } else {
            stringResource(R.string.file_search_thumbnail_crop_disabled_subtitle)
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.file_search_thumbnail_crop),
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
                    Text(
                        text =
                            when (mode) {
                                FileSearchThumbnailCropMode.FIT -> stringResource(R.string.file_search_crop_fit)
                                FileSearchThumbnailCropMode.CENTER_CROP -> stringResource(R.string.file_search_crop_center)
                            },
                    )
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
            text = stringResource(R.string.file_search_sort_order),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.file_search_sort_order_subtitle),
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
                    Text(
                        text =
                            when (mode) {
                                FileSearchSortMode.DATE -> stringResource(R.string.file_search_sort_date_modified)
                                FileSearchSortMode.NAME -> stringResource(R.string.file_search_sort_name)
                            },
                    )
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
                    text = stringResource(R.string.file_search_ascending_order),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.file_search_ascending_subtitle),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncIntervalRow(
    selectedInterval: Int,
    onIntervalSelected: (Int) -> Unit,
) {
    val options =
        listOf(
            0 to stringResource(R.string.off),
            15 to stringResource(R.string.value_minutes_short, 15),
            30 to stringResource(R.string.value_minutes_short, 30),
            60 to stringResource(R.string.value_hours_short, 1),
            120 to stringResource(R.string.value_hours_short, 2),
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.file_search_background_sync),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.file_search_background_sync_subtitle),
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
                text = stringResource(R.string.file_search_no_folders),
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
            Text(text = stringResource(R.string.file_search_add_folder))
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
    val context = LocalContext.current

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
                val (status, detail) = resolveFileSearchStatus(context, root, metadata)
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
                Text(text = stringResource(R.string.file_search_rescan))
            }
            TextButton(onClick = onRemove) {
                Text(text = stringResource(R.string.remove))
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
    val context = LocalContext.current

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
                    text = stringResource(R.string.file_search_downloads_folder),
                    style = MaterialTheme.typography.bodyLarge,
                )
                val (status, detail) = resolveDownloadsStatusText(context, enabled, permissionGranted, metadata)
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
                Text(text = stringResource(R.string.file_search_rescan))
            }
        }
    }
}

private fun resolveFileSearchStatus(
    context: Context,
    root: FileSearchRoot,
    metadata: FileSearchScanMetadata?,
): Pair<String, String?> {
    if (!root.isEnabled) {
        return context.getString(R.string.file_search_status_disabled) to context.getString(R.string.file_search_status_enable_matches)
    }
    if (metadata == null) {
        return context.getString(R.string.file_search_status_pending_scan) to context.getString(R.string.file_search_status_rescan_after_permission)
    }
    return when (metadata.state) {
        FileSearchScanState.INDEXING -> {
            val detail =
                if (metadata.indexedItemCount > 0) {
                    context.getString(R.string.file_search_status_items_scanned, metadata.indexedItemCount)
                } else {
                    context.getString(R.string.file_search_status_may_take_minute)
                }
            context.getString(R.string.file_search_status_indexing) to detail
        }

        FileSearchScanState.ERROR -> {
            context.getString(R.string.file_search_status_index_failed) to (metadata.errorMessage ?: context.getString(R.string.file_search_status_check_permissions))
        }

        FileSearchScanState.SUCCESS -> {
            val detail =
                if (metadata.updatedAtMillis > 0L) {
                    context.getString(R.string.file_search_status_updated, formatRelativeTime(context, metadata.updatedAtMillis))
                } else {
                    null
                }
            context.getString(R.string.file_search_status_indexed_items, metadata.indexedItemCount) to detail
        }

        FileSearchScanState.IDLE -> {
            val detail =
                if (metadata.updatedAtMillis > 0L) {
                    context.getString(R.string.file_search_status_updated, formatRelativeTime(context, metadata.updatedAtMillis))
                } else {
                    null
                }
            context.getString(R.string.file_search_status_idle) to detail
        }
    }
}

private fun resolveDownloadsStatusText(
    context: Context,
    enabled: Boolean,
    permissionGranted: Boolean,
    metadata: FileSearchScanMetadata?,
): Pair<String, String?> {
    if (!permissionGranted) {
        return context.getString(R.string.permission_required) to context.getString(R.string.file_search_status_all_files_access)
    }
    if (!enabled) {
        return context.getString(R.string.file_search_status_disabled) to context.getString(R.string.file_search_status_enable_downloads)
    }
    val placeholderRoot =
        FileSearchRoot(
            id = FileSearchSettings.DOWNLOADS_ROOT_ID,
            uri = Uri.EMPTY,
            displayName = context.getString(R.string.file_search_downloads_folder),
            isEnabled = true,
            addedAtMillis = 0L,
            parentDisplayName = null,
        )
    return resolveFileSearchStatus(context, placeholderRoot, metadata)
}

@Composable
private fun DownloadsPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.file_search_allow_downloads)) },
        text = {
            Text(
                text = stringResource(R.string.file_search_downloads_permission),
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(text = stringResource(R.string.file_search_open_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
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
                text = stringResource(R.string.file_search_cant_index, rootName),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = metadata.errorMessage ?: stringResource(R.string.file_search_error_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = stringResource(R.string.file_search_fix_issue),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private fun formatRelativeTime(
    context: Context,
    timestamp: Long,
): String {
    if (timestamp <= 0L) return context.getString(R.string.just_now)
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
    repository: SettingsRepository<FileSearchSettings>,
    fileSearchRepository: FileSearchRepository,
) {
    val existingRoot =
        repository.flow.value.roots
            .firstOrNull { it.uri == uri }
    if (existingRoot != null) {
        val folderName =
            existingRoot.displayName.ifBlank {
                existingRoot.uri.lastPathSegment ?: context.getString(R.string.folder_label)
            }
        Toast
            .makeText(
                context,
                context.getString(R.string.file_search_already_indexed, folderName),
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
            displayName = document.name ?: document.uri.lastPathSegment ?: context.getString(R.string.folder_label),
            isEnabled = true,
            addedAtMillis = System.currentTimeMillis(),
            parentDisplayName = parentDisplayName,
        )
    repository.update { it.copy(roots = it.roots + root) }
    repository.update { settings ->
        settings.copy(scanMetadata = settings.scanMetadata.toMutableMap().apply {
            set(root.id, FileSearchScanMetadata(state = FileSearchScanState.INDEXING, indexedItemCount = 0, errorMessage = null, updatedAtMillis = System.currentTimeMillis()))
        })
    }
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
