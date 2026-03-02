package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.intent.IntentConfig
import com.mrndstvndv.search.provider.intent.IntentSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.ui.components.ContentDialog
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import java.util.UUID

@Composable
fun IntentSettingsScreen(
    repository: SettingsRepository<IntentSettings>,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val intentSettings by repository.flow.collectAsState()
    var configs by remember { mutableStateOf(intentSettings.configs) }
    var editingConfig by remember { mutableStateOf<Pair<Int, IntentConfig>?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(intentSettings) {
        configs = intentSettings.configs
    }

    fun saveSettings() {
        repository.replace(IntentSettings(configs))
    }

    fun addConfig(config: IntentConfig) {
        configs = configs + config
        saveSettings()
    }

    fun updateConfig(
        index: Int,
        config: IntentConfig,
    ) {
        configs = configs.toMutableList().apply { this[index] = config }
        saveSettings()
    }

    fun removeConfig(index: Int) {
        configs = configs.toMutableList().apply { removeAt(index) }
        saveSettings()
    }

    // Edit dialog
    editingConfig?.let { (index, config) ->
        IntentConfigEditDialog(
            config = config,
            onDismiss = { editingConfig = null },
            onSave = { updated ->
                updateConfig(index, updated)
                editingConfig = null
            },
            onRemove = {
                removeConfig(index)
                editingConfig = null
            },
        )
    }

    // Add dialog
    if (isAddDialogOpen) {
        IntentConfigAddDialog(
            onDismiss = { isAddDialogOpen = false },
            onAdd = { config ->
                addConfig(config)
                isAddDialogOpen = false
            },
        )
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
                    title = "Intent Launcher",
                    subtitle = "Launch apps by fuzzy searching titles.",
                    onBack = onBack
                )
            }

            // Intents section
            item {
                SettingsSection(
                    title = "Intents",
                    subtitle = "Define intents to launch apps.",
                ) {
                    SettingsGroup {
                        if (configs.isEmpty()) {
                            Text(
                                text = "No intents configured. Add your first intent.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            configs.forEachIndexed { index, config ->
                                IntentConfigRow(
                                    config = config,
                                    onClick = { editingConfig = index to config }
                                )
                                if (index < configs.lastIndex) {
                                    SettingsDivider()
                                }
                            }
                        }

                        SettingsDivider()
                        TextButton(
                            onClick = { isAddDialogOpen = true },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Add Intent")
                        }
                    }
                }
            }

            // Info section
            item {
                SettingsSection(
                    title = "About",
                    subtitle = "How intent matching works.",
                ) {
                    SettingsGroup {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Usage:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Type keywords to fuzzy match intent titles, followed by text or a URL to launch.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Examples:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• ytdl https://youtube.com/watch?v=... → Opens in YTDLnis\n" +
                                        "• open https://google.com → Opens in browser\n" +
                                        "• share Check this out! → Opens share sheet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Custom extras:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use \$query in extra value to include the payload text.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntentConfigRow(
    config: IntentConfig,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = config.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IntentConfigAddDialog(
    onDismiss: () -> Unit,
    onAdd: (IntentConfig) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("android.intent.action.SEND") }
    var type by remember { mutableStateOf("text/plain") }
    var extraKey by remember { mutableStateOf("") }
    var extraValue by remember { mutableStateOf("") }

    val canSave = title.isNotBlank()

    fun save() {
        if (!canSave) return
        val config =
            IntentConfig(
                id = UUID.randomUUID().toString(),
                title = title.trim(),
                packageName = packageName.trim(),
                action = action,
                type = type,
                extraKey = extraKey.trim().takeIf { it.isNotBlank() },
                extraValue = extraValue.trim().takeIf { it.isNotBlank() },
            )
        onAdd(config)
    }

    IntentConfigDialogContent(
        title = "Add Intent",
        title_ = title,
        onTitleChange = { title = it },
        packageName = packageName,
        onPackageNameChange = { packageName = it },
        action = action,
        onActionChange = { action = it },
        type = type,
        onTypeChange = { type = it },
        extraKey = extraKey,
        onExtraKeyChange = { extraKey = it },
        extraValue = extraValue,
        onExtraValueChange = { extraValue = it },
        canSave = canSave,
        showRemove = false,
        onRemove = {},
        onDismiss = onDismiss,
        onSave = { save() },
    )
}

@Composable
private fun IntentConfigEditDialog(
    config: IntentConfig,
    onDismiss: () -> Unit,
    onSave: (IntentConfig) -> Unit,
    onRemove: () -> Unit,
) {
    var title by remember { mutableStateOf(config.title) }
    var packageName by remember { mutableStateOf(config.packageName) }
    var action by remember { mutableStateOf(config.action) }
    var type by remember { mutableStateOf(config.type) }
    var extraKey by remember { mutableStateOf(config.extraKey ?: "") }
    var extraValue by remember { mutableStateOf(config.extraValue ?: "") }

    val canSave = title.isNotBlank()

    fun save() {
        if (!canSave) return
        val updated =
            config.copy(
                title = title.trim(),
                packageName = packageName.trim(),
                action = action,
                type = type,
                extraKey = extraKey.trim().takeIf { it.isNotBlank() },
                extraValue = extraValue.trim().takeIf { it.isNotBlank() },
            )
        onSave(updated)
    }

    IntentConfigDialogContent(
        title = "Edit Intent",
        title_ = title,
        onTitleChange = { title = it },
        packageName = packageName,
        onPackageNameChange = { packageName = it },
        action = action,
        onActionChange = { action = it },
        type = type,
        onTypeChange = { type = it },
        extraKey = extraKey,
        onExtraKeyChange = { extraKey = it },
        extraValue = extraValue,
        onExtraValueChange = { extraValue = it },
        canSave = canSave,
        showRemove = true,
        onRemove = onRemove,
        onDismiss = onDismiss,
        onSave = { save() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntentConfigDialogContent(
    title: String,
    title_: String,
    onTitleChange: (String) -> Unit,
    packageName: String,
    onPackageNameChange: (String) -> Unit,
    action: String,
    onActionChange: (String) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
    extraKey: String,
    onExtraKeyChange: (String) -> Unit,
    extraValue: String,
    onExtraValueChange: (String) -> Unit,
    canSave: Boolean,
    showRemove: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val actionOptions = listOf(
        "android.intent.action.SEND",
        "android.intent.action.VIEW",
        "android.intent.action.SENDTO",
    )
    
    val typeOptions = listOf(
        "text/plain",
        "text/*",
        "*/*",
        "video/*",
        "audio/*",
        "image/*",
    )

    var actionExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    ContentDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showRemove) {
                    TextButton(onClick = onRemove) {
                        Text(
                            text = "Remove",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        enabled = canSave,
                    ) {
                        Text("Save")
                    }
                }
            }
        },
        content = {
            OutlinedTextField(
                value = title_,
                onValueChange = onTitleChange,
                label = { Text("Title") },
                placeholder = { Text("Download Video") },
                supportingText = { Text("Display name - fuzzy matched when searching") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = packageName,
                onValueChange = onPackageNameChange,
                label = { Text("Package Name (optional)") },
                placeholder = { Text("com.deniscerri.ytdl") },
                supportingText = { Text("Target app's package (leave empty for system)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = actionExpanded,
                onExpandedChange = { actionExpanded = it },
            ) {
                OutlinedTextField(
                    value = action.substringAfterLast("."),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Action") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = actionExpanded,
                    onDismissRequest = { actionExpanded = false }
                ) {
                    actionOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.substringAfterLast(".")) },
                            onClick = {
                                onActionChange(option)
                                actionExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("MIME Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    typeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onTypeChange(option)
                                typeExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = extraKey,
                onValueChange = onExtraKeyChange,
                label = { Text("Extra Key (optional)") },
                placeholder = { Text("TYPE") },
                supportingText = { Text("Custom intent extra key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = extraValue,
                onValueChange = onExtraValueChange,
                label = { Text("Extra Value (optional)") },
                placeholder = { Text("video") },
                supportingText = { Text("Use \$query to include the payload") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
