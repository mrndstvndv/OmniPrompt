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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

class TextUtilitiesProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: ProviderSettingsRepository
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
        settings: TextUtilitiesSettings
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
            extras = mapOf(
                EXTRA_UTILITY_ID to command.utility.id,
                EXTRA_MODE to command.mode.name,
                EXTRA_PAYLOAD to payload
            ),
            onSelect = action,
            keepOverlayUntilExit = autoLaunchUri != null
        )
    }

    private fun buildInvalidInputResult(
        command: ParsedCommand,
        outcome: TransformOutcome.InvalidInput
    ): ProviderResult {
        val payload = command.payload.orEmpty()
        return ProviderResult(
            id = "$id:${command.utility.id}:invalid:${payload.hashCode()}",
            title = outcome.message,
            subtitle = command.utility.invalidInputHint,
            providerId = id,
            extras = mapOf(
                EXTRA_UTILITY_ID to command.utility.id,
                EXTRA_MODE to command.mode.name,
                EXTRA_PAYLOAD to payload
            ),
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
            extras = mapOf(PREFILL_QUERY_EXTRA to prefill)
        )
    }

    private fun copyToClipboard(label: String, value: String) {
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

        var mode = TransformMode.DECODE
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
            consumedModeToken = consumedModeToken
        )
    }

    private fun matchUtility(token: String): UtilityMatch? {
        val normalized = token.lowercase()
        if (normalized.isBlank()) return null
        for (utility in utilities) {
            val exact = utility.keywords.firstOrNull { normalized == it }
            val prefix = utility.keywords.firstOrNull { it.startsWith(normalized) }
            val startsWith = utility.keywords.firstOrNull { normalized.startsWith(it) }
            val match = exact ?: prefix ?: startsWith
            if (match != null) {
                return UtilityMatch(utility, utility.primaryKeyword)
            }
        }
        return null
    }

    private fun previewText(value: String, maxLength: Int = 60): String {
        if (value.isEmpty()) return "(empty string)"
        if (value.length <= maxLength) return value
        val softLimit = min(maxLength, value.length)
        return value.substring(0, softLimit).trimEnd() + ELLIPSIS
    }

    private fun resolveAutoLaunchUri(
        command: ParsedCommand,
        decodedText: String,
        settings: TextUtilitiesSettings
    ): Uri? {
        if (command.mode != TransformMode.DECODE) return null
        if (!settings.openDecodedUrls) return null
        return decodedText.toNavigableUriOrNull()
    }

    private fun buildActionSubtitle(command: ParsedCommand, suffix: String = ""): String {
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
        val consumedModeToken: String?
    )

    private enum class TransformMode(val verb: String, val action: String) {
        ENCODE(verb = "encoded", action = "encode"),
        DECODE(verb = "decoded", action = "decode")
    }

    private interface TextUtility {
        val id: String
        val displayName: String
        val primaryKeyword: String
        val keywords: Set<String>
        val invalidInputHint: String

        fun transform(mode: TransformMode, text: String): TransformOutcome
    }

    private sealed interface TransformOutcome {
        data class Success(val output: String) : TransformOutcome
        data class InvalidInput(val message: String) : TransformOutcome
    }

    private class Base64Utility : TextUtility {
        override val id: String = "base64"
        override val displayName: String = "Base64"
        override val primaryKeyword: String = "base64"
        override val keywords: Set<String> = setOf("base64", "b64")
        override val invalidInputHint: String = "Example: base64 SGVhZCBtZSB0byBxdWFydGVycy4="

        override fun transform(mode: TransformMode, text: String): TransformOutcome {
            return when (mode) {
                TransformMode.ENCODE -> encode(text)
                TransformMode.DECODE -> decode(text)
            }
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
        override val primaryKeyword: String = "trim"
        override val keywords: Set<String> = setOf("trim", "strip")
        override val invalidInputHint: String = "Example: trim   hello world   "

        override fun transform(mode: TransformMode, text: String): TransformOutcome {
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
        override val primaryKeyword: String = "rmws"
        override val keywords: Set<String> = setOf("rmws", "removews", "nows")
        override val invalidInputHint: String = "Example: rmws hello world"

        override fun transform(mode: TransformMode, text: String): TransformOutcome {
            val result = text.replace("\\s+".toRegex(), "")
            return if (result.isEmpty()) {
                TransformOutcome.InvalidInput("Nothing left after removing whitespaces")
            } else {
                TransformOutcome.Success(result)
            }
        }
    }

    private data class UtilityMatch(
        val utility: TextUtility,
        val canonicalKeyword: String
    )

    companion object {
        private const val ELLIPSIS = "…"
        private const val EXTRA_UTILITY_ID = "utilityId"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PAYLOAD = "payload"
        const val PREFILL_QUERY_EXTRA = "textUtilitiesPrefillQuery"
        private val ENCODE_TOKENS = setOf("e", "enc", "encode")
        private val DECODE_TOKENS = setOf("d", "dec", "decode")
        private val utilities: List<TextUtility> = listOf(
            Base64Utility(),
            TrimUtility(),
            RemoveWhitespacesUtility()
        )
    }
}
