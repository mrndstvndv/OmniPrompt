package com.mrndstvndv.search.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypoFallbackMatcherTest {
    @Test
    fun distanceCountsSubstitutionsInsertionsAndDeletions() {
        assertEquals(0, LevenshteinMatcher.distanceWithinThreshold("gmail", "gmail", 2))
        assertEquals(1, LevenshteinMatcher.distanceWithinThreshold("gnail", "gmail", 2))
        assertEquals(1, LevenshteinMatcher.distanceWithinThreshold("gmal", "gmail", 2))
        assertEquals(1, LevenshteinMatcher.distanceWithinThreshold("gmaill", "gmail", 2))
    }

    @Test
    fun distanceReturnsNullBeyondThreshold() {
        assertNull(LevenshteinMatcher.distanceWithinThreshold("abc", "abcdef", 2))
        assertNull(LevenshteinMatcher.distanceWithinThreshold("abcd", "wxyz", 3))
    }

    @Test
    fun allowedDistanceScalesWithQueryLength() {
        fun matches(query: String, label: String) =
            TypoFallbackMatcher.match(query, listOf(label), labelOf = { it }).isNotEmpty()

        assertTrue(matches("cat", "car"))
        assertTrue(!matches("cat", "cog"))
        assertTrue(matches("chrme", "chrome"))
        assertTrue(matches("chrmo", "chrome"))
        assertTrue(!matches("chrmoo", "cinema"))
        assertTrue(matches("calculatr", "calculator"))
        assertTrue(matches("calcuator", "calculator"))
    }

    @Test
    fun closerDistanceBeatsHigherFrequency() {
        // "gmail" is 1 edit away and never used; "email" is 2 away and used often.
        val frequencies = mapOf("gmail" to 0f, "email" to 100f)

        val result =
            TypoFallbackMatcher.match(
                queryLower = "gmil",
                candidates = listOf("email", "gmail"),
                labelOf = { it },
                frequencyOf = { frequencies.getValue(it) },
            )

        assertEquals(listOf("gmail", "email"), result.map { it.item })
        assertEquals(listOf(1, 2), result.map { it.distance })
    }

    @Test
    fun frequencyBreaksDistanceTies() {
        val frequencies = mapOf("maps" to 1f, "mops" to 9f)

        val result =
            TypoFallbackMatcher.match(
                queryLower = "mups",
                candidates = listOf("maps", "mops"),
                labelOf = { it },
                frequencyOf = { frequencies.getValue(it) },
            )

        assertEquals(listOf("mops", "maps"), result.map { it.item })
    }

    @Test
    fun blankQueryMatchesNothing() {
        assertTrue(TypoFallbackMatcher.match("  ", listOf("a"), labelOf = { it }).isEmpty())
    }
}
