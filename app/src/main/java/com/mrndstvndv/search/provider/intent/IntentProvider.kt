package com.mrndstvndv.search.provider.intent

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.model.SearchTrigger
import com.mrndstvndv.search.provider.model.TriggerInvocation
import com.mrndstvndv.search.provider.model.TriggerParser
import com.mrndstvndv.search.provider.model.TriggerResultPolicy
import com.mrndstvndv.search.provider.model.createTriggerResult
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.util.FuzzyMatcher

/**
 * Provider for launching Android intents based on fuzzy-matched titles.
 *
 * Users type keywords that fuzzy match against intent titles to launch intents.
 */
class IntentProvider(
    private val activity: ComponentActivity,
    private val globalSettingsRepository: ProviderSettingsRepository,
    private val settingsRepository: SettingsRepository<IntentSettings>,
) : Provider {
    override val id: String = "intent"
    override val displayName: String = activity.getString(R.string.provider_intent_launcher)

    override val triggers: List<SearchTrigger>
        get() = settingsRepository.value.configs
            .filter { it.hasQuerySlot }
            .map { config ->
                SearchTrigger.create(
                    id = config.id,
                    ownerProviderId = id,
                    label = config.title,
                    vectorIcon = Icons.Outlined.Share,
                    resultPolicy = TriggerResultPolicy.EXCLUSIVE,
                    execute = { invocation -> executeIntentTrigger(config.id, invocation) },
                )
            }

    private suspend fun executeIntentTrigger(
        configId: String,
        invocation: TriggerInvocation,
    ): List<ProviderResult> {
        val settings = settingsRepository.value
        val config = settings.configs.firstOrNull { it.id == configId } ?: return emptyList()

        val payload = invocation.payload
        val systemLabel = activity.getString(R.string.intent_system)
        val targetLabel = config.packageName.ifEmpty { systemLabel }
        val subtitle = if (payload.isNotEmpty()) {
            activity.getString(R.string.intent_result_subtitle_with_payload, payload, targetLabel)
        } else if (config.hasQuerySlot) {
            activity.getString(R.string.intent_payload_required_hint)
        } else {
            targetLabel
        }

        return listOf(
            createTriggerResult(
                invocation = invocation,
                id = "$id:${config.id}",
                title = config.title,
                subtitle = subtitle,
                vectorIcon = Icons.Outlined.Share,
                providerId = id,
                triggerId = config.id,
                onSelect = { executeIntent(config, payload) },
            )
        )
    }

    override fun canHandle(query: Query): Boolean {
        val isEnabled = globalSettingsRepository.enabledProviders.value[id] ?: true
        if (!isEnabled) return false
        
        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return false
        
        val settings = settingsRepository.value
        val configs = settings.configs
        return configs.isNotEmpty()
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return emptyList()

        val settings = settingsRepository.value
        val configs = settings.configs
        if (configs.isEmpty()) return emptyList()

        val parsedTrigger = TriggerParser.parse(cleaned)
        val searchTerm = parsedTrigger.firstToken

        // Fuzzy match first word against titles
        val scored = configs.mapNotNull { config ->
            val titleMatch = FuzzyMatcher.match(searchTerm, config.title)
            if (titleMatch != null) {
                Triple(config, titleMatch.score, titleMatch.matchedIndices)
            } else {
                null
            }
        }.sortedByDescending { it.second }

        if (scored.isEmpty()) return emptyList()

        val rawPayload = parsedTrigger.payload.trim()
        
        val systemLabel = activity.getString(R.string.intent_system)
        val payloadHint = activity.getString(R.string.intent_payload_required_hint)

        return scored.map { (config, _, matchedIndices) ->
            // For the best match use actual payload, for others show hint if payload is needed
            val displayPayload = if (config == scored.first().first) {
                if (rawPayload.isNotEmpty()) {
                    rawPayload
                } else if (config.hasQuerySlot) {
                    payloadHint
                } else {
                    ""
                }
            } else {
                if (config.hasQuerySlot) payloadHint else ""
            }

            val targetLabel = config.packageName.ifEmpty { systemLabel }

            ProviderResult(
                id = "$id:${config.id}",
                title = config.title,
                subtitle = if (displayPayload.isNotEmpty()) {
                    activity.getString(R.string.intent_result_subtitle_with_payload, displayPayload, targetLabel)
                } else {
                    targetLabel
                },
                vectorIcon = Icons.Outlined.Share,
                providerId = id,
                onSelect = { executeIntent(config, rawPayload) },
                keepOverlayUntilExit = true,
                matchedTitleIndices = matchedIndices,
                frequencyQuery = searchTerm,
            )
        }
    }

    private fun executeIntent(config: IntentConfig, rawPayload: String) {
        // Resolve payload using template
        val resolvedPayload = when {
            config.payloadTemplate == null -> rawPayload
            config.payloadTemplate.contains("\$query") -> config.payloadTemplate.replace("\$query", rawPayload)
            else -> config.payloadTemplate // Fixed template
        }

        val intent = Intent().apply {
            action = config.action
            type = config.type

            // Set package if specified
            if (config.packageName.isNotEmpty()) {
                setPackage(config.packageName)
            }

            // Standard intent handling based on action
            when (config.action) {
                Intent.ACTION_SEND -> {
                    putExtra(Intent.EXTRA_TEXT, resolvedPayload)
                }
                Intent.ACTION_VIEW -> {
                    if (resolvedPayload.isNotEmpty()) {
                        data = android.net.Uri.parse(resolvedPayload)
                    }
                }
                Intent.ACTION_SENDTO -> {
                    if (resolvedPayload.isNotEmpty()) {
                        data = android.net.Uri.parse(resolvedPayload)
                    }
                }
            }

            // Custom extras with $query replacement
            config.extras.forEach { extra ->
                val resolvedExtraValue = extra.value.replace("\$query", rawPayload)
                putExtra(extra.key, resolvedExtraValue)
            }

            // Clear launch flags for external apps
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback for ACTION_SEND with URL
            if (config.action == Intent.ACTION_SEND && resolvedPayload.isNotEmpty() && 
                (resolvedPayload.startsWith("http://") || resolvedPayload.startsWith("https://"))) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(resolvedPayload)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    activity.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    // Both failed
                }
            }
        }
    }
}
