package com.mrndstvndv.search.ui.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.mrndstvndv.search.ui.components.settings.SettingsSingleChoiceSegmentedButtons
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mrndstvndv.search.R
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.provider.settings.AppListType
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.ui.components.ContentDialog
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch
import com.mrndstvndv.search.util.FuzzyMatcher

@Composable
fun AppSearchSettingsScreen(
    repository: SettingsRepository<AppSearchSettings>,
    appListRepository: AppListRepository,
    onBack: () -> Unit,
) {
    val appSearchSettings by repository.flow.collectAsState()
    var isAddAppDialogOpen by remember { mutableStateOf(false) }

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
                SettingsHeader(title = stringResource(R.string.app_search_header), subtitle = stringResource(R.string.app_search_header_subtitle), onBack = onBack)
            }

            item {
                SettingsGroup {
                    SettingsSwitch(
                        title = stringResource(R.string.app_search_include_package_name),
                        subtitle = stringResource(R.string.app_search_include_package_name_subtitle),
                        checked = appSearchSettings.includePackageName,
                        onCheckedChange = { newValue ->
                            repository.update { it.copy(includePackageName = newValue) }
                        },
                    )
                    SettingsDivider()
                    SettingsSwitch(
                        title = stringResource(R.string.app_search_ai_queries),
                        subtitle = stringResource(R.string.app_search_ai_queries_subtitle),
                        checked = appSearchSettings.aiAssistantQueriesEnabled,
                        onCheckedChange = { newValue ->
                            repository.update { it.copy(aiAssistantQueriesEnabled = newValue) }
                        },
                    )
                }
            }

            item {
                val appListEnabled = appSearchSettings.appListEnabled
                val disabledAlpha = 0.38f

                SettingsSection(
                    title = stringResource(R.string.app_list_section_title),
                    subtitle = stringResource(R.string.app_list_section_subtitle),
                ) {
                    SettingsGroup {
                        // Master toggle
                        SettingsSwitch(
                            title = stringResource(R.string.app_list_show),
                            subtitle = stringResource(R.string.app_list_show_subtitle),
                            checked = appListEnabled,
                            onCheckedChange = { newValue ->
                                repository.update { it.copy(appListEnabled = newValue) }
                            },
                        )

                        SettingsDivider()

                        // App list type chooser
                        AppListTypeChooser(
                            selectedType = appSearchSettings.appListType,
                            enabled = appListEnabled,
                            onTypeSelected = { newType ->
                                repository.update { it.copy(appListType = newType) }
                            },
                        )

                        SettingsDivider()

                        // Center app list toggle
                        Box(
                            modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                        ) {
                            SettingsSwitch(
                                title = stringResource(R.string.app_list_center),
                                subtitle = stringResource(R.string.app_list_center_subtitle),
                                checked = appSearchSettings.centerAppList,
                                enabled = appListEnabled,
                                onCheckedChange = { newValue ->
                                    repository.update { it.copy(centerAppList = newValue) }
                                },
                            )
                        }

                        SettingsDivider()

                        // Hide when results visible toggle
                        Box(
                            modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                        ) {
                            SettingsSwitch(
                                title = stringResource(R.string.app_list_hide_when_results),
                                subtitle = stringResource(R.string.app_list_hide_when_results_subtitle),
                                checked = appSearchSettings.hideAppListWhenResultsVisible,
                                enabled = appListEnabled,
                                onCheckedChange = { newValue ->
                                    repository.update { it.copy(hideAppListWhenResultsVisible = newValue) }
                                },
                            )
                        }

                        SettingsDivider()

                        // Conditional options based on type
                        when (appSearchSettings.appListType) {
                            AppListType.RECENT -> {
                                Box(
                                    modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                                ) {
                                    SettingsSwitch(
                                        title = stringResource(R.string.app_list_reverse_order),
                                        subtitle = stringResource(R.string.app_list_reverse_recent_subtitle),
                                        checked = appSearchSettings.reverseRecentAppsOrder,
                                        enabled = appListEnabled,
                                        onCheckedChange = { newValue ->
                                            repository.update { it.copy(reverseRecentAppsOrder = newValue) }
                                        },
                                    )
                                }
                            }

                            AppListType.PINNED -> {
                                Box(
                                    modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                                ) {
                                    SettingsSwitch(
                                        title = stringResource(R.string.app_list_reverse_order),
                                        subtitle = stringResource(R.string.app_list_reverse_pinned_subtitle),
                                        checked = appSearchSettings.reversePinnedAppsOrder,
                                        enabled = appListEnabled,
                                        onCheckedChange = { newValue ->
                                            repository.update { it.copy(reversePinnedAppsOrder = newValue) }
                                        },
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )

                                // Pinned apps list
                                PinnedAppsSection(
                                    appListRepository = appListRepository,
                                    pinnedApps = appSearchSettings.pinnedApps,
                                    enabled = appListEnabled,
                                    onMoveUp = { packageName ->
                                        repository.update {
                                            val currentList = it.pinnedApps.toMutableList()
                                            val index = currentList.indexOf(packageName)
                                            if (index > 0) {
                                                val temp = currentList[index]
                                                currentList[index] = currentList[index - 1]
                                                currentList[index - 1] = temp
                                                it.copy(pinnedApps = currentList)
                                            } else {
                                                it
                                            }
                                        }
                                    },
                                    onMoveDown = { packageName ->
                                        repository.update {
                                            val currentList = it.pinnedApps.toMutableList()
                                            val index = currentList.indexOf(packageName)
                                            if (index >= 0 && index < currentList.size - 1) {
                                                val temp = currentList[index]
                                                currentList[index] = currentList[index + 1]
                                                currentList[index + 1] = temp
                                                it.copy(pinnedApps = currentList)
                                            } else {
                                                it
                                            }
                                        }
                                    },
                                    onRemove = { packageName -> repository.update { it.copy(pinnedApps = it.pinnedApps - packageName) } },
                                    onAddClick = { isAddAppDialogOpen = true },
                                )
                            }

                            AppListType.BOTH -> {
                                Box(
                                    modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                                ) {
                                    SettingsSwitch(
                                        title = stringResource(R.string.app_list_reverse_recent_order),
                                        subtitle = stringResource(R.string.app_list_reverse_recent_subtitle),
                                        checked = appSearchSettings.reverseRecentAppsOrder,
                                        enabled = appListEnabled,
                                        onCheckedChange = { newValue ->
                                            repository.update { it.copy(reverseRecentAppsOrder = newValue) }
                                        },
                                    )
                                }

                                SettingsDivider()

                                Box(
                                    modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                                ) {
                                    SettingsSwitch(
                                        title = stringResource(R.string.app_list_reverse_pinned_order),
                                        subtitle = stringResource(R.string.app_list_reverse_pinned_subtitle),
                                        checked = appSearchSettings.reversePinnedAppsOrder,
                                        enabled = appListEnabled,
                                        onCheckedChange = { newValue ->
                                            repository.update { it.copy(reversePinnedAppsOrder = newValue) }
                                        },
                                    )
                                }

                                SettingsDivider()

                                Box(
                                    modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                                ) {
                                    SettingsSwitch(
                                        title = stringResource(R.string.app_list_pinned_on_left),
                                        subtitle = stringResource(R.string.app_list_pinned_on_left_subtitle),
                                        checked = appSearchSettings.bothLayoutPinnedOnLeft,
                                        enabled = appListEnabled,
                                        onCheckedChange = { newValue ->
                                            repository.update { it.copy(bothLayoutPinnedOnLeft = newValue) }
                                        },
                                    )
                                }

                                SettingsDivider()

                                Box(
                                    modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                                ) {
                                    SettingsSwitch(
                                        title = stringResource(R.string.app_list_filter_pinned_from_recents),
                                        subtitle = stringResource(R.string.app_list_filter_pinned_from_recents_subtitle),
                                        checked = appSearchSettings.filterPinnedFromRecentsInBoth,
                                        enabled = appListEnabled,
                                        onCheckedChange = { newValue ->
                                            repository.update { it.copy(filterPinnedFromRecentsInBoth = newValue) }
                                        },
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )

                                // Pinned apps list (same as PINNED)
                                PinnedAppsSection(
                                    appListRepository = appListRepository,
                                    pinnedApps = appSearchSettings.pinnedApps,
                                    enabled = appListEnabled,
                                    onMoveUp = { packageName ->
                                        repository.update {
                                            val currentList = it.pinnedApps.toMutableList()
                                            val index = currentList.indexOf(packageName)
                                            if (index > 0) {
                                                val temp = currentList[index]
                                                currentList[index] = currentList[index - 1]
                                                currentList[index - 1] = temp
                                                it.copy(pinnedApps = currentList)
                                            } else {
                                                it
                                            }
                                        }
                                    },
                                    onMoveDown = { packageName ->
                                        repository.update {
                                            val currentList = it.pinnedApps.toMutableList()
                                            val index = currentList.indexOf(packageName)
                                            if (index >= 0 && index < currentList.size - 1) {
                                                val temp = currentList[index]
                                                currentList[index] = currentList[index + 1]
                                                currentList[index + 1] = temp
                                                it.copy(pinnedApps = currentList)
                                            } else {
                                                it
                                            }
                                        }
                                    },
                                    onRemove = { packageName -> repository.update { it.copy(pinnedApps = it.pinnedApps - packageName) } },
                                    onAddClick = { isAddAppDialogOpen = true },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isAddAppDialogOpen) {
        AddPinnedAppDialog(
            appListRepository = appListRepository,
            existingPinnedApps = appSearchSettings.pinnedApps,
            onDismiss = { isAddAppDialogOpen = false },
            onAddApp = { packageName ->
                repository.update { it.copy(pinnedApps = it.pinnedApps + packageName) }
                isAddAppDialogOpen = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListTypeChooser(
    selectedType: AppListType,
    enabled: Boolean,
    onTypeSelected: (AppListType) -> Unit,
) {
    val disabledAlpha = 0.38f

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .alpha(if (enabled) 1f else disabledAlpha),
    ) {
        Text(
            text = stringResource(R.string.app_list_type),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.app_list_type_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val options = listOf(AppListType.RECENT, AppListType.PINNED, AppListType.BOTH)
        SettingsSingleChoiceSegmentedButtons(
            options = options,
            selectedOption = selectedType,
            enabled = enabled,
            label = { type -> type.userFacingLabel() },
            onOptionSelected = onTypeSelected,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            showSelectedIcon = false,
        )
    }
}

@Composable
private fun PinnedAppsSection(
    appListRepository: AppListRepository,
    pinnedApps: List<String>,
    enabled: Boolean,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAddClick: () -> Unit,
) {
    val disabledAlpha = 0.38f
    val allApps by appListRepository.getAllApps().collectAsState()
    val pinnedAppInfos =
        remember(pinnedApps, allApps) {
            pinnedApps.mapNotNull { packageName ->
                allApps.find { it.packageName == packageName }
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else disabledAlpha),
    ) {
        if (pinnedApps.isEmpty()) {
            Text(
                text = stringResource(R.string.app_list_no_pinned_apps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            pinnedAppInfos.forEachIndexed { index, appInfo ->
                val appIcon by produceState<Bitmap?>(null, appInfo.packageName) {
                    value = appListRepository.getIcon(appInfo.packageName)
                }

                PinnedAppItem(
                    label = appInfo.label,
                    packageName = appInfo.packageName,
                    icon = appIcon,
                    isFirst = index == 0,
                    isLast = index == pinnedAppInfos.size - 1,
                    enabled = enabled,
                    onMoveUp = { onMoveUp(appInfo.packageName) },
                    onMoveDown = { onMoveDown(appInfo.packageName) },
                    onRemove = { onRemove(appInfo.packageName) },
                )

                if (index < pinnedAppInfos.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        TextButton(
            onClick = onAddClick,
            enabled = enabled,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(text = stringResource(R.string.app_list_add_app))
        }
    }
}

@Composable
private fun PinnedAppItem(
    label: String,
    packageName: String,
    icon: Bitmap?,
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon.asImageBitmap(),
                    contentDescription = label,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row {
            IconButton(
                onClick = onMoveUp,
                enabled = enabled && !isFirst,
                modifier = Modifier.width(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.move_up),
                    modifier = Modifier.size(16.dp),
                )
            }

            IconButton(
                onClick = onMoveDown,
                enabled = enabled && !isLast,
                modifier = Modifier.width(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = stringResource(R.string.move_down),
                    modifier = Modifier.size(16.dp),
                )
            }

            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.width(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.remove),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AddPinnedAppDialog(
    appListRepository: AppListRepository,
    existingPinnedApps: List<String>,
    onDismiss: () -> Unit,
    onAddApp: (packageName: String) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val allApps by appListRepository.getAllApps().collectAsState()

    LaunchedEffect(appListRepository) {
        appListRepository.initialize()
    }

    // Filter apps based on search query and exclude already pinned apps
    val filteredApps by remember(searchQuery, existingPinnedApps, allApps) {
        derivedStateOf {
            val query = searchQuery.trim()
            allApps
                .filter { it.packageName !in existingPinnedApps }
                .let { apps ->
                    if (query.isBlank()) {
                        apps
                    } else {
                        apps
                            .mapNotNull { app ->
                                val match = FuzzyMatcher.match(query, app.label)
                                if (match != null) app to match.score else null
                            }.sortedByDescending { it.second }
                            .map { it.first }
                    }
                }.take(20)
        }
    }

    ContentDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.app_list_add_pinned_app),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
        content = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.intent_search_apps)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )

            if (filteredApps.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) stringResource(R.string.app_list_all_pinned) else stringResource(R.string.app_list_no_apps_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Column(modifier = Modifier.heightIn(max = 450.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val appIcon by produceState<Bitmap?>(null, app.packageName) {
                            value = appListRepository.getIcon(app.packageName)
                        }

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onAddApp(app.packageName) }
                                    .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon!!.asImageBitmap(),
                                    contentDescription = app.label,
                                    modifier = Modifier.size(40.dp),
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                            Column {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (filteredApps.indexOf(app) < filteredApps.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    }
                }
            }
        },
    )
}
