package com.mrndstvndv.search

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.UserManager
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import com.mrndstvndv.search.ui.SearchViewModel
import com.mrndstvndv.search.ui.SearchViewModelFactory
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.layout.layout
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mrndstvndv.search.util.PerformanceLogger
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mrndstvndv.search.alias.AliasCreationCandidate
import com.mrndstvndv.search.alias.AliasEntry
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.alias.AppLaunchAliasTarget
import com.mrndstvndv.search.alias.WebSearchAliasTarget
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.apps.AppListProvider
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.provider.apps.PinnedAppsRepository
import com.mrndstvndv.search.provider.apps.RecentAppsRepository
import com.mrndstvndv.search.provider.apps.createAppSearchSettingsRepository
import com.mrndstvndv.search.provider.calculator.CalculatorProvider
import com.mrndstvndv.search.provider.contacts.ContactsProvider
import com.mrndstvndv.search.provider.contacts.ContactsRepository
import com.mrndstvndv.search.provider.contacts.PhoneNumber
import com.mrndstvndv.search.provider.contacts.createContactsSettingsRepository
import com.mrndstvndv.search.provider.files.FileSearchProvider
import com.mrndstvndv.search.provider.files.FileSearchRepository
import com.mrndstvndv.search.provider.files.FileThumbnailRepository
import com.mrndstvndv.search.provider.files.createFileSearchSettingsRepository
import com.mrndstvndv.search.provider.intent.IntentProvider
import com.mrndstvndv.search.provider.intent.createIntentSettingsRepository
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.model.TriggerParser
import com.mrndstvndv.search.provider.model.TriggerResultPolicy
import com.mrndstvndv.search.provider.model.dynamicTriggerFrequencyQuery
import com.mrndstvndv.search.provider.settings.AppListType
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.SearchBarPosition
import com.mrndstvndv.search.provider.settings.SettingsIconPosition
import com.mrndstvndv.search.provider.settings.WebSearchSettings
import com.mrndstvndv.search.provider.system.DeveloperSettingsManager
import com.mrndstvndv.search.provider.system.SettingsProvider
import com.mrndstvndv.search.provider.system.createSystemSettingsSettingsRepository
import com.mrndstvndv.search.provider.termux.TermuxProvider
import com.mrndstvndv.search.provider.termux.createTermuxSettingsRepository
import com.mrndstvndv.search.provider.text.TextUtilitiesProvider
import com.mrndstvndv.search.provider.text.createTextUtilitiesSettingsRepository
import com.mrndstvndv.search.provider.web.WebSearchProvider
import com.mrndstvndv.search.provider.web.createWebSearchSettingsRepository
import com.mrndstvndv.search.ui.components.AppListContainer
import com.mrndstvndv.search.ui.components.AppListSection
import com.mrndstvndv.search.ui.components.ContactActionData
import com.mrndstvndv.search.ui.components.ContactActionSheet
import com.mrndstvndv.search.ui.components.ItemsList
import com.mrndstvndv.search.ui.components.SearchField
import com.mrndstvndv.search.ui.components.TriggerChip
import com.mrndstvndv.search.ui.components.TriggerState
import com.mrndstvndv.search.ui.components.findTriggerMatch
import com.mrndstvndv.search.ui.settings.AliasCreationDialog
import com.mrndstvndv.search.ui.theme.SearchTheme
import com.mrndstvndv.search.ui.theme.motionAwareVisibility
import com.mrndstvndv.search.ui.theme.rememberMotionAwareFloat
import com.mrndstvndv.search.util.FaviconLoader
import com.mrndstvndv.search.util.GitHubUpdateChecker
import com.mrndstvndv.search.util.loadAppIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private data class SuppressedTriggerMatch(
    val triggerId: String,
    val matchedToken: String,
)

private data class ResultSortMetadata(
    val result: ProviderResult,
    val providerRank: Int,
    val frequencyScore: Float,
) {
    val hasFrequency: Boolean
        get() = frequencyScore > 0f
}

private fun buildTriggerText(
    matchedToken: String,
    payload: String,
): String =
    when {
        matchedToken.isBlank() -> payload
        payload.isBlank() -> "$matchedToken "
        else -> "$matchedToken $payload"
    }

class SearchActivity : ComponentActivity() {
    companion object {
        private const val MAX_BACKGROUND_BLUR_RADIUS = 80
        private val SLOW_PROVIDER_IDS = setOf("contacts", "file-search", "termux")
    }

