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
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mrndstvndv.search.R
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.settings.FirstResultHighlightMode
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.settings.SearchBarPosition
import com.mrndstvndv.search.provider.settings.SettingsIconPosition
import com.mrndstvndv.search.provider.termux.TermuxProvider
import com.mrndstvndv.search.ui.components.TermuxPermissionDialog
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsNavigationRow
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import com.mrndstvndv.search.ui.components.settings.SettingsSingleChoiceSegmentedButtons
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
    onOpenAbout: () -> Unit,
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

    val providerSubtitle = stringResource(R.string.settings_providers_subtitle)
    val appearanceSubtitle = stringResource(R.string.settings_appearance_subtitle)
    val behaviorSubtitle = stringResource(R.string.settings_behavior_subtitle)
    val aliasesSubtitle = stringResource(R.string.settings_aliases_subtitle)
    val rankingSubtitle = stringResource(R.string.settings_result_ranking_subtitle)
    val aboutSubtitle = stringResource(R.string.settings_about_subtitle)

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
                SettingsHeader(title = stringResource(R.string.settings), onBack = onClose)
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
                        title = stringResource(R.string.settings_providers),
                        subtitle = providerSubtitle,
                        onClick = onOpenProviders,
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.settings_appearance),
                        subtitle = appearanceSubtitle,
                        onClick = onOpenAppearance,
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        icon = Icons.Rounded.Speed,
                        title = stringResource(R.string.settings_behavior),
                        subtitle = behaviorSubtitle,
                        onClick = onOpenBehavior,
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.AutoMirrored.Rounded.Label,
                        title = stringResource(R.string.settings_aliases),
                        subtitle = aliasesSubtitle,
                        onClick = onOpenAliases,
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Rounded.BarChart,
                        title = stringResource(R.string.settings_result_ranking),
                        subtitle = rankingSubtitle,
                        onClick = onOpenResultRanking,
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsNavigationRow(
                        icon = Icons.Rounded.CloudUpload,
                        title = stringResource(R.string.settings_backup_restore),
                        subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                        onClick = onOpenBackupRestore,
                    )
                    SettingsDivider()
                    SettingsNavigationRow(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.settings_about),
                        subtitle = aboutSubtitle,
                        onClick = onOpenAbout,
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
    onOpenIntentSettings: () -> Unit,
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
                Toast.makeText(context, context.getString(R.string.permission_contacts_required), Toast.LENGTH_SHORT).show()
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
        title = stringResource(R.string.settings_providers),
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
                    name = stringResource(R.string.provider_applications),
                    description = stringResource(R.string.provider_desc_applications),
                    enabled = enabledProviders["app-list"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("app-list", it) },
                    onClick = onOpenAppSearchSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "contacts",
                    name = stringResource(R.string.provider_contacts),
                    description = stringResource(R.string.provider_desc_contacts),
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
                    name = stringResource(R.string.provider_web_search),
                    description = stringResource(R.string.provider_desc_web_search),
                    enabled = enabledProviders["web-search"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("web-search", it) },
                    onClick = onOpenWebSearchSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "file-search",
                    name = stringResource(R.string.provider_file_search),
                    description = stringResource(R.string.provider_desc_file_search),
                    enabled = enabledProviders["file-search"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("file-search", it) },
                    onClick = onOpenFileSearchSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "calculator",
                    name = stringResource(R.string.provider_calculator),
                    description = stringResource(R.string.provider_desc_calculator),
                    enabled = enabledProviders["calculator"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("calculator", it) },
                )
                SettingsDivider()
                ProviderRow(
                    id = "system-settings",
                    name = stringResource(R.string.provider_system_settings),
                    description = stringResource(R.string.provider_desc_system_settings),
                    enabled = enabledProviders["system-settings"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("system-settings", it) },
                    onClick = onOpenSystemSettingsSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "text-utilities",
                    name = stringResource(R.string.provider_text_utilities),
                    description = stringResource(R.string.provider_desc_text_utilities),
                    enabled = enabledProviders["text-utilities"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("text-utilities", it) },
                    onClick = onOpenTextUtilitiesSettings,
                )
                SettingsDivider()
                ProviderRow(
                    id = "termux",
                    name = stringResource(R.string.provider_termux),
                    description = if (isTermuxInstalled) stringResource(R.string.provider_desc_termux_installed) else stringResource(R.string.provider_desc_termux_not_installed),
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
                SettingsDivider()
                ProviderRow(
                    id = "intent",
                    name = stringResource(R.string.provider_intent_launcher),
                    description = stringResource(R.string.provider_desc_intent_launcher),
                    enabled = enabledProviders["intent"] ?: true,
                    onToggle = { settingsRepository.setProviderEnabled("intent", it) },
                    onClick = onOpenIntentSettings,
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
    val settingsIconPosition by settingsRepository.settingsIconPosition.collectAsState()
    val searchBarPosition by settingsRepository.searchBarPosition.collectAsState()
    val firstResultHighlightEnabled by settingsRepository.firstResultHighlightEnabled.collectAsState()
    val firstResultHighlightMode by settingsRepository.firstResultHighlightMode.collectAsState()
    val firstResultBorderThickness by settingsRepository.firstResultBorderThickness.collectAsState()
    val firstResultChangeAnimationEnabled by settingsRepository.firstResultChangeAnimationEnabled.collectAsState()
    val firstResultColorAnimationEnabled by settingsRepository.firstResultColorAnimationEnabled.collectAsState()
    val alwaysShowEnterBadge by settingsRepository.alwaysShowEnterBadge.collectAsState()

    SettingsScaffold(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
    ) {
        item {
            SettingsSection(
                title = stringResource(R.string.appearance_section_results),
                subtitle = stringResource(R.string.appearance_section_results_subtitle),
            ) {
                SettingsGroup {
                    SettingsSwitch(
                        title = stringResource(R.string.appearance_translucent_results),
                        subtitle = stringResource(R.string.appearance_translucent_results_subtitle),
                        checked = translucentResultsEnabled,
                        onCheckedChange = { settingsRepository.setTranslucentResultsEnabled(it) },
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = stringResource(R.string.appearance_section_default_action),
                subtitle = stringResource(R.string.appearance_section_default_action_subtitle),
            ) {
                val highlightControlsAlpha = if (firstResultHighlightEnabled) 1f else 0.38f
                val colorPulseControlsAlpha = if (firstResultHighlightEnabled && firstResultChangeAnimationEnabled) 1f else 0.38f

                SettingsGroup {
                    SettingsSwitch(
                        title = stringResource(R.string.appearance_highlight_first_result),
                        subtitle = stringResource(R.string.appearance_highlight_first_result_subtitle),
                        checked = firstResultHighlightEnabled,
                        onCheckedChange = { settingsRepository.setFirstResultHighlightEnabled(it) },
                    )
                    SettingsDivider()
                    Box(modifier = Modifier.alpha(highlightControlsAlpha)) {
                        FirstResultHighlightModeChooser(
                            selectedMode = firstResultHighlightMode,
                            enabled = firstResultHighlightEnabled,
                            onModeSelected = settingsRepository::setFirstResultHighlightMode,
                        )
                    }
                    SettingsDivider()
                    Box(modifier = Modifier.alpha(highlightControlsAlpha)) {
                        SettingsSliderRow(
                            title = stringResource(R.string.appearance_first_result_border_thickness),
                            subtitle = stringResource(R.string.appearance_first_result_border_thickness_subtitle),
                            value = firstResultBorderThickness,
                            onValueChange = settingsRepository::setFirstResultBorderThickness,
                            valueRange = 0.5f..3f,
                            steps = 9,
                            valueText = stringResource(R.string.value_dp, firstResultBorderThickness),
                            enabled = firstResultHighlightEnabled,
                        )
                    }
                    SettingsDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.appearance_animate_first_result_changes),
                        subtitle = stringResource(R.string.appearance_animate_first_result_changes_subtitle),
                        checked = firstResultChangeAnimationEnabled,
                        onCheckedChange = { settingsRepository.setFirstResultChangeAnimationEnabled(it) },
                    )
                    SettingsDivider()
                    Box(modifier = Modifier.alpha(colorPulseControlsAlpha)) {
                        SettingsSwitch(
                            title = stringResource(R.string.appearance_animate_first_result_color),
                            subtitle = stringResource(R.string.appearance_animate_first_result_color_subtitle),
                            checked = firstResultColorAnimationEnabled,
                            enabled = firstResultHighlightEnabled && firstResultChangeAnimationEnabled,
                            onCheckedChange = { settingsRepository.setFirstResultColorAnimationEnabled(it) },
                        )
                    }
                    SettingsDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.appearance_always_show_enter_badge),
                        subtitle = stringResource(R.string.appearance_always_show_enter_badge_subtitle),
                        checked = alwaysShowEnterBadge,
                        onCheckedChange = { settingsRepository.setAlwaysShowEnterBadge(it) },
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = stringResource(R.string.appearance_section_background),
                subtitle = stringResource(R.string.appearance_section_background_subtitle),
            ) {
                SettingsGroup {
                    SettingsSliderRow(
                        title = stringResource(R.string.appearance_background_opacity),
                        subtitle = stringResource(R.string.appearance_background_opacity_subtitle),
                        valueText = stringResource(R.string.appearance_opacity_value, (backgroundOpacity * 100).roundToInt()),
                        value = backgroundOpacity,
                        onValueChange = { settingsRepository.setBackgroundOpacity(it) },
                        steps = 19,
                    )
                    SettingsDivider()
                    SettingsSliderRow(
                        title = stringResource(R.string.appearance_background_blur),
                        subtitle = stringResource(R.string.appearance_background_blur_subtitle),
                        valueText = stringResource(R.string.appearance_opacity_value, (backgroundBlurStrength * 100).roundToInt()),
                        value = backgroundBlurStrength,
                        onValueChange = { settingsRepository.setBackgroundBlurStrength(it) },
                        steps = 19,
                    )
                }
            }
        }

        item {
            SettingsSection(
                title = stringResource(R.string.appearance_section_layout),
                subtitle = stringResource(R.string.appearance_section_layout_subtitle),
            ) {
                SettingsGroup {
                    SettingsIconPositionChooser(
                        selectedPosition = settingsIconPosition,
                        onPositionSelected = settingsRepository::setSettingsIconPosition,
                    )
                    SettingsDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.appearance_search_bar_bottom),
                        subtitle = stringResource(R.string.appearance_search_bar_bottom_subtitle),
                        checked = searchBarPosition == SearchBarPosition.BOTTOM,
                        onCheckedChange = { isBottom ->
                            settingsRepository.setSearchBarPosition(
                                if (isBottom) SearchBarPosition.BOTTOM else SearchBarPosition.TOP,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FirstResultHighlightModeChooser(
    selectedMode: FirstResultHighlightMode,
    enabled: Boolean,
    onModeSelected: (FirstResultHighlightMode) -> Unit,
) {
    val context = LocalContext.current
    val options = listOf(
        FirstResultHighlightMode.SUBTLE,
        FirstResultHighlightMode.BALANCED,
        FirstResultHighlightMode.STRONG,
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.appearance_first_result_highlight),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.appearance_first_result_highlight_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSingleChoiceSegmentedButtons(
            options = options,
            selectedOption = selectedMode,
            enabled = enabled,
            label = { mode -> context.getString(mode.labelResId) },
            onOptionSelected = onModeSelected,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            showSelectedIcon = false,
        )
    }
}

@Composable
private fun SettingsIconPositionChooser(
    selectedPosition: SettingsIconPosition,
    onPositionSelected: (SettingsIconPosition) -> Unit,
) {
    val context = LocalContext.current
    val options = listOf(SettingsIconPosition.BELOW, SettingsIconPosition.INSIDE, SettingsIconPosition.OFF)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.appearance_settings_icon),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.appearance_settings_icon_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsSingleChoiceSegmentedButtons(
            options = options,
            selectedOption = selectedPosition,
            enabled = true,
            label = { position -> context.getString(position.labelResId) },
            onOptionSelected = onPositionSelected,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            showSelectedIcon = false,
        )
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
        title = stringResource(R.string.settings_behavior),
        onBack = onBack,
    ) {
        item {
            SettingsGroup {
                SettingsSwitch(
                    title = stringResource(R.string.behavior_enable_animations),
                    subtitle = stringResource(R.string.behavior_enable_animations_subtitle),
                    checked = motionPreferences.animationsEnabled,
                    onCheckedChange = { settingsRepository.setAnimationsEnabled(it) },
                )
                SettingsDivider()
                SettingsSliderRow(
                    title = stringResource(R.string.behavior_activity_indicator_delay),
                    subtitle = stringResource(R.string.behavior_activity_indicator_delay_subtitle),
                    valueText = stringResource(R.string.value_milliseconds, activityIndicatorDelayMs),
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
        title = stringResource(R.string.settings_aliases),
        onBack = onBack,
    ) {
        item {
            SettingsGroup {
                if (aliasEntries.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(20.dp),
                        text = stringResource(R.string.settings_aliases_empty),
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
        title = stringResource(R.string.settings_result_ranking),
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
            Text(text = stringResource(R.string.remove))
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
                text = stringResource(R.string.assistant_set_default, appName),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.assistant_set_default_detail, appName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRequestSetDefaultAssistant) {
                Text(text = stringResource(R.string.assistant_set_as_default))
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
                    text = stringResource(R.string.assistant_set_compact, appName),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.assistant_set_compact_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onRequestSetDefaultAssistant) {
                Text(text = stringResource(R.string.assistant_set_up))
            }
        }
    }
}
