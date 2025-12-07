package com.mrndstvndv.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mrndstvndv.search.alias.AliasRepository
import com.mrndstvndv.search.provider.files.FileSearchRepository
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.settings.AssistantRoleManager
import com.mrndstvndv.search.ui.settings.GeneralSettingsScreen
import com.mrndstvndv.search.ui.settings.ProvidersSettingsScreen
import com.mrndstvndv.search.ui.settings.AppearanceSettingsScreen
import com.mrndstvndv.search.ui.settings.BehaviorSettingsScreen
import com.mrndstvndv.search.ui.settings.AliasesSettingsScreen
import com.mrndstvndv.search.ui.settings.ResultRankingSettingsScreen
import com.mrndstvndv.search.ui.settings.WebSearchSettingsScreen
import com.mrndstvndv.search.ui.settings.FileSearchSettingsScreen
import com.mrndstvndv.search.ui.settings.TextUtilitiesSettingsScreen
import com.mrndstvndv.search.ui.settings.ProviderListScreen
import com.mrndstvndv.search.ui.settings.AppSearchSettingsScreen
import com.mrndstvndv.search.ui.settings.ContactsSettingsScreen
import com.mrndstvndv.search.ui.settings.SystemSettingsScreen
import com.mrndstvndv.search.ui.settings.BackupRestoreSettingsScreen
import com.mrndstvndv.search.ui.theme.SearchTheme
import com.mrndstvndv.search.provider.contacts.ContactsRepository
import com.mrndstvndv.search.provider.system.DeveloperSettingsManager
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalAnimationApi::class)
class SettingsActivity : ComponentActivity() {
    private val assistantRoleManager by lazy { AssistantRoleManager(this) }
    private val defaultAssistantState = mutableStateOf(false)

    private enum class Screen {
        Home,
        Providers,
        Appearance,
        Behavior,
        Aliases,
        Ranking,
        WebSearch,
        FileSearch,
        TextUtilities,
        AppSearch,
        ProviderList,
        SystemSettings,
        ContactsSettings,
        BackupRestore
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val coroutineScope = rememberCoroutineScope()
            val aliasRepository = remember { AliasRepository(this@SettingsActivity, coroutineScope) }
            val settingsRepository = remember { ProviderSettingsRepository(this@SettingsActivity, coroutineScope) }
            val fileSearchRepository = remember { FileSearchRepository.getInstance(this@SettingsActivity) }
            val rankingRepository = remember { ProviderRankingRepository.getInstance(this@SettingsActivity, coroutineScope) }
            val contactsRepository = remember { ContactsRepository.getInstance(this@SettingsActivity) }
            val developerSettingsManager = remember { DeveloperSettingsManager.getInstance(this@SettingsActivity) }
            val isDefaultAssistant by defaultAssistantState
            val motionPreferences by settingsRepository.motionPreferences.collectAsState()
            val webSearchSettings by settingsRepository.webSearchSettings.collectAsState()
            
            val initialScreen = remember {
                when (intent.getStringExtra(EXTRA_SCREEN)) {
                    SCREEN_PROVIDERS -> Screen.Providers
                    else -> Screen.Home
                }
            }
            var currentScreen by remember { mutableStateOf(initialScreen) }
            
