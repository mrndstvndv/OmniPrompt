package com.mrndstvndv.search.provider.text

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.settings.TextUtilitiesSettings
import com.mrndstvndv.search.provider.settings.TextUtilityDefaultMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

class TextUtilitiesProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: ProviderSettingsRepository,
) : Provider {
    override val id: String = "text-utilities"
    override val displayName: String = "Text Utilities"

    override fun canHandle(query: Query): Boolean = parseCommand(query.text) != null

    override suspend fun query(query: Query): List<ProviderResult> {
        val parsed = parseCommand(query.text) ?: return emptyList()
        val payload = parsed.payload
        if (payload == null) {
            return listOf(buildSuggestionResult(parsed))
        }
        val textUtilitiesSettings = settingsRepository.textUtilitiesSettings.value
        return when (val outcome = parsed.utility.transform(parsed.mode, parsed.payload)) {
            is TransformOutcome.Success -> listOf(buildSuccessResult(parsed, outcome, textUtilitiesSettings))
            is TransformOutcome.InvalidInput -> listOf(buildInvalidInputResult(parsed, outcome))
        }
    }

    private fun buildSuccessResult(
        command: ParsedCommand,
        outcome: TransformOutcome.Success,
        settings: TextUtilitiesSettings,
    ): ProviderResult {
        val preview = previewText(outcome.output)
        val autoLaunchUri = resolveAutoLaunchUri(command, outcome.output, settings)
        val subtitleSuffix = if (autoLaunchUri != null) " • Tap to open" else " • Tap to copy"
        val subtitle = buildActionSubtitle(command, suffix = subtitleSuffix)
        val payload = command.payload.orEmpty()
        val action: suspend () -> Unit = {
            if (autoLaunchUri != null) {
                openUri(autoLaunchUri)
            } else {
                withContext(Dispatchers.Main) {
                    copyToClipboard(command.utility.displayName, outcome.output)
                    finishOverlay()
                }
            }
        }
        return ProviderResult(
            id = "$id:${command.utility.id}:${command.mode.name}:${payload.hashCode()}",
            title = preview,
            subtitle = subtitle,
            providerId = id,
            extras =
                mapOf(
                    EXTRA_UTILITY_ID to command.utility.id,
                    EXTRA_MODE to command.mode.name,
                    EXTRA_PAYLOAD to payload,
                ),
            onSelect = action,
            keepOverlayUntilExit = autoLaunchUri != null,
            excludeFromFrequencyRanking = true,
        )
    }

    private fun buildInvalidInputResult(
        command: ParsedCommand,
        outcome: TransformOutcome.InvalidInput,
    ): ProviderResult {
        val payload = command.payload.orEmpty()
        return ProviderResult(
            id = "$id:${command.utility.id}:invalid:${payload.hashCode()}",
            title = outcome.message,
            subtitle = command.utility.invalidInputHint,
            providerId = id,
            extras =
                mapOf(
                    EXTRA_UTILITY_ID to command.utility.id,
                    EXTRA_MODE to command.mode.name,
                    EXTRA_PAYLOAD to payload,
                ),
            excludeFromFrequencyRanking = true,
        )
    }

    private fun buildSuggestionResult(command: ParsedCommand): ProviderResult {
        val instruction = buildActionSubtitle(command)
        val prefill = buildPrefillText(command)
        return ProviderResult(
            id = "$id:${command.utility.id}:suggest:${command.mode.name}:${command.canonicalKeyword.hashCode()}",
            title = command.utility.displayName,
            subtitle = instruction,
            providerId = id,
            extras = mapOf(PREFILL_QUERY_EXTRA to prefill),
            excludeFromFrequencyRanking = true,
        )
    }

    private fun copyToClipboard(
        label: String,
        value: String,
    ) {
        val clipboard = activity.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, value)
        clipboard?.setPrimaryClip(clip)
    }

    private suspend fun openUri(uri: Uri) {
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            activity.startActivity(intent)
            finishOverlay()
        }
    }

    private fun finishOverlay() {
        activity.finish()
    }

    private fun parseCommand(rawText: String): ParsedCommand? {
        val trimmed = rawText.trimStart()
        if (trimmed.isBlank()) return null

        val firstSpaceIndex = trimmed.indexOfFirst { it.isWhitespace() }
        val firstToken = if (firstSpaceIndex == -1) trimmed else trimmed.substring(0, firstSpaceIndex)
        val utilityMatch = matchUtility(firstToken) ?: return null
        var remainder = if (firstSpaceIndex == -1) "" else trimmed.substring(firstSpaceIndex).trimStart()

        // Get per-utility default mode from settings, fallback to utility's built-in default
        val settings = settingsRepository.textUtilitiesSettings.value
        val savedMode = settings.utilityDefaultModes[utilityMatch.utility.id]
        var mode =
            if (savedMode != null) {
                when (savedMode) {
                    TextUtilityDefaultMode.ENCODE -> TransformMode.ENCODE
                    TextUtilityDefaultMode.DECODE -> TransformMode.DECODE
                }
            } else {
                utilityMatch.utility.defaultMode
            }
        var consumedModeToken: String? = null
        if (remainder.isNotBlank()) {
            val nextSpaceIndex = remainder.indexOfFirst { it.isWhitespace() }
            val candidate = if (nextSpaceIndex == -1) remainder else remainder.substring(0, nextSpaceIndex)
            val normalized = candidate.lowercase()
            if (normalized in ENCODE_TOKENS) {
                mode = TransformMode.ENCODE
                consumedModeToken = candidate
                remainder = if (nextSpaceIndex == -1) "" else remainder.substring(nextSpaceIndex).trimStart()
            } else if (normalized in DECODE_TOKENS) {
                mode = TransformMode.DECODE
                consumedModeToken = candidate
                remainder = if (nextSpaceIndex == -1) "" else remainder.substring(nextSpaceIndex).trimStart()
            }
        }

        val payload = remainder.takeIf { it.isNotBlank() }
        return ParsedCommand(
            utility = utilityMatch.utility,
            canonicalKeyword = utilityMatch.canonicalKeyword,
            mode = mode,
            payload = payload,
            consumedModeToken = consumedModeToken,
        )
    }

    private fun matchUtility(token: String): UtilityMatch? {
        val normalized = token.lowercase()
        if (normalized.isBlank()) return null
        val settings = settingsRepository.textUtilitiesSettings.value

        for (utility in utilities) {
            // Skip disabled utilities
            if (utility.id in settings.disabledUtilities) continue

            // Get enabled keywords only
            val disabledKeywords = settings.disabledKeywords[utility.id] ?: emptySet()
            val enabledKeywords = utility.keywords - disabledKeywords
            if (enabledKeywords.isEmpty()) continue

            val exact = enabledKeywords.firstOrNull { normalized == it }
            val prefix = enabledKeywords.firstOrNull { it.startsWith(normalized) }
            val startsWith = enabledKeywords.firstOrNull { normalized.startsWith(it) }
            val match = exact ?: prefix ?: startsWith
            if (match != null) {
                return UtilityMatch(utility, utility.primaryKeyword)
            }
        }
        return null
    }

    private fun previewText(
        value: String,
        maxLength: Int = 60,
    ): String {
        if (value.isEmpty()) return "(empty string)"
        if (value.length <= maxLength) return value
        val softLimit = min(maxLength, value.length)
        return value.substring(0, softLimit).trimEnd() + ELLIPSIS
    }

    private fun resolveAutoLaunchUri(
        command: ParsedCommand,
        decodedText: String,
        settings: TextUtilitiesSettings,
    ): Uri? {
        if (command.mode != TransformMode.DECODE) return null
        if (!settings.openDecodedUrls) return null
        return decodedText.toNavigableUriOrNull()
    }

    private fun buildActionSubtitle(
        command: ParsedCommand,
        suffix: String = "",
    ): String {
        val verb = command.mode.action.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return buildString {
            append(verb)
            append(' ')
            append(command.utility.displayName)
            append(" text")
            append(suffix)
        }
    }

    private fun String.toNavigableUriOrNull(): Uri? {
        val trimmed = trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        val candidate = if (hasScheme) trimmed else "https://$trimmed"
        return if (Patterns.WEB_URL.matcher(candidate).matches()) candidate.toUri() else null
    }

    private fun buildPrefillText(command: ParsedCommand): String {
        val builder = StringBuilder(command.canonicalKeyword).append(' ')
        val modeToken = command.consumedModeToken
        if (modeToken != null) {
            builder.append(modeToken).append(' ')
        }
        return builder.toString()
    }

    private data class ParsedCommand(
        val utility: TextUtility,
        val canonicalKeyword: String,
        val mode: TransformMode,
        val payload: String?,
        val consumedModeToken: String?,
    )

    private enum class TransformMode(
        val verb: String,
        val action: String,
    ) {
        ENCODE(verb = "encoded", action = "encode"),
        DECODE(verb = "decoded", action = "decode"),
    }

    private interface TextUtility {
        val id: String
        val displayName: String
        val description: String
        val primaryKeyword: String
        val keywords: Set<String>
        val invalidInputHint: String
        val supportsBothModes: Boolean
        val defaultMode: TransformMode

        fun transform(
            mode: TransformMode,
            text: String,
        ): TransformOutcome
    }

    private sealed interface TransformOutcome {
        data class Success(
            val output: String,
        ) : TransformOutcome

        data class InvalidInput(
            val message: String,
        ) : TransformOutcome
    }

    private class Base64Utility : TextUtility {
        override val id: String = "base64"
        override val displayName: String = "Base64"
        override val description: String = "Encode or decode Base64 text"
        override val primaryKeyword: String = "base64"
        override val keywords: Set<String> = setOf("base64", "b64")
        override val invalidInputHint: String = "base64 SGVsbG8gV29ybGQ= → Hello World"
        override val supportsBothModes: Boolean = true
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
        ): TransformOutcome =
            when (mode) {
                TransformMode.ENCODE -> encode(text)
                TransformMode.DECODE -> decode(text)
            }

        private fun encode(text: String): TransformOutcome {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return TransformOutcome.Success(encoded)
        }

        private fun decode(text: String): TransformOutcome {
            val sanitized = text.trim().replace("\\s+".toRegex(), "")
            if (sanitized.isEmpty()) {
                return TransformOutcome.InvalidInput("Nothing to decode")
            }
            return try {
                val decoded = Base64.decode(sanitized, Base64.DEFAULT)
                TransformOutcome.Success(decoded.toString(Charsets.UTF_8))
            } catch (error: IllegalArgumentException) {
                TransformOutcome.InvalidInput("Input is not valid Base64")
            }
        }
    }

    private class TrimUtility : TextUtility {
        override val id: String = "trim"
        override val displayName: String = "Trim"
        override val description: String = "Remove leading and trailing whitespace"
        override val primaryKeyword: String = "trim"
        override val keywords: Set<String> = setOf("trim", "strip")
        override val invalidInputHint: String = "trim   hello world   → hello world"
        override val supportsBothModes: Boolean = false
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
        ): TransformOutcome {
            val trimmed = text.trim()
            return if (trimmed.isEmpty()) {
                TransformOutcome.InvalidInput("Nothing to trim")
            } else {
                TransformOutcome.Success(trimmed)
            }
        }
    }

    private class RemoveWhitespacesUtility : TextUtility {
        override val id: String = "remove-whitespaces"
        override val displayName: String = "Remove Whitespaces"
        override val description: String = "Remove all whitespace characters"
        override val primaryKeyword: String = "rmws"
        override val keywords: Set<String> = setOf("rmws", "removews", "nows")
        override val invalidInputHint: String = "rmws hello world → helloworld"
        override val supportsBothModes: Boolean = false
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
        ): TransformOutcome {
            val result = text.replace("\\s+".toRegex(), "")
            return if (result.isEmpty()) {
                TransformOutcome.InvalidInput("Nothing left after removing whitespaces")
            } else {
                TransformOutcome.Success(result)
            }
        }
    }

    private class MessengerUrlExtractorUtility : TextUtility {
        override val id: String = "messenger-url"
        override val displayName: String = "Extract Messenger URL"
        override val description: String = "Extract the original URL from Facebook Messenger redirect links"
        override val primaryKeyword: String = "fblink"
        override val keywords: Set<String> = setOf("fblink", "fburl", "messengerurl")
        override val invalidInputHint: String = "fblink <messenger link> → original URL"
        override val supportsBothModes: Boolean = false
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
        ): TransformOutcome {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                return TransformOutcome.InvalidInput("No URL provided")
            }

            val uri =
                try {
                    Uri.parse(trimmed)
                } catch (e: Exception) {
                    return TransformOutcome.InvalidInput("Invalid URL format")
                }

            // Only accept l.facebook.com
            val host = uri.host?.lowercase()
            if (host != "l.facebook.com") {
                return TransformOutcome.InvalidInput("Not a Facebook Messenger redirect URL")
            }

            // Extract the 'u' parameter (already URL-decoded by getQueryParameter)
            val originalUrl =
                uri.getQueryParameter("u")
                    ?: return TransformOutcome.InvalidInput("No redirect URL found in link")

            return TransformOutcome.Success(originalUrl)
        }
    }

    private class UrlEncodeUtility : TextUtility {
        override val id: String = "url-encode"
        override val displayName: String = "URL Encode/Decode"
        override val description: String = "Encode or decode URL percent-encoded text"
        override val primaryKeyword: String = "url"
        override val keywords: Set<String> = setOf("url", "urlencode", "urldecode", "percent")
        override val invalidInputHint: String = "url encode hello world → hello%20world"
        override val supportsBothModes: Boolean = true
        override val defaultMode: TransformMode = TransformMode.ENCODE

        override fun transform(
            mode: TransformMode,
            text: String,
        ): TransformOutcome =
            when (mode) {
                TransformMode.ENCODE -> encode(text)
                TransformMode.DECODE -> decode(text)
            }

        private fun encode(text: String): TransformOutcome {
            if (text.isEmpty()) {
                return TransformOutcome.InvalidInput("Nothing to encode")
            }
            val encoded = Uri.encode(text)
            return TransformOutcome.Success(encoded)
        }

        private fun decode(text: String): TransformOutcome {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                return TransformOutcome.InvalidInput("Nothing to decode")
            }
            return try {
                val decoded = Uri.decode(trimmed)
                TransformOutcome.Success(decoded)
            } catch (e: Exception) {
                TransformOutcome.InvalidInput("Invalid URL-encoded text")
            }
        }
    }

    private data class UtilityMatch(
        val utility: TextUtility,
        val canonicalKeyword: String,
    )

    companion object {
        private const val ELLIPSIS = "…"
        private const val EXTRA_UTILITY_ID = "utilityId"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PAYLOAD = "payload"
        const val PREFILL_QUERY_EXTRA = "textUtilitiesPrefillQuery"
        private val ENCODE_TOKENS = setOf("e", "enc", "encode")
        private val DECODE_TOKENS = setOf("d", "dec", "decode")
        private val utilities: List<TextUtility> =
            listOf(
                Base64Utility(),
                TrimUtility(),
                RemoveWhitespacesUtility(),
                MessengerUrlExtractorUtility(),
                UrlEncodeUtility(),
            )

        fun getUtilitiesInfo(): List<TextUtilityInfo> =
            utilities.map { utility ->
                TextUtilityInfo(
                    id = utility.id,
                    displayName = utility.displayName,
                    description = utility.description,
                    keywords = utility.keywords,
                    example = utility.invalidInputHint,
                    supportsBothModes = utility.supportsBothModes,
                    defaultMode =
                        when (utility.defaultMode) {
                            TransformMode.ENCODE -> TextUtilityDefaultMode.ENCODE
                            TransformMode.DECODE -> TextUtilityDefaultMode.DECODE
                        },
                )
            }
    }
}

data class TextUtilityInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val keywords: Set<String>,
    val example: String,
    val supportsBothModes: Boolean,
    val defaultMode: TextUtilityDefaultMode,
)
