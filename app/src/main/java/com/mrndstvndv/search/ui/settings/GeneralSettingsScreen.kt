package com.mrndstvndv.search.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
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
    onClose: () -> Unit
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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                SettingsHomeHeader(onClose = onClose)
            }

            if (!isDefaultAssistant) {
                item {
                    CompactAssistantCard(appName = appName, onRequestSetDefaultAssistant = onRequestSetDefaultAssistant)
                }
            }

            item {
                SettingsTileGroup {
                    SettingsTile(
                        icon = Icons.Rounded.Apps,
                        title = "Providers",
                        subtitle = providerSubtitle.ifEmpty { "Manage search sources" },
                        onClick = onOpenProviders
                    )
                    SettingsDivider()
                    SettingsTile(
                        icon = Icons.Rounded.Palette,
                        title = "Appearance",
                        subtitle = appearanceSubtitle,
                        onClick = onOpenAppearance
                    )
                }
            }

            item {
                SettingsTileGroup {
                    SettingsTile(
                        icon = Icons.Rounded.Speed,
                        title = "Behavior",
                        subtitle = behaviorSubtitle,
                        onClick = onOpenBehavior
                    )
                    SettingsDivider()
                    SettingsTile(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        title = "Aliases",
                        subtitle = aliasesSubtitle,
                        onClick = onOpenAliases
                    )
                    SettingsDivider()
                    SettingsTile(
                        icon = Icons.Rounded.BarChart,
                        title = "Result ranking",
                        subtitle = rankingSubtitle,
                        onClick = onOpenResultRanking
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
    onBack: () -> Unit
) {
    val enabledProviders by settingsRepository.enabledProviders.collectAsState()

    SettingsScaffold(
        title = "Providers",
        onBack = onBack
    ) {
        if (!isDefaultAssistant) {
            item {
                DefaultAssistantCard(appName = appName, onRequestSetDefaultAssistant = onRequestSetDefaultAssistant)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        item {
            SettingsCardGroup {
                ProviderRow(
                    id = "app-list",
                    name = "Applications",
                    description = "Search installed apps",
                    enabled = enabledProviders["app-list"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("app-list", it) },
                    onClick = onOpenAppSearchSettings
                )
                SettingsDivider()
                ProviderRow(
                    id = "web-search",
                    name = "Web search",
                    description = "Configure engines and defaults",
                    enabled = enabledProviders["web-search"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("web-search", it) },
                    onClick = onOpenWebSearchSettings
                )
                SettingsDivider()
                ProviderRow(
                    id = "file-search",
                    name = "Files & folders",
                    description = "Local device indexing",
                    enabled = enabledProviders["file-search"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("file-search", it) },
                    onClick = onOpenFileSearchSettings
                )
                SettingsDivider()
                ProviderRow(
                    id = "calculator",
                    name = "Calculator",
                    description = "Solve math expressions",
                    enabled = enabledProviders["calculator"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("calculator", it) }
                )
                SettingsDivider()
                ProviderRow(
                    id = "system-settings",
                    name = "System Settings",
                    description = "Search for system settings",
                    enabled = enabledProviders["system-settings"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("system-settings", it) },
                    onClick = onOpenSystemSettingsSettings
                )
                SettingsDivider()
                ProviderRow(
                    id = "text-utilities",
                    name = "Text utilities",
                    description = "Base64, URL tools",
                    enabled = enabledProviders["text-utilities"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("text-utilities", it) },
                    onClick = onOpenTextUtilitiesSettings
                )
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit
) {
    val translucentResultsEnabled by settingsRepository.translucentResultsEnabled.collectAsState()
    val backgroundOpacity by settingsRepository.backgroundOpacity.collectAsState()
    val backgroundBlurStrength by settingsRepository.backgroundBlurStrength.collectAsState()

    SettingsScaffold(
        title = "Appearance",
        onBack = onBack
    ) {
        item {
            SettingsCardGroup {
                SettingsToggleRow(
                    title = "Translucent results",
                    subtitle = "Make list items slightly see-through.",
                    checked = translucentResultsEnabled,
                    onCheckedChange = { settingsRepository.setTranslucentResultsEnabled(it) }
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Background opacity",
                    subtitle = "Dim the wallpaper behind search.",
                    valueText = "${(backgroundOpacity * 100).roundToInt()}%",
                    value = backgroundOpacity,
                    onValueChange = { settingsRepository.setBackgroundOpacity(it) },
                    steps = 19
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Background blur strength",
                    subtitle = "Control how soft the wallpaper appears.",
                    valueText = "${(backgroundBlurStrength * 100).roundToInt()}%",
                    value = backgroundBlurStrength,
                    onValueChange = { settingsRepository.setBackgroundBlurStrength(it) },
                    steps = 19
                )
            }
        }
    }
}

@Composable
fun BehaviorSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit
) {
    val motionPreferences by settingsRepository.motionPreferences.collectAsState()
    val activityIndicatorDelayMs by settingsRepository.activityIndicatorDelayMs.collectAsState()

    SettingsScaffold(
        title = "Behavior",
        onBack = onBack
    ) {
        item {
            SettingsCardGroup {
                SettingsToggleRow(
                    title = "Enable animations",
                    subtitle = "Turn off to remove most in-app transitions.",
                    checked = motionPreferences.animationsEnabled,
                    onCheckedChange = { settingsRepository.setAnimationsEnabled(it) }
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = "Activity indicator delay",
                    subtitle = "Wait time before showing the loading spinner.",
                    valueText = "${activityIndicatorDelayMs} ms",
                    value = activityIndicatorDelayMs.toFloat(),
                    onValueChange = { settingsRepository.setActivityIndicatorDelayMs(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    steps = 19
                )
            }
        }
    }
}

@Composable
fun AliasesSettingsScreen(
    aliasRepository: AliasRepository,
    onBack: () -> Unit
) {
    val aliasEntries by aliasRepository.aliases.collectAsState()

    SettingsScaffold(
        title = "Aliases",
        onBack = onBack
    ) {
        item {
            SettingsCardGroup {
                if (aliasEntries.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(20.dp),
                        text = "No aliases yet. Long press a result in search to save one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        aliasEntries.forEachIndexed { index, entry ->
                            AliasRow(
                                alias = entry.alias,
                                summary = entry.target.summary,
                                onRemove = { aliasRepository.removeAlias(entry.alias) }
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
    onBack: () -> Unit
) {
    val enabledProviders by settingsRepository.enabledProviders.collectAsState()

    SettingsScaffold(
        title = "Result ranking",
        onBack = onBack
    ) {
        item {
            ProviderRankingSection(
                rankingRepository = rankingRepository,
                enabledProviders = enabledProviders
            )
        }
    }
}

@Composable
private fun SettingsHomeHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close settings",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun SettingsTileGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .padding(end = 16.dp)
                    .clip(MaterialTheme.shapes.medium),
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = {
            item {
                SectionHeader(title = title, onBack = onBack)
            }
            content()
        }
    )
}

@Composable
private fun SectionHeader(
    title: String,
    onBack: () -> Unit
) {
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
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
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

@Composable
private fun ProviderRow(
    id: String,
    name: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant, shape = MaterialTheme.shapes.extraSmall)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
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

@Composable
private fun SettingsSliderRow(
    title: String,
    subtitle: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 9
) {
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
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge
            )
        }
        androidx.compose.material3.Slider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun AliasRow(
    alias: String,
    summary: String,
    onRemove: () -> Unit
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
                text = alias,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRemove) {
            Text(text = "Remove")
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun DefaultAssistantCard(
    appName: String,
    onRequestSetDefaultAssistant: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Set $appName as the default assistant",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "$appName opens instantly from the system assistant gesture.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onRequestSetDefaultAssistant: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Set $appName as default assistant",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Enable the system gesture for quicker launch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRequestSetDefaultAssistant) {
                Text(text = "Set up")
            }
        }
    }
}
