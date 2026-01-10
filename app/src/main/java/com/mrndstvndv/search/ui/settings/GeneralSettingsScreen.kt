package com.mrndstvndv.search.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.termux.TermuxProvider
import com.mrndstvndv.search.ui.components.TermuxPermissionDialog
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsNavigationRow
import com.mrndstvndv.search.ui.components.settings.SettingsSliderRow
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch
import kotlin.math.roundToInt

@Composable
fun GeneralSettingsScreen(
    aliasRepository: AliasRepository,
    settingsRepository: ProviderSettingsRepository,
    rankingRepository: ProviderRankingRepository,
    appName: String,
    isDefaultAssistant: Boolean,
    onRequestSetDefaultAssistant: () -> Unit,
    onOpenProviders: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenBehavior: () -> Unit,
    onOpenAliases: () -> Unit,
    onOpenResultRanking: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onClose: () -> Unit,
) {
    // Collect once so future tweaks can surface live states on tiles if desired.
    aliasRepository.aliases.collectAsState()
    settingsRepository.enabledProviders.collectAsState()
    settingsRepository.translucentResultsEnabled.collectAsState()
    settingsRepository.backgroundOpacity.collectAsState()
    settingsRepository.backgroundBlurStrength.collectAsState()
    settingsRepository.motionPreferences.collectAsState()
    settingsRepository.activityIndicatorDelayMs.collectAsState()
    rankingRepository.useFrequencyRanking.collectAsState()
    rankingRepository.providerOrder.collectAsState()

    val providerSubtitle = "Turn sources on or off and configure them."
    val appearanceSubtitle = "Control how search looks on screen."
    val behaviorSubtitle = "Adjust responsiveness and motion."
    val aliasesSubtitle = "Shortcuts you’ve added from results."
    val rankingSubtitle = "Control the order of results and providers."

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
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsHeader(title = "Settings", onBack = onClose)
            }

            if (!isDefaultAssistant) {
                item {
                    CompactAssistantCard(appName = appName, onRequestSetDefaultAssistant = onRequestSetDefaultAssistant)
                }
            }

            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        icon = Icons.Rounded.Apps,
                        title = "Providers",
                        subtitle = providerSubtitle.ifEmpty { "Manage search sources" },
                        onClick = onOpenProviders,
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Rounded.Palette,
                        title = "Appearance",
                        subtitle = appearanceSubtitle,
                        onClick = onOpenAppearance,
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        icon = Icons.Rounded.Speed,
                        title = "Behavior",
                        subtitle = behaviorSubtitle,
                        onClick = onOpenBehavior,
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        title = "Aliases",
                        subtitle = aliasesSubtitle,
                        onClick = onOpenAliases,
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Rounded.BarChart,
                        title = "Result ranking",
                        subtitle = rankingSubtitle,
                        onClick = onOpenResultRanking,
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        icon = Icons.Rounded.CloudUpload,
                        title = "Backup & Restore",
                        subtitle = "Export or import your settings",
                        onClick = onOpenBackupRestore,
                    )
                }
            }
        }
    }
}

