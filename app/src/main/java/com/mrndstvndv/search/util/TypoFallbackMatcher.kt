package com.mrndstvndv.search.util

/**
 * Generic typo-tolerant fallback matching, shared by any provider that wants
 * it. Only ever meant to be invoked when a provider's primary (cheap,
 * subsequence-based [FuzzyMatcher]) pass finds nothing at all for a query —
 * see call sites for the specific "if empty, fall back" wiring.
 *
 * Ordering: distance ascending first (closer textual match wins), frequency
 * descending as a tie-breaker among *equally close* candidates. Frequency
 * deliberately does not outrank a better distance match outright — a
 * rarely-used app that's a near-exact typo match should still beat a
 * frequently-used one that's only loosely within the distance threshold.
 */
object TypoFallbackMatcher {
    data class Candidate<T>(
        val item: T,
        val distance: Int,
        val frequency: Float,
    )

    /**
     * @param labelOf extracts the text to match against for each candidate.
     * @param frequencyOf extracts a cross-query usage frequency for each
     *   candidate (e.g. [com.mrndstvndv.search.provider.ProviderRankingRepository.getGlobalResultFrequency]);
     *   defaults to 0 for callers that don't have a frequency signal.
     */
    fun <T> match(
        queryLower: String,
        candidates: List<T>,
        labelOf: (T) -> String,
        frequencyOf: (T) -> Float = { 0f },
    ): List<Candidate<T>> {
        if (queryLower.isBlank()) return emptyList()
        val maxDistance = maxAllowedEditDistance(queryLower.length)
        return candidates
            .mapNotNull { item ->
                val distance =
                    LevenshteinMatcher.distanceWithinThreshold(queryLower, labelOf(item), maxDistance)
                        ?: return@mapNotNull null
                Candidate(item = item, distance = distance, frequency = frequencyOf(item))
            }.sortedWith(
                compareBy<Candidate<T>> { it.distance }
                    .thenByDescending { it.frequency },
            )
    }

    /**
     * Scales with query length so a short query doesn't match almost
     * everything: a distance of 2 on a 3-letter query is nearly meaningless,
     * but perfectly reasonable tolerance on a 9-letter one.
     */
    private fun maxAllowedEditDistance(queryLength: Int): Int =
        when {
            queryLength <= 3 -> 1
            queryLength <= 6 -> 2
            else -> 3
        }
}