    private val viewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((application as SearchApplication).container)
    }

    private val defaultAppIconSize by lazy { resources.getDimensionPixelSize(android.R.dimen.app_icon_size) }

    // When launched via the assistant gesture, the system delivers the gesture's
    // ongoing touch events (ACTION_MOVE, ACTION_UP) to this activity's window.
    // These stray events hit the full-screen background clickable and trigger finish().
    // Fix: swallow all touch events until we see a fresh ACTION_DOWN, which means the
    // user has lifted their finger from the gesture and is now intentionally tapping.
    private var hasSeenFreshDown = false

    // Track whether this instance was launched from the assist gesture so we can
    // fight the system's attempt to immediately background us.
    private var launchedFromAssist = false

    private var isExiting = false
    private var finishRequestedDuringAction = false
    private var forceFinish = false
    private var isLaunched by mutableStateOf(false)
    private var launchTrigger by mutableStateOf(0)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            hasSeenFreshDown = true
        }
        if (hasSeenFreshDown) {
            return super.dispatchTouchEvent(event)
        }
        return true // Consume the event silently
    }

    override fun finish() {
        if (viewModel.uiState.value.isPerformingAction && !finishRequestedDuringAction && !forceFinish) {
            finishRequestedDuringAction = true
            return
        }
        forceFinish = false
        val isResumed = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        val revealEnabled = (application as SearchApplication).container.settingsRepository.revealAnimationEnabled.value
        if (revealEnabled && !isExiting && isResumed) {
            isExiting = true
            isLaunched = false
        } else {
            super.finish()
        }
    }

    override fun onResume() {
        super.onResume()
        isExiting = false
        launchTrigger++
    }

    override fun onStop() {
        super.onStop()
        if (finishRequestedDuringAction) {
            finishRequestedDuringAction = false
            super.finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        isExiting = false
        finishRequestedDuringAction = false
        super.onNewIntent(intent)
        viewModel.clearState()

        val isAssistAction = intent.action == Intent.ACTION_ASSIST || intent.action == "android.intent.action.SEARCH_LONG_PRESS"
        // Rewrite assist actions to escape session lifecycle
        val effectiveIntent =
            if (isAssistAction) {
                launchedFromAssist = true
                Intent(intent).apply { action = Intent.ACTION_MAIN }
            } else {
                launchedFromAssist = false
                intent
            }
        setIntent(effectiveIntent)
        // Reset touch gate for the new gesture
        if (isAssistAction) {
            hasSeenFreshDown = false
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // If the user is intentionally leaving the activity (e.g. starting settings),
        // we should not fight the system's attempt to background us later.
        launchedFromAssist = false
    }

    override fun onPause() {
        // When the system backgrounds us as part of assist session teardown (isFinishing=false),
        // fight back by bringing our task to the front. Done in onPause (not onStop) because
        // on API 29+ the app is still considered foreground here, so moveTaskToFront succeeds.
        if (!isFinishing && launchedFromAssist) {
            launchedFromAssist = false // Only fight once to avoid infinite loops
            val hasReorderPermission = checkSelfPermission(android.Manifest.permission.REORDER_TASKS) == PackageManager.PERMISSION_GRANTED
            if (!hasReorderPermission) {
                try {
                    val relaunchIntent =
                        Intent(this, SearchActivity::class.java).apply {
                            action = Intent.ACTION_MAIN
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    startActivity(relaunchIntent)
                    suppressNextOpenTransition()
                } catch (_: Exception) {
                }
            } else {
                try {
                    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    am.moveTaskToFront(taskId, ActivityManager.MOVE_TASK_WITH_HOME)
                } catch (_: SecurityException) {
                }
            }
        }
        super.onPause()
    }

    private fun suppressNextOpenTransition() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
            return
        }

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        PerformanceLogger.log(this, "ISSUE_1_CONTEXT_LEAK", "SearchActivity onDestroy! HashCode: ${System.identityHashCode(this)}")
        super.onDestroy()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerformanceLogger.log(this, "ISSUE_1_CONTEXT_LEAK", "SearchActivity onCreate! HashCode: ${System.identityHashCode(this)}")
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    forceFinish = true
                    finish()
                }
            }
        )
        if (savedInstanceState == null) {
            viewModel.clearState()
        }

        // When launched via ACTION_ASSIST, the system treats this activity as an ephemeral
        // assist session and may immediately pause/stop it once the gesture animation completes.
        // Work around this by replacing the intent action so the system no longer considers
        // this activity part of the assist session lifecycle.
        if (intent?.action == Intent.ACTION_ASSIST || intent?.action == "android.intent.action.SEARCH_LONG_PRESS") {
            launchedFromAssist = true
            intent =
                Intent(intent).apply {
                    action = Intent.ACTION_MAIN
                }
            setIntent(intent)
        }

        enableEdgeToEdge()
        // enableEdgeToEdge() overrides the transparent window background set by the theme,
        // causing a white flash on first launch. Reset it.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setContent {
            val container = remember(this@SearchActivity) { (application as SearchApplication).container }
            val textState by viewModel.textState.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            val focusRequester = remember { FocusRequester() }
            val coroutineScope = rememberCoroutineScope()
            val settingsRepository = container.settingsRepository
            val aliasRepository = container.aliasRepository
            val aliasEntries by aliasRepository.aliases.collectAsState()

            // Create new provider-specific settings repositories (auto-register for backup)
            val webSearchSettingsRepo = container.webSearchSettingsRepo
            val appSearchSettingsRepo = container.appSearchSettingsRepo
            val textUtilitiesSettingsRepo = container.textUtilitiesSettingsRepo
            val fileSearchSettingsRepo = container.fileSearchSettingsRepo
            val systemSettingsSettingsRepo = container.systemSettingsSettingsRepo
            val contactsSettingsRepo = container.contactsSettingsRepo
            val termuxSettingsRepo = container.termuxSettingsRepo
            val intentSettingsRepo = container.intentSettingsRepo

            // Collect settings from new repositories
            val webSearchSettings by webSearchSettingsRepo.flow.collectAsState()
            val appSearchSettings by appSearchSettingsRepo.flow.collectAsState()
            val fileSearchSettings by fileSearchSettingsRepo.flow.collectAsState()
            val systemSettingsSettings by systemSettingsSettingsRepo.flow.collectAsState()

            // Collect UI/global settings from old repository
            val translucentResultsEnabled by settingsRepository.translucentResultsEnabled.collectAsState()
            val backgroundOpacity by settingsRepository.backgroundOpacity.collectAsState()
            val backgroundBlurStrength by settingsRepository.backgroundBlurStrength.collectAsState()
            val activityIndicatorDelayMs by settingsRepository.activityIndicatorDelayMs.collectAsState()
            val backgroundAnimationDelayMs by settingsRepository.backgroundAnimationDelayMs.collectAsState()
            val motionPreferences by settingsRepository.motionPreferences.collectAsState()
            val revealAnimationEnabled by settingsRepository.revealAnimationEnabled.collectAsState()
            val settingsIconPosition by settingsRepository.settingsIconPosition.collectAsState()
            val searchBarPosition by settingsRepository.searchBarPosition.collectAsState()
            val firstResultHighlightEnabled by settingsRepository.firstResultHighlightEnabled.collectAsState()
            val firstResultHighlightMode by settingsRepository.firstResultHighlightMode.collectAsState()
            val firstResultBorderThickness by settingsRepository.firstResultBorderThickness.collectAsState()
            val firstResultChangeAnimationEnabled by settingsRepository.firstResultChangeAnimationEnabled.collectAsState()
            val firstResultColorAnimationEnabled by settingsRepository.firstResultColorAnimationEnabled.collectAsState()
            val alwaysShowEnterBadge by settingsRepository.alwaysShowEnterBadge.collectAsState()
            val hasUsedEnter by settingsRepository.hasUsedEnter.collectAsState()
            val enabledProviders by settingsRepository.enabledProviders.collectAsState()
            val updateCheckInterval by settingsRepository.updateCheckInterval.collectAsState()
            val customUpdateIntervalDays by settingsRepository.customUpdateIntervalDays.collectAsState()
            val lastUpdateCheckTime by settingsRepository.lastUpdateCheckTime.collectAsState()
            val dismissedVersion by settingsRepository.dismissedVersion.collectAsState()
            val latestUpdate by settingsRepository.latestUpdate.collectAsState()
            val checkPrereleaseBuilds by settingsRepository.checkPrereleaseBuilds.collectAsState()
            val showEnterBadge = alwaysShowEnterBadge || !hasUsedEnter

            LaunchedEffect(launchTrigger) {
                if (launchTrigger > 0) {
                    isLaunched = true
                }
            }

            val animatedOpacity by rememberMotionAwareFloat(
                targetValue = if (isLaunched) backgroundOpacity else 0f,
                durationMillis = if (!revealAnimationEnabled) 0 else (if (isLaunched) 300 else 350),
                delayMillis = if (isLaunched && revealAnimationEnabled) backgroundAnimationDelayMs else 0,
                easing = if (isLaunched) FastOutSlowInEasing else FastOutLinearInEasing,
                label = "backgroundOpacity",
            )

            val animatedBlurStrength by rememberMotionAwareFloat(
                targetValue = if (isLaunched) backgroundBlurStrength else 0f,
                durationMillis = if (!revealAnimationEnabled) 0 else (if (isLaunched) 300 else 350),
                delayMillis = if (isLaunched && revealAnimationEnabled) backgroundAnimationDelayMs else 0,
                easing = if (isLaunched) FastOutSlowInEasing else FastOutLinearInEasing,
                label = "backgroundBlurStrength",
            )

            val animatedFraction by rememberMotionAwareFloat(
                targetValue = if (isLaunched) 1f else 0f,
                durationMillis = if (!revealAnimationEnabled) 0 else (if (isLaunched) 300 else 350),
                delayMillis = if (isLaunched && revealAnimationEnabled) backgroundAnimationDelayMs else 0,
                easing = if (isLaunched) FastOutSlowInEasing else FastOutLinearInEasing,
                label = "backgroundFraction",
                finishedListener = { value ->
                    if (value == 0f && isExiting) {
                        finish()
                    }
                }
            )

            SideEffect {
                PerformanceLogger.log(this@SearchActivity, "ISSUE_3_ANIMATION_CHURN", "SideEffect running for strength: $animatedBlurStrength")
                applyWindowBlur(animatedBlurStrength)
            }

            val fileSearchRepository = container.fileSearchRepository
            val fileThumbnailRepository = container.fileThumbnailRepository
            val contactsRepository = container.contactsRepository
            val rankingRepository = container.rankingRepository
            val appListRepository = container.appListRepository
            val recentAppsRepository = container.recentAppsRepository
            val pinnedAppsRepository = container.pinnedAppsRepository
            val developerSettingsManager = container.developerSettingsManager
            val providerOrder by rankingRepository.providerOrder.collectAsState()
            val useFrequencyRanking by rankingRepository.useFrequencyRanking.collectAsState()
            val queryBasedRankingEnabled by rankingRepository.queryBasedRankingEnabled.collectAsState()


            // Initialize developer settings manager if feature is enabled
            LaunchedEffect(systemSettingsSettings.developerToggleEnabled) {
                if (systemSettingsSettings.developerToggleEnabled) {
                    developerSettingsManager.registerListeners()
                    developerSettingsManager.refreshStatus()
                }
            }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val settings = fileSearchSettingsRepo.value
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

            var activeUpdateResult by remember { mutableStateOf<GitHubUpdateChecker.UpdateResult?>(null) }
            var showUpdateBanner by remember { mutableStateOf(false) }

            LaunchedEffect(latestUpdate, dismissedVersion) {
                val update = latestUpdate
                if (update != null && update.version != dismissedVersion) {
                    activeUpdateResult = update
                    showUpdateBanner = true
                } else {
                    showUpdateBanner = false
                }
            }

            LaunchedEffect(Unit) {
                val now = System.currentTimeMillis()
                val intervalMillis =
                    when (updateCheckInterval) {
                        "daily" -> 24 * 60 * 60 * 1000L
                        "weekly" -> 7 * 24 * 60 * 60 * 1000L
                        "monthly" -> 30 * 24 * 60 * 60 * 1000L
                        "custom" -> customUpdateIntervalDays * 24 * 60 * 60 * 1000L
                        else -> 7 * 24 * 60 * 60 * 1000L
                    }

                if (now - lastUpdateCheckTime >= intervalMillis) {
                    val result = GitHubUpdateChecker.checkForUpdates(BuildConfig.VERSION_NAME, checkPrereleaseBuilds)
                    if (result is GitHubUpdateChecker.CheckResult.NewUpdate) {
                        settingsRepository.setLastUpdateCheckTime(now)
                        settingsRepository.setLatestUpdate(result.update)
                    } else if (result is GitHubUpdateChecker.CheckResult.UpToDate) {
                        settingsRepository.setLastUpdateCheckTime(now)
                        settingsRepository.setLatestUpdate(null)
                    }
                }
            }

            val aliasResult = uiState.matchedAlias?.let { (entry, normalizedText) ->
                buildAliasResult(entry, normalizedText, webSearchSettings, appSearchSettings)
            }
            val displayedResults = remember(uiState.providerResults, aliasResult) {
                buildList {
                    aliasResult?.let { add(it) }
                    addAll(uiState.providerResults)
                }
            }



            var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

            fun startPendingAction(result: ProviderResult?) {
                val action = result?.onSelect ?: return
                if (uiState.isPerformingAction) return
                viewModel.setIsPerformingAction(true)
                viewModel.recordResultUsage(result)
                pendingAction = PendingAction(action, result.keepOverlayUntilExit)
            }

            fun textFieldValueAtEnd(text: String): TextFieldValue =
                TextFieldValue(
                    text = text,
                    selection = TextRange(text.length),
                )

            fun handleResultSelection(result: ProviderResult?): Boolean {
                val candidate = result ?: return false
                val prefillQuery = candidate.extras[TextUtilitiesProvider.PREFILL_QUERY_EXTRA] as? String
                if (prefillQuery != null) {
                    viewModel.recordResultUsage(candidate)
                    if (uiState.triggerState != null) return true
                    viewModel.applyPrefillQuery(prefillQuery)
                    return true
                }
                if (candidate.providerId == "contacts") {
                    @Suppress("UNCHECKED_CAST")
                    val phoneNumbers = candidate.extras[ContactsProvider.EXTRA_PHONE_NUMBERS] as? List<PhoneNumber> ?: emptyList()
                    val displayName = candidate.extras[ContactsProvider.EXTRA_DISPLAY_NAME] as? String ?: candidate.title
                    val isSimNumber = candidate.extras[ContactsProvider.EXTRA_IS_SIM_NUMBER] as? Boolean ?: false
                    viewModel.setContactActionData(
                        ContactActionData(
                            contactId = candidate.extras[ContactsProvider.EXTRA_CONTACT_ID] as? String,
                            lookupKey = candidate.extras[ContactsProvider.EXTRA_LOOKUP_KEY] as? String,
                            displayName = displayName,
                            phoneNumbers = phoneNumbers,
                            isSimNumber = isSimNumber,
                        )
                    )
                    viewModel.recordResultUsage(candidate)
                    return true
                }
                if (candidate.onSelect != null) {
                    startPendingAction(candidate)
                    return true
                }
                return false
            }

            LaunchedEffect(uiState.triggerState?.trigger?.id, uiState.triggerState?.matchedToken) {
                val activeTrigger = uiState.triggerState
                if (activeTrigger != null && textState.text != activeTrigger.payload) {
                    viewModel.onSearchChange(textFieldValueAtEnd(activeTrigger.payload))
                }
                focusRequester.requestFocus()
            }

            fun openSettingsScreen() {
                val intent =
                    Intent(this@SearchActivity, SettingsActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            }

            fun openSystemSettingsScreen() {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                finish()
            }

            fun submitSearch() {
                val primaryResult = displayedResults.firstOrNull()
                val query = textState.text.trim()
                val hasSubmissionTarget = primaryResult != null || query.isNotEmpty()

                if (hasSubmissionTarget) {
                    settingsRepository.markEnterUsed()
                }

                val handled = handleResultSelection(primaryResult)
                if (handled) return

                if (query.isNotEmpty()) {
                    handleQuerySubmission(query)
                }
            }



            SearchTheme(motionPreferences = motionPreferences) {
                val hasVisibleResults = uiState.shouldShowResults && displayedResults.isNotEmpty()

                // No spacer-weight animation — per-frame Animatable.value reads in composition
                // would force recomposition on every animation frame under SSM.
                // Instead we use constant weight distribution + AnimatedVisibility on ItemsList
                // for smooth enter/exit of results; the search bar snaps between centered and bottom.
                // The visual transition is masked by ItemsList's fade+expand animation.
                val spacerWeight = if (hasVisibleResults) 0.01f else 1f

                val tintedPrimaryBackground =
                    lerp(
                        start = MaterialTheme.colorScheme.surfaceBright,
                        stop = MaterialTheme.colorScheme.primaryContainer,
                        fraction = 0.65f,
                    )
                val backgroundColor = tintedPrimaryBackground.copy(alpha = animatedOpacity.coerceIn(0f, 1f))
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            finish()
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val center = this.center
                                val featherWidthPx = 80.dp.toPx()
                                val maxRadius = (kotlin.math.hypot(size.width, size.height) / 2f) + featherWidthPx
                                val currentRadius = maxRadius * animatedFraction
                                val color = tintedPrimaryBackground.copy(alpha = animatedOpacity.coerceIn(0f, 1f))
                                if (currentRadius > 0f) {
                                    val startFadeRatio = ((currentRadius - featherWidthPx) / currentRadius).coerceIn(0f, 1f)
                                    val brush = Brush.radialGradient(
                                        0f to color,
                                        startFadeRatio to color,
                                        1f to color.copy(alpha = 0f),
                                        center = center,
                                        radius = currentRadius,
                                    )
                                    drawCircle(
                                        brush = brush,
                                        radius = currentRadius,
                                        center = center,
                                    )
                                }
                            }
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(top = 50.dp)
                            .graphicsLayer {
                                alpha = animatedFraction
                            }
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                        ) {
                        if (searchBarPosition == SearchBarPosition.TOP) {
                            Spacer(Modifier.weight(spacerWeight))
                        } else if (searchBarPosition == SearchBarPosition.BOTTOM && hasVisibleResults) {
                            Spacer(Modifier.weight(0.01f))
                        }

                        if (searchBarPosition == SearchBarPosition.BOTTOM && hasVisibleResults) {
                            val listEnterDuration = 250
                            val listExitDuration = 200
                            motionAwareVisibility(
                                visible = hasVisibleResults,
                                modifier =
                                    Modifier
                                        .weight(1f)
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
                                    results = displayedResults,
                                    onItemClick = { result -> handleResultSelection(result) },
                                    onItemLongPress = onItemLongPress@{ result ->
                                        val target = result.aliasTarget ?: return@onItemLongPress
                                        val suggestion = sanitizeAliasSuggestion(result.title)
                                        viewModel.showAliasDialog(
                                            AliasCreationCandidate(
                                                target = target,
                                                suggestion = suggestion,
                                                description = result.subtitle ?: result.title,
                                            )
                                        )
                                    },
                                    translucentItems = translucentResultsEnabled,
                                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
                                    reverseOrder = true,
                                    showEnterBadge = showEnterBadge,
                                    firstResultHighlightEnabled = firstResultHighlightEnabled,
                                    firstResultHighlightMode = firstResultHighlightMode,
                                    firstResultBorderThickness = firstResultBorderThickness,
                                    animateFirstResultChanges = firstResultChangeAnimationEnabled,
                                    animateFirstResultColorPulse = firstResultColorAnimationEnabled,
                                )
                            }
                        }
                        if (searchBarPosition == SearchBarPosition.BOTTOM) {
                            val density = LocalDensity.current
                            val imeInsets = WindowInsets.ime

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (!hasVisibleResults) {
                                                Modifier.weight(1f)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .layout { measurable, constraints ->
                                            val keyboardHeightPx = imeInsets.getBottom(density)
                                            val keyboardHeightDp = keyboardHeightPx.toDp()

                                            val containerHeight = constraints.maxHeight.toDp()
                                            val searchBarHeight = 56.dp
                                            val appListHeight = if (appSearchSettings.appListEnabled && !hasVisibleResults) 64.dp else 0.dp
                                            val totalContentHeight = searchBarHeight + appListHeight + 10.dp

                                            val centeringPadding =
                                                if (!hasVisibleResults) {
                                                    (containerHeight - totalContentHeight) / 2
                                                } else {
                                                    0.dp
                                                }

                                            val centeredBottomPosition = containerHeight / 2 + totalContentHeight / 2
                                            val keyboardTopPosition = containerHeight - keyboardHeightDp
                                            val shouldPushUp = centeredBottomPosition > keyboardTopPosition && keyboardHeightPx > 0
                                            val needsKeyboardPadding = hasVisibleResults && keyboardHeightPx > 0

                                            val targetPadding =
                                                when {
                                                    needsKeyboardPadding -> keyboardHeightDp
                                                    shouldPushUp -> keyboardHeightDp
                                                    !hasVisibleResults -> centeringPadding
                                                    else -> 0.dp
                                                }

                                            val targetPaddingPx = targetPadding.roundToPx()
                                            val newMaxHeight = (constraints.maxHeight - targetPaddingPx).coerceAtLeast(0)
                                            val newMinHeight = constraints.minHeight.coerceAtMost(newMaxHeight)

                                            val placeable =
                                                measurable.measure(
                                                    constraints.copy(
                                                        minHeight = newMinHeight,
                                                        maxHeight = newMaxHeight,
                                                    ),
                                                )

                                            layout(placeable.width, placeable.height + targetPaddingPx) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        },
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    if (showUpdateBanner && !hasVisibleResults) {
                                        activeUpdateResult?.let { update ->
                                            UpdateBanner(
                                                result = update,
                                                onDismiss = {
                                                    showUpdateBanner = false
                                                    settingsRepository.setDismissedVersion(update.version)
                                                },
                                                onDownload = {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                                                    browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                    startActivity(browserIntent)
                                                },
                                                firstResultHighlightEnabled = firstResultHighlightEnabled,
                                                firstResultBorderThickness = firstResultBorderThickness,
                                                translucentResultsEnabled = translucentResultsEnabled,
                                            )
                                        }
                                    }
                                    SearchBar(
                                        context = this@SearchActivity,
                                        textState = textState,
                                        onSearchChange = viewModel::onSearchChange,
                                        triggerState = uiState.triggerState,
                                        onDismissTrigger = viewModel::dismissTrigger,
                                        focusRequester = focusRequester,
                                        settingsIconPosition = settingsIconPosition,
                                        onSubmitSearch = ::submitSearch,
                                        onOpenSettings = ::openSettingsScreen,
                                        onOpenSystemSettings = ::openSystemSettingsScreen,
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    val shouldCenterAppList =
                                        appSearchSettings.centerAppList &&
                                            settingsIconPosition != SettingsIconPosition.BELOW
                                    val showAppList =
                                        appSearchSettings.appListEnabled &&
                                            (!appSearchSettings.hideAppListWhenResultsVisible || !hasVisibleResults)
                                    // App list enter duration (not tied to spacer animation anymore)
                                    val appListEnterDuration =
                                        when {
                                            !appSearchSettings.hideAppListWhenResultsVisible -> 300
                                            appSearchSettings.appListType == AppListType.BOTH -> 300
                                            else -> 300
                                        }
                                    val appListExitDuration = 200
                                    AppListContainer(
                                        visible = showAppList,
                                        enterDuration = appListEnterDuration,
                                        exitDuration = appListExitDuration,
                                        shouldCenter = shouldCenterAppList,
                                        showSettingsIcon = settingsIconPosition == SettingsIconPosition.BELOW,
                                        onSettingsClick = {
                                            val intent =
                                                Intent(this@SearchActivity, SettingsActivity::class.java)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            startActivity(intent)
                                            finish()
                                        },
                                        onSettingsLongClick = {
                                            startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            finish()
                                        },
                                    ) {
                                        AppListSection(
                                            appListType = appSearchSettings.appListType,
                                            recentAppsRepository = recentAppsRepository,
                                            pinnedAppsRepository = pinnedAppsRepository,
                                            isReversedRecent = appSearchSettings.reverseRecentAppsOrder,
                                            isReversedPinned = appSearchSettings.reversePinnedAppsOrder,
                                            pinnedOnLeft = appSearchSettings.bothLayoutPinnedOnLeft,
                                            filterPinnedFromRecentsInBoth = appSearchSettings.filterPinnedFromRecentsInBoth,
                                            shouldCenter = shouldCenterAppList,
                                            modifier =
                                                Modifier
                                                    .weight(1f)
                                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                            visible = showAppList,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                if (showUpdateBanner && !hasVisibleResults) {
                                    activeUpdateResult?.let { update ->
                                        UpdateBanner(
                                            result = update,
                                            onDismiss = {
                                                showUpdateBanner = false
                                                settingsRepository.setDismissedVersion(update.version)
                                            },
                                            onDownload = {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                                                browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                startActivity(browserIntent)
                                            },
                                            firstResultHighlightEnabled = firstResultHighlightEnabled,
                                            firstResultBorderThickness = firstResultBorderThickness,
                                            translucentResultsEnabled = translucentResultsEnabled,
                                        )
                                    }
                                }
                                SearchBar(
                                    context = this@SearchActivity,
                                    textState = textState,
                                    onSearchChange = viewModel::onSearchChange,
                                    triggerState = uiState.triggerState,
                                    onDismissTrigger = viewModel::dismissTrigger,
                                    focusRequester = focusRequester,
                                    settingsIconPosition = settingsIconPosition,
                                    onSubmitSearch = ::submitSearch,
                                    onOpenSettings = ::openSettingsScreen,
                                    onOpenSystemSettings = ::openSystemSettingsScreen,
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                val shouldCenterAppList =
                                    appSearchSettings.centerAppList &&
                                        settingsIconPosition != SettingsIconPosition.BELOW
                                val showAppList =
                                    appSearchSettings.appListEnabled &&
                                        (!appSearchSettings.hideAppListWhenResultsVisible || !hasVisibleResults)
                                // App list enter duration (not tied to spacer animation anymore)
                                val appListEnterDuration = 300
                                val appListExitDuration = 200
                                AppListContainer(
                                    visible = showAppList,
                                    enterDuration = appListEnterDuration,
                                    exitDuration = appListExitDuration,
                                    shouldCenter = shouldCenterAppList,
                                    showSettingsIcon = settingsIconPosition == SettingsIconPosition.BELOW,
                                    onSettingsClick = {
                                        val intent =
                                            Intent(this@SearchActivity, SettingsActivity::class.java)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(intent)
                                        finish()
                                    },
                                    onSettingsLongClick = {
                                        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        finish()
                                    },
                                ) {
                                    AppListSection(
                                        appListType = appSearchSettings.appListType,
                                        recentAppsRepository = recentAppsRepository,
                                        pinnedAppsRepository = pinnedAppsRepository,
                                        isReversedRecent = appSearchSettings.reverseRecentAppsOrder,
                                        isReversedPinned = appSearchSettings.reversePinnedAppsOrder,
                                        pinnedOnLeft = appSearchSettings.bothLayoutPinnedOnLeft,
                                        filterPinnedFromRecentsInBoth = appSearchSettings.filterPinnedFromRecentsInBoth,
                                        shouldCenter = shouldCenterAppList,
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                        visible = showAppList,
                                    )
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
                                    results = displayedResults,
                                    onItemClick = { result -> handleResultSelection(result) },
                                    onItemLongPress = onItemLongPress@{ result ->
                                        val target = result.aliasTarget ?: return@onItemLongPress
                                        val suggestion = sanitizeAliasSuggestion(result.title)
                                        viewModel.showAliasDialog(
                                            AliasCreationCandidate(
                                                target = target,
                                                suggestion = suggestion,
                                                description = result.subtitle ?: result.title,
                                            )
                                        )
                                    },
                                    translucentItems = translucentResultsEnabled,
                                    showEnterBadge = showEnterBadge,
                                    firstResultHighlightEnabled = firstResultHighlightEnabled,
                                    firstResultHighlightMode = firstResultHighlightMode,
                                    firstResultBorderThickness = firstResultBorderThickness,
                                    animateFirstResultChanges = firstResultChangeAnimationEnabled,
                                    animateFirstResultColorPulse = firstResultColorAnimationEnabled,
                                )
                            }
                        }
                    }

                    if (uiState.isPerformingAction) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { },
                        ) {
                            if (uiState.showLoadingOverlay) {
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
            }

                uiState.aliasDialogCandidate?.let { candidate ->
                    AliasCreationDialog(
                        candidate = candidate,
                        alias = uiState.aliasDialogValue,
                        errorMessage = uiState.aliasDialogError,
                        onAliasChange = { viewModel.onAliasDialogValueChange(it) },
                        onDismiss = { viewModel.dismissAliasDialog() },
                        onSave = { viewModel.confirmAliasCreation() },
                    )
                }

                // Contact action sheet
                uiState.contactActionData?.let { contact ->
                    ContactActionSheet(
                        contact = contact,
                        onDismiss = { viewModel.dismissContactActionSheet() },
                        onActionComplete = {
                            viewModel.dismissContactActionSheet()
                            finish()
                        },
                    )
                }
            }

            LaunchedEffect(uiState.isPerformingAction, activityIndicatorDelayMs) {
                if (uiState.isPerformingAction) {
                    viewModel.setShowLoadingOverlay(false)
                    val delayDuration = activityIndicatorDelayMs.coerceAtLeast(0)
                    if (delayDuration > 0) {
                        delay(delayDuration.toLong())
                    }
                    if (uiState.isPerformingAction) {
                        viewModel.setShowLoadingOverlay(true)
                    }
                } else {
                    viewModel.setShowLoadingOverlay(false)
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
                    if (completed && action.keepOverlayUntilExit) {
                        finish()
                    } else {
                        val shouldDismissOverlay =
                            !action.keepOverlayUntilExit || !completed || (!this@SearchActivity.isFinishing && !isExiting && !finishRequestedDuringAction)
                        if (shouldDismissOverlay) {
                            viewModel.setIsPerformingAction(false)
                        }
                        if (finishRequestedDuringAction) {
                            finish()
                        }
                    }
                }
            }

            LaunchedEffect(searchBarPosition) {
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

    // TODO: The alias feature needs a proper refactor:
    // - Add an explicit "isAlias" field to ProviderResult instead of relying on id prefix
    // - Consider creating a dedicated AliasResult class that wraps ProviderResult
    // - The icon loading should be handled more elegantly (maybe a separate IconResolver)
    // - Consider caching loaded icons to avoid repeated loading
    private fun buildAliasResult(
        entry: AliasEntry,
        query: String,
        webSearchSettings: WebSearchSettings,
        appSearchSettings: AppSearchSettings,
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
                    id = "alias:web:${entry.alias}",
                    title = if (query.isBlank()) resolvedSite.displayName else query,
                    subtitle = getString(R.string.alias_web_subtitle, entry.alias, resolvedSite.displayName),
                    providerId = target.providerId,
                    onSelect = action,
                    aliasTarget = target,
                    keepOverlayUntilExit = true,
                    // Aggregate frequency by site rather than per-query
                    frequencyKey = "web-search:${resolvedSite.id}",
                    // Load favicon from the site's URL
                    iconLoader = { FaviconLoader.loadFavicon(this@SearchActivity, resolvedSite.id) },
                )
            }

            is AppLaunchAliasTarget -> {
                val action: suspend () -> Unit = {
                    withContext(Dispatchers.Main) {
                        val userManager = getSystemService(Context.USER_SERVICE) as UserManager
                        val userHandle = userManager.getUserForSerialNumber(target.userSerialNumber)
                            ?: android.os.Process.myUserHandle()
                        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        val activities = launcherApps.getActivityList(target.packageName, userHandle)
                        val activityInfo = activities.firstOrNull()
                        if (activityInfo != null) {
                            launcherApps.startMainActivity(
                                activityInfo.componentName,
                                userHandle,
                                intent.sourceBounds,
                                null
                            )
                            finish()
                        } else {
                            val launchIntent = packageManager.getLaunchIntentForPackage(target.packageName)
                            if (launchIntent != null) {
                                startActivity(launchIntent)
                                finish()
                            }
                        }
                    }
                }
                ProviderResult(
                    id = "alias:app:${entry.alias}",
                    title = target.label,
                    subtitle = getString(R.string.alias_app_subtitle, entry.alias),
                    providerId = target.providerId,
                    onSelect = action,
                    aliasTarget = target,
                    keepOverlayUntilExit = true,
                    // Load app icon from PackageManager with profile badging and theming/icon pack support
                    iconLoader = {
                        loadAppIconBitmap(
                            context = this@SearchActivity,
                            packageName = target.packageName,
                            iconSize = defaultAppIconSize,
                            themedIconsEnabled = appSearchSettings.themedIconsEnabled,
                            themeAllIcons = appSearchSettings.themeAllIcons,
                            iconPackPackageName = appSearchSettings.iconPackPackageName,
                            userSerialNumber = target.userSerialNumber,
                        )
                    },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateBanner(
    result: GitHubUpdateChecker.UpdateResult,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    firstResultHighlightEnabled: Boolean,
    firstResultBorderThickness: Float,
    translucentResultsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.6f },
        )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd ||
            dismissState.currentValue == SwipeToDismissBoxValue.EndToStart
        ) {
            onDismiss()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Transparent),
            )
        },
        modifier = modifier,
    ) {
        val borderStroke =
            if (firstResultHighlightEnabled) {
                val borderColor =
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = if (translucentResultsEnabled) 0.5f else 0.22f,
                    )
                BorderStroke(firstResultBorderThickness.dp, borderColor)
            } else {
                null
            }

        Card(
            onClick = onDownload,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                ),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = borderStroke,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Update available: ${result.version}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Download Update",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    context: Context,
    textState: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    triggerState: TriggerState?,
    onDismissTrigger: () -> Unit,
    focusRequester: FocusRequester,
    settingsIconPosition: SettingsIconPosition,
    onSubmitSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SideEffect {
        PerformanceLogger.log(context, "ISSUE_2_RECOMPOSITION", "SearchBar composable recomposed! Current search text: '${textState.text}'")
    }
    Box(modifier = modifier) {
        SearchField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            value = textState,
            onValueChange = onSearchChange,
            triggerChip =
                triggerState?.let { activeTrigger ->
                    {
                        TriggerChip(
                            item = activeTrigger.trigger,
                            onDismiss = onDismissTrigger,
                        )
                    }
                },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            trailingIcon = {
                if (settingsIconPosition == SettingsIconPosition.INSIDE) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            onClear = { onSearchChange(TextFieldValue("")) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmitSearch() }),
            onBackspaceAtStart = triggerState?.let { { onDismissTrigger() } },
        )

        if (settingsIconPosition == SettingsIconPosition.INSIDE && textState.text.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                        .size(48.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenSettings,
                            onLongClick = onOpenSystemSettings,
                        ),
            )
        }
    }
}
