package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.SystemSettingsSettings
import com.mrndstvndv.search.provider.system.DeveloperSettingsManager
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch

@Composable
fun SystemSettingsScreen(
    repository: SettingsRepository<SystemSettingsSettings>,
    developerSettingsManager: DeveloperSettingsManager,
    onBack: () -> Unit,
) {
    val systemSettings by repository.flow.collectAsState()
    val permissionStatus by developerSettingsManager.permissionStatus.collectAsState()

    // Register/unregister Shizuku listeners based on feature toggle
    DisposableEffect(systemSettings.developerToggleEnabled) {
        if (systemSettings.developerToggleEnabled) {
            developerSettingsManager.registerListeners()
        }
        onDispose {
            // Only unregister if disabling the feature
            if (!systemSettings.developerToggleEnabled) {
                developerSettingsManager.unregisterListeners()
            }
        }
    }

    // Refresh status when screen is shown
    LaunchedEffect(Unit) {
        developerSettingsManager.refreshStatus()
    }

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
            SettingsHeader(title = stringResource(R.string.provider_system_settings), onBack = onBack)
        }

        item {
            SettingsGroup {
                SettingsSwitch(
                    title = stringResource(R.string.system_developer_toggle),
                    subtitle = stringResource(R.string.system_developer_toggle_subtitle),
                    checked = systemSettings.developerToggleEnabled,
                    onCheckedChange = { enabled ->
                        repository.update { it.copy(developerToggleEnabled = enabled) }
                        if (enabled) {
                            developerSettingsManager.registerListeners()
                            developerSettingsManager.refreshStatus()
                        } else {
                            developerSettingsManager.unregisterListeners()
                        }
                    },
                )
            }
        }

        if (systemSettings.developerToggleEnabled) {
            item {
                PermissionStatusCard(
                    permissionStatus = permissionStatus,
                    onRequestShizukuPermission = {
                        developerSettingsManager.requestShizukuPermission()
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    permissionStatus: DeveloperSettingsManager.PermissionStatus,
    onRequestShizukuPermission: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.system_permission_status),
                style = MaterialTheme.typography.titleMedium,
            )

            when {
                permissionStatus.availableMethod == DeveloperSettingsManager.PermissionMethod.ADB -> {
                    ReadyStatusRow(
                        title = stringResource(R.string.system_status_adb_granted),
                        subtitle = stringResource(R.string.system_status_ready_toggle),
                    )
                }

                permissionStatus.availableMethod == DeveloperSettingsManager.PermissionMethod.ROOT -> {
                    ReadyStatusRow(
                        title = stringResource(R.string.system_status_root_detected),
                        subtitle = stringResource(R.string.system_status_ready_toggle),
                    )
                }

                permissionStatus.availableMethod == DeveloperSettingsManager.PermissionMethod.SHIZUKU -> {
                    ReadyStatusRow(
                        title = stringResource(R.string.system_status_shizuku_connected),
                        subtitle = stringResource(R.string.system_status_ready_toggle),
                    )
                }

                permissionStatus.isShizukuAvailable && !permissionStatus.hasShizukuPermission -> {
                    ShizukuPermissionCard(onRequestPermission = onRequestShizukuPermission)
                }

                else -> {
                    AdbInstructionsCard()
                }
            }
        }
    }
}

@Composable
private fun ReadyStatusRow(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShizukuPermissionCard(onRequestPermission: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.system_shizuku_available),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.system_permission_required_feature),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.system_settings_grant_shizuku))
        }
    }
}

@Composable
private fun AdbInstructionsCard() {
    val context = LocalContext.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.system_permission_required),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.system_grant_adb_shizuku),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.system_adb_command),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SelectionContainer {
                Text(
                    text = stringResource(R.string.system_adb_command_value, context.packageName),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = stringResource(R.string.system_install_shizuku),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
