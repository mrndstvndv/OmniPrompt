package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.R
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.provider.intent.AppDiscovery
import com.mrndstvndv.search.provider.intent.AppInfo
import com.mrndstvndv.search.provider.intent.IntentConfig
import com.mrndstvndv.search.provider.intent.IntentExtra
import com.mrndstvndv.search.provider.intent.IntentOption
import com.mrndstvndv.search.provider.intent.ActivityOption
import com.mrndstvndv.search.provider.intent.IntentSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.ui.components.ContentDialog
import com.mrndstvndv.search.util.FuzzyMatcher
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

private enum class AddDialogStep {
    AppSelection,
    IntentSelection,
    ActivitySelection,
    Configuration
}

@Composable
fun IntentSettingsScreen(
    repository: SettingsRepository<IntentSettings>,
    appListRepository: AppListRepository,
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
        val config = configs[index]
        config.customIconPath?.let { path ->
            try {
                java.io.File(path).delete()
            } catch (e: Exception) {}
        }
        configs = configs.toMutableList().apply { removeAt(index) }
        saveSettings()
    }

    // Edit dialog
    editingConfig?.let { (index, config) ->
        IntentConfigEditDialog(
            appListRepository = appListRepository,
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
            appListRepository = appListRepository,
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
                    title = stringResource(R.string.provider_intent_launcher),
                    subtitle = stringResource(R.string.intent_header_subtitle),
                    onBack = onBack
                )
            }

            // Intents section
            item {
                SettingsSection(
                    title = stringResource(R.string.intent_section_intents),
                    subtitle = stringResource(R.string.intent_section_intents_subtitle),
                ) {
                    SettingsGroup {
                        if (configs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.intent_no_configured),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            configs.forEachIndexed { index, config ->
                                IntentConfigRow(
                                    appListRepository = appListRepository,
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
                            Text(text = stringResource(R.string.intent_add))
                        }
                    }
                }
            }

            // Info section
            item {
                SettingsSection(
                    title = stringResource(R.string.about),
                    subtitle = stringResource(R.string.intent_about_subtitle),
                ) {
                    SettingsGroup {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.intent_usage),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.intent_usage_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.intent_examples),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.intent_examples_detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.intent_custom_extras),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.intent_custom_extras_detail),
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
    appListRepository: AppListRepository,
    config: IntentConfig,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = config.customIconPath, key2 = config.packageName) {
        withContext(Dispatchers.IO) {
            value = when {
                !config.customIconPath.isNullOrEmpty() -> {
                    try {
                        android.graphics.BitmapFactory.decodeFile(config.customIconPath)
                    } catch (e: Exception) {
                        null
                    }
                }
                config.packageName.isNotEmpty() -> {
                    appListRepository.getIcon(config.packageName)
                }
                else -> null
            }
        }
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp)) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.small)
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
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
            }

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-3).dp, y = 3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(9.dp)
                )
            }
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
            val actionOrClassLabel = if (!config.className.isNullOrEmpty()) {
                config.className.substringAfterLast(".")
            } else {
                config.action.substringAfterLast(".")
            }
            Text(
                text = stringResource(
                    R.string.intent_action_package,
                    actionOrClassLabel,
                    config.packageName.ifEmpty { stringResource(R.string.intent_system) },
                ),
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
    appListRepository: AppListRepository,
    onDismiss: () -> Unit,
    onAdd: (IntentConfig) -> Unit,
) {
    val context = LocalContext.current
    val discovery = remember { AppDiscovery(context, appListRepository) }
    var currentStep by remember { mutableStateOf(AddDialogStep.AppSelection) }
    
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedIntent by remember { mutableStateOf<IntentOption?>(null) }
    var selectedActivity by remember { mutableStateOf<ActivityOption?>(null) }
    
    // Configuration fields
    var title by remember { mutableStateOf("") }
    var mimeType by remember { mutableStateOf<String?>(null) }
    var payloadTemplate by remember { mutableStateOf<String?>(null) }
    var extras by remember { mutableStateOf(listOf<IntentExtra>()) }
    var customIconPath by remember { mutableStateOf<String?>(null) }
    val anyMimeTypeLabel = stringResource(R.string.intent_any_mime_type)

    val handleDismiss = {
        customIconPath?.let { path ->
            try {
                java.io.File(path).delete()
            } catch (e: Exception) {}
        }
        onDismiss()
    }

    when (currentStep) {
        AddDialogStep.AppSelection -> {
            AppSelectionStep(
                discovery = discovery,
                onAppSelected = { app ->
                    selectedApp = app
                    if (app.packageName.isEmpty()) {
                        // Manual entry - skip to config
                        currentStep = AddDialogStep.Configuration
                        selectedIntent = IntentOption("android.intent.action.SEND", context.getString(R.string.intent_share_content))
                        selectedActivity = null
                        title = ""
                    } else {
                        currentStep = AddDialogStep.IntentSelection
                    }
                },
                onDismiss = handleDismiss
            )
        }
        AddDialogStep.IntentSelection -> {
            IntentSelectionStep(
                discovery = discovery,
                app = selectedApp!!,
                onIntentSelected = { intent ->
                    selectedIntent = intent
                    selectedActivity = null
                    title = selectedApp!!.name
                    mimeType = intent.mimeTypes.firstOrNull()
                    currentStep = AddDialogStep.Configuration
                },
                onActivityLauncherSelected = {
                    currentStep = AddDialogStep.ActivitySelection
                },
                onBack = { currentStep = AddDialogStep.AppSelection },
                onDismiss = handleDismiss
            )
        }
        AddDialogStep.ActivitySelection -> {
            ActivitySelectionStep(
                discovery = discovery,
                app = selectedApp!!,
                onActivitySelected = { activity ->
                    selectedActivity = activity
                    selectedIntent = null
                    title = "${selectedApp!!.name} - ${activity.label}"
                    mimeType = null
                    currentStep = AddDialogStep.Configuration
                },
                onBack = { currentStep = AddDialogStep.IntentSelection },
                onDismiss = handleDismiss
            )
        }
        AddDialogStep.Configuration -> {
            var manualPackageName by remember { mutableStateOf("") }
            var manualAction by remember { mutableStateOf("android.intent.action.SEND") }

            IntentConfigDialogContent(
                title = if (selectedApp!!.packageName.isEmpty()) stringResource(R.string.intent_manual_intent) else stringResource(R.string.intent_configure),
                title_ = title,
                onTitleChange = { title = it },
                packageName = selectedApp!!.packageName.ifEmpty { manualPackageName },
                onPackageNameChange = { manualPackageName = it },
                action = selectedIntent?.action ?: if (selectedActivity != null) "android.intent.action.MAIN" else manualAction,
                onActionChange = { manualAction = it },
                className = selectedActivity?.name,
                appListRepository = appListRepository,
                customIconPath = customIconPath,
                onCustomIconPathChange = { customIconPath = it },
                type = mimeType ?: "",
                onTypeChange = { mimeType = it },
                typeOptions = selectedIntent?.mimeTypes ?: emptyList(),
                anyMimeTypeLabel = anyMimeTypeLabel,
                payloadTemplate = payloadTemplate ?: "",
                onPayloadTemplateChange = { payloadTemplate = it.takeIf { it.isNotBlank() } },
                extras = extras,
                onExtrasChange = { extras = it },
                canSave = title.isNotBlank(),
                showRemove = false,
                onRemove = {},
                onBack = {
                    currentStep = when {
                        selectedApp!!.packageName.isEmpty() -> AddDialogStep.AppSelection
                        selectedActivity != null -> AddDialogStep.ActivitySelection
                        else -> AddDialogStep.IntentSelection
                    }
                },
                onDismiss = handleDismiss,
                onSave = {
                    onAdd(IntentConfig(
                        title = title.trim(),
                        packageName = selectedApp!!.packageName.ifEmpty { manualPackageName.trim() },
                        action = selectedIntent?.action ?: if (selectedActivity != null) "android.intent.action.MAIN" else manualAction,
                        className = selectedActivity?.name,
                        customIconPath = customIconPath,
                        type = mimeType?.takeIf { it != anyMimeTypeLabel },
                        payloadTemplate = payloadTemplate?.trim(),
                        extras = extras
                    ))
                }
            )
        }
    }
}

