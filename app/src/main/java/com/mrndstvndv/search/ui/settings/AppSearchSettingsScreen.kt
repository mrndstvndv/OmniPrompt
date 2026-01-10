package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch

@Composable
fun AppSearchSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit,
) {
    val appSearchSettings by settingsRepository.appSearchSettings.collectAsState()

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
                SettingsSection(
                    title = "Recent Apps",
                    subtitle = "Configure how recent apps appear.",
                ) {
                    SettingsGroup {
                        SettingsSwitch(
                            title = "Show recent apps",
                            subtitle = "Display recently used apps on the home screen.",
                            checked = appSearchSettings.showRecentApps,
                            onCheckedChange = {
                                settingsRepository.saveAppSearchSettings(
                                    appSearchSettings.copy(showRecentApps = it),
                                )
                            },
                        )
                        SettingsDivider()
                        SettingsSwitch(
                            title = "Reverse order",
                            subtitle = "Place the most recently used app on the right side.",
                            checked = appSearchSettings.reverseRecentAppsOrder,
                            onCheckedChange = {
                                settingsRepository.saveAppSearchSettings(
                                    appSearchSettings.copy(reverseRecentAppsOrder = it),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
