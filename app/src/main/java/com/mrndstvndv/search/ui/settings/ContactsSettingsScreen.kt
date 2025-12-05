package com.mrndstvndv.search.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mrndstvndv.search.provider.contacts.ContactsRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository

@Composable
fun ContactsSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    contactsRepository: ContactsRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contactsSettings by settingsRepository.contactsSettings.collectAsState()

    // Permission states
    var hasContactsPermission by remember {
        mutableStateOf(contactsRepository.hasContactsPermission())
    }
    var hasPhoneStatePermission by remember {
        mutableStateOf(contactsRepository.hasPhoneStatePermission())
    }

    // Permission launchers
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactsPermission = isGranted
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPhoneStatePermission = permissions.values.all { it }
        if (hasPhoneStatePermission) {
            settingsRepository.setContactsShowSimNumbers(true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            ContactsSettingsHeader(onBack = onBack)
        }

        // Permission status card
        item {
            PermissionStatusCard(
                hasContactsPermission = hasContactsPermission,
                onRequestPermission = {
                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            )
        }

        // Settings card (only show if permission granted)
        if (hasContactsPermission) {
            item {
                ContactsSettingsCard(
                    includePhoneNumbers = contactsSettings.includePhoneNumbers,
                    onIncludePhoneNumbersChange = { settingsRepository.setContactsIncludePhoneNumbers(it) },
                    showSimNumbers = contactsSettings.showSimNumbers,
                    hasPhoneStatePermission = hasPhoneStatePermission,
                    onShowSimNumbersChange = { enabled ->
                        if (enabled && !hasPhoneStatePermission) {
                            // Request phone state permissions
                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                arrayOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_PHONE_NUMBERS
                                )
                            } else {
                                arrayOf(Manifest.permission.READ_PHONE_STATE)
                            }
                            phoneStatePermissionLauncher.launch(permissions)
                        } else {
                            settingsRepository.setContactsShowSimNumbers(enabled)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ContactsSettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.size(40.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Contacts",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(
    hasContactsPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasContactsPermission) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = if (hasContactsPermission) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (hasContactsPermission) "Permission granted" else "Permission required",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (hasContactsPermission) {
                            "Contacts can be searched"
                        } else {
                            "Allow access to search your contacts"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!hasContactsPermission) {
                TextButton(onClick = onRequestPermission) {
                    Text("Grant")
                }
            }
        }
    }
}

@Composable
private fun ContactsSettingsCard(
    includePhoneNumbers: Boolean,
    onIncludePhoneNumbersChange: (Boolean) -> Unit,
    showSimNumbers: Boolean,
    hasPhoneStatePermission: Boolean,
    onShowSimNumbersChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // Include phone numbers in search
            SettingsToggleRow(
                title = "Search phone numbers",
                subtitle = "Include phone numbers when searching contacts",
                checked = includePhoneNumbers,
                onCheckedChange = onIncludePhoneNumbersChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Show SIM numbers
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show my SIM numbers",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Display your own phone numbers in search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showSimNumbers && hasPhoneStatePermission,
                        onCheckedChange = onShowSimNumbersChange
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: SIM card numbers may not be available depending on your carrier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
