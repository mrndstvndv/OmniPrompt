package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.Image
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
import androidx.core.graphics.drawable.toBitmap
import android.util.Log
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.provider.intent.AppDiscovery
import com.mrndstvndv.search.provider.intent.AppInfo
import com.mrndstvndv.search.provider.intent.IntentConfig
import com.mrndstvndv.search.provider.intent.IntentExtra
import com.mrndstvndv.search.provider.intent.IntentOption
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
                    title = stringResource(R.string.intent_header),
                    subtitle = "Launch apps by fuzzy searching titles.",
                    onBack = onBack
                )
            }

            // Intents section
            item {
                SettingsSection(
                    title = stringResource(R.string.intent_section_intents),
                    subtitle = "Define intents to launch apps.",
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
                    title = stringResource(R.string.intent_about),
                    subtitle = "How intent matching works.",
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
                                text = "• ytdl https://youtube.com/watch?v=... → Opens in YTDLnis\n" +
                                        "• open https://google.com → Opens in browser\n" +
                                        "• share Check this out! → Opens share sheet",
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
    config: IntentConfig,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
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
            Text(
                text = "${config.action.substringAfterLast(".")} • ${config.packageName.ifEmpty { "System" }}",
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
    
    // Configuration fields
    var title by remember { mutableStateOf("") }
    var mimeType by remember { mutableStateOf<String?>(null) }
    var payloadTemplate by remember { mutableStateOf<String?>(null) }
    var extras by remember { mutableStateOf(listOf<IntentExtra>()) }

    when (currentStep) {
        AddDialogStep.AppSelection -> {
            AppSelectionStep(
                discovery = discovery,
                onAppSelected = { app ->
                    selectedApp = app
                    if (app.packageName.isEmpty()) {
                        // Manual entry - skip to config
                        currentStep = AddDialogStep.Configuration
                        selectedIntent = IntentOption("android.intent.action.SEND", "Share content")
                        title = ""
                    } else {
                        currentStep = AddDialogStep.IntentSelection
                    }
                },
                onDismiss = onDismiss
            )
        }
        AddDialogStep.IntentSelection -> {
            IntentSelectionStep(
                discovery = discovery,
                app = selectedApp!!,
                onIntentSelected = { intent ->
                    selectedIntent = intent
                    title = selectedApp!!.name
                    mimeType = intent.mimeTypes.firstOrNull()
                    currentStep = AddDialogStep.Configuration
                },
                onBack = { currentStep = AddDialogStep.AppSelection },
                onDismiss = onDismiss
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
                action = selectedIntent?.action ?: manualAction,
                onActionChange = { manualAction = it },
                type = mimeType ?: "",
                onTypeChange = { mimeType = it },
                typeOptions = selectedIntent?.mimeTypes ?: emptyList(),
                payloadTemplate = payloadTemplate ?: "",
                onPayloadTemplateChange = { payloadTemplate = it.takeIf { it.isNotBlank() } },
                extras = extras,
                onExtrasChange = { extras = it },
                canSave = title.isNotBlank(),
                showRemove = false,
                onRemove = {},
                onBack = { currentStep = if (selectedApp!!.packageName.isEmpty()) AddDialogStep.AppSelection else AddDialogStep.IntentSelection },
                onDismiss = onDismiss,
                onSave = {
                    onAdd(IntentConfig(
                        title = title.trim(),
                        packageName = selectedApp!!.packageName.ifEmpty { manualPackageName.trim() },
                        action = selectedIntent?.action ?: manualAction,
                        type = mimeType?.takeIf { it != "(any)" },
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
                    text = "Select App",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Apps that support share/view/sendto",
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
                    placeholder = { Text(stringResource(R.string.intent_search_apps)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                
                if (appsState == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("Searching for apps...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No compatible apps found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { 
                                onAppSelected(AppInfo("", "Manual Entry", null))
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
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
                    Text("Cancel")
                }
            }
        },
        content = {
            Text(
                text = "Select an intent this app supports:",
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
                
                if (intents.isEmpty()) {
                    Text("No compatible intents found for this app.", color = MaterialTheme.colorScheme.error)
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
    var payloadTemplate by remember { mutableStateOf(config.payloadTemplate ?: "") }
    var extras by remember { mutableStateOf(config.extras) }

    val canSave = title.isNotBlank()

    fun save() {
        if (!canSave) return
        val updated =
            config.copy(
                title = title.trim(),
                action = action,
                type = mimeType.takeIf { it.isNotBlank() && it != "(any)" },
                payloadTemplate = payloadTemplate.trim().takeIf { it.isNotBlank() },
                extras = extras
            )
        onSave(updated)
    }

    IntentConfigDialogContent(
        title = stringResource(R.string.intent_edit),
        title_ = title,
        onTitleChange = { title = it },
        packageName = config.packageName,
        action = action,
        type = mimeType,
        onTypeChange = { mimeType = it },
        typeOptions = (currentIntentOption?.mimeTypes ?: emptyList()) + listOf("(any)"),
        payloadTemplate = payloadTemplate,
        onPayloadTemplateChange = { payloadTemplate = it },
        extras = extras,
        onExtrasChange = { extras = it },
        canSave = canSave,
        showRemove = true,
        onRemove = onRemove,
        onBack = null,
        onDismiss = onDismiss,
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
    type: String,
    onTypeChange: (String) -> Unit,
    typeOptions: List<String>,
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
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
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
                        Text("Cancel")
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
                        value = type.ifEmpty { "(any)" },
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
                        val options = (typeOptions + listOf("text/plain", "text/*", "*/*", "(any)")).distinct()
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
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove Extra", tint = MaterialTheme.colorScheme.error)
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