            val appName = getString(R.string.app_name)
            SearchTheme(motionPreferences = motionPreferences) {
                LaunchedEffect(Unit) {
                    refreshDefaultAssistantState()
                }
                AnimatedContent(
                    targetState = currentScreen,
                    label = "settings_nav",
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        slideInHorizontally(
                            animationSpec = tween(durationMillis = 220)
                        ) { fullWidth -> fullWidth * direction } + fadeIn(
                            animationSpec = tween(durationMillis = 180)
                        ) togetherWith slideOutHorizontally(
                            animationSpec = tween(durationMillis = 220)
                        ) { fullWidth -> -fullWidth * direction } + fadeOut(
                            animationSpec = tween(durationMillis = 180)
                        )
                    }
                ) { screen ->
                    when (screen) {
                    Screen.Home -> {
                        GeneralSettingsScreen(
                            aliasRepository = aliasRepository,
                            settingsRepository = settingsRepository,
                            rankingRepository = rankingRepository,
                            appName = appName,
                            isDefaultAssistant = isDefaultAssistant,
                            onRequestSetDefaultAssistant = { assistantRoleManager.launchDefaultAssistantSettings() },
                            onOpenProviders = { currentScreen = Screen.Providers },
                            onOpenAppearance = { currentScreen = Screen.Appearance },
                            onOpenBehavior = { currentScreen = Screen.Behavior },
                            onOpenAliases = { currentScreen = Screen.Aliases },
                            onOpenResultRanking = { currentScreen = Screen.Ranking },
                            onOpenBackupRestore = { currentScreen = Screen.BackupRestore },
                            onClose = { finish() }
                        )
                    }
                    Screen.Providers -> {
                        BackHandler {
                            if (initialScreen == Screen.Providers) {
                                finish()
                            } else {
                                currentScreen = Screen.Home
                            }
                        }
                        ProvidersSettingsScreen(
                            settingsRepository = settingsRepository,
                            appName = appName,
                            isDefaultAssistant = isDefaultAssistant,
                            onRequestSetDefaultAssistant = { assistantRoleManager.launchDefaultAssistantSettings() },
                            onOpenWebSearchSettings = { currentScreen = Screen.WebSearch },
                            onOpenFileSearchSettings = { currentScreen = Screen.FileSearch },
                            onOpenTextUtilitiesSettings = { currentScreen = Screen.TextUtilities },
                            onOpenAppSearchSettings = { currentScreen = Screen.AppSearch },
                            onOpenSystemSettingsSettings = { currentScreen = Screen.SystemSettings },
                            onOpenContactsSettings = { currentScreen = Screen.ContactsSettings },
                            onBack = {
                                if (initialScreen == Screen.Providers) {
                                    finish()
                                } else {
                                    currentScreen = Screen.Home
                                }
                            }
                        )
                    }
                    Screen.Appearance -> {
                        BackHandler { currentScreen = Screen.Home }
                        AppearanceSettingsScreen(
                            settingsRepository = settingsRepository,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    Screen.Behavior -> {
                        BackHandler { currentScreen = Screen.Home }
                        BehaviorSettingsScreen(
                            settingsRepository = settingsRepository,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    Screen.Aliases -> {
                        BackHandler { currentScreen = Screen.Home }
                        AliasesSettingsScreen(
                            aliasRepository = aliasRepository,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    Screen.Ranking -> {
                        BackHandler { currentScreen = Screen.Home }
                        ResultRankingSettingsScreen(
                            rankingRepository = rankingRepository,
                            settingsRepository = settingsRepository,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                    Screen.WebSearch -> {
                        BackHandler { currentScreen = Screen.Providers }
                        WebSearchSettingsScreen(
                            initialSettings = webSearchSettings,
                            onBack = { currentScreen = Screen.Providers },
                            onSave = { newSettings ->
                                settingsRepository.saveWebSearchSettings(newSettings)
                            }
                        )
                    }
                    Screen.FileSearch -> {
                        BackHandler { currentScreen = Screen.Providers }
                        FileSearchSettingsScreen(
                            settingsRepository = settingsRepository,
                            fileSearchRepository = fileSearchRepository,
                            onBack = { currentScreen = Screen.Providers }
                        )
                    }
                    Screen.TextUtilities -> {
                        BackHandler { currentScreen = Screen.Providers }
                        TextUtilitiesSettingsScreen(
                            settingsRepository = settingsRepository,
                            onBack = { currentScreen = Screen.Providers }
                        )
                    }
                    Screen.AppSearch -> {
                        BackHandler { currentScreen = Screen.Providers }
                        AppSearchSettingsScreen(
                            settingsRepository = settingsRepository,
                            onBack = { currentScreen = Screen.Providers }
                        )
                    }
                    Screen.ProviderList -> {
                        // Legacy/Deep-link support if needed, or can be removed if unused
                        if (initialScreen != Screen.ProviderList) {
                            BackHandler { currentScreen = Screen.Home }
                        }
                        ProviderListScreen(
                            settingsRepository = settingsRepository,
                            onBack = {
                                if (initialScreen == Screen.ProviderList) {
                                    finish()
                                } else {
                                    currentScreen = Screen.Home
                                }
                            },
                            onOpenWebSearchSettings = { currentScreen = Screen.WebSearch },
                            onOpenFileSearchSettings = { currentScreen = Screen.FileSearch },
                            onOpenTextUtilitiesSettings = { currentScreen = Screen.TextUtilities }
                        )
                    }
                    Screen.SystemSettings -> {
                        BackHandler { currentScreen = Screen.Providers }
                        SystemSettingsScreen(
                            settingsRepository = settingsRepository,
                            developerSettingsManager = developerSettingsManager,
                            onBack = { currentScreen = Screen.Providers }
                        )
                    }
                    Screen.ContactsSettings -> {
                        BackHandler { currentScreen = Screen.Providers }
                        ContactsSettingsScreen(
                            settingsRepository = settingsRepository,
                            contactsRepository = contactsRepository,
                            onBack = { currentScreen = Screen.Providers }
                        )
                    }
                    Screen.BackupRestore -> {
                        BackHandler { currentScreen = Screen.Home }
                        BackupRestoreSettingsScreen(
                            settingsRepository = settingsRepository,
                            rankingRepository = rankingRepository,
                            aliasRepository = aliasRepository,
                            onBack = { currentScreen = Screen.Home }
                        )
                    }
                }
                }
            }
        }
    }

    companion object {
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
}