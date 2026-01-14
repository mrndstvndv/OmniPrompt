package com.mrndstvndv.search

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mrndstvndv.search.BuildConfig
import com.mrndstvndv.search.alias.AliasCreationCandidate
import com.mrndstvndv.search.alias.AliasEntry
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.alias.AppLaunchAliasTarget
import com.mrndstvndv.search.alias.WebSearchAliasTarget
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.apps.AppListProvider
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.provider.apps.PinnedAppsRepository
import com.mrndstvndv.search.provider.apps.RecentAppsRepository
import com.mrndstvndv.search.provider.calculator.CalculatorProvider
import com.mrndstvndv.search.provider.contacts.ContactsProvider
import com.mrndstvndv.search.provider.contacts.ContactsRepository
import com.mrndstvndv.search.provider.contacts.PhoneNumber
import com.mrndstvndv.search.provider.files.FileSearchProvider
import com.mrndstvndv.search.provider.files.FileSearchRepository
import com.mrndstvndv.search.provider.files.FileThumbnailRepository
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.AppListType
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.settings.SettingsIconPosition
import com.mrndstvndv.search.provider.settings.WebSearchSettings
import com.mrndstvndv.search.provider.system.DeveloperSettingsManager
import com.mrndstvndv.search.provider.system.SettingsProvider
import com.mrndstvndv.search.provider.termux.TermuxProvider
import com.mrndstvndv.search.provider.text.TextUtilitiesProvider
import com.mrndstvndv.search.provider.web.WebSearchProvider
import com.mrndstvndv.search.ui.components.AppListRow
import com.mrndstvndv.search.ui.components.ContactActionData
import com.mrndstvndv.search.ui.components.ContactActionSheet
import com.mrndstvndv.search.ui.components.ItemsList
import com.mrndstvndv.search.ui.components.RecentAppsList
import com.mrndstvndv.search.ui.components.SearchField
import com.mrndstvndv.search.ui.settings.AliasCreationDialog
import com.mrndstvndv.search.ui.theme.SearchTheme
import com.mrndstvndv.search.ui.theme.motionAwareVisibility
import com.mrndstvndv.search.ui.theme.rememberMotionAwareFloat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    companion object {
        private const val MAX_BACKGROUND_BLUR_RADIUS = 80
    }

    private val defaultAppIconSize by lazy { resources.getDimensionPixelSize(android.R.dimen.app_icon_size) }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val textState = remember { mutableStateOf(TextFieldValue("")) }
            val focusRequester = remember { FocusRequester() }
            val coroutineScope = rememberCoroutineScope()
            val settingsRepository = remember(this@MainActivity) { ProviderSettingsRepository(this@MainActivity, coroutineScope) }
            val aliasRepository = remember(this@MainActivity) { AliasRepository(this@MainActivity, coroutineScope) }
            val aliasEntries by aliasRepository.aliases.collectAsState()
            val webSearchSettings by settingsRepository.webSearchSettings.collectAsState()
            val appSearchSettings by settingsRepository.appSearchSettings.collectAsState()
            val translucentResultsEnabled by settingsRepository.translucentResultsEnabled.collectAsState()
            val backgroundOpacity by settingsRepository.backgroundOpacity.collectAsState()
            val backgroundBlurStrength by settingsRepository.backgroundBlurStrength.collectAsState()
            val activityIndicatorDelayMs by settingsRepository.activityIndicatorDelayMs.collectAsState()
            val motionPreferences by settingsRepository.motionPreferences.collectAsState()
            val settingsIconPosition by settingsRepository.settingsIconPosition.collectAsState()
            val enabledProviders by settingsRepository.enabledProviders.collectAsState()

            LaunchedEffect(backgroundBlurStrength) {
                applyWindowBlur(backgroundBlurStrength)
            }

            val fileSearchRepository = remember(this@MainActivity) { FileSearchRepository.getInstance(this@MainActivity) }
            val fileThumbnailRepository = remember(this@MainActivity) { FileThumbnailRepository.getInstance(this@MainActivity) }
            val contactsRepository = remember(this@MainActivity) { ContactsRepository.getInstance(this@MainActivity) }
            val rankingRepository = remember(this@MainActivity) { ProviderRankingRepository.getInstance(this@MainActivity, coroutineScope) }
            val appListRepository = remember(this@MainActivity) { AppListRepository.getInstance(this@MainActivity, defaultAppIconSize) }
            val recentAppsRepository =
                remember(this@MainActivity) {
                    RecentAppsRepository(this@MainActivity, defaultAppIconSize)
                }
            val pinnedAppsRepository =
                remember(this@MainActivity, settingsRepository) {
                    PinnedAppsRepository(this@MainActivity, settingsRepository, defaultAppIconSize)
                }
            val developerSettingsManager = remember(this@MainActivity) { DeveloperSettingsManager.getInstance(this@MainActivity) }
            val providerOrder by rankingRepository.providerOrder.collectAsState()
            val useFrequencyRanking by rankingRepository.useFrequencyRanking.collectAsState()
            val providers =
                remember(this@MainActivity) {
                    buildList {
                        add(AppListProvider(this@MainActivity, settingsRepository, appListRepository))
                        add(SettingsProvider(this@MainActivity, settingsRepository, developerSettingsManager))
                        add(CalculatorProvider(this@MainActivity))
                        add(TextUtilitiesProvider(this@MainActivity, settingsRepository))
                        add(FileSearchProvider(this@MainActivity, settingsRepository, fileSearchRepository, fileThumbnailRepository))
                        add(ContactsProvider(settingsRepository, contactsRepository))
                        add(WebSearchProvider(this@MainActivity, settingsRepository))
                        add(TermuxProvider(this@MainActivity, settingsRepository))
                    }
                }

            // Pre-initialize heavy providers on first composition
            LaunchedEffect(Unit) {
                withContext(Dispatchers.Default) {
                    providers.forEach { it.initialize() }
                    appListRepository.initialize()
                }
            }

            // Sync-on-open: trigger incremental file sync if enabled and enough time has passed
            val fileSearchSettings by settingsRepository.fileSearchSettings.collectAsState()
            val systemSettingsSettings by settingsRepository.systemSettingsSettings.collectAsState()

            // Initialize developer settings manager if feature is enabled
            LaunchedEffect(systemSettingsSettings.developerToggleEnabled) {
                if (systemSettingsSettings.developerToggleEnabled) {
                    developerSettingsManager.registerListeners()
                    developerSettingsManager.refreshStatus()
                }
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val settings = settingsRepository.fileSearchSettings.value
                    if (settings.syncOnAppOpen && settings.hasEnabledRoots()) {
                        val lastSync = settings.lastSyncTimestamp
                        val minGap = 60_000L // 1 minute
                        if (System.currentTimeMillis() - lastSync > minGap) {
                            fileSearchRepository.triggerImmediateSync()
                        }
                    }
                    // Also ensure periodic sync is scheduled based on current settings
                    if (settings.syncIntervalMinutes > 0 && settings.hasEnabledRoots()) {
                        fileSearchRepository.schedulePeriodicSync(settings.syncIntervalMinutes)
                    }
                }
            }

            val providerResults = remember { mutableStateListOf<ProviderResult>() }
            var shouldShowResults by remember { mutableStateOf(false) }
            var pendingQueryJob by remember { mutableStateOf<Job?>(null) }
            var refreshTrigger by remember { mutableStateOf(0) }

            // Listen for provider refresh signals
            LaunchedEffect(providers) {
                merge(*providers.map { it.refreshSignal }.toTypedArray())
                    .collect { refreshTrigger++ }
            }

            var aliasDialogCandidate by remember { mutableStateOf<AliasCreationCandidate?>(null) }
            var aliasDialogValue by remember { mutableStateOf("") }
            var aliasDialogError by remember { mutableStateOf<String?>(null) }
            var isPerformingAction by remember { mutableStateOf(false) }
            var showLoadingOverlay by remember { mutableStateOf(false) }
            var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
            var contactActionData by remember { mutableStateOf<ContactActionData?>(null) }
            var currentNormalizedQuery by remember { mutableStateOf("") }

            fun startPendingAction(result: ProviderResult?) {
                val action = result?.onSelect ?: return
                if (isPerformingAction) return
                isPerformingAction = true
                // Track result usage frequency when result is selected
                rankingRepository.incrementResultUsage(result.id, currentNormalizedQuery)
                pendingAction = PendingAction(action, result.keepOverlayUntilExit)
            }

            fun ensureTrailingSpace(input: String): String {
                val trimmed = input.trimEnd()
                return if (trimmed.isEmpty()) " " else "$trimmed "
            }

            fun handleResultSelection(result: ProviderResult?): Boolean {
                val candidate = result ?: return false
                val prefillQuery = candidate.extras[TextUtilitiesProvider.PREFILL_QUERY_EXTRA] as? String
                if (prefillQuery != null) {
                    val completedPrefill = ensureTrailingSpace(prefillQuery)
                    textState.value =
                        TextFieldValue(
                            text = completedPrefill,
                            selection = TextRange(completedPrefill.length),
                        )
                    shouldShowResults = true
                    return true
                }
                // Handle contacts specially - show action sheet
                if (candidate.providerId == "contacts") {
                    @Suppress("UNCHECKED_CAST")
                    val phoneNumbers = candidate.extras[ContactsProvider.EXTRA_PHONE_NUMBERS] as? List<PhoneNumber> ?: emptyList()
                    val displayName = candidate.extras[ContactsProvider.EXTRA_DISPLAY_NAME] as? String ?: candidate.title
                    val isSimNumber = candidate.extras[ContactsProvider.EXTRA_IS_SIM_NUMBER] as? Boolean ?: false
                    contactActionData =
                        ContactActionData(
                            contactId = candidate.extras[ContactsProvider.EXTRA_CONTACT_ID] as? String,
                            lookupKey = candidate.extras[ContactsProvider.EXTRA_LOOKUP_KEY] as? String,
                            displayName = displayName,
                            phoneNumbers = phoneNumbers,
                            isSimNumber = isSimNumber,
                        )
                    // Track usage for contacts too
                    rankingRepository.incrementResultUsage(candidate.id, currentNormalizedQuery)
                    return true
                }
                if (candidate.onSelect != null) {
                    startPendingAction(candidate)
                    return true
                }
                return false
            }

            LaunchedEffect(textState.value.text, aliasEntries, webSearchSettings, refreshTrigger) {
                // Cancel previous query job to debounce typing
                pendingQueryJob?.cancel()

                pendingQueryJob =
                    launch {
                        // Minimal debounce: 50ms to reduce jank while still batching keystrokes
                        delay(50)

                        val currentText = textState.value.text
                        val match = aliasRepository.matchAlias(currentText)
                        val normalizedText = match?.remainingQuery ?: currentText
                        currentNormalizedQuery = normalizedText
                        val query = Query(normalizedText, originalText = currentText)

                        val aggregated = mutableListOf<ProviderResult>()
                        val seenIds = mutableSetOf<String>()
                        val matchingProviders =
                            providers
                                .filter { enabledProviders[it.id] ?: true }
                                .filter { it.canHandle(query) }
                        val aliasResult = match?.let { buildAliasResult(it.entry, normalizedText, webSearchSettings) }

                        // Use supervisorScope to isolate provider failures
                        supervisorScope {
                            matchingProviders.forEach { provider ->
                                launch {
                                    try {
                                        val results = withContext(Dispatchers.IO) { provider.query(query) }
                                        val newItems = results.filterNot { seenIds.contains(it.id) }
                                        if (newItems.isNotEmpty()) {
                                            newItems.forEach { seenIds.add(it.id) }
                                            aggregated += newItems
                                        }
                                    } catch (error: Exception) {
                                        // Silently ignore individual provider failures
                                    }
                                }
                            }
                        }

                        // Final update after all providers complete - only if results changed
                        val filtered =
                            match?.entry?.target?.let { aliasTarget ->
                                aggregated.filterNot { it.aliasTarget == aliasTarget }
                            } ?: aggregated

                        // Sort results:
                        // If frequency ranking enabled:
                        //   - Items with usage frequency appear first (sorted by frequency)
                        //   - Items with no usage follow provider ranking
                        // Otherwise: sort purely by provider ranking
                        val sortedResults =
                            if (useFrequencyRanking) {
                                filtered.sortedWith(
                                    compareBy(
                                        { result ->
                                            // Primary: items with frequency (0) come first, then items without (-1)
                                            if (rankingRepository.getResultFrequency(result.id, normalizedText) > 0) 0 else 1
                                        },
                                        { result ->
                                            // Secondary: within each group, sort appropriately
                                            val freq = rankingRepository.getResultFrequency(result.id, normalizedText)
                                            if (freq > 0) {
                                                // For frequent items, sort by frequency rank
                                                rankingRepository.getResultFrequencyRank(result.id, normalizedText)
                                            } else {
                                                // For non-frequent items, sort by provider rank
                                                rankingRepository.getProviderRank(result.providerId)
                                            }
                                        },
                                    ),
                                )
                            } else {
                                filtered.sortedBy { result ->
                                    rankingRepository.getProviderRank(result.providerId)
                                }
                            }

                        val newResults =
                            buildList {
                                aliasResult?.let { add(it) }
                                addAll(sortedResults)
                            }

                        // Only update UI if results actually changed
                        if (providerResults != newResults) {
                            providerResults.clear()
                            providerResults.addAll(newResults)
                        }

                        shouldShowResults = normalizedText.isNotBlank() || match != null
                    }
            }

            SearchTheme(motionPreferences = motionPreferences) {
                val hasVisibleResults = shouldShowResults && providerResults.isNotEmpty()
                val spacerWeight by rememberMotionAwareFloat(
                    targetValue = if (hasVisibleResults) 0.01f else 1f,
                    durationMillis = 300,
                    label = "resultsSpacer",
                )

                val tintedPrimaryBackground =
                    lerp(
                        start = MaterialTheme.colorScheme.surfaceBright,
                        stop = MaterialTheme.colorScheme.primaryContainer,
                        fraction = 0.65f,
                    )
                val backgroundColor = tintedPrimaryBackground.copy(alpha = backgroundOpacity.coerceIn(0f, 1f))
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(backgroundColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            finish()
                        }.padding(top = 50.dp),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                    ) {
                        Spacer(Modifier.weight(spacerWeight))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            SearchField(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                value = textState.value,
                                onValueChange = { textState.value = it },
                                singleLine = true,
                                placeholder = { Text("Search") },
                                trailingIcon = {
                                    if (settingsIconPosition == SettingsIconPosition.INSIDE) {
                                        IconButton(
                                            modifier = Modifier.padding(end = 6.dp),
                                            onClick = {
                                                val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                                                startActivity(intent)
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Settings,
                                                contentDescription = "Settings",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions =
                                    KeyboardActions(onDone = {
                                        val primaryResult = providerResults.firstOrNull()
                                        val handled = handleResultSelection(primaryResult)
                                        if (!handled) {
                                            val query = textState.value.text.trim()
                                            if (query.isNotEmpty()) {
                                                handleQuerySubmission(query)
                                            }
                                        }
                                    }),
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            val shouldCenterAppList =
                                appSearchSettings.centerAppList &&
                                    settingsIconPosition != SettingsIconPosition.BELOW
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (shouldCenterAppList) Arrangement.Center else Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (appSearchSettings.appListEnabled) {
                                    when (appSearchSettings.appListType) {
                                        AppListType.RECENT -> {
                                            RecentAppsList(
                                                repository = recentAppsRepository,
                                                isReversed = appSearchSettings.reverseRecentAppsOrder,
                                                shouldCenter = shouldCenterAppList,
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                            )
                                        }

                                        AppListType.PINNED -> {
                                            val pinnedApps by pinnedAppsRepository.getPinnedApps().collectAsState(initial = emptyList())
                                            if (pinnedApps.isNotEmpty()) {
                                                AppListRow(
                                                    apps = pinnedApps,
                                                    isReversed = appSearchSettings.reversePinnedAppsOrder,
                                                    shouldCenter = shouldCenterAppList,
                                                    modifier =
                                                        Modifier
                                                            .weight(1f)
                                                            .padding(horizontal = 4.dp, vertical = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                if (settingsIconPosition == SettingsIconPosition.BELOW) {
                                    if (appSearchSettings.appListEnabled) {
                                        VerticalDivider(
                                            modifier =
                                                Modifier
                                                    .height(24.dp)
                                                    .padding(horizontal = 4.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                        )
                                    }

                                    IconButton(onClick = {
                                        val intent = Intent(this@MainActivity, SettingsActivity::class.java)
                                        startActivity(intent)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Settings,
                                            contentDescription = "Settings",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (!hasVisibleResults) {
                            Spacer(Modifier.weight(spacerWeight))
                        }

                        val listEnterDuration = 250
                        val listExitDuration = 200
                        motionAwareVisibility(
                            visible = hasVisibleResults,
                            modifier =
                                Modifier
                                    .weight(if (hasVisibleResults) 1f else 0.01f)
                                    .imePadding()
                                    .padding(bottom = 8.dp),
                            enter =
                                fadeIn(animationSpec = tween(durationMillis = listEnterDuration)) +
                                    expandVertically(
                                        expandFrom = Alignment.Top,
                                        animationSpec = tween(durationMillis = listEnterDuration),
                                    ),
                            exit =
                                fadeOut(animationSpec = tween(durationMillis = listExitDuration)) +
                                    shrinkVertically(
                                        shrinkTowards = Alignment.Top,
                                        animationSpec = tween(durationMillis = listExitDuration),
                                    ),
                        ) {
                            ItemsList(
                                modifier = Modifier.fillMaxSize(),
                                results = providerResults,
                                onItemClick = { result -> handleResultSelection(result) },
                                onItemLongPress = onItemLongPress@{ result ->
                                    val target = result.aliasTarget ?: return@onItemLongPress
                                    val suggestion = sanitizeAliasSuggestion(result.title)
                                    aliasDialogValue = suggestion
                                    aliasDialogError = null
                                    aliasDialogCandidate =
                                        AliasCreationCandidate(
                                            target = target,
                                            suggestion = suggestion,
                                            description = result.subtitle ?: result.title,
                                        )
                                },
                                translucentItems = translucentResultsEnabled,
                            )
                        }
                    }

                    if (isPerformingAction) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { },
                        ) {
                            if (showLoadingOverlay) {
                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center)
                                            .background(
                                                color =
                                                    MaterialTheme.colorScheme
                                                        .surfaceColorAtElevation(6.dp)
                                                        .copy(alpha = 0.95f),
                                                shape = RoundedCornerShape(28.dp),
                                            ).padding(horizontal = 28.dp, vertical = 24.dp),
                                ) {
                                    LoadingIndicator(
                                        modifier =
                                            Modifier
                                                .size(48.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                aliasDialogCandidate?.let { candidate ->
                    AliasCreationDialog(
                        candidate = candidate,
                        alias = aliasDialogValue,
                        errorMessage = aliasDialogError,
                        onAliasChange = { aliasDialogValue = it },
                        onDismiss = {
                            aliasDialogCandidate = null
                            aliasDialogError = null
                        },
                        onSave = {
                            when (aliasRepository.addAlias(aliasDialogValue, candidate.target)) {
                                AliasRepository.SaveResult.SUCCESS -> {
                                    aliasDialogCandidate = null
                                    aliasDialogError = null
                                }

                                AliasRepository.SaveResult.DUPLICATE -> {
                                    aliasDialogError = "Alias already exists"
                                }

                                AliasRepository.SaveResult.INVALID_ALIAS -> {
                                    aliasDialogError = "Alias cannot be empty"
                                }
                            }
                        },
                    )
                }

                // Contact action sheet
                contactActionData?.let { contact ->
                    ContactActionSheet(
                        contact = contact,
                        onDismiss = { contactActionData = null },
                        onActionComplete = {
                            contactActionData = null
                            finish()
                        },
                    )
                }
            }

            LaunchedEffect(isPerformingAction, activityIndicatorDelayMs) {
                if (isPerformingAction) {
                    showLoadingOverlay = false
                    val delayDuration = activityIndicatorDelayMs.coerceAtLeast(0)
                    if (delayDuration > 0) {
                        delay(delayDuration.toLong())
                    }
                    if (isPerformingAction) {
                        showLoadingOverlay = true
                    }
                } else {
                    showLoadingOverlay = false
                }
            }

            LaunchedEffect(pendingAction) {
                val action = pendingAction ?: return@LaunchedEffect
                var completed = false
                try {
                    withContext(Dispatchers.Default) {
                        action.block()
                    }
                    completed = true
                } finally {
                    pendingAction = null
                    val shouldDismissOverlay =
                        !action.keepOverlayUntilExit || !completed || !this@MainActivity.isFinishing
                    if (shouldDismissOverlay) {
                        isPerformingAction = false
                    }
                }
            }



            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }
    }

    private fun handleQuerySubmission(query: String) {
        if (Patterns.WEB_URL.matcher(query).matches()) {
            val normalizedUrl =
                if (query.startsWith("http://", ignoreCase = true) || query.startsWith("https://", ignoreCase = true)) {
                    query
                } else {
                    "https://$query"
                }
            val intent = Intent(Intent.ACTION_VIEW, normalizedUrl.toUri())
            startActivity(intent)
        } else {
            val url = "https://www.bing.com/search?q=${Uri.encode(query)}&form=QBLH"
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        }
        finish()
    }

    private fun buildAliasResult(
        entry: AliasEntry,
        query: String,
        webSearchSettings: WebSearchSettings,
    ): ProviderResult? {
        return when (val target = entry.target) {
            is WebSearchAliasTarget -> {
                val site = webSearchSettings.siteForId(target.siteId) ?: webSearchSettings.sites.firstOrNull()
                val resolvedSite = site ?: return null
                val searchUrl = resolvedSite.buildUrl(query)
                val action: suspend () -> Unit = {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(Intent.ACTION_VIEW, searchUrl.toUri())
                        startActivity(intent)
                        finish()
                    }
                }
                ProviderResult(
                    id = "alias:web:${entry.alias}:${query.hashCode()}",
                    title = if (query.isBlank()) resolvedSite.displayName else query,
                    subtitle = "Alias \"${entry.alias}\" → ${resolvedSite.displayName}",
                    providerId = target.providerId,
                    onSelect = action,
                    aliasTarget = target,
                    keepOverlayUntilExit = true,
                )
            }

            is AppLaunchAliasTarget -> {
                val action: suspend () -> Unit = {
                    withContext(Dispatchers.Main) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(target.packageName)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                            finish()
                        }
                    }
                }
                ProviderResult(
                    id = "alias:app:${entry.alias}",
                    title = target.label,
                    subtitle = "Alias \"${entry.alias}\"",
                    providerId = target.providerId,
                    onSelect = action,
                    aliasTarget = target,
                    keepOverlayUntilExit = true,
                )
            }

            else -> {
                null
            }
        }
    }

    private fun sanitizeAliasSuggestion(text: String): String {
        val normalized =
            text
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
        return normalized.ifBlank { "alias" }
    }

    private fun applyWindowBlur(strength: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = (strength.coerceIn(0f, 1f) * MAX_BACKGROUND_BLUR_RADIUS).roundToInt()
            window.decorView?.let { window.setBackgroundBlurRadius(radius) }
        }
    }
}

private data class PendingAction(
    val block: suspend () -> Unit,
    val keepOverlayUntilExit: Boolean,
)
