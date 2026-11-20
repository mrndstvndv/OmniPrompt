package com.mrndstvndv.search.ui.debug

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.system.ExcludedActivity
import com.mrndstvndv.search.provider.system.SettingsCatalogRepository
import com.mrndstvndv.search.provider.system.SettingsExclusionReason
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Debug-only diagnostic list of settings activities the explorer chose not to
 * surface, and why — reflects the most recent scan regardless of whether it
 * happened in this process or a previous one (read straight from disk, not
 * the live in-memory catalog).
 */
@Composable
fun ExcludedSettingsScreen(
    settingsCatalogRepository: SettingsCatalogRepository,
    onBack: () -> Unit,
) {
    var items by remember { mutableStateOf<List<ExcludedActivity>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) { settingsCatalogRepository.readLastExcludedActivities() }
        loaded = true
    }

    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                top = systemBarsPadding.calculateTopPadding(),
                end = 20.dp,
                bottom = systemBarsPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            SettingsHeader(title = "Excluded settings screens (debug)", onBack = onBack)
        }

        item {
            Text(
                text = "Tap an entry to try opening it and judge whether it's worth adding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (loaded && items.isEmpty()) {
            item {
                Text(
                    text = "Nothing excluded in the most recent scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(items, key = { it.componentName.flattenToString() }) { excluded ->
            ExcludedActivityRow(excluded)
        }
    }
}

@Composable
private fun ExcludedActivityRow(excluded: ExcludedActivity) {
    val context = LocalContext.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    try {
                        val intent =
                            Intent().apply {
                                component = excluded.componentName
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Couldn't open: ${e.javaClass.simpleName}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = excluded.componentName.flattenToString(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = excluded.reason.toDisplayString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun SettingsExclusionReason.toDisplayString(): String =
    when (this) {
        SettingsExclusionReason.NO_LABEL_NO_HARDCODED_MATCH -> "No label, no known match"
        SettingsExclusionReason.DIALOG_STYLED -> "Detected as dialog/popup"
    }
