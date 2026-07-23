package com.mrndstvndv.search.provider.intent

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import com.mrndstvndv.search.provider.model.dynamicTriggerFrequencyQuery
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.apps.AppListRepository
import com.mrndstvndv.search.util.FuzzyMatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider for launching Android intents based on fuzzy-matched titles.
 *
 * Users type keywords that fuzzy match against intent titles to launch intents.
 */
class IntentProvider(
    private val context: Context,
    private val globalSettingsRepository: SettingsRepository,
    private val settingsRepository: ProviderSettingsRepository<IntentSettings>,
    private val appListRepository: AppListRepository,
) : Provider {
    override val id: String = "intent"
    override val displayName: String = context.getString(R.string.provider_intent_launcher)

    override val triggers: List<SearchTrigger>
        get() =
            settingsRepository.value.configs
                .filter { it.hasQuerySlot }
                .map { config ->
                    SearchTrigger.create(
                        id = config.id,
                        ownerProviderId = id,
                        label = config.title,
                        vectorIcon = Icons.Outlined.Share,
                        iconLoader = getIconLoader(config),
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
        val systemLabel = context.getString(R.string.intent_system)
        val targetLabel = config.packageName.ifEmpty { systemLabel }
        val subtitle =
            if (payload.isNotEmpty()) {
                context.getString(
                    R.string.intent_result_subtitle_with_payload,
                    payload,
                    targetLabel,
                )
            } else if (config.hasQuerySlot) {
                context.getString(R.string.intent_payload_required_hint)
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
                iconLoader = getIconLoader(config),
                providerId = id,
                triggerId = config.id,
                onSelect = { executeIntent(config, payload) },
            ),
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
        val scored =
            configs.mapNotNull { config ->
                val titleMatch = FuzzyMatcher.match(searchTerm, config.title)
                if (titleMatch != null) {
                    Triple(config, titleMatch.score, titleMatch.matchedIndices)
                } else {
                    null
                }
            }.sortedByDescending { it.second }

        if (scored.isEmpty()) return emptyList()

        val rawPayload = parsedTrigger.payload.trim()

        val systemLabel = context.getString(R.string.intent_system)
        val payloadHint = context.getString(R.string.intent_payload_required_hint)

        return scored.map { (config, _, matchedIndices) ->
            // For the best match use actual payload, for others show hint if payload is needed
            val displayPayload =
                if (config == scored.first().first) {
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
                subtitle =
                    if (displayPayload.isNotEmpty()) {
                        context.getString(
                            R.string.intent_result_subtitle_with_payload,
                            displayPayload,
                            targetLabel,
                        )
                    } else {
                        targetLabel
                    },
                vectorIcon = Icons.Outlined.Share,
                iconLoader = getIconLoader(config),
                providerId = id,
                onSelect = { executeIntent(config, rawPayload) },
                keepOverlayUntilExit = true,
                matchedTitleIndices = matchedIndices,
                frequencyQuery =
                    if (config.hasQuerySlot && parsedTrigger.hasPayloadSeparator) {
                        dynamicTriggerFrequencyQuery(searchTerm)
                    } else {
                        searchTerm
                    },
            )
        }
    }

    private suspend fun executeIntent(
        config: IntentConfig,
        rawPayload: String,
    ) {
        // Resolve payload using template
        val resolvedPayload =
            when {
                config.payloadTemplate == null -> rawPayload
                config.payloadTemplate.contains(
                    "\$query",
                ) -> config.payloadTemplate.replace("\$query", rawPayload)
                else -> config.payloadTemplate // Fixed template
            }

        val intent =
            Intent().apply {
                action = config.action

                // Set package or class name if specified
                if (config.packageName.isNotEmpty()) {
                    if (!config.className.isNullOrEmpty()) {
                        setClassName(config.packageName, config.className)
                    } else {
                        setPackage(config.packageName)
                    }
                }

                // Standard intent handling based on action
                when (config.action) {
                    Intent.ACTION_SEND -> {
                        type = config.type
                        putExtra(Intent.EXTRA_TEXT, resolvedPayload)
                    }
                    Intent.ACTION_VIEW -> {
                        if (resolvedPayload.isNotEmpty()) {
                            val uri = android.net.Uri.parse(resolvedPayload)
                            if (!config.type.isNullOrEmpty()) {
                                setDataAndType(uri, config.type)
                            } else {
                                data = uri
                            }
                        } else {
                            type = config.type
                        }
                    }
                    Intent.ACTION_SENDTO -> {
                        if (resolvedPayload.isNotEmpty()) {
                            val uri = android.net.Uri.parse(resolvedPayload)
                            if (!config.type.isNullOrEmpty()) {
                                setDataAndType(uri, config.type)
                            } else {
                                data = uri
                            }
                        } else {
                            type = config.type
                        }
                    }
                    else -> {
                        type = config.type
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

        withContext(Dispatchers.Main) {
            try {
                val oldPolicy = android.os.StrictMode.getVmPolicy()
                try {
                    android.os.StrictMode.setVmPolicy(android.os.StrictMode.VmPolicy.Builder().build())
                    context.startActivity(intent)
                } finally {
                    android.os.StrictMode.setVmPolicy(oldPolicy)
                }
            } catch (e: Exception) {
                // Fallback for ACTION_SEND with URL
                if (config.action == Intent.ACTION_SEND && resolvedPayload.isNotEmpty() &&
                    (resolvedPayload.startsWith("http://") || resolvedPayload.startsWith("https://"))
                ) {
                    val fallbackIntent =
                        Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse(resolvedPayload)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    try {
                        context.startActivity(fallbackIntent)
                        return@withContext
                    } catch (e2: Exception) {
                        // Both failed
                    }
                }
                android.widget.Toast.makeText(
                    context,
                    e.localizedMessage ?: context.getString(R.string.toast_cant_open),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun getIconLoader(config: IntentConfig): (suspend () -> Bitmap?) {
        return {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val baseBitmap =
                    when {
                        !config.customIconPath.isNullOrEmpty() -> {
                            try {
                                android.graphics.BitmapFactory.decodeFile(config.customIconPath)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        config.packageName.isNotEmpty() -> {
                            appListRepository.getIcon(config.packageName)
                        }
                        else -> null
                    }
                if (baseBitmap != null) {
                    com.mrndstvndv.search.util.createBadgedIcon(
                        context,
                        baseBitmap,
                        R.drawable.ic_share,
                    )
                } else {
                    null
                }
            }
        }
    }
}
