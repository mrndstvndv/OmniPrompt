package com.mrndstvndv.search.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.settings.FileSearchRoot
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.settings.BackupRestoreManager
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun BackupRestoreSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    rankingRepository: ProviderRankingRepository,
    aliasRepository: AliasRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupManager = remember { BackupRestoreManager(context) }

    val fileSearchSettings by settingsRepository.fileSearchSettings.collectAsState()

    // UI State
    var isExporting by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestorePreviewDialog by remember { mutableStateOf(false) }
    var backupPreview by remember { mutableStateOf<BackupRestoreManager.BackupPreview?>(null) }
    var pendingRestoreJson by remember { mutableStateOf<JSONObject?>(null) }
    var showWarningsDialog by remember { mutableStateOf(false) }
    var restoreWarnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var restoreSuccessMessage by remember { mutableStateOf("") }

    // Track which root is currently requesting permission
    var pendingPermissionRootId by remember { mutableStateOf<String?>(null) }

    // SAF Create Document launcher (for backup)
    val createDocumentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(BackupRestoreManager.MIME_TYPE_JSON),
        ) { uri ->
            if (uri != null) {
                isExporting = true
                coroutineScope.launch {
                    val backupJson =
                        backupManager.createBackup(
                            settingsRepository,
                            rankingRepository,
                            aliasRepository,
                        )
                    when (val result = backupManager.writeBackupToUri(uri, backupJson)) {
                        is BackupRestoreManager.BackupResult.Success -> {
                            val sizeKb = result.sizeBytes / 1024.0
                            Toast
                                .makeText(
                                    context,
                                    "Backup saved (${String.format("%.1f", sizeKb)} KB)",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }

                        is BackupRestoreManager.BackupResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    isExporting = false
                }
            }
        }

    // SAF Open Document launcher (for restore)
    val openDocumentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    val result = backupManager.readBackupFromUri(uri)
                    result.fold(
                        onSuccess = { json ->
                            val preview = backupManager.parseBackupPreview(json)
                            if (preview != null) {
                                backupPreview = preview
                                pendingRestoreJson = json
                                showRestorePreviewDialog = true
                            } else {
                                Toast.makeText(context, "Invalid backup file", Toast.LENGTH_LONG).show()
                            }
                        },
                        onFailure = { error ->
                            Toast.makeText(context, error.message, Toast.LENGTH_LONG).show()
                        },
                    )
                }
            }
        }

    // SAF Open Document Tree launcher (for folder permission)
    val openDocumentTreeLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null && pendingPermissionRootId != null) {
                // Take persistable permission
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                    // Update the root with the new URI
                    val currentRoot = fileSearchSettings.roots.find { it.id == pendingPermissionRootId }
                    if (currentRoot != null) {
                        // Remove old root and add new one with updated URI
                        settingsRepository.removeFileSearchRoot(currentRoot.id)
                        val newRoot = currentRoot.copy(uri = uri)
                        settingsRepository.addFileSearchRoot(newRoot)
                    }

                    Toast.makeText(context, "Permission granted", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to save permission", Toast.LENGTH_LONG).show()
                }
            }
            pendingPermissionRootId = null
        }

    // Restore Preview Dialog
    if (showRestorePreviewDialog && backupPreview != null) {
        RestorePreviewDialog(
            preview = backupPreview!!,
            onDismiss = {
                showRestorePreviewDialog = false
                backupPreview = null
                pendingRestoreJson = null
            },
            onConfirm = {
                showRestorePreviewDialog = false
                isRestoring = true
                coroutineScope.launch {
                    pendingRestoreJson?.let { json ->
                        when (
                            val result =
                                backupManager.restoreFromBackup(
                                    json,
                                    settingsRepository,
                                    rankingRepository,
                                    aliasRepository,
                                )
                        ) {
                            is BackupRestoreManager.RestoreResult.Success -> {
                                restoreSuccessMessage =
                                    buildString {
                                        append("Restored ")
                                        val parts = mutableListOf<String>()
                                        if (result.settingsRestored > 0) {
                                            parts.add("${result.settingsRestored} setting group(s)")
                                        }
                                        if (result.aliasesRestored > 0) {
                                            parts.add("${result.aliasesRestored} alias(es)")
                                        }
                                        append(parts.joinToString(", "))
                                    }
                                if (result.warnings.isNotEmpty()) {
                                    restoreWarnings = result.warnings
                                    showWarningsDialog = true
                                } else {
                                    Toast.makeText(context, restoreSuccessMessage, Toast.LENGTH_SHORT).show()
                                }
                            }

                            is BackupRestoreManager.RestoreResult.Error -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    isRestoring = false
                    pendingRestoreJson = null
                    backupPreview = null
                }
            },
        )
    }

    // Warnings Dialog
    if (showWarningsDialog) {
        RestoreWarningsDialog(
            successMessage = restoreSuccessMessage,
            warnings = restoreWarnings,
            onDismiss = {
                showWarningsDialog = false
                restoreWarnings = emptyList()
                restoreSuccessMessage = ""
            },
        )
    }

    // Main UI
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SettingsHeader(title = "Backup & Restore", onBack = onBack)
        }

        // Backup Section
        item {
            SectionTitle(title = "Backup")
        }

        item {
            SettingsGroup {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "Export Settings",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Save all settings to a file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (!isExporting) {
                                createDocumentLauncher.launch(backupManager.generateBackupFilename())
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isExporting) {
                            Text("Exporting...")
                        } else {
                            Text("Export")
                        }
                    }
                }
            }
        }

        // Restore Section
        item {
            SectionTitle(title = "Restore")
        }

        item {
            SettingsGroup {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "Import Settings",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Load settings from a backup file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            if (!isRestoring) {
                                openDocumentLauncher.launch(arrayOf(BackupRestoreManager.MIME_TYPE_JSON, "application/octet-stream", "*/*"))
                            }
                        },
                        enabled = !isRestoring,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isRestoring) {
                            Text("Importing...")
                        } else {
                            Text("Import")
                        }
                    }
                }
            }
        }

        // File Search Permissions Section (only show if there are roots)
        if (fileSearchSettings.roots.isNotEmpty()) {
            item {
                SectionTitle(title = "File Search Permissions")
            }

            item {
                SettingsGroup {
                    fileSearchSettings.roots.forEachIndexed { index, root ->
                        FileSearchRootRow(
                            root = root,
                            onRequestPermission = {
                                pendingPermissionRootId = root.id
                                openDocumentTreeLauncher.launch(root.uri)
                            },
                        )
                        if (index < fileSearchSettings.roots.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun FileSearchRootRow(
    root: FileSearchRoot,
    onRequestPermission: () -> Unit,
) {
    val context = LocalContext.current
    val hasPermission =
        remember(root.uri) {
            try {
                val docFile = DocumentFile.fromTreeUri(context, root.uri)
                docFile?.canRead() == true
            } catch (e: Exception) {
                // For file:// URIs (like Downloads), check differently
                try {
                    val docFile = DocumentFile.fromFile(java.io.File(root.uri.path ?: ""))
                    docFile.canRead()
                } catch (e2: Exception) {
                    false
                }
            }
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .then(
                    if (!hasPermission) {
                        Modifier.clickable(onClick = onRequestPermission)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = root.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (root.parentDisplayName != null) {
                    Text(
                        text = root.parentDisplayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Icon(
            imageVector = if (hasPermission) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
            contentDescription = if (hasPermission) "Has permission" else "Needs permission",
            tint = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RestorePreviewDialog(
    preview: BackupRestoreManager.BackupPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restore from Backup?",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Backup from: ${preview.formattedTimestamp()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "This will restore:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    if (preview.webSearchSitesCount > 0) {
                        BulletPoint("${preview.webSearchSitesCount} web search engine(s)")
                    }
                    if (preview.quicklinksCount > 0) {
                        BulletPoint("${preview.quicklinksCount} quicklink(s)")
                    }
                    if (preview.aliasesCount > 0) {
                        BulletPoint("${preview.aliasesCount} alias(es)")
                    }
                    if (preview.fileSearchRootsCount > 0) {
                        BulletPoint("${preview.fileSearchRootsCount} file search folder(s)")
                    }
                    if (preview.enabledProvidersCount > 0) {
                        BulletPoint("${preview.enabledProvidersCount} provider toggle(s)")
                    }
                    if (preview.hasAppearanceSettings) {
                        BulletPoint("Appearance settings")
                    }
                    if (preview.hasBehaviorSettings) {
                        BulletPoint("Behavior settings")
                    }
                }

                if (preview.fileSearchRootsCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "File search folders may require permission requests after restore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Restore")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RestoreWarningsDialog(
    successMessage: String,
    warnings: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restore Completed",
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = successMessage,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Some items had issues:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        warnings.forEach { warning ->
                            BulletPoint(
                                text = warning,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@Composable
private fun BulletPoint(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}
