package com.mrndstvndv.search.provider.text

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TextFields
import androidx.core.net.toUri
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.model.SearchTrigger
import com.mrndstvndv.search.provider.model.TriggerInvocation
import com.mrndstvndv.search.provider.model.TriggerMatch
import com.mrndstvndv.search.provider.model.TriggerResultPolicy
import com.mrndstvndv.search.provider.model.createTriggerResult
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.TextUtilitiesSettings
import com.mrndstvndv.search.provider.settings.TextUtilityDefaultMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

class TextUtilitiesProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: SettingsRepository<TextUtilitiesSettings>,
) : Provider {
    override val id: String = "text-utilities"
    override val displayName: String = activity.getString(R.string.provider_text_utilities)

    override val triggers: List<SearchTrigger>
        get() {
            val settings = settingsRepository.value
            return utilities
                .filter { it.id !in settings.disabledUtilities }
                .mapNotNull { utility ->
                    val disabledKeywords = settings.disabledKeywords[utility.id] ?: emptySet()
                    val activeKeywords = utility.keywords - disabledKeywords
                    if (activeKeywords.isEmpty()) return@mapNotNull null

                    SearchTrigger.create(
                        id = utility.id,
                        ownerProviderId = id,
                        label = utility.displayName(activity),
                        aliases = activeKeywords,
                        vectorIcon = Icons.Outlined.TextFields,
                        resultPolicy = TriggerResultPolicy.EXCLUSIVE,
                        matchLogic = { trigger, firstToken ->
                            if (firstToken.isBlank()) return@create null
                            val exactAlias = trigger.aliases.firstOrNull { it.equals(firstToken, ignoreCase = true) }
                                ?: return@create null
                            TriggerMatch(
                                trigger = trigger,
                                score = 100 + exactAlias.length,
                                matchedIndices = emptyList(),
                            )
                        },
                        execute = { invocation -> executeUtilityTrigger(utility.id, invocation) },
                    )
                }
        }

    override fun canHandle(query: Query): Boolean {
        val text = query.text.trim()
        if (text.isBlank()) return false
        return triggers.any { trigger ->
            trigger.aliases.any { it.startsWith(text, ignoreCase = true) }
        }
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        val text = query.text.trim()
        if (text.isBlank()) return emptyList()
        return triggers.mapNotNull { trigger ->
            if (trigger.aliases.none { it.startsWith(text, ignoreCase = true) }) {
                return@mapNotNull null
            }

            val utility = utilities.firstOrNull { it.id == trigger.id } ?: return@mapNotNull null
            val settings = settingsRepository.value
            val savedMode = settings.utilityDefaultModes[utility.id]
            val mode = when (savedMode) {
                TextUtilityDefaultMode.ENCODE -> TransformMode.ENCODE
                TextUtilityDefaultMode.DECODE -> TransformMode.DECODE
                null -> utility.defaultMode
            }
            buildSuggestionResult(utility, mode)
        }
    }

    private suspend fun executeUtilityTrigger(
        utilityId: String,
        invocation: TriggerInvocation,
    ): List<ProviderResult> {
        val utility = utilities.firstOrNull { it.id == utilityId } ?: return emptyList()
        val settings = settingsRepository.value

        val (mode, cleanPayload) = parseModeFromPayload(invocation.payload, utility, settings)
        if (cleanPayload.isBlank()) {
            return listOf(buildSuggestionResult(utility, mode, includePrefill = false))
        }

        return when (val outcome = utility.transform(mode, cleanPayload, activity)) {
            is TransformOutcome.Success -> listOf(buildSuccessResult(invocation, utility, mode, cleanPayload, outcome, settings))
            is TransformOutcome.InvalidInput -> listOf(buildInvalidInputResult(utility, mode, cleanPayload, outcome))
        }
    }

    private fun parseModeFromPayload(
        payload: String,
        utility: TextUtility,
        settings: TextUtilitiesSettings,
    ): Pair<TransformMode, String> {
        val savedMode = settings.utilityDefaultModes[utility.id]
        var mode = if (savedMode != null) {
            when (savedMode) {
                TextUtilityDefaultMode.ENCODE -> TransformMode.ENCODE
                TextUtilityDefaultMode.DECODE -> TransformMode.DECODE
            }
        } else {
            utility.defaultMode
        }

        if (!utility.supportsBothModes || payload.isBlank()) return mode to payload

        val nextSpaceIndex = payload.indexOfFirst { it.isWhitespace() }
        val candidate = if (nextSpaceIndex == -1) payload else payload.substring(0, nextSpaceIndex)
        val normalized = candidate.lowercase()

        return when (normalized) {
            in ENCODE_TOKENS -> TransformMode.ENCODE to (if (nextSpaceIndex == -1) "" else payload.substring(nextSpaceIndex).trimStart())
            in DECODE_TOKENS -> TransformMode.DECODE to (if (nextSpaceIndex == -1) "" else payload.substring(nextSpaceIndex).trimStart())
            else -> mode to payload
        }
    }

    private fun buildSuccessResult(
        invocation: TriggerInvocation,
        utility: TextUtility,
        mode: TransformMode,
        payload: String,
        outcome: TransformOutcome.Success,
        settings: TextUtilitiesSettings,
    ): ProviderResult {
        val preview = previewText(outcome.output)
        val autoLaunchUri = resolveAutoLaunchUri(mode, outcome.output, settings)
        val suffixResId =
            if (autoLaunchUri != null) {
                R.string.text_utilities_tap_to_open
            } else {
                R.string.text_utilities_tap_to_copy
            }
        val subtitle = buildActionSubtitle(utility, mode, suffixResId)
        val action: suspend () -> Unit = {
            if (autoLaunchUri != null) {
                openUri(autoLaunchUri)
            } else {
                withContext(Dispatchers.Main) {
                    copyToClipboard(utility.displayName(activity), outcome.output)
                    finishOverlay()
                }
            }
        }
        return createTriggerResult(
            invocation = invocation,
            id = "$id:${utility.id}:${mode.name}:${payload.hashCode()}",
            title = preview,
            subtitle = subtitle,
            providerId = id,
            triggerId = utility.id,
            extras =
                mapOf(
                    EXTRA_UTILITY_ID to utility.id,
                    EXTRA_MODE to mode.name,
                    EXTRA_PAYLOAD to payload,
                ),
            onSelect = action,
            keepOverlayUntilExit = autoLaunchUri != null,
            frequencyKey = utilityFrequencyKey(utility.id),
            frequencyQuery = id,
        )
    }

    private fun buildInvalidInputResult(
        utility: TextUtility,
        mode: TransformMode,
        payload: String,
        outcome: TransformOutcome.InvalidInput,
    ): ProviderResult {
        return ProviderResult(
            id = "$id:${utility.id}:invalid:${payload.hashCode()}",
            title = outcome.message,
            subtitle = utility.invalidInputHint(activity),
            providerId = id,
            extras =
                mapOf(
                    EXTRA_UTILITY_ID to utility.id,
                    EXTRA_MODE to mode.name,
                    EXTRA_PAYLOAD to payload,
                ),
            excludeFromFrequencyRanking = true,
        )
    }

    private fun buildSuggestionResult(
        utility: TextUtility,
        mode: TransformMode,
        includePrefill: Boolean = true,
    ): ProviderResult {
        val instruction = buildActionSubtitle(utility, mode)
        val extras =
            if (includePrefill) {
                mapOf(PREFILL_QUERY_EXTRA to "${utility.primaryKeyword} ")
            } else {
                emptyMap()
            }
        return ProviderResult(
            id = "$id:${utility.id}:suggest:${mode.name}:${utility.primaryKeyword.hashCode()}",
            title = utility.displayName(activity),
            subtitle = instruction,
            providerId = id,
            extras = extras,
            frequencyKey = utilityFrequencyKey(utility.id),
            frequencyQuery = id,
        )
    }

    private fun utilityFrequencyKey(utilityId: String): String = "$id:$utilityId"

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

    private fun previewText(
        value: String,
        maxLength: Int = 60,
    ): String {
        if (value.isEmpty()) return activity.getString(R.string.text_utilities_empty_string)
        if (value.length <= maxLength) return value
        val softLimit = min(maxLength, value.length)
        return value.substring(0, softLimit).trimEnd() + ELLIPSIS
    }

    private fun resolveAutoLaunchUri(
        mode: TransformMode,
        decodedText: String,
        settings: TextUtilitiesSettings,
    ): Uri? {
        if (mode != TransformMode.DECODE) return null
        if (!settings.openDecodedUrls) return null
        return decodedText.toNavigableUriOrNull()
    }

    private fun buildActionSubtitle(
        utility: TextUtility,
        mode: TransformMode,
        @StringRes suffixResId: Int? = null,
    ): String {
        val action = activity.getString(mode.actionRes)
        val utilityName = utility.displayName(activity)
        return if (suffixResId == null) {
            activity.getString(R.string.text_utilities_action_subtitle, action, utilityName)
        } else {
            activity.getString(
                R.string.text_utilities_action_subtitle_with_suffix,
                action,
                utilityName,
                activity.getString(suffixResId),
            )
        }
    }

    private fun String.toNavigableUriOrNull(): Uri? {
        val trimmed = trim()
        if (trimmed.isEmpty()) return null
        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        val candidate = if (hasScheme) trimmed else "https://$trimmed"
        return if (Patterns.WEB_URL.matcher(candidate).matches()) candidate.toUri() else null
    }

    private enum class TransformMode(
        @StringRes val actionRes: Int,
    ) {
        ENCODE(R.string.text_utilities_encode),
        DECODE(R.string.text_utilities_decode),
    }

    private interface TextUtility {
        val id: String

        @get:StringRes
        val displayNameRes: Int

        @get:StringRes
        val descriptionRes: Int

        val primaryKeyword: String
        val keywords: Set<String>

        @get:StringRes
        val invalidInputHintRes: Int

        val supportsBothModes: Boolean
        val defaultMode: TransformMode

        fun displayName(context: Context): String = context.getString(displayNameRes)

        fun description(context: Context): String = context.getString(descriptionRes)

        fun invalidInputHint(context: Context): String = context.getString(invalidInputHintRes)

        fun transform(
            mode: TransformMode,
            text: String,
            context: Context,
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
        override val displayNameRes: Int = R.string.text_utility_base64_name
        override val descriptionRes: Int = R.string.text_utility_base64_description
        override val primaryKeyword: String = "base64"
        override val keywords: Set<String> = setOf("base64", "b64")
        override val invalidInputHintRes: Int = R.string.text_utility_base64_example
        override val supportsBothModes: Boolean = true
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
            context: Context,
        ): TransformOutcome =
            when (mode) {
                TransformMode.ENCODE -> encode(text)
                TransformMode.DECODE -> decode(text, context)
            }

        private fun encode(text: String): TransformOutcome {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            return TransformOutcome.Success(encoded)
        }

        private fun decode(
            text: String,
            context: Context,
        ): TransformOutcome {
            val sanitized = text.trim().replace("\\s+".toRegex(), "")
            if (sanitized.isEmpty()) {
                return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_base64_error_nothing_to_decode))
            }
            return try {
                val decoded = Base64.decode(sanitized, Base64.DEFAULT)
                TransformOutcome.Success(decoded.toString(Charsets.UTF_8))
            } catch (_: IllegalArgumentException) {
                TransformOutcome.InvalidInput(context.getString(R.string.text_utility_base64_error_invalid))
            }
        }
    }

    private class TrimUtility : TextUtility {
        override val id: String = "trim"
        override val displayNameRes: Int = R.string.text_utility_trim_name
        override val descriptionRes: Int = R.string.text_utility_trim_description
        override val primaryKeyword: String = "trim"
        override val keywords: Set<String> = setOf("trim", "strip")
        override val invalidInputHintRes: Int = R.string.text_utility_trim_example
        override val supportsBothModes: Boolean = false
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
            context: Context,
        ): TransformOutcome {
            val trimmed = text.trim()
            return if (trimmed.isEmpty()) {
                TransformOutcome.InvalidInput(context.getString(R.string.text_utility_trim_error_nothing_to_trim))
            } else {
                TransformOutcome.Success(trimmed)
            }
        }
    }

    private class RemoveWhitespacesUtility : TextUtility {
        override val id: String = "remove-whitespaces"
        override val displayNameRes: Int = R.string.text_utility_remove_whitespaces_name
        override val descriptionRes: Int = R.string.text_utility_remove_whitespaces_description
        override val primaryKeyword: String = "rmws"
        override val keywords: Set<String> = setOf("rmws", "removews", "nows")
        override val invalidInputHintRes: Int = R.string.text_utility_remove_whitespaces_example
        override val supportsBothModes: Boolean = false
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
            context: Context,
        ): TransformOutcome {
            val result = text.replace("\\s+".toRegex(), "")
            return if (result.isEmpty()) {
                TransformOutcome.InvalidInput(context.getString(R.string.text_utility_remove_whitespaces_error_empty))
            } else {
                TransformOutcome.Success(result)
            }
        }
    }

    private class RemoveNewlinesUtility : TextUtility {
        override val id: String = "remove-newlines"
        override val displayNameRes: Int = R.string.text_utility_remove_newlines_name
        override val descriptionRes: Int = R.string.text_utility_remove_newlines_description
        override val primaryKeyword: String = "rnl"
        override val keywords: Set<String> = setOf("rnl", "removenl", "removelines", "stripnl", "stripnewlines")
        override val invalidInputHintRes: Int = R.string.text_utility_remove_newlines_example
        override val supportsBothModes: Boolean = false
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
            context: Context,
        ): TransformOutcome {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_remove_newlines_error_no_text))
            }
            val result =
                trimmed
                    .replace("\\r\\n|\\r|\\n".toRegex(), " ")
                    .replace("\\s+".toRegex(), " ")
                    .trim()
            return if (result.isEmpty()) {
                TransformOutcome.InvalidInput(context.getString(R.string.text_utility_remove_newlines_error_empty))
            } else {
                TransformOutcome.Success(result)
            }
        }
    }

    private class MessengerUrlExtractorUtility : TextUtility {
        override val id: String = "messenger-url"
        override val displayNameRes: Int = R.string.text_utility_messenger_url_name
        override val descriptionRes: Int = R.string.text_utility_messenger_url_description
        override val primaryKeyword: String = "fblink"
        override val keywords: Set<String> = setOf("fblink", "fburl", "messengerurl")
        override val invalidInputHintRes: Int = R.string.text_utility_messenger_url_example
        override val supportsBothModes: Boolean = false
        override val defaultMode: TransformMode = TransformMode.DECODE

        override fun transform(
            mode: TransformMode,
            text: String,
            context: Context,
        ): TransformOutcome {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_messenger_url_error_no_url))
            }

            val uri =
                try {
                    Uri.parse(trimmed)
                } catch (_: Exception) {
                    return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_messenger_url_error_invalid_url))
                }

            val host = uri.host?.lowercase()
            if (host != "l.facebook.com") {
                return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_messenger_url_error_not_redirect))
            }

            val originalUrl =
                uri.getQueryParameter("u")
                    ?: return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_messenger_url_error_missing_redirect))

            return TransformOutcome.Success(originalUrl)
        }
    }

    private class UrlEncodeUtility : TextUtility {
        override val id: String = "url-encode"
        override val displayNameRes: Int = R.string.text_utility_url_name
        override val descriptionRes: Int = R.string.text_utility_url_description
        override val primaryKeyword: String = "url"
        override val keywords: Set<String> = setOf("url", "urlencode", "urldecode", "percent")
        override val invalidInputHintRes: Int = R.string.text_utility_url_example
        override val supportsBothModes: Boolean = true
        override val defaultMode: TransformMode = TransformMode.ENCODE

        override fun transform(
            mode: TransformMode,
            text: String,
            context: Context,
        ): TransformOutcome =
            when (mode) {
                TransformMode.ENCODE -> encode(text, context)
                TransformMode.DECODE -> decode(text, context)
            }

        private fun encode(
            text: String,
            context: Context,
        ): TransformOutcome {
            if (text.isEmpty()) {
                return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_url_error_nothing_to_encode))
            }
            val encoded = Uri.encode(text)
            return TransformOutcome.Success(encoded)
        }

        private fun decode(
            text: String,
            context: Context,
        ): TransformOutcome {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                return TransformOutcome.InvalidInput(context.getString(R.string.text_utility_url_error_nothing_to_decode))
            }
            return try {
                val decoded = Uri.decode(trimmed)
                TransformOutcome.Success(decoded)
            } catch (_: Exception) {
                TransformOutcome.InvalidInput(context.getString(R.string.text_utility_url_error_invalid))
            }
        }
    }

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
                RemoveNewlinesUtility(),
                MessengerUrlExtractorUtility(),
                UrlEncodeUtility(),
            )

        fun getUtilitiesInfo(context: Context): List<TextUtilityInfo> =
            utilities.map { utility ->
                TextUtilityInfo(
                    id = utility.id,
                    displayName = utility.displayName(context),
                    description = utility.description(context),
                    keywords = utility.keywords,
                    example = utility.invalidInputHint(context),
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
