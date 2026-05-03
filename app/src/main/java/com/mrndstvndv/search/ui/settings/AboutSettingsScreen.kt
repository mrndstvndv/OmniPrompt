package com.mrndstvndv.search.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.R
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsNavigationRow

@Composable
fun AboutSettingsScreen(
    versionName: String,
    repositoryUrl: String,
    onOpenLicenses: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repositoryLabel = repositoryUrl.removePrefix("https://").removePrefix("http://")

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item {
            SettingsHeader(
                title = stringResource(R.string.settings_about),
                subtitle = stringResource(R.string.settings_about_subtitle),
                onBack = onBack,
            )
        }

        item {
            SettingsGroup {
                AboutValueRow(
                    icon = Icons.Rounded.Info,
                    title = stringResource(R.string.about_version),
                    value = versionName,
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.about_github),
                    subtitle = repositoryLabel,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repositoryUrl)))
                    },
                )
                SettingsDivider()
                SettingsNavigationRow(
                    icon = Icons.Rounded.Description,
                    title = stringResource(R.string.about_open_source_licenses),
                    subtitle = stringResource(R.string.about_open_source_licenses_subtitle),
                    onClick = onOpenLicenses,
                )
            }
        }
    }
}

@Composable
private fun AboutValueRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier =
                Modifier
                    .padding(end = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .size(40.dp),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