@Composable
fun ProvidersSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    appName: String,
    isDefaultAssistant: Boolean,
    onRequestSetDefaultAssistant: () -> Unit,
    onOpenWebSearchSettings: () -> Unit,
    onOpenFileSearchSettings: () -> Unit,
    onOpenTextUtilitiesSettings: () -> Unit,
    onOpenAppSearchSettings: () -> Unit,
    onOpenSystemSettingsSettings: () -> Unit,
    onOpenContactsSettings: () -> Unit,
    onOpenTermuxSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val enabledProviders by settingsRepository.enabledProviders.collectAsState()

    // Check if Termux is installed
    val isTermuxInstalled =
        remember {
            context.packageManager.getLaunchIntentForPackage("com.termux") != null
        }

    // Contacts permission state
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED,
        )
    }

    // Termux permission state
    var hasTermuxPermission by remember {
        mutableStateOf(TermuxProvider.hasRunCommandPermission(context))
    }
    var showTermuxPermissionDialog by remember { mutableStateOf(false) }

    // Permission launcher for contacts
    val contactsPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            hasContactsPermission = isGranted
            if (isGranted) {
                settingsRepository.setProviderEnabled("contacts", true)
            } else {
                Toast.makeText(context, "Permission required to search contacts", Toast.LENGTH_SHORT).show()
            }
        }

    // Permission dialog for Termux
    if (showTermuxPermissionDialog) {
        TermuxPermissionDialog(
            onDismiss = { showTermuxPermissionDialog = false },
            onOpenSettings = {
                showTermuxPermissionDialog = false
                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                context.startActivity(intent)
            },
        )
    }

    SettingsScaffold(
        title = "Providers",
        onBack = onBack,
    ) {
        if (!isDefaultAssistant) {
            item {
                DefaultAssistantCard(appName = appName, onRequestSetDefaultAssistant = onRequestSetDefaultAssistant)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            SettingsGroup {
                ProviderRow(
                    id = "app-list",
                    name = "Applications",
                    description = "Search installed apps",
                    enabled = enabledProviders["app-list"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("app-list", it) },
                    onClick = onOpenAppSearchSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "contacts",
                    name = "Contacts",
                    description = "Search your contacts",
                    enabled = enabledProviders["contacts"] ?: false,
                    onToggle = { enabled ->
                        if (enabled) {
                            if (hasContactsPermission) {
                                settingsRepository.setProviderEnabled("contacts", true)
                            } else {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        } else {
                            settingsRepository.setProviderEnabled("contacts", false)
                        }
                    },
                    onClick = onOpenContactsSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "web-search",
                    name = "Web search",
                    description = "Configure engines and defaults",
                    enabled = enabledProviders["web-search"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("web-search", it) },
                    onClick = onOpenWebSearchSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "file-search",
                    name = "Files & folders",
                    description = "Local device indexing",
                    enabled = enabledProviders["file-search"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("file-search", it) },
                    onClick = onOpenFileSearchSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "calculator",
                    name = "Calculator",
                    description = "Solve math expressions",
                    enabled = enabledProviders["calculator"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("calculator", it) },
                )
                SettingsDivider()
                ProviderRow(
                    id = "system-settings",
                    name = "System Settings",
                    description = "Search for system settings",
                    enabled = enabledProviders["system-settings"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("system-settings", it) },
                    onClick = onOpenSystemSettingsSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "text-utilities",
                    name = "Text utilities",
                    description = "Base64, URL tools",
                    enabled = enabledProviders["text-utilities"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("text-utilities", it) },
                    onClick = onOpenTextUtilitiesSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "termux",
                    name = "Termux Commands",
                    description = if (isTermuxInstalled) "Run commands in Termux" else "Install Termux to enable",
                    enabled = if (isTermuxInstalled) enabledProviders["termux"] ?: true else false,
                    onToggle = { enabled ->
                        if (isTermuxInstalled) {
                            hasTermuxPermission = TermuxProvider.hasRunCommandPermission(context)
                            if (enabled && !hasTermuxPermission) {
                                showTermuxPermissionDialog = true
                            } else {
                                settingsRepository.setProviderEnabled("termux", enabled)
                            }
                        }
                    },
                    onClick = if (isTermuxInstalled) onOpenTermuxSettings else null,
                    toggleEnabled = isTermuxInstalled,
                )
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit,
) {
    val translucentResultsEnabled by settingsRepository.translucentResultsEnabled.collectAsState()
    val backgroundOpacity by settingsRepository.backgroundOpacity.collectAsState()
    val backgroundBlurStrength by settingsRepository.backgroundBlurStrength.collectAsState()

    SettingsScaffold(
        title = "Appearance",
        onBack = onBack,
    ) {
        item {
            SettingsGroup {
                SettingsSwitch(
                    title = "Translucent results",
                    subtitle = "Make list items slightly see-through.",
                    checked = translucentResultsEnabled,
                    onCheckedChange = { settingsRepository.setTranslucentResultsEnabled(it) },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Background opacity",
                    subtitle = "Dim the wallpaper behind search.",
                    valueText = "${(backgroundOpacity * 100).roundToInt()}%",
                    value = backgroundOpacity,
                    onValueChange = { settingsRepository.setBackgroundOpacity(it) },
                    steps = 19,
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Background blur strength",
                    subtitle = "Control how soft the wallpaper appears.",
                    valueText = "${(backgroundBlurStrength * 100).roundToInt()}%",
                    value = backgroundBlurStrength,
                    onValueChange = { settingsRepository.setBackgroundBlurStrength(it) },
                    steps = 19,
                )
            }
        }
    }
}

@Composable
fun BehaviorSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit,
) {
    val motionPreferences by settingsRepository.motionPreferences.collectAsState()
    val activityIndicatorDelayMs by settingsRepository.activityIndicatorDelayMs.collectAsState()

    SettingsScaffold(
        title = "Behavior",
        onBack = onBack,
    ) {
        item {
            SettingsGroup {
                SettingsSwitch(
                    title = "Enable animations",
                    subtitle = "Turn off to remove most in-app transitions.",
                    checked = motionPreferences.animationsEnabled,
                    onCheckedChange = { settingsRepository.setAnimationsEnabled(it) },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Activity indicator delay",
                    subtitle = "Wait time before showing the loading spinner.",
                    valueText = "$activityIndicatorDelayMs ms",
                    value = activityIndicatorDelayMs.toFloat(),
                    onValueChange = { settingsRepository.setActivityIndicatorDelayMs(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    steps = 19,
                )
            }
        }
    }
}

@Composable
fun AliasesSettingsScreen(
    aliasRepository: AliasRepository,
    onBack: () -> Unit,
) {
    val aliasEntries by aliasRepository.aliases.collectAsState()

    SettingsScaffold(
        title = "Aliases",
        onBack = onBack,
    ) {
        item {
            SettingsGroup {
                if (aliasEntries.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(20.dp),
                        text = "No aliases yet. Long press a result in search to save one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        aliasEntries.forEachIndexed { index, entry ->
                            AliasRow(
                                alias = entry.alias,
                                summary = entry.target.summary,
                                onRemove = { aliasRepository.removeAlias(entry.alias) },
                            )
                            if (index < aliasEntries.lastIndex) {
                                SettingsDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRankingSettingsScreen(
    rankingRepository: ProviderRankingRepository,
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit,
) {
    val enabledProviders by settingsRepository.enabledProviders.collectAsState()

    SettingsScaffold(
        title = "Result ranking",
        onBack = onBack,
    ) {
        item {
            ProviderRankingSection(
                rankingRepository = rankingRepository,
                enabledProviders = enabledProviders,
            )
        }
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = {
            item {
                SettingsHeader(title = title, onBack = onBack)
            }
            content()
        },
    )
}

@Composable
private fun ProviderRow(
    id: String,
    name: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    toggleEnabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier =
                        Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, shape = MaterialTheme.shapes.extraSmall),
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = toggleEnabled,
            )
        }
    }
}

@Composable
private fun AliasRow(
    alias: String,
    summary: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alias,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onRemove) {
            Text(text = "Remove")
        }
    }
}

@Composable
private fun DefaultAssistantCard(
    appName: String,
    onRequestSetDefaultAssistant: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Set $appName as the default assistant",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "$appName opens instantly from the system assistant gesture.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequestSetDefaultAssistant) {
                Text(text = "Set as default")
            }
        }
    }
}

@Composable
private fun CompactAssistantCard(
    appName: String,
    onRequestSetDefaultAssistant: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set $appName as default assistant",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Enable the system gesture for quicker launch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRequestSetDefaultAssistant) {
                Text(text = "Set up")
            }
        }
    }
}
