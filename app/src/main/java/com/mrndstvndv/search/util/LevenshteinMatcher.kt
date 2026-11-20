package com.mrndstvndv.search.util

/**
 * Plain Levenshtein edit distance (insert/delete/substitute), used only as a
 * fallback when the primary, cheap [FuzzyMatcher] (strict ordered subsequence
 * matching) finds nothing at all for a query. Subsequence matching can never
 * tolerate an actual typo — a wrong, missing, or extra letter breaks the
 * required in-order character chain entirely — whereas edit distance handles
 * exactly that case. It's deliberately not used as the primary matcher: O(n*m)
 * per candidate is meaningfully more expensive than a single linear scan, and
 * paying that cost on every keystroke for every app would be a real slowdown.
 * Restricting it to a last-resort fallback means it only ever runs when
 * there's nothing else to show.
 */
object LevenshteinMatcher {
    /**
     * Returns the edit distance between [a] and [b] if it's within
     * [maxDistance], or null otherwise. Bails out early — both before
     * starting (pure length difference already exceeds the threshold) and
     * mid-computation (an entire row already exceeds it) — so a hopeless
     * comparison against a long, unrelated label doesn't run to completion.
     */
    fun distanceWithinThreshold(
        a: String,
        b: String,
        maxDistance: Int,
    ): Int? {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return null

        var previousRow = IntArray(b.length + 1) { it }
        var currentRow = IntArray(b.length + 1)

        for (i in 1..a.length) {
            currentRow[0] = i
            var rowMin = currentRow[0]
            for (j in 1..b.length) {
                val substitutionCost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] =
                    minOf(
                        previousRow[j] + 1, // deletion
                        currentRow[j - 1] + 1, // insertion
                        previousRow[j - 1] + substitutionCost, // substitution
                    )
                if (currentRow[j] < rowMin) rowMin = currentRow[j]
            }
            if (rowMin > maxDistance) return null

            // Swap rows instead of allocating a new array each iteration.
            val temp = previousRow
            previousRow = currentRow
            currentRow = temp
        }

        val result = previousRow[b.length]
        return if (result <= maxDistance) result else null
    }
}
