package com.mrndstvndv.search.provider.intent

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
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
    override val displayName: String = "Intent Launcher"

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

        // Extract first word for matching
        val spaceIndex = cleaned.indexOf(' ')
        val searchTerm = if (spaceIndex > 0) cleaned.substring(0, spaceIndex) else cleaned

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

        // Use remaining text after the first word as raw payload
        val rawPayload = if (spaceIndex > 0) cleaned.substring(spaceIndex + 1).trim() else ""
        
        return scored.map { (config, score, matchedIndices) ->
            // For the best match use actual payload, for others show hint if payload is needed
            val displayPayload = if (config == scored.first().first) {
                if (rawPayload.isNotEmpty()) {
                    rawPayload
                } else if (config.requiresPayload) {
                    "(type to add payload)"
                } else {
                    ""
                }
            } else {
                if (config.requiresPayload) "(type to add payload)" else ""
            }
            
            ProviderResult(
                id = "$id:${config.id}",
                title = config.title,
                subtitle = if (displayPayload.isNotEmpty()) {
                    "$displayPayload → ${config.packageName.ifEmpty { "System" }}"
                } else {
                    config.packageName.ifEmpty { "System" }
                },
                vectorIcon = Icons.Outlined.Share,
                providerId = id,
                onSelect = { executeIntent(config, rawPayload) },
                keepOverlayUntilExit = true,
                matchedTitleIndices = matchedIndices,
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