@Composable
private fun AppSelectionStep(
    discovery: AppDiscovery,
    onAppSelected: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val appsState by produceState<List<AppInfo>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            val apps = discovery.getTargetApps()
            Log.d("IntentSetup", "Discovered ${apps.size} apps")
            apps.forEach { Log.d("IntentSetup", "App: ${it.name} (${it.packageName})") }
            apps
        }
    }

    val filteredApps = remember(searchQuery, appsState) {
        val apps = appsState ?: emptyList()
        if (searchQuery.isBlank()) apps
        else apps
            .mapNotNull { app ->
                val nameMatch = FuzzyMatcher.match(searchQuery, app.name)
                val pkgMatch = FuzzyMatcher.match(searchQuery, app.packageName)
                val bestScore = listOfNotNull(nameMatch?.score, pkgMatch?.score).maxOrNull()
                if (bestScore != null) app to bestScore else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    ContentDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.intent_select_app),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.intent_select_app_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        buttons = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_apps_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                
                if (appsState == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.intent_searching_apps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.intent_no_compatible_apps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { 
                                onAppSelected(AppInfo("", context.getString(R.string.intent_manual_entry), null))
                            }) {
                                Text(stringResource(R.string.intent_configure_manually))
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.heightIn(max = 450.dp)) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAppSelected(app) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (app.icon != null) {
                                        Image(
                                            bitmap = app.icon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    } else {
                                        Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Outlined.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(app.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (filteredApps.indexOf(app) < filteredApps.lastIndex) {
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun IntentSelectionStep(
    discovery: AppDiscovery,
    app: AppInfo,
    onIntentSelected: (IntentOption) -> Unit,
    onActivityLauncherSelected: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val intents = remember(app) { discovery.getIntentsForApp(app.packageName) }

    ContentDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        buttons = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        content = {
            Text(
                text = stringResource(R.string.intent_select_supported_intent),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                intents.forEach { intent ->
                    Surface(
                        onClick = { onIntentSelected(intent) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 32.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(intent.label, style = MaterialTheme.typography.titleMedium)
                            Text(intent.action.substringAfterLast("."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                Surface(
                    onClick = onActivityLauncherSelected,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 32.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(stringResource(R.string.intent_activity_launcher), style = MaterialTheme.typography.titleMedium)
                        Text("MAIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (intents.isEmpty()) {
                    Text(stringResource(R.string.intent_no_compatible_intents), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
private fun ActivitySelectionStep(
    discovery: AppDiscovery,
    app: AppInfo,
    onActivitySelected: (ActivityOption) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val activitiesState by produceState<List<ActivityOption>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            discovery.getActivitiesForApp(app.packageName)
        }
    }

    val filteredActivities = remember(searchQuery, activitiesState) {
        val activities = activitiesState ?: emptyList()
        if (searchQuery.isBlank()) activities
        else activities
            .mapNotNull { activity ->
                val labelMatch = FuzzyMatcher.match(searchQuery, activity.label)
                val nameMatch = FuzzyMatcher.match(searchQuery, activity.name)
                val bestScore = listOfNotNull(labelMatch?.score, nameMatch?.score).maxOrNull()
                if (bestScore != null) activity to bestScore else null
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    ContentDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column {
                    Text(
                        text = stringResource(R.string.intent_select_activity),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        buttons = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_activities_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )

                if (activitiesState == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.intent_searching_activities), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (filteredActivities.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.intent_no_activities_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(modifier = Modifier.heightIn(max = 400.dp)) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filteredActivities, key = { it.name }) { activity ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onActivitySelected(activity) }
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(activity.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(activity.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (filteredActivities.indexOf(activity) < filteredActivities.lastIndex) {
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun IntentConfigEditDialog(
    appListRepository: AppListRepository,
    config: IntentConfig,
    onDismiss: () -> Unit,
    onSave: (IntentConfig) -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val discovery = remember { AppDiscovery(context, appListRepository) }
    val intentOptions = remember(config.packageName) {
        if (config.packageName.isNotEmpty()) {
            discovery.getIntentsForApp(config.packageName)
        } else {
            emptyList()
        }
    }
    val currentIntentOption = intentOptions.find { it.action == config.action }

    var title by remember { mutableStateOf(config.title) }
    var action by remember { mutableStateOf(config.action) }
    var mimeType by remember { mutableStateOf(config.type ?: "") }
    var className by remember { mutableStateOf(config.className ?: "") }
    var customIconPath by remember { mutableStateOf(config.customIconPath) }
    var payloadTemplate by remember { mutableStateOf(config.payloadTemplate ?: "") }
    var extras by remember { mutableStateOf(config.extras) }
    val anyMimeTypeLabel = stringResource(R.string.intent_any_mime_type)
    
    val initialCustomIconPath = remember { config.customIconPath }

    val canSave = title.isNotBlank()

    fun save() {
        if (!canSave) return
        val updated =
            config.copy(
                title = title.trim(),
                action = action,
                type = mimeType.takeIf { it.isNotBlank() && it != anyMimeTypeLabel },
                className = className.takeIf { it.isNotBlank() },
                customIconPath = customIconPath,
                payloadTemplate = payloadTemplate.trim().takeIf { it.isNotBlank() },
                extras = extras
            )
        if (initialCustomIconPath != null && initialCustomIconPath != customIconPath) {
            try {
                java.io.File(initialCustomIconPath).delete()
            } catch (e: Exception) {}
        }
        onSave(updated)
    }

    val handleDismiss = {
        if (customIconPath != initialCustomIconPath) {
            customIconPath?.let { path ->
                try {
                    java.io.File(path).delete()
                } catch (e: Exception) {}
            }
        }
        onDismiss()
    }

    IntentConfigDialogContent(
        title = stringResource(R.string.intent_edit),
        title_ = title,
        onTitleChange = { title = it },
        packageName = config.packageName,
        action = action,
        className = className,
        appListRepository = appListRepository,
        customIconPath = customIconPath,
        onCustomIconPathChange = { customIconPath = it },
        type = mimeType,
        onTypeChange = { mimeType = it },
        typeOptions = currentIntentOption?.mimeTypes ?: emptyList(),
        anyMimeTypeLabel = anyMimeTypeLabel,
        payloadTemplate = payloadTemplate,
        onPayloadTemplateChange = { payloadTemplate = it },
        extras = extras,
        onExtrasChange = { extras = it },
        canSave = canSave,
        showRemove = true,
        onRemove = onRemove,
        onBack = null,
        onDismiss = handleDismiss,
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
    onPackageNameChange: (String) -> Unit = {},
    action: String,
    onActionChange: (String) -> Unit = {},
    className: String? = null,
    appListRepository: AppListRepository,
    customIconPath: String?,
    onCustomIconPathChange: (String?) -> Unit,
    type: String,
    onTypeChange: (String) -> Unit,
    typeOptions: List<String>,
    anyMimeTypeLabel: String,
    payloadTemplate: String,
    onPayloadTemplateChange: (String) -> Unit,
    extras: List<IntentExtra>,
    onExtrasChange: (List<IntentExtra>) -> Unit,
    canSave: Boolean,
    showRemove: Boolean,
    onRemove: () -> Unit,
    onBack: (() -> Unit)?,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var typeExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }
    
    val standardActions = listOf(
        "android.intent.action.SEND",
        "android.intent.action.VIEW",
        "android.intent.action.SENDTO",
    )

    ContentDialog(
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
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
                            text = stringResource(R.string.remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSave,
                        enabled = canSave,
                    ) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val context = LocalContext.current
                var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
                
                LaunchedEffect(customIconPath, packageName) {
                    withContext(Dispatchers.IO) {
                        previewBitmap = when {
                            !customIconPath.isNullOrEmpty() -> {
                                try {
                                    android.graphics.BitmapFactory.decodeFile(customIconPath)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            packageName.isNotEmpty() -> {
                                appListRepository.getIcon(packageName)
                            }
                            else -> null
                        }
                    }
                }
                
                val iconPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: android.net.Uri? ->
                    if (uri != null) {
                        val savedPath = com.mrndstvndv.search.util.saveCustomIcon(context, uri)
                        if (savedPath != null) {
                            onCustomIconPathChange(savedPath)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { iconPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomStart)
                                .offset(x = (-4).dp, y = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = stringResource(R.string.intent_icon_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { iconPickerLauncher.launch("image/*") }) {
                                Text(stringResource(R.string.intent_select_icon))
                            }
                            if (!customIconPath.isNullOrEmpty()) {
                                TextButton(onClick = { onCustomIconPathChange(null) }) {
                                    Text(
                                        text = stringResource(R.string.intent_reset_icon),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title_,
                    onValueChange = onTitleChange,
                    label = { Text(stringResource(R.string.intent_label_title)) },
                    placeholder = { Text(stringResource(R.string.intent_placeholder_title)) },
                    supportingText = { Text(stringResource(R.string.intent_supporting_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = packageName,
                    onValueChange = onPackageNameChange,
                    label = { Text(stringResource(R.string.intent_label_package)) },
                    placeholder = { Text(stringResource(R.string.intent_placeholder_package)) },
                    readOnly = packageName.isNotEmpty() && onPackageNameChange == {},
                    supportingText = { Text(stringResource(R.string.intent_supporting_package)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (!className.isNullOrEmpty()) {
                    OutlinedTextField(
                        value = className,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.intent_label_class_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (onActionChange == {}) {
                    OutlinedTextField(
                        value = action.substringAfterLast("."),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.intent_label_action_auto)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = actionExpanded,
                        onExpandedChange = { actionExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = action.substringAfterLast("."),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.intent_label_action)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = actionExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = actionExpanded,
                            onDismissRequest = { actionExpanded = false }
                        ) {
                            standardActions.forEach { option ->
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
                }

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = type.ifEmpty { anyMimeTypeLabel },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.intent_label_mime_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        val options = (typeOptions + listOf("text/plain", "text/*", "*/*", anyMimeTypeLabel)).distinct()
                        options.forEach { option ->
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

                OutlinedTextField(
                    value = payloadTemplate,
                    onValueChange = onPayloadTemplateChange,
                    label = { Text(stringResource(R.string.intent_label_payload_template)) },
                    placeholder = { Text(stringResource(R.string.intent_placeholder_payload)) },
                    supportingText = { Text(stringResource(R.string.intent_supporting_payload)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.intent_custom_extras_label), style = MaterialTheme.typography.titleSmall)
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    extras.forEachIndexed { index, extra ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = extra.key,
                                    onValueChange = { key ->
                                        val newExtras = extras.toMutableList().apply {
                                            this[index] = extra.copy(key = key)
                                        }
                                        onExtrasChange(newExtras)
                                    },
                                    label = { Text(stringResource(R.string.intent_label_extra_key)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = extra.value,
                                    onValueChange = { value ->
                                        val newExtras = extras.toMutableList().apply {
                                            this[index] = extra.copy(value = value)
                                        }
                                        onExtrasChange(newExtras)
                                    },
                                    label = { Text(stringResource(R.string.intent_label_extra_value)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            IconButton(onClick = {
                                onExtrasChange(extras.toMutableList().apply { removeAt(index) })
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.cd_remove_extra), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    
                    TextButton(onClick = {
                        onExtrasChange(extras + IntentExtra("", ""))
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.intent_add_extra))
                    }
                }
            }
        },
    )
}
