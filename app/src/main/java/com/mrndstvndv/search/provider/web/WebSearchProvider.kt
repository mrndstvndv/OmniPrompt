package com.mrndstvndv.search.provider.web

import android.content.Intent
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.core.net.toUri
import com.mrndstvndv.search.alias.QuicklinkAliasTarget
import com.mrndstvndv.search.alias.WebSearchAliasTarget
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
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
    override val displayName: String = "Web Search"

    override fun canHandle(query: Query): Boolean {
        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return false
        return !Patterns.WEB_URL.matcher(cleaned).matches()
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return emptyList()

        val settings = settingsRepository.value

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
        val sites = settings.sites
        if (sites.isEmpty()) return emptyList()

        val defaultSite = settings.siteForId(settings.defaultSiteId) ?: sites.first()
        val triggerToken =
            query.originalText.trimStart().takeWhile { char ->
                !char.isWhitespace() && char != ':'
            }

        // Use fuzzy matching for trigger tokens
        val triggerMatches =
            if (triggerToken.isBlank()) {
                emptyList()
            } else {
                sites
                    .filter { it.id != defaultSite.id }
                    .mapNotNull { site ->
                        val match = FuzzyMatcher.match(triggerToken, site.displayName)
                        match?.let { site to it }
                    }.sortedByDescending { it.second.score }
                    .map { it.first }
            }

        val searchTerms = dropTriggerToken(cleaned, triggerToken).ifBlank { cleaned }.trim()
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
                title = "Search \"$actualQuery\"",
                subtitle =
                    if (site.id == defaultSite.id) {
                        "${site.displayName} (default)"
                    } else {
                        site.displayName
                    },
                providerId = id,
                onSelect = action,
                aliasTarget = WebSearchAliasTarget(site.id, site.displayName),
                keepOverlayUntilExit = true,
                excludeFromFrequencyRanking = true,
            )
        }
    }

    private fun dropTriggerToken(
        queryText: String,
        triggerToken: String,
    ): String {
        if (triggerToken.isBlank()) return queryText.trimStart()
        val trimmed = queryText.trimStart()
        val lowerTrimmed = trimmed.lowercase()
        val lowerToken = triggerToken.lowercase()
        if (!lowerTrimmed.startsWith(lowerToken)) return trimmed
        if (trimmed.length > triggerToken.length) {
            val boundary = trimmed[triggerToken.length]
            if (!boundary.isWhitespace()) return trimmed
        }
        return trimmed.substring(triggerToken.length).trimStart()
    }

    private companion object {
        const val DOMAIN_MATCH_PENALTY = 10
    }
}
