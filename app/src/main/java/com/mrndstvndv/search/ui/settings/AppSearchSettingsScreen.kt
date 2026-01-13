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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.provider.settings.AppListType
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.ui.components.ScrimDialog
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch
import com.mrndstvndv.search.util.FuzzyMatcher

@Composable
fun AppSearchSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    appListRepository: AppListRepository,
    onBack: () -> Unit,
) {
    val appSearchSettings by settingsRepository.appSearchSettings.collectAsState()
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
                SettingsHeader(title = "Applications", subtitle = "Configure app search.", onBack = onBack)
            }

            item {
                SettingsGroup {
                    SettingsSwitch(
                        title = "Include package name",
                        subtitle = "Search apps by their package name (e.g. com.android.settings).",
                        checked = appSearchSettings.includePackageName,
                        onCheckedChange = {
                            settingsRepository.saveAppSearchSettings(
                                appSearchSettings.copy(includePackageName = it),
                            )
                        },
                    )
                    SettingsDivider()
                    SettingsSwitch(
                        title = "AI Assistant queries",
                        subtitle = "Send queries directly to AI apps like Gemini using \"ask gemini <query>\".",
                        checked = appSearchSettings.aiAssistantQueriesEnabled,
                        onCheckedChange = {
                            settingsRepository.saveAppSearchSettings(
                                appSearchSettings.copy(aiAssistantQueriesEnabled = it),
                            )
                        },
                    )
                }
            }

            item {
                val appListEnabled = appSearchSettings.appListEnabled
                val disabledAlpha = 0.38f

                SettingsSection(
                    title = "App List",
                    subtitle = "Configure the app list on the home screen.",
                ) {
                    SettingsGroup {
                        // Master toggle
                        SettingsSwitch(
                            title = "Show app list",
                            subtitle = "Display an app list on the home screen.",
                            checked = appListEnabled,
                            onCheckedChange = {
                                settingsRepository.saveAppSearchSettings(
                                    appSearchSettings.copy(appListEnabled = it),
                                )
                            },
                        )

                        SettingsDivider()

                        // App list type chooser
                        AppListTypeChooser(
                            selectedType = appSearchSettings.appListType,
                            enabled = appListEnabled,
                            onTypeSelected = { type ->
                                settingsRepository.saveAppSearchSettings(
                                    appSearchSettings.copy(appListType = type),
                                )
                            },
                        )

                        SettingsDivider()

                        // Center app list toggle
                        Box(
                            modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                        ) {
                            SettingsSwitch(
                                title = "Center app list",
                                subtitle = "Only works when the settings icon is hidden or inside the search bar.",
                                checked = appSearchSettings.centerAppList,
                                enabled = appListEnabled,
                                onCheckedChange = {
                                    settingsRepository.saveAppSearchSettings(
                                        appSearchSettings.copy(centerAppList = it),
                                    )
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
                                        title = "Reverse order",
                                        subtitle = "Place the most recently used app on the right side.",
                                        checked = appSearchSettings.reverseRecentAppsOrder,
                                        enabled = appListEnabled,
                                        onCheckedChange = {
                                            settingsRepository.saveAppSearchSettings(
                                                appSearchSettings.copy(reverseRecentAppsOrder = it),
                                            )
                                        },
                                    )
                                }
                            }

                            AppListType.PINNED -> {
                                Box(
                                    modifier = Modifier.alpha(if (appListEnabled) 1f else disabledAlpha),
                                ) {
                                    SettingsSwitch(
                                        title = "Reverse order",
                                        subtitle = "Display the first pinned app on the right side.",
                                        checked = appSearchSettings.reversePinnedAppsOrder,
                                        enabled = appListEnabled,
                                        onCheckedChange = {
                                            settingsRepository.saveAppSearchSettings(
                                                appSearchSettings.copy(reversePinnedAppsOrder = it),
                                            )
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
                                    onMoveUp = { packageName -> settingsRepository.movePinnedAppUp(packageName) },
                                    onMoveDown = { packageName -> settingsRepository.movePinnedAppDown(packageName) },
                                    onRemove = { packageName -> settingsRepository.removePinnedApp(packageName) },
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
                settingsRepository.addPinnedApp(packageName)
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
            text = "App list type",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Choose which apps to display.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val options = listOf(AppListType.RECENT, AppListType.PINNED)
        SingleChoiceSegmentedButtonRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        ) {
            options.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = type == selectedType,
                    onClick = {
                        if (type != selectedType && enabled) {
                            onTypeSelected(type)
                        }
                    },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(text = type.userFacingLabel())
                }
            }
        }
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
                text = "No pinned apps",
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
            Text(text = "Add App")
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
                    contentDescription = "Move up",
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
                    contentDescription = "Move down",
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
                    contentDescription = "Remove",
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

    ScrimDialog(onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Add Pinned App",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredApps.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) "All apps are already pinned" else "No apps found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                ) {
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
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape),
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
            }
        }
    }
}
