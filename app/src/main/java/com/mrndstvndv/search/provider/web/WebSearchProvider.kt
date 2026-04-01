package com.mrndstvndv.search.provider.web

import android.content.Intent
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.core.net.toUri
import com.mrndstvndv.search.R
import com.mrndstvndv.search.alias.QuicklinkAliasTarget
import com.mrndstvndv.search.alias.WebSearchAliasTarget
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.model.SearchTrigger
import com.mrndstvndv.search.provider.model.TriggerInvocation
import com.mrndstvndv.search.provider.model.TriggerParser
import com.mrndstvndv.search.provider.model.TriggerResultPolicy
import com.mrndstvndv.search.provider.model.createTriggerResult
import com.mrndstvndv.search.provider.settings.Quicklink
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.WebSearchSettings
import com.mrndstvndv.search.util.FaviconLoader
import com.mrndstvndv.search.util.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// TODO: the query should be empty if a whitespace between the trigger and query is not yet made. the behavior right now is that the searchitem searches for the trigger if a query is not yet made
class WebSearchProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: SettingsRepository<WebSearchSettings>,
) : Provider {
    override val id: String = "web-search"
    override val displayName: String = activity.getString(R.string.provider_web_search)

    override val triggers: List<SearchTrigger>
        get() {
            val settings = settingsRepository.value.normalized()
            val defaultId = settings.defaultSiteId
            return settings.sites
                .filter { it.enabled && it.id != defaultId }
                .map { site ->
                    SearchTrigger.create(
                        id = site.id,
                        ownerProviderId = id,
                        label = site.displayName,
                        vectorIcon = Icons.Outlined.Search,
                        resultPolicy = TriggerResultPolicy.INCLUDE_OWNER_RESULTS,
                        execute = { invocation -> executeSiteTrigger(site.id, invocation) },
                    )
                }
        }

    private suspend fun executeSiteTrigger(
        siteId: String,
        invocation: TriggerInvocation,
    ): List<ProviderResult> {
        val settings = settingsRepository.value.normalized()
        val site = settings.sites.firstOrNull { it.id == siteId } ?: return emptyList()
        val queryText = invocation.payload
        val searchUrl = site.buildUrl(queryText)
        val action: suspend () -> Unit = {
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW, searchUrl.toUri())
                activity.startActivity(intent)
                activity.finish()
            }
        }
        return listOf(
            createTriggerResult(
                invocation = invocation,
                id = "$id:${site.id}:${queryText.hashCode()}",
                title = activity.getString(R.string.web_search_result_title, queryText),
                subtitle = site.displayName,
                providerId = id,
                triggerId = site.id,
                onSelect = action,
                aliasTarget = WebSearchAliasTarget(site.id, site.displayName),
            )
        )
    }

    override fun canHandle(query: Query): Boolean {
        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return false
        return !Patterns.WEB_URL.matcher(cleaned).matches()
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return emptyList()

        val settings = settingsRepository.value.normalized()

        // 1. Match quicklinks first (prioritized)
        val quicklinkResults = matchQuicklinks(cleaned, settings.quicklinks)

        // 2. Match search engines
        val searchResults = matchSearchEngines(query, cleaned, settings)

        // Return quicklinks before search results
        return quicklinkResults + searchResults
    }

    private fun matchQuicklinks(
        queryText: String,
        quicklinks: List<Quicklink>,
    ): List<ProviderResult> {
        if (quicklinks.isEmpty()) return emptyList()

        data class ScoredQuicklink(
            val quicklink: Quicklink,
            val score: Int,
            val matchedTitleIndices: List<Int>,
            val matchedSubtitleIndices: List<Int>,
        )

        val scored =
            quicklinks
                .mapNotNull { quicklink ->
                    val titleMatch = FuzzyMatcher.match(queryText, quicklink.title)
                    val domainMatch = FuzzyMatcher.match(queryText, quicklink.domain())

                    // Apply penalty to domain matches (like AppListProvider does for package names)
                    val domainScoreWithPenalty = domainMatch?.let { it.score - DOMAIN_MATCH_PENALTY }

                    val titleIsBest =
                        when {
                            titleMatch == null -> false
                            domainScoreWithPenalty == null -> true
                            else -> titleMatch.score >= domainScoreWithPenalty
                        }

                    when {
                        titleIsBest && titleMatch != null -> {
                            ScoredQuicklink(
                                quicklink = quicklink,
                                score = titleMatch.score,
                                matchedTitleIndices = titleMatch.matchedIndices,
                                matchedSubtitleIndices = domainMatch?.matchedIndices ?: emptyList(),
                            )
                        }

                        domainMatch != null -> {
                            ScoredQuicklink(
                                quicklink = quicklink,
                                score = domainScoreWithPenalty!!,
                                matchedTitleIndices = emptyList(),
                                matchedSubtitleIndices = domainMatch.matchedIndices,
                            )
                        }

                        else -> {
                            null
                        }
                    }
                }.sortedByDescending { it.score }

        return scored.map { (quicklink, _, matchedTitleIndices, matchedSubtitleIndices) ->
            val action: suspend () -> Unit = {
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW, quicklink.url.toUri())
                    activity.startActivity(intent)
                    activity.finish()
                }
            }

            ProviderResult(
                id = "$id:quicklink:${quicklink.id}",
                title = quicklink.title,
                subtitle = quicklink.displayUrl(),
                defaultVectorIcon = Icons.Outlined.Link,
                iconLoader =
                    if (quicklink.hasFavicon) {
                        { FaviconLoader.loadFavicon(activity, quicklink.id) }
                    } else {
                        null
                    },
                providerId = id,
                onSelect = action,
                aliasTarget = QuicklinkAliasTarget(quicklink.id, quicklink.title),
                keepOverlayUntilExit = true,
                matchedTitleIndices = matchedTitleIndices,
                matchedSubtitleIndices = matchedSubtitleIndices,
            )
        }
    }

    private fun matchSearchEngines(
        query: Query,
        cleaned: String,
        settings: WebSearchSettings,
    ): List<ProviderResult> {
        val sites = settings.sites.filter { it.enabled }
        if (sites.isEmpty()) return emptyList()

        val defaultSite = settings.effectiveDefaultSite() ?: return emptyList()
        val parsedTrigger = TriggerParser.parse(query.originalText)
        val triggerToken = parsedTrigger.firstToken

        // Use fuzzy matching for trigger tokens and keep matched indices for highlighting
        val triggerMatchMap: Map<String, com.mrndstvndv.search.util.FuzzyMatchResult> =
            if (triggerToken.isBlank()) {
                emptyMap()
            } else {
                sites
                    .filter { it.id != defaultSite.id }
                    .mapNotNull { site ->
                        val match = FuzzyMatcher.match(triggerToken, site.displayName)
                        match?.let { site.id to it }
                    }.sortedByDescending { it.second.score }
                    .toMap()
            }

        val triggerMatches = triggerMatchMap.keys.mapNotNull { id -> sites.firstOrNull { it.id == id } }

        val searchTerms = parsedTrigger.payload.ifBlank { cleaned }.trim()
        val visibleSites =
            if (triggerMatches.isEmpty()) {
                listOf(defaultSite)
            } else {
                triggerMatches + defaultSite
            }

        return visibleSites.map { site ->
            val actualQuery = if (site.id == defaultSite.id) cleaned else searchTerms
            val searchUrl = site.buildUrl(actualQuery)
            val action: suspend () -> Unit = {
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW, searchUrl.toUri())
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
            ProviderResult(
                id = "$id:${site.id}:${actualQuery.hashCode()}",
                title = activity.getString(R.string.web_search_result_title, actualQuery),
                subtitle =
                    if (site.id == defaultSite.id) {
                        activity.getString(R.string.web_search_default_site, site.displayName)
                    } else {
                        site.displayName
                    },
                providerId = id,
                onSelect = action,
                aliasTarget = WebSearchAliasTarget(site.id, site.displayName),
                keepOverlayUntilExit = true,
                // Highlight trigger token matches in the subtitle (site display name)
                matchedSubtitleIndices = triggerMatchMap[site.id]?.matchedIndices ?: emptyList(),
                // Use stable frequency key without query hash so dynamic queries aggregate under site
                frequencyKey = "$id:${site.id}",
                frequencyQuery = triggerToken,
                excludeFromFrequencyRanking = site.id == defaultSite.id,
            )
        }
    }

    private companion object {
        const val DOMAIN_MATCH_PENALTY = 10
    }
}
