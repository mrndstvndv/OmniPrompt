package com.mrndstvndv.search.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.BuildConfig
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.ui.components.MarkdownText
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsNavigationRow
import com.mrndstvndv.search.ui.components.settings.SettingsSingleChoiceSegmentedButtons
import com.mrndstvndv.search.ui.components.settings.SettingsSliderRow
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch
import com.mrndstvndv.search.util.GitHubUpdateChecker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun UpdatesSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val updateCheckInterval by settingsRepository.updateCheckInterval.collectAsState()
    val customUpdateIntervalDays by settingsRepository.customUpdateIntervalDays.collectAsState()
    val latestUpdate by settingsRepository.latestUpdate.collectAsState()
    val checkPrereleaseBuilds by settingsRepository.checkPrereleaseBuilds.collectAsState()

    var isChecking by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<GitHubUpdateChecker.UpdateResult?>(null) }
    var showUpToDateDialog by remember { mutableStateOf(false) }
    var showFailedDialog by remember { mutableStateOf(false) }

    fun runUpdateCheck() {
        if (isChecking) return
        isChecking = true
        coroutineScope.launch {
            val result =
                GitHubUpdateChecker.checkForUpdates(
                    BuildConfig.VERSION_NAME,
                    checkPrereleaseBuilds,
                )
            isChecking = false
            when (result) {
                is GitHubUpdateChecker.CheckResult.NewUpdate -> {
                    updateResult = result.update
                    settingsRepository.setLatestUpdate(result.update)
                }
                is GitHubUpdateChecker.CheckResult.UpToDate -> {
                    showUpToDateDialog = true
                    settingsRepository.setLatestUpdate(null)
                }
                is GitHubUpdateChecker.CheckResult.Error -> {
                    showFailedDialog = true
                }
            }
        }
    }

    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, top = systemBarsPadding.calculateTopPadding(), end = 20.dp, bottom = systemBarsPadding.calculateBottomPadding() + 28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            SettingsHeader(
                title = stringResource(R.string.settings_updates),
                subtitle = stringResource(R.string.settings_updates_subtitle),
                onBack = onBack,
            )
        }

        if (latestUpdate != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.primaryContainer.copy(
                                    alpha = 0.9f,
                                ),
                        ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "New Update: ${latestUpdate!!.version}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        if (latestUpdate!!.changelog.isNotBlank()) {
                            MarkdownText(
                                markdown = latestUpdate!!.changelog,
                                color =
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(
                                        alpha = 0.8f,
                                    ),
                                linkColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }

                        Button(
                            onClick = {
                                val browserIntent =
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(latestUpdate!!.downloadUrl),
                                    )
                                browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(browserIntent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Download Update")
                        }
                    }
                }
            }
        }

        item {
            SettingsGroup {
                SettingsNavigationRow(
                    icon = Icons.Rounded.SystemUpdate,
                    title = stringResource(R.string.updates_check_now),
                    subtitle = stringResource(R.string.about_version) + ": " + BuildConfig.VERSION_NAME,
                    onClick = { runUpdateCheck() },
                    showChevron = false,
                )

                SettingsDivider()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = stringResource(R.string.updates_check_interval),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_updates_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val intervalOptions = listOf("daily", "weekly", "monthly", "custom")
                    val intervalLabels =
                        mapOf(
                            "daily" to stringResource(R.string.updates_interval_daily),
                            "weekly" to stringResource(R.string.updates_interval_weekly),
                            "monthly" to stringResource(R.string.updates_interval_monthly),
                            "custom" to stringResource(R.string.updates_interval_custom),
                        )

                    SettingsSingleChoiceSegmentedButtons(
                        options = intervalOptions,
                        selectedOption = updateCheckInterval,
                        enabled = true,
                        label = { option -> intervalLabels[option] ?: option },
                        onOptionSelected = { settingsRepository.setUpdateCheckInterval(it) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                        showSelectedIcon = false,
                    )
                }

                if (updateCheckInterval == "custom") {
                    SettingsDivider()

                    SettingsSliderRow(
                        title = stringResource(R.string.updates_custom_days),
                        subtitle = null,
                        valueText =
                            stringResource(
                                R.string.updates_custom_days_val,
                                customUpdateIntervalDays,
                            ),
                        value = customUpdateIntervalDays.toFloat(),
                        onValueChange = {
                            settingsRepository.setCustomUpdateIntervalDays(
                                it.roundToInt(),
                            )
                        },
                        valueRange = 1f..90f,
                        steps = 88,
                    )
                }

                SettingsDivider()

                SettingsSwitch(
                    title = stringResource(R.string.updates_check_prerelease),
                    subtitle = stringResource(R.string.updates_check_prerelease_desc),
                    checked = checkPrereleaseBuilds,
                    onCheckedChange = { settingsRepository.setCheckPrereleaseBuilds(it) },
                )
            }
        }
    }

    if (isChecking) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.updates_checking)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                }
            },
            confirmButton = {},
        )
    }

    updateResult?.let { update ->
        AlertDialog(
            onDismissRequest = { updateResult = null },
            title = { Text(stringResource(R.string.updates_new_version_available)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.about_version) + ": " + update.version,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (update.changelog.isNotBlank()) {
                        MarkdownText(
                            markdown = update.changelog,
                            modifier = Modifier.padding(top = 4.dp),
                            linkColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val browserIntent =
                            Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                        browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(browserIntent)
                        updateResult = null
                    },
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { updateResult = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showUpToDateDialog) {
        AlertDialog(
            onDismissRequest = { showUpToDateDialog = false },
            title = { Text(stringResource(R.string.updates_up_to_date)) },
            text = {
                Text(
                    stringResource(R.string.updates_up_to_date_desc, BuildConfig.VERSION_NAME),
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpToDateDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showFailedDialog) {
        AlertDialog(
            onDismissRequest = { showFailedDialog = false },
            title = { Text(stringResource(R.string.updates_error)) },
            text = { Text(stringResource(R.string.updates_error_desc)) },
            confirmButton = {
                TextButton(onClick = { showFailedDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }
}
