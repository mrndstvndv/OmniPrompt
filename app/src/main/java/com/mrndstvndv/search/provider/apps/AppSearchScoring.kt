package com.mrndstvndv.search.provider.apps

import com.mrndstvndv.search.alias.AliasEntry
import com.mrndstvndv.search.alias.AppLaunchAliasTarget
import com.mrndstvndv.search.provider.apps.models.AppInfo
import com.mrndstvndv.search.util.FuzzyMatcher

/** Lowercase alias names keyed by the (packageName, userSerialNumber) they launch. */
internal typealias AppAliasNames = Map<Pair<String, Long>, List<String>>

internal data class ScoredApp(
    val app: AppInfo,
    val score: Int,
    val matchedTitleIndices: List<Int>,
    val matchedSubtitleIndices: List<Int>,
    /** Set when an alias outscored the label and package name; the alias text then replaces the subtitle. */
    val matchedAlias: String? = null,
)

internal data class AppQueryCacheState(
    val generation: Long,
    val includePackageName: Boolean,
    val queryLower: String,
    val matches: List<ScoredApp>,
    val aliasNames: AppAliasNames = emptyMap(),
)

internal fun selectAppSearchCandidates(
    queryLower: String,
    includePackageName: Boolean,
    catalog: AppCatalog,
    previousCache: AppQueryCacheState?,
    aliasNames: AppAliasNames = emptyMap(),
): List<AppInfo> {
    val canNarrow =
        previousCache != null &&
            previousCache.generation == catalog.generation &&
            previousCache.includePackageName == includePackageName &&
            previousCache.aliasNames == aliasNames &&
            queryLower.startsWith(previousCache.queryLower) &&
            queryLower.length > previousCache.queryLower.length

    return if (canNarrow) previousCache!!.matches.map { it.app } else catalog.apps
}

internal fun buildAppAliasNames(aliases: List<AliasEntry>): AppAliasNames {
    val names = mutableMapOf<Pair<String, Long>, MutableList<String>>()
    for (entry in aliases) {
        val target = entry.target as? AppLaunchAliasTarget ?: continue
        names.getOrPut(target.packageName to target.userSerialNumber) { mutableListOf() }
            .add(entry.alias.lowercase())
    }
    return names
}

internal fun scoreApp(
    app: AppInfo,
    queryLower: String,
    includePackageName: Boolean,
    aliasNames: List<String> = emptyList(),
): ScoredApp? {
    val labelMatch = FuzzyMatcher.match(queryLower, app.label, app.labelLower)
    val packageMatch =
        if (includePackageName) {
            FuzzyMatcher.match(queryLower, app.packageName, app.packageNameLower)
        } else {
            null
        }
    val aliasMatch =
        aliasNames
            .mapNotNull { alias -> FuzzyMatcher.match(queryLower, alias, alias)?.let { alias to it } }
            .maxByOrNull { (_, match) -> match.score }

    val packageScoreWithPenalty = packageMatch?.let { it.score - PACKAGE_NAME_PENALTY }
    val bestScore =
        listOfNotNull(labelMatch?.score, packageScoreWithPenalty, aliasMatch?.second?.score).maxOrNull()
            ?: return null

    // Label wins ties, then package name, then alias. Alias indices point into the alias text, so
    // an alias win drops highlights: the subtitle becomes the alias badge text instead.
    return when {
        labelMatch != null && labelMatch.score == bestScore ->
            ScoredApp(
                app = app,
                score = bestScore,
                matchedTitleIndices = labelMatch.matchedIndices,
                matchedSubtitleIndices = packageMatch?.matchedIndices ?: emptyList(),
            )
        packageMatch != null && packageScoreWithPenalty == bestScore ->
            ScoredApp(
                app = app,
                score = bestScore,
                matchedTitleIndices = emptyList(),
                matchedSubtitleIndices = packageMatch.matchedIndices,
            )
        else ->
            ScoredApp(
                app = app,
                score = bestScore,
                matchedTitleIndices = emptyList(),
                matchedSubtitleIndices = emptyList(),
                matchedAlias = aliasMatch?.first,
            )
    }
}

internal fun List<ScoredApp>.sortedForAppSearch(): List<ScoredApp> =
    sortedWith(
        compareByDescending<ScoredApp> { it.score }
            .thenBy { it.app.labelLower }
            .thenBy { it.app.packageNameLower }
            .thenBy { it.app.userSerialNumber },
    )

private const val PACKAGE_NAME_PENALTY = 10
