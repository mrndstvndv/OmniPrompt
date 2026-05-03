package com.mrndstvndv.search.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.R
import com.mrndstvndv.search.ui.components.settings.SettingsDivider
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsHeader
import com.mrndstvndv.search.ui.components.settings.SettingsNavigationRow

@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val licenses = remember(context) {
        runCatching { OpenSourceLicensesParser.load(context) }.getOrDefault(emptyList())
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val normalizedSearch = searchQuery.trim()
    val filteredLicenses =
        remember(licenses, normalizedSearch) {
            if (normalizedSearch.isEmpty()) return@remember licenses
            licenses.filter { license ->
                license.libraryName.contains(normalizedSearch, ignoreCase = true) ||
                    license.licenseUrl.contains(normalizedSearch, ignoreCase = true)
            }
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            SettingsHeader(
                title = stringResource(R.string.about_open_source_licenses),
                subtitle = stringResource(R.string.about_open_source_licenses_page_subtitle),
                onBack = onBack,
            )
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = stringResource(R.string.search_placeholder)) },
                singleLine = true,
            )
        }

        item {
            if (filteredLicenses.isEmpty()) {
                SettingsGroup {
                    Text(
                        text = stringResource(R.string.about_open_source_licenses_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    )
                }
                return@item
            }

            SettingsGroup {
                filteredLicenses.forEachIndexed { index, license ->
                    SettingsNavigationRow(
                        title = license.libraryName,
                        subtitle = license.displayUrl,
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(license.licenseUrl)))
                        },
                    )
                    if (index < filteredLicenses.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

private data class OpenSourceLicenseItem(
    val libraryName: String,
    val licenseUrl: String,
) {
    val displayUrl: String
        get() = licenseUrl.removePrefix("https://").removePrefix("http://")
}

private object OpenSourceLicensesParser {
    private val metadataPattern = Regex("^(\\d+):(\\d+)\\s+(.*)$")
    private val urlPattern = Regex("https?://\\S+")
    private const val OSS_LICENSE_METADATA_FILE = "third_party_license_metadata"
    private const val OSS_LICENSE_TEXT_FILE = "third_party_licenses"
    private val manualLicenseUrls =
        mapOf(
            "Protobuf Nano" to "https://github.com/protocolbuffers/protobuf/blob/main/LICENSE",
            "STL" to "https://gcc.gnu.org/onlinedocs/libstdc++/manual/license.html",
            "UTF" to "https://android.googlesource.com/platform/prebuilts/go/windows-x86/+/7fb3c4cd96adbd78ba51a9b3fdb812e3091bf9fa/src/lib9/utf/utf.h",
        )

    fun load(context: Context): List<OpenSourceLicenseItem> {
        val metadataText = context.readAssetText(OSS_LICENSE_METADATA_FILE)
        val licensesText = context.readAssetText(OSS_LICENSE_TEXT_FILE)

        return metadataText
            .lineSequence()
            .mapNotNull { line -> parseLine(line, licensesText) }
            .distinctBy { item -> item.libraryName to item.licenseUrl }
            .sortedBy { item -> item.libraryName.lowercase() }
            .toList()
    }

    private fun parseLine(
        line: String,
        licensesText: String,
    ): OpenSourceLicenseItem? {
        val match = metadataPattern.matchEntire(line) ?: return null
        val offset = match.groupValues[1].toIntOrNull() ?: return null
        val length = match.groupValues[2].toIntOrNull() ?: return null
        val libraryName = match.groupValues[3].trim()
        if (libraryName.isEmpty()) return null

        val chunkStart = (offset - 2).coerceAtLeast(0)
        val chunkEnd = (offset + length).coerceAtMost(licensesText.length)
        val licenseChunk = licensesText.substring(chunkStart, chunkEnd)
        val resolvedUrl = urlPattern.find(licenseChunk)?.value?.trimEnd(')', ']', ';', ',', '.')
            ?: manualLicenseUrls[libraryName]
            ?: return null

        return OpenSourceLicenseItem(
            libraryName = libraryName,
            licenseUrl = resolvedUrl,
        )
    }
}

private fun Context.readAssetText(path: String): String =
    assets.open(path).bufferedReader().use { reader ->
        reader.readText()
    }
