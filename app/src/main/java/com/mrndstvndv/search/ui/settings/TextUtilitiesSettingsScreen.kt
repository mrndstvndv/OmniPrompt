package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.TextUtilitiesSettings
import com.mrndstvndv.search.provider.settings.TextUtilityDefaultMode
import com.mrndstvndv.search.provider.text.TextUtilitiesProvider
import com.mrndstvndv.search.provider.text.TextUtilityInfo
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch

@Composable
fun TextUtilitiesSettingsScreen(
    repository: SettingsRepository<TextUtilitiesSettings>,
    onBack: () -> Unit,
) {
    val textUtilitiesSettings by repository.flow.collectAsState()
    val utilitiesInfo = remember { TextUtilitiesProvider.getUtilitiesInfo() }

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
                SettingsHeader(title = "Text Utilities", subtitle = "Configure text tools.", onBack = onBack)
            }

            item {
                SettingsGroup {
                    SettingsSwitch(
                        title = "Open decoded URLs",
                        subtitle = "Launch web links instead of copying them when decoding.",
                        checked = textUtilitiesSettings.openDecodedUrls,
                        onCheckedChange = { enabled -> repository.update { settings -> settings.copy(openDecodedUrls = enabled) } },
                    )
                }
            }

            item {
                SettingsGroup {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = "Available Utilities",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Enable or disable utilities and their trigger keywords.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    utilitiesInfo.forEachIndexed { index, info ->
                        if (index > 0) {
                            SettingsDivider()
                        }

                        val isEnabled = info.id !in textUtilitiesSettings.disabledUtilities
                        val disabledKeywords = textUtilitiesSettings.disabledKeywords[info.id] ?: emptySet()
                        val enabledKeywords = info.keywords - disabledKeywords
                        val defaultMode =
                            textUtilitiesSettings.utilityDefaultModes[info.id]
                                ?: info.defaultMode

                        UtilityRow(
                            info = info,
                            enabled = isEnabled,
                            enabledKeywords = enabledKeywords,
                            defaultMode = defaultMode,
                            onToggleUtility = { enabled ->
                                repository.update { settings ->
                                    settings.copy(
                                        disabledUtilities =
                                            if (enabled) {
                                                settings.disabledUtilities - info.id
                                            } else {
                                                settings.disabledUtilities + info.id
                                            },
                                    )
                                }
                            },
                            onToggleKeyword = { keyword, enabled ->
                                repository.update { settings ->
                                    val currentKeywords = settings.disabledKeywords[info.id] ?: emptySet()
                                    val newKeywords = if (enabled) currentKeywords - keyword else currentKeywords + keyword
                                    settings.copy(
                                        disabledKeywords = settings.disabledKeywords + (info.id to newKeywords),
                                    )
                                }
                            },
                            onModeChange = { mode ->
                                repository.update { settings ->
                                    settings.copy(
                                        utilityDefaultModes = settings.utilityDefaultModes + (info.id to mode),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun UtilityRow(
    info: TextUtilityInfo,
    enabled: Boolean,
    enabledKeywords: Set<String>,
    defaultMode: TextUtilityDefaultMode,
    onToggleUtility: (Boolean) -> Unit,
    onToggleKeyword: (String, Boolean) -> Unit,
    onModeChange: (TextUtilityDefaultMode) -> Unit,
) {
    val contentAlpha = if (enabled) 1f else 0.5f

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = onToggleUtility,
            )
        }

        // Keywords section (grayed out when utility is disabled)
        Column(
            modifier = Modifier.alpha(contentAlpha),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Keywords",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                info.keywords.forEach { keyword ->
                    val isKeywordEnabled = keyword in enabledKeywords
                    val isLastEnabled = enabledKeywords.size == 1 && isKeywordEnabled
                    FilterChip(
                        selected = isKeywordEnabled,
                        onClick = {
                            if (enabled && !(isLastEnabled && isKeywordEnabled)) {
                                onToggleKeyword(keyword, !isKeywordEnabled)
                            }
                        },
                        label = { Text(keyword) },
                        enabled = enabled && !(isLastEnabled && isKeywordEnabled),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Mode selector for utilities that support both modes
            if (info.supportsBothModes) {
                Text(
                    text = "Default mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SegmentedButton(
                        selected = defaultMode == TextUtilityDefaultMode.DECODE,
                        onClick = { onModeChange(TextUtilityDefaultMode.DECODE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        enabled = enabled,
                    ) {
                        Text("Decode")
                    }
                    SegmentedButton(
                        selected = defaultMode == TextUtilityDefaultMode.ENCODE,
                        onClick = { onModeChange(TextUtilityDefaultMode.ENCODE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        enabled = enabled,
                    ) {
                        Text("Encode")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = info.example,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
