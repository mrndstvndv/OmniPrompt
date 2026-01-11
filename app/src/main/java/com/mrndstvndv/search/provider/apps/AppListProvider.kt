package com.mrndstvndv.search.provider.apps

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import com.mrndstvndv.search.alias.AppLaunchAliasTarget
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.apps.models.AppInfo
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.util.FuzzyMatcher
import com.mrndstvndv.search.util.loadAppIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppListProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: ProviderSettingsRepository,
    private val appListRepository: AppListRepository,
) : Provider {
    override val id: String = "app-list"
    override val displayName: String = "Applications"

    private val packageManager = activity.packageManager

    override fun canHandle(query: Query): Boolean = true

    override suspend fun query(query: Query): List<ProviderResult> {
        val normalized = query.trimmedText
        val settings = settingsRepository.appSearchSettings.value
        val includePackageName = settings.includePackageName
        val aiEnabled = settings.aiAssistantQueriesEnabled

        // Build set of installed package names for quick lookup
        val installedPackages =
            appListRepository
                .getAllApps()
                .value
                .map { it.packageName }
                .toSet()

        // Check for "ask <assistant> <query>" OR "<assistant> <query>" pattern
        val askMatch =
            if (aiEnabled) {
                parseAskQuery(normalized, installedPackages)
                    ?: parseDirectAiQuery(normalized, installedPackages)
            } else {
                null
            }

        val matches: List<ScoredApp> =
            if (normalized.isBlank()) {
                // No query - return all apps with zero score
                appListRepository.getAllApps().value.map { ScoredApp(it, 0, emptyList(), emptyList()) }
            } else if (askMatch != null) {
                // When "ask <assistant>" is detected, only show that assistant's app
                appListRepository
                    .getAllApps()
                    .value
                    .filter { it.packageName == askMatch.assistant.packageName }
                    .map { ScoredApp(it, 100, emptyList(), emptyList()) }
            } else {
                // Apply fuzzy matching and scoring
                appListRepository
                    .getAllApps()
                    .value
                    .mapNotNull { app ->
                        val labelMatch = FuzzyMatcher.match(normalized, app.label)
                        val packageMatch =
                            if (includePackageName) {
                                FuzzyMatcher.match(normalized, app.packageName)
                            } else {
                                null
                            }

                        // Calculate effective score for package match (with penalty)
                        val packageScoreWithPenalty = packageMatch?.let { it.score - PACKAGE_NAME_PENALTY }

                        // Determine which match to use for ranking
                        val labelIsBest =
                            when {
                                labelMatch == null -> false
                                packageScoreWithPenalty == null -> true
                                else -> labelMatch.score >= packageScoreWithPenalty
                            }

                        // Calculate effective score and matched indices
                        when {
                            labelIsBest -> {
                                ScoredApp(
                                    app = app,
                                    score = labelMatch!!.score,
                                    matchedTitleIndices = labelMatch.matchedIndices,
                                    matchedSubtitleIndices = packageMatch?.matchedIndices ?: emptyList(),
                                )
                            }

                            packageMatch != null -> {
                                ScoredApp(
                                    app = app,
                                    score = packageScoreWithPenalty!!,
                                    matchedTitleIndices = emptyList(),
                                    matchedSubtitleIndices = packageMatch.matchedIndices,
                                )
                            }

                            else -> {
                                null
                            }
                        }
                    }.sortedByDescending { it.score }
            }

        val limited = matches.take(MAX_RESULTS)
        val results = mutableListOf<ProviderResult>()

        for (scoredApp in limited) {
            val entry = scoredApp.app

            // Check if this app should be transformed into an AI query result
            val isAiQueryResult =
                askMatch != null &&
                    entry.packageName == askMatch.assistant.packageName

            // Determine title, subtitle, and action based on whether this is an AI query
            val title: String
            val subtitle: String
            val action: suspend () -> Unit

            if (isAiQueryResult && askMatch!!.query.isNotEmpty()) {
                // "ask gemini <query>" - send query to AI
                title = "Ask: ${askMatch.query}"
                subtitle = askMatch.assistant.displayName
                action = {
                    withContext(Dispatchers.Main) {
                        val intent = buildAiQueryIntent(askMatch.assistant, askMatch.query)
                        activity.startActivity(intent)
                        activity.finish()
                    }
                }
            } else {
                // Normal app launch (or "ask gemini" with no query)
                title = entry.label
                subtitle = entry.packageName
                action = {
                    withContext(Dispatchers.Main) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(entry.packageName)
                        if (launchIntent != null) {
                            activity.startActivity(launchIntent)
                            activity.finish()
                        }
                    }
                }
            }

            results +=
                ProviderResult(
                    id = "$id:${entry.packageName}",
                    title = title,
                    subtitle = subtitle,
                    icon = null,
                    defaultVectorIcon = Icons.Outlined.Android,
                    iconLoader = { appListRepository.getIcon(entry.packageName) },
                    providerId = id,
                    extras = mapOf(EXTRA_PACKAGE_NAME to entry.packageName),
                    onSelect = action,
                    aliasTarget = AppLaunchAliasTarget(entry.packageName, entry.label),
                    keepOverlayUntilExit = true,
                    matchedTitleIndices = if (isAiQueryResult) emptyList() else scoredApp.matchedTitleIndices,
                    matchedSubtitleIndices = if (isAiQueryResult) emptyList() else scoredApp.matchedSubtitleIndices,
                )
        }
        return results
    }

    /**
     * Parses "ask <assistant> <query>" pattern.
     * Returns null if pattern doesn't match or assistant app isn't installed.
     */
    private fun parseAskQuery(
        query: String,
        installedPackages: Set<String>,
    ): AskMatch? {
        if (!query.startsWith("ask ", ignoreCase = true)) return null
        val afterAsk = query.drop(4).trimStart() // Remove "ask "
        if (afterAsk.isBlank()) return null

        val triggerToken = afterAsk.takeWhile { !it.isWhitespace() }

        for (assistant in AI_ASSISTANTS) {
            // Skip if app not installed
            if (assistant.packageName !in installedPackages) continue

            val match = FuzzyMatcher.match(triggerToken, assistant.triggerName)
            if (match != null && match.score >= ASK_TRIGGER_MIN_SCORE) {
                val remainingQuery = afterAsk.drop(triggerToken.length).trimStart()
                return AskMatch(assistant, remainingQuery)
            }
        }
        return null
    }

    /**
     * Parses "<assistant> <query>" pattern (without "ask" prefix).
     * Returns null if pattern doesn't match, no query content after trigger, or assistant app isn't installed.
     */
    private fun parseDirectAiQuery(
        query: String,
        installedPackages: Set<String>,
    ): AskMatch? {
        if (query.isBlank()) return null

        val triggerToken = query.takeWhile { !it.isWhitespace() }
        val remainingQuery = query.drop(triggerToken.length).trimStart()

        // Only match if there's actual query content after the trigger
        if (remainingQuery.isBlank()) return null

        for (assistant in AI_ASSISTANTS) {
            // Skip if app not installed
            if (assistant.packageName !in installedPackages) continue

            val match = FuzzyMatcher.match(triggerToken, assistant.triggerName)
            if (match != null && match.score >= ASK_TRIGGER_MIN_SCORE) {
                return AskMatch(assistant, remainingQuery)
            }
        }
        return null
    }

    /**
     * Builds an ACTION_SEND intent to send a query to an AI assistant.
     */
    private fun buildAiQueryIntent(
        assistant: AiAssistant,
        query: String,
    ): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(assistant.packageName)
            putExtra(Intent.EXTRA_TEXT, query)
        }

    private data class ScoredApp(
        val app: AppInfo,
        val score: Int,
        val matchedTitleIndices: List<Int>,
        val matchedSubtitleIndices: List<Int>,
    )

    /** Definition of a supported AI assistant app */
    private data class AiAssistant(
        val id: String,
        val packageName: String,
        val displayName: String,
        val triggerName: String,
    )

    /** Result of parsing an "ask <assistant> <query>" pattern */
    private data class AskMatch(
        val assistant: AiAssistant,
        val query: String,
    )

    private companion object {
        const val MAX_RESULTS = 40
        private const val EXTRA_PACKAGE_NAME = "packageName"

        /** Penalty applied to package name matches so label matches rank higher */
        private const val PACKAGE_NAME_PENALTY = 10

        /** Minimum fuzzy match score for "ask <trigger>" pattern */
        private const val ASK_TRIGGER_MIN_SCORE = 40

        /** Supported AI assistant apps */
        private val AI_ASSISTANTS =
            listOf(
                AiAssistant(
                    id = "gemini",
                    packageName = "com.google.android.apps.bard",
                    displayName = "Gemini",
                    triggerName = "gemini",
                ),
                AiAssistant(
                    id = "chatgpt",
                    packageName = "com.openai.chatgpt",
                    displayName = "ChatGPT",
                    triggerName = "chatgpt",
                ),
            )
    }
}
