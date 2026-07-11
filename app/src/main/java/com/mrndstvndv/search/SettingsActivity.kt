package com.mrndstvndv.search

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.mrndstvndv.search.provider.termux.TermuxProvider
import com.mrndstvndv.search.settings.AssistantRoleManager
import com.mrndstvndv.search.ui.settings.About
import com.mrndstvndv.search.ui.settings.AboutSettingsScreen
import com.mrndstvndv.search.ui.settings.Aliases
import com.mrndstvndv.search.ui.settings.AliasesSettingsScreen
import com.mrndstvndv.search.ui.settings.AppSearch
import com.mrndstvndv.search.ui.settings.AppSearchSettingsScreen
import com.mrndstvndv.search.ui.settings.Appearance
import com.mrndstvndv.search.ui.settings.AppearanceSettingsScreen
import com.mrndstvndv.search.ui.settings.BackupRestore
import com.mrndstvndv.search.ui.settings.BackupRestoreSettingsScreen
import com.mrndstvndv.search.ui.settings.Behavior
import com.mrndstvndv.search.ui.settings.BehaviorSettingsScreen
import com.mrndstvndv.search.ui.settings.ContactsSettings
import com.mrndstvndv.search.ui.settings.ContactsSettingsScreen
import com.mrndstvndv.search.ui.settings.FileSearch
import com.mrndstvndv.search.ui.settings.FileSearchSettingsScreen
import com.mrndstvndv.search.ui.settings.GeneralSettingsScreen
import com.mrndstvndv.search.ui.settings.Home
import com.mrndstvndv.search.ui.settings.IntentSettings
import com.mrndstvndv.search.ui.settings.IntentSettingsScreen
import com.mrndstvndv.search.ui.settings.Licenses
import com.mrndstvndv.search.ui.settings.OpenSourceLicensesScreen
import com.mrndstvndv.search.ui.settings.Providers
import com.mrndstvndv.search.ui.settings.ProvidersSettingsScreen
import com.mrndstvndv.search.ui.settings.Ranking
import com.mrndstvndv.search.ui.settings.ResultRankingSettingsScreen
import com.mrndstvndv.search.ui.settings.SettingsKey
import com.mrndstvndv.search.ui.settings.SystemSettings
import com.mrndstvndv.search.ui.settings.SystemSettingsScreen
import com.mrndstvndv.search.ui.settings.TermuxSettings
import com.mrndstvndv.search.ui.settings.TermuxSettingsScreen
import com.mrndstvndv.search.ui.settings.TextUtilities
import com.mrndstvndv.search.ui.settings.TextUtilitiesSettingsScreen
import com.mrndstvndv.search.ui.settings.Updates
import com.mrndstvndv.search.ui.settings.UpdatesSettingsScreen
import com.mrndstvndv.search.ui.settings.WebSearch
import com.mrndstvndv.search.ui.settings.WebSearchSettingsScreen
import com.mrndstvndv.search.ui.theme.SearchTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    private val assistantRoleManager by lazy { AssistantRoleManager(this) }
    private val defaultAssistantState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        setContent {
            val container = (application as SearchApplication).container
            val settingsRepository = container.settingsRepository
            val aliasRepository = container.aliasRepository
            val webSearchSettingsRepo = container.webSearchSettingsRepo
            val appSearchSettingsRepo = container.appSearchSettingsRepo
            val textUtilitiesSettingsRepo = container.textUtilitiesSettingsRepo
            val fileSearchSettingsRepo = container.fileSearchSettingsRepo
            val systemSettingsSettingsRepo = container.systemSettingsSettingsRepo
            val contactsSettingsRepo = container.contactsSettingsRepo
            val termuxSettingsRepo = container.termuxSettingsRepo
            val intentSettingsRepo = container.intentSettingsRepo

            val fileSearchRepository = container.fileSearchRepository
            val rankingRepository = container.rankingRepository
            val contactsRepository = container.contactsRepository
            val developerSettingsManager = container.developerSettingsManager
            val appListRepository = container.appListRepository
            val isDefaultAssistant by defaultAssistantState
            val motionPreferences by settingsRepository.motionPreferences.collectAsState()
            val webSearchSettings by webSearchSettingsRepo.flow.collectAsState()

            val initialScreen: SettingsKey =
                remember {
                    when (intent.getStringExtra(EXTRA_SCREEN)) {
                        SCREEN_PROVIDERS -> Providers
                        else -> Home
                    }
                }
            val backStack = rememberNavBackStack(initialScreen)

            val appName = getString(R.string.app_name)
            SearchTheme(motionPreferences = motionPreferences) {
                LaunchedEffect(Unit) {
                    refreshDefaultAssistantState()
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color.Black
                ) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = {
                            if (backStack.size <= 1) {
                                finish()
                            } else {
                                backStack.removeLastOrNull()
                            }
                        },
                        transitionSpec = {
                            (slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            ) togetherWith (slideOutHorizontally(
                                targetOffsetX = { 0 },
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            ) + fadeOut(
                                targetAlpha = 0.5f,
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            ))).apply {
                                targetContentZIndex = 1f
                            }
                        },
                        popTransitionSpec = {
                            ((slideInHorizontally(
                                initialOffsetX = { 0 },
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            ) + fadeIn(
                                initialAlpha = 0.5f,
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            )) togetherWith slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            )).apply {
                                targetContentZIndex = -1f
                            }
                        },
                        predictivePopTransitionSpec = {
                            ((slideInHorizontally(
                                initialOffsetX = { 0 },
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            ) + fadeIn(
                                initialAlpha = 0.5f,
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            )) togetherWith slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(durationMillis = 500, easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.03f))
                            )).apply {
                                targetContentZIndex = -1f
                            }
                        },
                    entryProvider = entryProvider {
                        entry<Home> {
                            RoundedScreen {
                                GeneralSettingsScreen(
                                    aliasRepository = aliasRepository,
                                    settingsRepository = settingsRepository,
                                    rankingRepository = rankingRepository,
                                    appName = appName,
                                    isDefaultAssistant = isDefaultAssistant,
                                    onRequestSetDefaultAssistant = { assistantRoleManager.launchDefaultAssistantSettings() },
                                    onOpenProviders = dropUnlessResumed { backStack.add(Providers) },
                                    onOpenAppearance = dropUnlessResumed { backStack.add(Appearance) },
                                    onOpenBehavior = dropUnlessResumed { backStack.add(Behavior) },
                                    onOpenAliases = dropUnlessResumed { backStack.add(Aliases) },
                                    onOpenResultRanking = dropUnlessResumed { backStack.add(Ranking) },
                                    onOpenBackupRestore = dropUnlessResumed { backStack.add(BackupRestore) },
                                    onOpenUpdates = dropUnlessResumed { backStack.add(Updates) },
                                    onOpenAbout = dropUnlessResumed { backStack.add(About) },
                                    onClose = { finish() },
                                )
                            }
                        }

                        entry<Providers> {
                            RoundedScreen {
                                ProvidersSettingsScreen(
                                    settingsRepository = settingsRepository,
                                    appName = appName,
                                    isDefaultAssistant = isDefaultAssistant,
                                    onRequestSetDefaultAssistant = { assistantRoleManager.launchDefaultAssistantSettings() },
                                    onOpenWebSearchSettings = dropUnlessResumed { backStack.add(WebSearch) },
                                    onOpenFileSearchSettings = dropUnlessResumed { backStack.add(FileSearch) },
                                    onOpenTextUtilitiesSettings = dropUnlessResumed { backStack.add(TextUtilities) },
                                    onOpenAppSearchSettings = dropUnlessResumed { backStack.add(AppSearch) },
                                    onOpenSystemSettingsSettings = dropUnlessResumed { backStack.add(SystemSettings) },
                                    onOpenContactsSettings = dropUnlessResumed { backStack.add(ContactsSettings) },
                                    onOpenTermuxSettings = dropUnlessResumed { backStack.add(TermuxSettings) },
                                    onOpenIntentSettings = dropUnlessResumed { backStack.add(IntentSettings) },
                                    onBack = {
                                        if (backStack.size <= 1) {
                                            finish()
                                        } else {
                                            backStack.removeLastOrNull()
                                        }
                                    },
                                )
                            }
                        }

                        entry<Appearance> {
                            RoundedScreen {
                                AppearanceSettingsScreen(
                                    settingsRepository = settingsRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<Behavior> {
                            RoundedScreen {
                                BehaviorSettingsScreen(
                                    settingsRepository = settingsRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<Aliases> {
                            RoundedScreen {
                                AliasesSettingsScreen(
                                    aliasRepository = aliasRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<Ranking> {
                            RoundedScreen {
                                ResultRankingSettingsScreen(
                                    rankingRepository = rankingRepository,
                                    settingsRepository = settingsRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<WebSearch> {
                            RoundedScreen {
                                WebSearchSettingsScreen(
                                    initialSettings = webSearchSettings,
                                    onBack = { backStack.removeLastOrNull() },
                                    onSave = { newSettings ->
                                        webSearchSettingsRepo.replace(newSettings)
                                    },
                                )
                            }
                        }

                        entry<FileSearch> {
                            RoundedScreen {
                                FileSearchSettingsScreen(
                                    repository = fileSearchSettingsRepo,
                                    fileSearchRepository = fileSearchRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<TextUtilities> {
                            RoundedScreen {
                                TextUtilitiesSettingsScreen(
                                    repository = textUtilitiesSettingsRepo,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<AppSearch> {
                            RoundedScreen {
                                AppSearchSettingsScreen(
                                    repository = appSearchSettingsRepo,
                                    appListRepository = appListRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<SystemSettings> {
                            RoundedScreen {
                                SystemSettingsScreen(
                                    repository = systemSettingsSettingsRepo,
                                    developerSettingsManager = developerSettingsManager,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<ContactsSettings> {
                            RoundedScreen {
                                ContactsSettingsScreen(
                                    repository = contactsSettingsRepo,
                                    contactsRepository = contactsRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<BackupRestore> {
                            RoundedScreen {
                                BackupRestoreSettingsScreen(
                                    settingsRepository = settingsRepository,
                                    fileSearchSettingsRepo = fileSearchSettingsRepo,
                                    rankingRepository = rankingRepository,
                                    aliasRepository = aliasRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<Updates> {
                            RoundedScreen {
                                UpdatesSettingsScreen(
                                    settingsRepository = settingsRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<About> {
                            RoundedScreen {
                                AboutSettingsScreen(
                                    versionName = BuildConfig.VERSION_NAME,
                                    repositoryUrl = GITHUB_REPOSITORY_URL,
                                    onOpenLicenses = dropUnlessResumed { backStack.add(Licenses) },
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<Licenses> {
                            RoundedScreen {
                                OpenSourceLicensesScreen(
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<TermuxSettings> {
                            RoundedScreen {
                                TermuxSettingsScreen(
                                    repository = termuxSettingsRepo,
                                    isTermuxInstalled =
                                        TermuxProvider.isTermuxInstalled(
                                            this@SettingsActivity,
                                        ),
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }

                        entry<IntentSettings> {
                            RoundedScreen {
                                IntentSettingsScreen(
                                    repository = intentSettingsRepo,
                                    appListRepository = appListRepository,
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }
                    }
                )
            }
        }
        }
    }

    companion object {
        private const val GITHUB_REPOSITORY_URL = "https://github.com/mrndstvndv/Search"
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_PROVIDERS = "providers"
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultAssistantState()
    }

    private fun refreshDefaultAssistantState() {
        lifecycleScope.launch(Dispatchers.Default) {
            val isDefault = assistantRoleManager.isDefaultAssistant()
            withContext(Dispatchers.Main) {
                defaultAssistantState.value = isDefault
            }
        }
    }

    @Composable
    private fun RoundedScreen(content: @Composable () -> Unit) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)),
            color = MaterialTheme.colorScheme.background
        ) {
            content()
        }
    }
}
