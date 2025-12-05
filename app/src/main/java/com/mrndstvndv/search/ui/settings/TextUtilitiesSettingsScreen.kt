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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.text.TextUtilitiesProvider
import com.mrndstvndv.search.provider.text.TextUtilityInfo

@Composable
fun TextUtilitiesSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit
) {
    val textUtilitiesSettings by settingsRepository.textUtilitiesSettings.collectAsState()
    val utilitiesInfo = remember { TextUtilitiesProvider.getUtilitiesInfo() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            item {
                SettingsHeader(onBack = onBack)
            }

            item {
                SettingsCardGroup {
                    SettingsToggleRow(
                        title = "Open decoded URLs",
                        subtitle = "Launch web links instead of copying them when decoding.",
                        checked = textUtilitiesSettings.openDecodedUrls,
                        onCheckedChange = { settingsRepository.setOpenDecodedUrlsAutomatically(it) }
                    )
                }
            }

            item {
                SettingsCardGroup {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = "Available Utilities",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Enable or disable utilities and their trigger keywords.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    utilitiesInfo.forEachIndexed { index, info ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        val isEnabled = info.id !in textUtilitiesSettings.disabledUtilities
                        val disabledKeywords = textUtilitiesSettings.disabledKeywords[info.id] ?: emptySet()
                        val enabledKeywords = info.keywords - disabledKeywords

                        UtilityRow(
                            info = info,
                            enabled = isEnabled,
                            enabledKeywords = enabledKeywords,
                            onToggleUtility = { enabled ->
                                settingsRepository.setUtilityEnabled(info.id, enabled)
                            },
                            onToggleKeyword = { keyword, enabled ->
                                settingsRepository.setKeywordEnabled(info.id, keyword, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Text Utilities",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Configure text tools.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun SettingsCardGroup(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UtilityRow(
    info: TextUtilityInfo,
    enabled: Boolean,
    enabledKeywords: Set<String>,
    onToggleUtility: (Boolean) -> Unit,
    onToggleKeyword: (String, Boolean) -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.displayName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggleUtility
            )
        }

        // Keywords section (grayed out when utility is disabled)
        Column(
            modifier = Modifier.alpha(contentAlpha)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Keywords",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        enabled = enabled && !(isLastEnabled && isKeywordEnabled)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = info.example,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
