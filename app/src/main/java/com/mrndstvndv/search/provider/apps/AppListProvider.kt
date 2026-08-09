package com.mrndstvndv.search.provider.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import com.mrndstvndv.search.R
import com.mrndstvndv.search.alias.AppLaunchAliasTarget
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.apps.models.AppInfo
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.util.FuzzyMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

class AppListProvider(
        private val context: Context,
        private val settingsRepository: ProviderSettingsRepository<AppSearchSettings>,
        private val appListRepository: AppListRepository,
        private val scope: CoroutineScope,
) : Provider {
    override val id: String = "app-list"
    override val displayName: String = context.getString(R.string.provider_applications)
    override val refreshSignal: SharedFlow<Unit> =
            appListRepository
                    .catalog
                    .drop(1)
                    .map { Unit }
                    .shareIn(
                            scope = scope,
                            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                            replay = 0,
                    )

    private val packageManager = context.packageManager
    private val queryCacheLock = Any()
    private var queryCache: QueryCacheState? = null

    override fun canHandle(query: Query): Boolean = true

    override suspend fun query(query: Query): List<ProviderResult> {
        val normalized = query.trimmedText
        val catalog = appListRepository.catalog.value
        val allApps = catalog.apps
        val settings = settingsRepository.value
        val includePackageName = settings.includePackageName
        val aiEnabled = settings.aiAssistantQueriesEnabled

        val installedPackages = catalog.packageNames

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
                    clearQueryCache()
                    // No query - return all apps with zero score
                    allApps.map {
                        ScoredApp(
                                it,
                                0,
                                emptyList(),
                                emptyList(),
                        )
                    }
                } else if (askMatch != null) {
                    clearQueryCache()
                    // When "ask <assistant>" is detected, only show that assistant's app
                    allApps.filter { it.packageName == askMatch.assistant.packageName }.map {
                        ScoredApp(it, 100, emptyList(), emptyList())
                    }
                } else {
                    val queryLower = normalized.lowercase()
                    scoreWithIncrementalNarrowing(
                            queryLower = queryLower,
                            includePackageName = includePackageName,
                            catalog = catalog,
                    )
                }

        val limited = matches.take(MAX_RESULTS)
        val results = mutableListOf<ProviderResult>()

        for (scoredApp in limited) {
            val entry = scoredApp.app

            // Check if this app should be transformed into an AI query result
            val isAiQueryResult =
                    askMatch != null && entry.packageName == askMatch.assistant.packageName

            // Determine title, subtitle, and action based on whether this is an AI query
            val title: String
            val subtitle: String
            val action: suspend () -> Unit

            if (isAiQueryResult && askMatch!!.query.isNotEmpty()) {
                // "ask gemini <query>" - send query to AI
                title = context.getString(R.string.app_search_ai_result_title, askMatch.query)
                subtitle = askMatch.assistant.displayName
                action = {
                    withContext(Dispatchers.Main) {
                        val intent = buildAiQueryIntent(askMatch.assistant, askMatch.query)
                        context.startActivity(
                                intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                    }
                }
            } else {
                // Normal app launch (or "ask gemini" with no query)
                title = entry.label
                subtitle = entry.packageName
                action = {
                    withContext(Dispatchers.Main) {
                        val userManager =
                                context.getSystemService(Context.USER_SERVICE) as UserManager
                        val userHandle =
                                userManager.getUserForSerialNumber(entry.userSerialNumber)
                                        ?: android.os.Process.myUserHandle()
                        val launcherApps =
                                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as
                                        LauncherApps
                        val activities = launcherApps.getActivityList(entry.packageName, userHandle)
                        val activityInfo = activities.firstOrNull()
                        if (activityInfo != null) {
                            launcherApps.startMainActivity(
                                    activityInfo.componentName,
                                    userHandle,
                                    null,
                                    null
                            )
                        } else {
                            val launchIntent =
                                    packageManager.getLaunchIntentForPackage(entry.packageName)
                            if (launchIntent != null) {
                                context.startActivity(
                                        launchIntent.apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                )
                            }
                        }
                    }
                }
            }

            results +=
                    ProviderResult(
                            id = "$id:${entry.packageName}:${entry.userSerialNumber}",
                            title = title,
                            subtitle = subtitle,
                            icon = null,
                            defaultVectorIcon = Icons.Outlined.Android,
                            iconLoader = {
                                appListRepository.getIcon(entry.packageName, entry.userSerialNumber)
                            },
                            providerId = id,
                            extras = mapOf(EXTRA_PACKAGE_NAME to entry.packageName),
                            onSelect = action,
                            aliasTarget =
                                    AppLaunchAliasTarget(
                                            entry.packageName,
                                            entry.label,
                                            entry.userSerialNumber
                                    ),
                            keepOverlayUntilExit = true,
                            matchedTitleIndices =
                                    if (isAiQueryResult) emptyList()
                                    else scoredApp.matchedTitleIndices,
                            matchedSubtitleIndices =
                                    if (isAiQueryResult) emptyList()
                                    else scoredApp.matchedSubtitleIndices,
                    )
        }
        return results
    }

    private suspend fun scoreWithIncrementalNarrowing(
            queryLower: String,
            includePackageName: Boolean,
            catalog: AppCatalog,
    ): List<ScoredApp> {
        val previousCache = synchronized(queryCacheLock) { queryCache }
        val canNarrowFromPrevious =
                previousCache != null &&
                        previousCache.generation == catalog.generation &&
                        previousCache.includePackageName == includePackageName &&
                        queryLower.startsWith(previousCache.queryLower) &&
                        queryLower.length > previousCache.queryLower.length

        val candidateApps =
                if (canNarrowFromPrevious) {
                    previousCache!!.matches.map { it.app }
                } else {
                    catalog.apps
                }

        var matches = scoreApps(candidateApps, queryLower, includePackageName)

        // Fallback to full scan if the narrowed set produced no candidates.
        if (canNarrowFromPrevious && matches.isEmpty()) {
            matches = scoreApps(catalog.apps, queryLower, includePackageName)
        }

        synchronized(queryCacheLock) {
            queryCache =
                    QueryCacheState(
                            generation = catalog.generation,
                            includePackageName = includePackageName,
                            queryLower = queryLower,
                            matches = matches,
                    )
        }

        return matches
    }

    private suspend fun scoreApps(
            apps: List<AppInfo>,
            queryLower: String,
            includePackageName: Boolean,
    ): List<ScoredApp> {
        val coroutineContext = currentCoroutineContext()
        return apps
                .mapNotNull { app ->
                    coroutineContext.ensureActive()
                    val labelMatch = FuzzyMatcher.match(queryLower, app.label, app.labelLower)
                    val packageMatch =
                            if (includePackageName) {
                                FuzzyMatcher.match(
                                        queryLower,
                                        app.packageName,
                                        app.packageNameLower
                                )
                            } else {
                                null
                            }

                    val packageScoreWithPenalty =
                            packageMatch?.let { it.score - PACKAGE_NAME_PENALTY }
                    val labelIsBest =
                            when {
                                labelMatch == null -> false
                                packageScoreWithPenalty == null -> true
                                else -> labelMatch.score >= packageScoreWithPenalty
                            }

                    when {
                        labelIsBest -> {
                            ScoredApp(
                                    app = app,
                                    score = labelMatch!!.score,
                                    matchedTitleIndices = labelMatch.matchedIndices,
                                    matchedSubtitleIndices = packageMatch?.matchedIndices
                                                    ?: emptyList(),
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
                        else -> null
                    }
                }
                .sortedByDescending { it.score }
    }

    private fun clearQueryCache() {
        synchronized(queryCacheLock) { queryCache = null }
    }

    /**
     * Parses "ask <assistant> <query>" pattern. Returns null if pattern doesn't match or assistant
     * app isn't installed.
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
     * Parses "<assistant> <query>" pattern (without "ask" prefix). Returns null if pattern doesn't
     * match, no query content after trigger, or assistant app isn't installed.
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

    /** Builds an ACTION_SEND intent to send a query to an AI assistant. */
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

    private data class QueryCacheState(
            val generation: Long,
            val includePackageName: Boolean,
            val queryLower: String,
            val matches: List<ScoredApp>,
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
