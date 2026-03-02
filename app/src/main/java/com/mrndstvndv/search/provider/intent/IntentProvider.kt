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

        // Extract first word for matching (like TermuxProvider)
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

        // Use remaining text after best matched title as payload
        val bestMatch = scored.first().first
        val payload = cleaned.removePrefix(bestMatch.title).trim()
        
        return scored.map { (config, score, matchedIndices) ->
            val resolvedExtra = config.extraValue?.replace("\$query", payload)
            
            // For the best match use actual payload, for others show "(type to see payload)"
            val displayPayload = if (config == bestMatch && payload.isNotEmpty()) {
                payload
            } else {
                "(type to see payload)"
            }
            
            ProviderResult(
                id = "$id:${config.id}",
                title = config.title,
                subtitle = "$displayPayload → ${config.packageName.ifEmpty { "System" }}",
                vectorIcon = Icons.Outlined.Share,
                providerId = id,
                onSelect = { executeIntent(config, payload, resolvedExtra) },
                keepOverlayUntilExit = true,
                matchedTitleIndices = matchedIndices,
            )
        }
    }

    private suspend fun executeIntent(config: IntentConfig, payload: String, resolvedExtra: String?) {
        val intent = Intent().apply {
            action = config.action
            type = config.type

            // Set package if specified
            if (config.packageName.isNotEmpty()) {
                setPackage(config.packageName)
            }

            // Add extras
            when (config.action) {
                Intent.ACTION_SEND -> {
                    putExtra(Intent.EXTRA_TEXT, payload)
                }
                Intent.ACTION_VIEW -> {
                    // For VIEW, payload could be a URL
                    if (payload.isNotEmpty()) {
                        data = android.net.Uri.parse(payload)
                    }
                }
            }

            // Add custom extra if specified
            config.extraKey?.let { key ->
                resolvedExtra?.let { value ->
                    putExtra(key, value)
                }
            }

            // Clear launch flags for external apps
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            activity.startActivity(intent)
        } catch (e: Exception) {
            // If the intent fails (e.g., app not found), try ACTION_VIEW as fallback for URLs
            if (config.action == Intent.ACTION_SEND && payload.isNotEmpty()) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse(payload)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    activity.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    // Both failed - ignore
                }
            }
        }
    }
}
