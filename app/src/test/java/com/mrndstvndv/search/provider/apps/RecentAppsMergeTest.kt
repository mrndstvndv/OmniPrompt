package com.mrndstvndv.search.provider.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentAppsMergeTest {
    @Test
    fun samePackageAcrossProfilesKeptSeparate() {
        val merged =
            mergeRecentEntries(
                usage = listOf(RecentEntry("com.example", 0L, null, 100L)),
                tracked = listOf(RecentEntry("com.example", 10L, "Example", 200L)),
            )
        assertEquals(
            listOf(
                RecentEntry("com.example", 10L, "Example", 200L),
                RecentEntry("com.example", 0L, null, 100L),
            ),
            merged,
        )
    }

    @Test
    fun freshestTimestampWinsPerProfileApp() {
        val merged =
            mergeRecentEntries(
                usage = listOf(RecentEntry("com.example", 0L, null, 300L)),
                tracked = listOf(RecentEntry("com.example", 0L, "Example", 100L)),
            )
        assertEquals(listOf(RecentEntry("com.example", 0L, null, 300L)), merged)
    }

    @Test
    fun trackedOnlyWorkEntryAppearsWithoutUsageStats() {
        val merged =
            mergeRecentEntries(
                usage = emptyList(),
                tracked = listOf(RecentEntry("com.work", 10L, "Work", 50L)),
            )
        assertEquals(listOf(RecentEntry("com.work", 10L, "Work", 50L)), merged)
    }
}
