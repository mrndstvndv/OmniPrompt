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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.termux.TermuxCommand
import com.mrndstvndv.search.provider.termux.TermuxProvider
import com.mrndstvndv.search.provider.termux.TermuxSettings
import com.mrndstvndv.search.ui.components.ScrimDialog
import java.util.UUID

@Composable
fun TermuxSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    isTermuxInstalled: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val termuxSettings by settingsRepository.termuxSettings.collectAsState()
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
        settingsRepository.saveTermuxSettings(TermuxSettings(commands))
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
                TermuxSettingsHeader(onBack = onBack)
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
                    title = "Commands",
                    subtitle = "Define commands to run in Termux.",
                ) {
                    SettingsCardGroup {
                        if (commands.isEmpty()) {
                            Text(
                                text =
                                    if (isTermuxInstalled) {
                                        "No commands yet. Add your first Termux command."
                                    } else {
                                        "Install Termux to add and run commands."
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
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 20.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                            }
                        }

                        if (isTermuxInstalled) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            TextButton(
                                onClick = { isAddDialogOpen = true },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(text = "Add Command")
                            }
                        }
                    }
                }
            }

            // Info section
            item {
                SettingsSection(
                    title = "About",
                    subtitle = "How Termux commands work.",
                ) {
                    SettingsCardGroup {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Commands are executed via Termux's RUN_COMMAND intent.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Requirements:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text =
                                    "1. Grant RUN_COMMAND permission to this app\n" +
                                        "2. Enable 'allow-external-apps' in ~/.termux/termux.properties\n" +
                                        "3. For foreground commands on Android 10+, grant Termux 'Draw Over Apps' permission",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Path shortcuts:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• ~/ expands to Termux home directory\n• \$PREFIX/ expands to Termux usr directory",
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
private fun TermuxSettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Termux Commands",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Run commands in Termux.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
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
                    text = if (hasPermission) "Permission Granted" else "Permission Required",
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
                            "RUN_COMMAND permission is enabled"
                        } else {
                            "Tap to grant RUN_COMMAND permission in Settings"
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
                        "Grant",
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
                    text = "Termux Not Installed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Install Termux from F-Droid to use this feature.",
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
            )
        onAdd(command)
    }

    ScrimDialog(onDismiss = onDismiss) {
        TermuxCommandDialogContent(
            title = "Add Command",
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
            canSave = canSave,
            showRemove = false,
            onRemove = {},
            onDismiss = onDismiss,
            onSave = { save() },
        )
    }
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
            )
        onSave(updated)
    }

    ScrimDialog(onDismiss = onDismiss) {
        TermuxCommandDialogContent(
            title = "Edit Command",
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
            canSave = canSave,
            showRemove = true,
            onRemove = onRemove,
            onDismiss = onDismiss,
            onSave = { save() },
        )
    }
}

@Composable
private fun TermuxCommandDialogContent(
    title: String,
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
    canSave: Boolean,
    showRemove: Boolean,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Display Name
        TextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text("Display Name") },
            placeholder = { Text("My Script") },
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
            label = { Text("Executable Path") },
            placeholder = { Text("~/myscript.sh") },
            supportingText = { Text("Use ~/ for home, \$PREFIX/ for usr") },
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
            label = { Text("Arguments (optional)") },
            placeholder = { Text("arg1,arg2,arg3") },
            supportingText = { Text("Comma-separated values") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Working Directory
        TextField(
            value = workingDir,
            onValueChange = onWorkingDirChange,
            label = { Text("Working Directory (optional)") },
            placeholder = { Text("~/projects") },
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
                    text = "Run in Background",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "Execute without opening Termux UI",
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
            text = "Session Action",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.selectableGroup()) {
            SessionActionOption(
                selected = sessionAction == TermuxCommand.SESSION_ACTION_NEW_AND_OPEN,
                label = "New session, open Termux",
                onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_NEW_AND_OPEN) },
            )
            SessionActionOption(
                selected = sessionAction == TermuxCommand.SESSION_ACTION_NEW_NO_OPEN,
                label = "New session, don't open",
                onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_NEW_NO_OPEN) },
            )
            SessionActionOption(
                selected = sessionAction == TermuxCommand.SESSION_ACTION_CURRENT_AND_OPEN,
                label = "Current session, open Termux",
                onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_CURRENT_AND_OPEN) },
            )
            SessionActionOption(
                selected = sessionAction == TermuxCommand.SESSION_ACTION_CURRENT_NO_OPEN,
                label = "Current session, don't open",
                onClick = { onSessionActionChange(TermuxCommand.SESSION_ACTION_CURRENT_NO_OPEN) },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
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
    }
}

@Composable
private fun SessionActionOption(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                ).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            tonalElevation = 4.dp,
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
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
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(content = content)
    }
}
