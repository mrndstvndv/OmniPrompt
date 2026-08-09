package com.mrndstvndv.search.provider.apps

import com.mrndstvndv.search.provider.apps.models.AppInfo
import com.mrndstvndv.search.util.FuzzyMatcher

internal data class ScoredApp(
    val app: AppInfo,
    val score: Int,
    val matchedTitleIndices: List<Int>,
    val matchedSubtitleIndices: List<Int>,
)

internal data class AppQueryCacheState(
    val generation: Long,
    val includePackageName: Boolean,
    val queryLower: String,
    val matches: List<ScoredApp>,
)

internal fun selectAppSearchCandidates(
    queryLower: String,
    includePackageName: Boolean,
    catalog: AppCatalog,
    previousCache: AppQueryCacheState?,
): List<AppInfo> {
    val canNarrow =
        previousCache != null &&
            previousCache.generation == catalog.generation &&
            previousCache.includePackageName == includePackageName &&
            queryLower.startsWith(previousCache.queryLower) &&
            queryLower.length > previousCache.queryLower.length

    return if (canNarrow) previousCache!!.matches.map { it.app } else catalog.apps
}

internal fun scoreApp(
    app: AppInfo,
    queryLower: String,
    includePackageName: Boolean,
): ScoredApp? {
    val labelMatch = FuzzyMatcher.match(queryLower, app.label, app.labelLower)
    val packageMatch =
        if (includePackageName) {
            FuzzyMatcher.match(queryLower, app.packageName, app.packageNameLower)
        } else {
            null
        }

    val packageScoreWithPenalty = packageMatch?.let { it.score - PACKAGE_NAME_PENALTY }
    val labelIsBest =
        when {
            labelMatch == null -> false
            packageScoreWithPenalty == null -> true
            else -> labelMatch.score >= packageScoreWithPenalty
        }

    return when {
        labelIsBest ->
            ScoredApp(
                app = app,
                score = labelMatch!!.score,
                matchedTitleIndices = labelMatch.matchedIndices,
                matchedSubtitleIndices = packageMatch?.matchedIndices ?: emptyList(),
            )
        packageMatch != null ->
            ScoredApp(
                app = app,
                score = packageScoreWithPenalty!!,
                matchedTitleIndices = emptyList(),
                matchedSubtitleIndices = packageMatch.matchedIndices,
            )
        else -> null
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
