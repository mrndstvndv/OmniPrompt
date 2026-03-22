package com.mrndstvndv.search.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import com.mrndstvndv.search.ui.components.ContentDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.mrndstvndv.search.provider.settings.WebSearchSettings
import com.mrndstvndv.search.provider.settings.WebSearchSite

@Composable
fun WebSearchProviderSettingsDialog(
    initialSettings: WebSearchSettings,
    onDismiss: () -> Unit,
    onSave: (WebSearchSettings) -> Unit
) {
    var sites by remember { mutableStateOf(initialSettings.sites) }
    var defaultSiteId by remember { mutableStateOf(initialSettings.defaultSiteId) }
    var newSiteName by remember { mutableStateOf("") }
    var newSiteTemplate by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val placeholder = WebSearchSettings.QUERY_PLACEHOLDER
    val context = LocalContext.current

    LaunchedEffect(initialSettings) {
        sites = initialSettings.sites
        defaultSiteId = initialSettings.defaultSiteId
    }

    val allTemplatesValid = sites.all { it.urlTemplate.contains(placeholder) }

    fun updateSite(index: Int, updater: (WebSearchSite) -> WebSearchSite) {
        val mutable = sites.toMutableList()
        mutable[index] = updater(mutable[index])
        sites = mutable
    }

    fun removeSite(index: Int) {
        if (sites.size <= 1) return
        val removed = sites[index]
        val mutable = sites.toMutableList().also { it.removeAt(index) }
        sites = mutable
        if (defaultSiteId == removed.id) {
            defaultSiteId = mutable.firstOrNull()?.id.orEmpty()
        }
    }

    fun addCustomSite() {
        val name = newSiteName.trim()
        val template = newSiteTemplate.trim()
        if (name.isBlank() || template.isBlank()) {
            errorMessage = context.getString(R.string.web_search_name_template_required)
            return
        }
        if (!template.contains(placeholder)) {
            errorMessage = context.getString(R.string.web_search_template_must_include, placeholder)
            return
        }
        val candidateId = name.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
            .ifBlank { name.lowercase() }
        if (sites.any { it.id == candidateId }) {
            errorMessage = context.getString(R.string.web_search_duplicate_site_name)
            return
        }
        val newSite = WebSearchSite(id = candidateId, displayName = name, urlTemplate = template)
        sites = sites + newSite
        defaultSiteId = candidateId
        newSiteName = ""
        newSiteTemplate = ""
        errorMessage = null
    }

    val isSaveEnabled = sites.isNotEmpty() && allTemplatesValid && defaultSiteId.isNotBlank()

    ContentDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.web_search_sites),
                style = MaterialTheme.typography.titleLarge
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val resolvedDefault = sites.firstOrNull { it.id == defaultSiteId }?.id
                        ?: sites.firstOrNull()?.id
                        ?: ""
                    if (resolvedDefault.isNotBlank()) {
                        onSave(WebSearchSettings(resolvedDefault, sites))
                    }
                },
                enabled = isSaveEnabled
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        content = {
            Text(
                text = stringResource(R.string.web_search_pick_engine),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            sites.forEachIndexed { index, site ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = defaultSiteId == site.id,
                                onClick = { defaultSiteId = site.id }
                            )
                            Text(text = stringResource(R.string.default_label), fontSize = 12.sp)
                        }
                        if (sites.size > 1) {
                            TextButton(onClick = { removeSite(index) }) {
                                Text(text = stringResource(R.string.remove))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = site.displayName,
                        onValueChange = { newValue ->
                            updateSite(index) { it.copy(displayName = newValue) }
                        },
                        label = { Text(stringResource(R.string.web_search_label_display_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = site.urlTemplate,
                        onValueChange = { newValue ->
                            updateSite(index) { it.copy(urlTemplate = newValue) }
                        },
                        label = { Text(stringResource(R.string.web_search_label_url_template)) },
                        supportingText = {
                            Text(text = stringResource(R.string.web_search_example_url))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!site.urlTemplate.contains(placeholder)) {
                        Text(
                            text = stringResource(R.string.web_search_missing_placeholder_short, placeholder),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = stringResource(R.string.web_search_preview, site.buildUrl("compose")),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = stringResource(R.string.web_search_add_custom), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = newSiteName,
                onValueChange = { newSiteName = it },
                label = { Text(stringResource(R.string.web_search_label_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = newSiteTemplate,
                onValueChange = { newSiteTemplate = it },
                label = { Text(stringResource(R.string.web_search_label_url_template)) },
                supportingText = { Text(text = stringResource(R.string.web_search_supporting_template)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = ::addCustomSite) {
                    Text(text = stringResource(R.string.web_search_add_site))
                }
            }

            if (!allTemplatesValid) {
                Text(
                    text = stringResource(R.string.web_search_every_template_needs),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
    )
}
