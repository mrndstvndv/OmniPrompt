package com.mrndstvndv.search.provider.apps

import com.mrndstvndv.search.provider.apps.models.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppSearchScoringTest {
    @Test
    fun extendedQueryUsesPreviousMatchesAsCandidates() {
        val matched = app("Alpha", "com.example.alpha")
        val missed = app("Beta", "com.example.beta")
        val catalog = catalog(generation = 1, matched, missed)
        val cache =
            AppQueryCacheState(
                generation = 1,
                includePackageName = false,
                queryLower = "a",
                matches = listOf(scoreApp(matched, "a", false)!!),
            )

        val candidates = selectAppSearchCandidates("al", false, catalog, cache)

        assertEquals(listOf(matched), candidates)
    }

    @Test
    fun cacheIsRejectedWhenCatalogOrSettingsChange() {
        val matched = app("Alpha", "com.example.alpha")
        val missed = app("Beta", "com.example.beta")
        val catalog = catalog(generation = 2, matched, missed)
        val staleCache =
            AppQueryCacheState(
                generation = 1,
                includePackageName = false,
                queryLower = "a",
                matches = listOf(scoreApp(matched, "a", false)!!),
            )

        assertSame(catalog.apps, selectAppSearchCandidates("al", false, catalog, staleCache))
        assertSame(
            catalog.apps,
            selectAppSearchCandidates(
                queryLower = "al",
                includePackageName = true,
                catalog = catalog,
                previousCache = staleCache.copy(generation = 2),
            ),
        )
    }

    @Test
    fun directAndIncrementalSearchHaveIdenticalDeterministicOrder() {
        val apps =
            listOf(
                app("Alpha", "com.example.same", userSerialNumber = 20),
                app("Able", "com.example.able"),
                app("Alpha", "com.example.same", userSerialNumber = 10),
            )
        val catalog = catalog(generation = 1, *apps.toTypedArray())
        val prefixMatches = apps.mapNotNull { scoreApp(it, "a", false) }.sortedForAppSearch()
        val cache =
            AppQueryCacheState(
                generation = 1,
                includePackageName = false,
                queryLower = "a",
                matches = prefixMatches.reversed(),
            )

        val direct =
            catalog.apps
                .mapNotNull { scoreApp(it, "al", false) }
                .sortedForAppSearch()
        val incremental =
            selectAppSearchCandidates("al", false, catalog, cache)
                .mapNotNull { scoreApp(it, "al", false) }
                .sortedForAppSearch()

        assertEquals(direct, incremental)
        assertEquals(listOf(10L, 20L, 0L), direct.map { it.app.userSerialNumber })
    }

    @Test
    fun extendingSubsequenceQueryCannotRevivePriorMiss() {
        val apps =
            listOf(
                app("Calculator", "com.example.calculator"),
                app("Calendar", "com.example.calendar"),
                app("Camera", "com.example.camera"),
                app("Clock", "com.example.clock"),
            )

        val prefixMatches = apps.mapNotNull { scoreApp(it, "ca", false) }
        val directExtended = apps.mapNotNull { scoreApp(it, "cal", false) }
        val narrowedExtended = prefixMatches.mapNotNull { scoreApp(it.app, "cal", false) }

        assertEquals(
            directExtended.sortedForAppSearch(),
            narrowedExtended.sortedForAppSearch(),
        )
    }

    private fun app(
        label: String,
        packageName: String,
        userSerialNumber: Long = 0,
    ) = AppInfo(packageName, label, userSerialNumber)

    private fun catalog(
        generation: Long,
        vararg apps: AppInfo,
    ) = AppCatalog(
        generation = generation,
        apps = apps.toList(),
        packageNames = apps.map { it.packageName }.toSet(),
    )
}
