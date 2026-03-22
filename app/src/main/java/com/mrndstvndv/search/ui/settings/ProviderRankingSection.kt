package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsSection
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch

@Composable
fun ProviderRankingSection(
    rankingRepository: ProviderRankingRepository,
    enabledProviders: Map<String, Boolean>,
) {
    val providerOrder by rankingRepository.providerOrder.collectAsState()
    val useFrequencyRanking by rankingRepository.useFrequencyRanking.collectAsState()
    val queryBasedRankingEnabled by rankingRepository.queryBasedRankingEnabled.collectAsState()
    val resultFrequency by rankingRepository.resultFrequency.collectAsState()
    val decayAmount by rankingRepository.decayAmount.collectAsState()
    var showFrequencyDialog by remember { mutableStateOf(false) }

    val visibleProviderOrder = providerOrder.filter { enabledProviders[it] != false }

    if (showFrequencyDialog) {
        FrequencyRankingDialog(
            frequency = resultFrequency,
            onDismiss = { showFrequencyDialog = false },
            onReset = { rankingRepository.resetResultFrequency() },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection(
            title = stringResource(R.string.ranking_result_ranking),
            subtitle = "Control how results are ordered.",
        ) {
            SettingsGroup {
                // Frequency-based ranking toggle
                // Using custom Row to support clicking the row to open the dialog
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { showFrequencyDialog = true }
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ranking_use_frequency),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (useFrequencyRanking) stringResource(R.string.ranking_frequency_desc_on) else stringResource(R.string.ranking_frequency_desc_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useFrequencyRanking,
                        onCheckedChange = { rankingRepository.setUseFrequencyRanking(it) },
                    )
                }

                if (useFrequencyRanking) {
                    SettingsDivider()
                    SettingsSwitch(
                        title = "Use query-specific ranking",
                        subtitle = if (queryBasedRankingEnabled) "Ranking depends on the specific search term" else "Ranking is global across all queries",
                        checked = queryBasedRankingEnabled,
                        onCheckedChange = { rankingRepository.setQueryBasedRankingEnabled(it) },
                    )
                    SettingsDivider()
                    com.mrndstvndv.search.ui.components.settings.SettingsSliderRow(
                        title = stringResource(R.string.ranking_competitive_decay),
                        subtitle = stringResource(R.string.ranking_competitive_decay_subtitle),
                        value = decayAmount,
                        onValueChange = { rankingRepository.setDecayAmount(it) },
                        valueRange = 0f..5f,
                        steps = 9,
                        valueText = String.format("%.1f", decayAmount),
                    )
                }
            }
        }

        SettingsSection(
            title = stringResource(R.string.ranking_provider_order),
        ) {
            SettingsGroup {
                visibleProviderOrder.forEachIndexed { index, providerId ->
                    ProviderRankingItem(
                        providerId = providerId,
                        isFirst = index == 0,
                        isLast = index == visibleProviderOrder.size - 1,
                        onMoveUp = {
                            rankingRepository.moveUp(providerId) { id -> enabledProviders[id] != false }
                        },
                        onMoveDown = {
                            rankingRepository.moveDown(providerId) { id -> enabledProviders[id] != false }
                        },
                    )
                    if (index < visibleProviderOrder.size - 1) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyRankingDialog(
    frequency: Map<String, Map<String, Float>>,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.ranking_frequency_data)) },
        text = {
            Column {
                Text(
                    text = "Usage is now scoped to specific search queries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (frequency.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ranking_no_usage_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val sortedQueries = frequency.keys.sorted()
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                    ) {
                        sortedQueries.forEach { query ->
                            item(key = query) {
                                Text(
                                    text = if (query.isEmpty()) "General (no query)" else "Query: \"$query\"",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                                )
                            }
                            val queryCounts = frequency[query] ?: emptyMap()
                            val sortedResults = queryCounts.entries.sortedByDescending { it.value }

                            itemsIndexed(sortedResults) { index, (resultId, score) ->
                                FrequencyItem(resultId = resultId, score = score)
                                if (index < sortedResults.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(start = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    )
                                }
                            }

                            item {
                                HorizontalDivider(
                                    modifier = Modifier.padding(top = 8.dp),
                                    thickness = 2.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(text = stringResource(R.string.ranking_reset_data))
            }
        },
    )
}

@Composable
private fun FrequencyItem(
    resultId: String,
    score: Float,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = resultId,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format("%.1f", score),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun ProviderRankingItem(
    providerId: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val context = LocalContext.current
    val displayName = getProviderDisplayName(context, providerId)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        Row {
            IconButton(
                onClick = onMoveUp,
                enabled = !isFirst,
                modifier = Modifier.width(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = stringResource(R.string.move_up),
                    modifier = Modifier.width(16.dp),
                )
            }

            IconButton(
                onClick = onMoveDown,
                enabled = !isLast,
                modifier = Modifier.width(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = stringResource(R.string.move_down),
                    modifier = Modifier.width(16.dp),
                )
            }
        }
    }
}

private fun getProviderDisplayName(context: android.content.Context, providerId: String): String =
    when (providerId) {
        "app-list" -> context.getString(R.string.provider_applications)
        "calculator" -> context.getString(R.string.provider_calculator)
        "text-utilities" -> context.getString(R.string.provider_text_utilities)
        "file-search" -> context.getString(R.string.provider_file_search)
        "web-search" -> context.getString(R.string.provider_web_search)
        "system-settings" -> context.getString(R.string.provider_system_settings)
        "contacts" -> context.getString(R.string.provider_contacts)
        "termux" -> context.getString(R.string.provider_termux)
        "intent" -> context.getString(R.string.provider_intent_launcher)
        else -> providerId
    }
