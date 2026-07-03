package com.mrndstvndv.search.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsNavigationRow
import com.mrndstvndv.search.ui.components.settings.SettingsSwitch

@Composable
fun DebugSettingsScreen(
    settingsRepository: ProviderSettingsRepository,
    rankingRepository: ProviderRankingRepository,
    onBack: () -> Unit,
) {
    val collectDebugData by settingsRepository.collectDebugData.collectAsState()
    val context = LocalContext.current
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = systemBarsPadding.calculateTopPadding(),
            end = 20.dp,
            bottom = systemBarsPadding.calculateBottomPadding() + 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            SettingsHeader(
                title = stringResource(R.string.settings_debug),
                subtitle = stringResource(R.string.settings_debug_subtitle),
                onBack = onBack,
            )
        }

        item {
            SettingsGroup {
                SettingsSwitch(
                    title = stringResource(R.string.settings_collect_debug_data),
                    subtitle = stringResource(R.string.settings_collect_debug_data_subtitle),
                    icon = Icons.Rounded.BugReport,
                    checked = collectDebugData,
                    onCheckedChange = { settingsRepository.setCollectDebugData(it) }
                )
                SettingsDivider()
                SettingsNavigationRow(
                    title = stringResource(R.string.settings_export_debug_info),
                    subtitle = if (collectDebugData) {
                        stringResource(R.string.settings_export_debug_info_subtitle)
                    } else {
                        "Enable debug collection above to record search data"
                    },
                    icon = Icons.Rounded.BugReport,
                    onClick = {
                        val json = com.mrndstvndv.search.util.SearchDebugCollector.generateDebugJson(
                            context,
                            rankingRepository,
                            settingsRepository
                        )
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Search Debug Info", json)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.debug_info_copied), Toast.LENGTH_SHORT).show()

                        // Share via Intent
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TEXT, json)
                            putExtra(Intent.EXTRA_SUBJECT, "Search Debug Info")
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Debug Info"))
                    }
                )
            }
        }
    }
}
