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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.ProviderRankingRepository

@Composable
fun ProviderRankingSection(
    rankingRepository: ProviderRankingRepository,
    enabledProviders: Map<String, Boolean>,
) {
    val providerOrder by rankingRepository.providerOrder.collectAsState()
    val useFrequencyRanking by rankingRepository.useFrequencyRanking.collectAsState()
    val queryBasedRankingEnabled by rankingRepository.queryBasedRankingEnabled.collectAsState()
    val resultFrequency by rankingRepository.resultFrequency.collectAsState()
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
        modifier =
            Modifier
                .fillMaxWidth(),
    ) {
        Text(
            text = "Result Ranking",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "Control how results are ordered.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
            // Toggle for frequency-based ranking
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
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
                            text = "Use frequency-based ranking",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (useFrequencyRanking) "Most used items appear first" else "Provider order",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = useFrequencyRanking,
                        onCheckedChange = { rankingRepository.setUseFrequencyRanking(it) },
                    )
                }
            }

            if (useFrequencyRanking) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { rankingRepository.setQueryBasedRankingEnabled(!queryBasedRankingEnabled) }
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Use query-specific ranking",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = if (queryBasedRankingEnabled) "Ranking depends on the specific search term" else "Ranking is global across all queries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = queryBasedRankingEnabled,
                            onCheckedChange = { rankingRepository.setQueryBasedRankingEnabled(it) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Provider order",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
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
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequencyRankingDialog(
    frequency: Map<String, Map<String, Int>>,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Frequency data") },
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
                        text = "No usage data yet",
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

                            itemsIndexed(sortedResults) { index, (resultId, count) ->
                                FrequencyItem(resultId = resultId, count = count)
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
                Text(text = "Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onReset) {
                Text(text = "Reset data")
            }
        },
    )
}

@Composable
private fun FrequencyItem(
    resultId: String,
    count: Int,
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
                text = "$count",
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
    val displayName = getProviderDisplayName(providerId)

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
                    contentDescription = "Move up",
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
                    contentDescription = "Move down",
                    modifier = Modifier.width(16.dp),
                )
            }
        }
    }
}

private fun getProviderDisplayName(providerId: String): String =
    when (providerId) {
        "app-list" -> "Applications"
        "calculator" -> "Calculator"
        "text-utilities" -> "Text Utilities"
        "file-search" -> "Files & Folders"
        "web-search" -> "Web Search"
        "system-settings" -> "System Settings"
        "debug-long-operation" -> "Debug • Long Operation"
        else -> providerId
    }
