package com.mrndstvndv.search.util

/**
 * Result of a fuzzy match operation.
 * @param score The match score (higher is better)
 * @param matchedIndices The indices of matched characters in the target string (for highlighting)
 */
data class FuzzyMatchResult(
    val score: Int,
    val matchedIndices: List<Int>,
)

/**
 * Raycast-style fuzzy matcher for search functionality.
 *
 * Matches characters in order (but not necessarily contiguous) and scores based on:
 * - Position bonuses (start of string, word boundaries)
 * - Consecutive character bonuses
 * - Gap penalties
 * - Length bonuses (shorter matches are more relevant)
 */
object FuzzyMatcher {
    /**
     * Performs fuzzy matching of query against target.
     *
     * @param query The search query
     * @param target The string to match against
     * @return FuzzyMatchResult if query matches target, null otherwise
     *
     * Scoring:
     * - First character match: +15
     * - Word boundary match (camelCase, underscore, space): +10
     * - Consecutive character match: +5
     * - Non-consecutive match: +1
     * - Gap penalty: -1 per skipped character (max -3)
     * - Length bonus: up to +10 for shorter targets
     */
    fun match(
        queryLower: String,
        target: String,
        targetLower: String,
    ): FuzzyMatchResult? {
        if (queryLower.isEmpty()) return FuzzyMatchResult(0, emptyList())
        if (target.isEmpty()) return null

        var queryIdx = 0
        var score = 0
        val matchedIndices = mutableListOf<Int>()
        var prevMatchIdx = -1

        for (targetIdx in targetLower.indices) {
            if (queryIdx >= queryLower.length) break

            if (targetLower[targetIdx] == queryLower[queryIdx]) {
                matchedIndices.add(targetIdx)

                val isFirstChar = targetIdx == 0
                val isWordBoundary = targetIdx > 0 && isWordBoundary(target, targetIdx)
                val isConsecutive = prevMatchIdx != -1 && targetIdx == prevMatchIdx + 1

                score +=
                    when {
                        isFirstChar -> 15
                        isWordBoundary -> 10
                        isConsecutive -> 5
                        else -> 1
                    }

                // Gap penalty for non-consecutive matches
                if (prevMatchIdx != -1 && !isConsecutive) {
                    score -= (targetIdx - prevMatchIdx - 1).coerceAtMost(3)
                }

                prevMatchIdx = targetIdx
                queryIdx++
            }
        }

        return if (queryIdx == queryLower.length) {
            // Bonus for shorter targets (more relevant matches)
            val lengthBonus = (50 - target.length).coerceIn(0, 10)
            FuzzyMatchResult(score + lengthBonus, matchedIndices)
        } else {
            null
        }
    }

    /**
     * Legacy compatibility overload.
     */
    fun match(
        query: String,
        target: String,
    ): FuzzyMatchResult? {
        return match(query.lowercase(), target, target.lowercase())
    }

    /**
     * Checks if the character at [index] is at a word boundary.
     *
     * Word boundaries include:
     * - After non-alphanumeric characters (spaces, underscores, dashes, etc.)
     * - camelCase transitions (lowercase to uppercase)
     * - Digit-to-letter and letter-to-digit transitions
     */
    private fun isWordBoundary(
        text: String,
        index: Int,
    ): Boolean {
        if (index == 0) return true
        val prev = text[index - 1]
        val curr = text[index]
        return !prev.isLetterOrDigit() || // After separator
            (prev.isLowerCase() && curr.isUpperCase()) || // camelCase
            (prev.isDigit() && curr.isLetter()) || // digit to letter
            (prev.isLetter() && curr.isDigit()) // letter to digit
    }
}
