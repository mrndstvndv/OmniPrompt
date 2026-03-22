package com.mrndstvndv.search.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.termux.TermuxCommand
import com.mrndstvndv.search.provider.termux.TermuxProvider
import com.mrndstvndv.search.provider.termux.TermuxSettings
import com.mrndstvndv.search.ui.components.ContentDialog
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import java.util.UUID

@Composable
fun TermuxSettingsScreen(
    repository: SettingsRepository<TermuxSettings>,
    isTermuxInstalled: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val termuxSettings by repository.flow.collectAsState()
    var commands by remember { mutableStateOf(termuxSettings.commands) }
    var editingCommand by remember { mutableStateOf<Pair<Int, TermuxCommand>?>(null) }
    var isAddDialogOpen by remember { mutableStateOf(false) }

    // Permission state - checked fresh on composition
    var hasRunCommandPermission by remember {
        mutableStateOf(TermuxProvider.hasRunCommandPermission(context))
    }

    LaunchedEffect(termuxSettings) {
        commands = termuxSettings.commands
    }

    fun saveSettings() {
        repository.replace(TermuxSettings(commands))
    }

    fun addCommand(command: TermuxCommand) {
        commands = commands + command
        saveSettings()
    }

    fun updateCommand(
        index: Int,
        command: TermuxCommand,
    ) {
        commands = commands.toMutableList().apply { this[index] = command }
        saveSettings()
    }

    fun removeCommand(index: Int) {
        commands = commands.toMutableList().apply { removeAt(index) }
        saveSettings()
    }

    // Edit dialog
    editingCommand?.let { (index, command) ->
        TermuxCommandEditDialog(
            command = command,
            onDismiss = { editingCommand = null },
            onSave = { updated ->
                updateCommand(index, updated)
                editingCommand = null
            },
            onRemove = {
                removeCommand(index)
                editingCommand = null
            },
        )
    }

    // Add dialog
    if (isAddDialogOpen) {
        TermuxCommandAddDialog(
            onDismiss = { isAddDialogOpen = false },
            onAdd = { command ->
                addCommand(command)
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
                    title = stringResource(R.string.termux_header),
                    subtitle = stringResource(R.string.termux_header_subtitle),
                    onBack = onBack,
                )
            }

            // Warning if Termux not installed
            if (!isTermuxInstalled) {
                item {
                    TermuxNotInstalledCard()
                }
            }

            // Permission status card (only show if Termux is installed)
            if (isTermuxInstalled) {
                item {
                    TermuxPermissionStatusCard(
                        hasPermission = hasRunCommandPermission,
                        onOpenSettings = {
                            val intent =
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            context.startActivity(intent)
                        },
                        onRefresh = {
                            hasRunCommandPermission = TermuxProvider.hasRunCommandPermission(context)
                        },
                    )
                }
            }

            // Commands section
            item {
                SettingsSection(
                    title = stringResource(R.string.termux_section_commands),
                    subtitle = stringResource(R.string.termux_section_commands_subtitle),
                ) {
                    SettingsGroup {
                        if (commands.isEmpty()) {
                            Text(
                                text =
                                    if (isTermuxInstalled) {
                                        stringResource(R.string.termux_no_commands)
                                    } else {
                                        stringResource(R.string.termux_no_commands_not_installed)
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            commands.forEachIndexed { index, command ->
                                TermuxCommandRow(
                                    command = command,
                                    enabled = isTermuxInstalled,
                                    onClick = {
                                        if (isTermuxInstalled) {
                                            editingCommand = index to command
                                        }
                                    },
                                )
                                if (index < commands.lastIndex) {
                                    SettingsDivider()
                                }
                            }
                        }

                        if (isTermuxInstalled) {
                            SettingsDivider()
                            TextButton(
                                onClick = { isAddDialogOpen = true },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(text = stringResource(R.string.termux_add_command))
                            }
                        }
                    }
                }
            }

            // Info section
            item {
                SettingsSection(
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.termux_about_subtitle),
                ) {
                    SettingsGroup {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.termux_commands_info),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.termux_requirements),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.termux_requirements_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.termux_path_shortcuts),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.termux_path_shortcuts_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.termux_dynamic_args),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.termux_dynamic_args_detail),
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
private fun TermuxPermissionStatusCard(
    hasPermission: Boolean,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color =
            if (hasPermission) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
    ) {
        Row(
            modifier =
                Modifier
                    .clickable {
                        if (hasPermission) {
                            onRefresh()
                        } else {
                            onOpenSettings()
                        }
                    }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (hasPermission) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint =
                    if (hasPermission) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPermission) stringResource(R.string.permission_granted) else stringResource(R.string.permission_required),
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (hasPermission) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                )
                Text(
                    text =
                        if (hasPermission) {
                            stringResource(R.string.termux_permission_enabled)
                        } else {
                            stringResource(R.string.termux_permission_tap_to_grant)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (hasPermission) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                )
            }
            if (!hasPermission) {
                TextButton(onClick = onOpenSettings) {
                    Text(
                        stringResource(R.string.grant),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun TermuxNotInstalledCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.termux_not_installed),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.termux_not_installed_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun TermuxCommandRow(
    command: TermuxCommand,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (enabled) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = null,
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = command.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = command.executablePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TermuxCommandAddDialog(
    onDismiss: () -> Unit,
    onAdd: (TermuxCommand) -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var executablePath by remember { mutableStateOf("") }
    var arguments by remember { mutableStateOf("") }
    var workingDir by remember { mutableStateOf("") }
    var runInBackground by remember { mutableStateOf(false) }
    var sessionAction by remember { mutableIntStateOf(TermuxCommand.SESSION_ACTION_NEW_AND_OPEN) }
    var shellName by remember { mutableStateOf("") }
    var shellCreateMode by remember { mutableStateOf("") }

    val canSave = displayName.isNotBlank() && executablePath.isNotBlank()

    fun save() {
        if (!canSave) return
        val command =
            TermuxCommand(
                id = UUID.randomUUID().toString(),
                displayName = displayName.trim(),
                executablePath = executablePath.trim(),
                arguments = arguments.trim().takeIf { it.isNotBlank() },
                workingDir = workingDir.trim().takeIf { it.isNotBlank() },
                runInBackground = runInBackground,
                sessionAction = sessionAction,
                shellName = shellName.trim().takeIf { it.isNotBlank() },
                shellCreateMode = shellCreateMode.trim().takeIf { it.isNotBlank() },
            )
        onAdd(command)
    }

    ContentDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Text(
                text = stringResource(R.string.termux_add_command),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(1.dp))

                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { save() },
                        enabled = canSave,
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
        content = {
            TermuxCommandDialogContent(
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                executablePath = executablePath,
                onExecutablePathChange = { executablePath = it },
                arguments = arguments,
                onArgumentsChange = { arguments = it },
                workingDir = workingDir,
                onWorkingDirChange = { workingDir = it },
                runInBackground = runInBackground,
                onRunInBackgroundChange = { runInBackground = it },
                sessionAction = sessionAction,
                onSessionActionChange = { sessionAction = it },
                shellName = shellName,
                onShellNameChange = { shellName = it },
                shellCreateMode = shellCreateMode,
                onShellCreateModeChange = { shellCreateMode = it },
            )
        },
    )
}

@Composable
private fun TermuxCommandEditDialog(
    command: TermuxCommand,
    onDismiss: () -> Unit,
    onSave: (TermuxCommand) -> Unit,
    onRemove: () -> Unit,
) {
    var displayName by remember { mutableStateOf(command.displayName) }
    var executablePath by remember { mutableStateOf(command.executablePath) }
    var arguments by remember { mutableStateOf(command.arguments ?: "") }
    var workingDir by remember { mutableStateOf(command.workingDir ?: "") }
    var runInBackground by remember { mutableStateOf(command.runInBackground) }
    var sessionAction by remember { mutableIntStateOf(command.sessionAction) }
    var shellName by remember { mutableStateOf(command.shellName ?: "") }
    var shellCreateMode by remember { mutableStateOf(command.shellCreateMode ?: "") }

    val canSave = displayName.isNotBlank() && executablePath.isNotBlank()

    fun save() {
        if (!canSave) return
        val updated =
            command.copy(
                displayName = displayName.trim(),
                executablePath = executablePath.trim(),
                arguments = arguments.trim().takeIf { it.isNotBlank() },
                workingDir = workingDir.trim().takeIf { it.isNotBlank() },
                runInBackground = runInBackground,
                sessionAction = sessionAction,
                shellName = shellName.trim().takeIf { it.isNotBlank() },
                shellCreateMode = shellCreateMode.trim().takeIf { it.isNotBlank() },
            )
        onSave(updated)
    }

    ContentDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Text(
                text = stringResource(R.string.termux_edit_command),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { save() },
                        enabled = canSave,
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
        content = {
            TermuxCommandDialogContent(
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                executablePath = executablePath,
                onExecutablePathChange = { executablePath = it },
                arguments = arguments,
                onArgumentsChange = { arguments = it },
                workingDir = workingDir,
                onWorkingDirChange = { workingDir = it },
                runInBackground = runInBackground,
                onRunInBackgroundChange = { runInBackground = it },
                sessionAction = sessionAction,
                onSessionActionChange = { sessionAction = it },
                shellName = shellName,
                onShellNameChange = { shellName = it },
                shellCreateMode = shellCreateMode,
                onShellCreateModeChange = { shellCreateMode = it },
            )
        },
    )
}

@Composable
private fun TermuxCommandDialogContent(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    executablePath: String,
    onExecutablePathChange: (String) -> Unit,
    arguments: String,
    onArgumentsChange: (String) -> Unit,
    workingDir: String,
    onWorkingDirChange: (String) -> Unit,
    runInBackground: Boolean,
    onRunInBackgroundChange: (Boolean) -> Unit,
    sessionAction: Int,
    onSessionActionChange: (Int) -> Unit,
    shellName: String,
    onShellNameChange: (String) -> Unit,
    shellCreateMode: String,
    onShellCreateModeChange: (String) -> Unit,
) {
    // Display Name
    TextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text(stringResource(R.string.termux_label_display_name)) },
        placeholder = { Text(stringResource(R.string.termux_placeholder_display_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Executable Path
    TextField(
        value = executablePath,
        onValueChange = onExecutablePathChange,
        label = { Text(stringResource(R.string.termux_label_executable_path)) },
        placeholder = { Text(stringResource(R.string.termux_placeholder_executable_path)) },
        supportingText = { Text(stringResource(R.string.termux_supporting_executable_path)) },
        singleLine = true,
        isError = executablePath.isBlank(),
        modifier = Modifier.fillMaxWidth(),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Arguments
    TextField(
        value = arguments,
        onValueChange = onArgumentsChange,
        label = { Text(stringResource(R.string.termux_label_arguments)) },
        placeholder = { Text(stringResource(R.string.termux_placeholder_arguments)) },
        supportingText = { Text(stringResource(R.string.termux_supporting_arguments)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
    )
    Spacer(modifier = Modifier.height(8.dp))

    val previewArgs =
        if (arguments.isBlank()) {
            ""
        } else {
            arguments
                .split(",")
                .map { it.trim() }
                .joinToString(" ") { arg ->
                    when {
                        arg.contains("\$*") -> arg.replace("\$*", "<args>")
                        arg.contains(Regex("\\\$\\d+")) ->
                            arg.replace(Regex("\\\$(\\d+)")) { match ->
                                "<arg${match.groupValues[1]}>"
                            }
                        else -> arg
                    }
                }
        }
    Text(
        text = stringResource(R.string.termux_preview),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = if (previewArgs.isBlank()) executablePath else "$executablePath $previewArgs",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Working Directory
    TextField(
        value = workingDir,
        onValueChange = onWorkingDirChange,
        label = { Text(stringResource(R.string.termux_label_working_directory)) },
        placeholder = { Text(stringResource(R.string.termux_placeholder_working_directory)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
    )
    Spacer(modifier = Modifier.height(16.dp))

    // Shell Name
    TextField(
        value = shellName,
        onValueChange = onShellNameChange,
        label = { Text(stringResource(R.string.termux_label_shell_name)) },
        placeholder = { Text(stringResource(R.string.termux_placeholder_shell_name)) },
        supportingText = { Text(stringResource(R.string.termux_supporting_shell_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Shell Create Mode
    TextField(
        value = shellCreateMode,
        onValueChange = onShellCreateModeChange,
        label = { Text(stringResource(R.string.termux_label_shell_create_mode)) },
        placeholder = { Text(stringResource(R.string.termux_placeholder_shell_create_mode)) },
        supportingText = { Text(stringResource(R.string.termux_supporting_shell_create_mode)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
    )
    Spacer(modifier = Modifier.height(16.dp))

    // Run in Background switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.termux_run_background),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.termux_run_background_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = runInBackground,
            onCheckedChange = onRunInBackgroundChange,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Session Action
    Text(
        text = stringResource(R.string.termux_session_action),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Column(modifier = Modifier.selectableGroup()) {
        SessionActionOption(
            selected = sessionAction == TermuxCommand.SESSION_ACTION_NEW_AND_OPEN,
            label = stringResource(R.string.termux_session_new_open),
            enabled = !runInBackground,
            onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_NEW_AND_OPEN) },
        )
        SessionActionOption(
            selected = sessionAction == TermuxCommand.SESSION_ACTION_CURRENT_AND_OPEN,
            label = stringResource(R.string.termux_session_current_open),
            enabled = !runInBackground,
            onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_CURRENT_AND_OPEN) },
        )
        SessionActionOption(
            selected = sessionAction == TermuxCommand.SESSION_ACTION_NEW_NO_OPEN,
            label = stringResource(R.string.termux_session_new_no_open),
            enabled = !runInBackground,
            onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_NEW_NO_OPEN) },
        )
        SessionActionOption(
            selected = sessionAction == TermuxCommand.SESSION_ACTION_CURRENT_NO_OPEN,
            label = stringResource(R.string.termux_session_current_no_open),
            enabled = !runInBackground,
            onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_CURRENT_NO_OPEN) },
        )
    }
}

@Composable
private fun SessionActionOption(
    selected: Boolean,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    onClick = onClick,
                    role = Role.RadioButton,
                ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            enabled = enabled,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
