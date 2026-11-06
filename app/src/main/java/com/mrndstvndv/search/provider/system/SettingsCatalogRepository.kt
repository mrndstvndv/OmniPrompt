package com.mrndstvndv.search.provider.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.mrndstvndv.search.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Published, ready-to-score settings catalog.
 *
 * [labeledDiscovered] entries always take priority for display over a
 * hardcoded entry resolving to the same [ComponentName] — a manifest label is
 * the OEM's own name for the screen, more useful than a generic curated one.
 * [overriddenComponents] is exactly the set of those components, precomputed
 * once per publish (not once per search keystroke) so callers can filter
 * hardcoded entries cheaply.
 *
 * [hardcodedActionToComponent] contains only the hardcoded actions that
 * actually resolve on this device; an action missing from this map should be
 * treated as unavailable and hidden from results.
 */
data class SettingsCatalog(
    val generation: Long,
    val labeledDiscovered: List<DiscoveredActivity>,
    val overriddenComponents: Set<ComponentName>,
    val hardcodedActionToComponent: Map<String, ComponentName>,
)

/**
 * Owns discovery and caching of settings screens: a cheap, always-fresh
 * resolvability check for the hardcoded [Settings.ACTION_*][android.provider.Settings]
 * list, plus a heavier manifest scan (cached to disk, re-run only when the
 * settings app itself changes) for OEM-specific screens with no documented
 * constant.
 */
class SettingsCatalogRepository private constructor(
    private val context: Context,
) {
    private val diskCache = SettingsCatalogDiskCache(context)
    private val excludedDiskCache = ExcludedSettingsDiskCache(context)
    private val mutex = Mutex()

    private var generation = 0L
    private var lastKnownPackageName: String = ""
    private var lastKnownVersionCode: Long = Long.MIN_VALUE
    private var lastKnownUpdateTime: Long = Long.MIN_VALUE

    private val _catalog = MutableStateFlow(SettingsCatalog(0L, emptyList(), emptySet(), emptyMap()))
    val catalog: StateFlow<SettingsCatalog> = _catalog

    /**
     * Seeds from disk (if present) and the cheap hardcoded-action check
     * immediately, then triggers a full manifest rescan only if the settings
     * app has changed since the last scan.
     */
    suspend fun initialize(hardcodedActions: List<String>) {
        val hardcodedActionToComponent = resolveHardcodedActions(hardcodedActions)
        val cached = withContext(Dispatchers.IO) { diskCache.read() }
        mutex.withLock {
            if (cached != null) {
                lastKnownPackageName = cached.settingsPackageName
                lastKnownVersionCode = cached.settingsPackageVersionCode
                lastKnownUpdateTime = cached.settingsPackageLastUpdateTime
                publishLocked(cached.labeledDiscovered, hardcodedActionToComponent)
            } else {
                publishLocked(emptyList(), hardcodedActionToComponent)
            }
        }
        maybeRescan(hardcodedActions)
    }

    /** Bypasses the version-change gate entirely — for the manual "refresh" action. */
    suspend fun forceRescan(hardcodedActions: List<String>) {
        runScanAndPublish(hardcodedActions)
    }

    private suspend fun maybeRescan(hardcodedActions: List<String>) {
        val meta = withContext(Dispatchers.IO) { SettingsActivityExplorer.peekPackageMeta(context) } ?: return
        val (_, versionCode, lastUpdate) = meta
        val unchanged =
            mutex.withLock {
                versionCode == lastKnownVersionCode && lastUpdate == lastKnownUpdateTime
            }
        if (unchanged) return
        runScanAndPublish(hardcodedActions)
    }

    private suspend fun runScanAndPublish(hardcodedActions: List<String>) {
        val hardcodedActionToComponent = resolveHardcodedActions(hardcodedActions)
        val scanResult =
            withContext(Dispatchers.IO) { SettingsActivityExplorer.scan(context) } ?: return
        val hardcodedComponents = hardcodedActionToComponent.values.toSet()

        val labeledDiscovered = scanResult.discovered.filter { it.hasOwnLabel }
        val unlabeledCandidates = scanResult.discovered.filterNot { it.hasOwnLabel }

        val excluded = scanResult.excluded.toMutableList()
        unlabeledCandidates.forEach { activity ->
            if (activity.componentName !in hardcodedComponents) {
                excluded.add(ExcludedActivity(activity.componentName, SettingsExclusionReason.NO_LABEL_NO_HARDCODED_MATCH))
            }
        }

        // Publish and persist together, under the same lock, so two
        // overlapping scans (e.g. the automatic post-initialize() rescan
        // racing a manual "Rescan now" tap) can't finish their disk writes
        // out of order relative to which one actually published last.
        mutex.withLock {
            lastKnownPackageName = scanResult.settingsPackageName
            lastKnownVersionCode = scanResult.settingsPackageVersionCode
            lastKnownUpdateTime = scanResult.settingsPackageLastUpdateTime
            publishLocked(labeledDiscovered, hardcodedActionToComponent)

            diskCache.write(
                SettingsCatalogSnapshot(
                    settingsPackageName = scanResult.settingsPackageName,
                    settingsPackageVersionCode = scanResult.settingsPackageVersionCode,
                    settingsPackageLastUpdateTime = scanResult.settingsPackageLastUpdateTime,
                    labeledDiscovered = labeledDiscovered,
                ),
            )
            if (BuildConfig.DEBUG) {
                excludedDiskCache.write(excluded)
            }
        }
    }

    /**
     * Immediately drops a discovered entry that failed to launch — mirrors
     * [com.mrndstvndv.search.provider.apps.AppListRepository.removeStaleEntry].
     */
    suspend fun removeStaleDiscoveredEntry(componentName: ComponentName) {
        mutex.withLock {
            val current = _catalog.value
            val filtered = current.labeledDiscovered.filterNot { it.componentName == componentName }
            if (filtered.size == current.labeledDiscovered.size) return
            publishLocked(filtered, current.hardcodedActionToComponent)
            diskCache.write(
                SettingsCatalogSnapshot(
                    settingsPackageName = lastKnownPackageName,
                    settingsPackageVersionCode = lastKnownVersionCode,
                    settingsPackageLastUpdateTime = lastKnownUpdateTime,
                    labeledDiscovered = filtered,
                ),
            )
        }
    }

    /**
     * Reads the excluded-activities list from the most recent scan, regardless
     * of whether that scan happened in this process or a previous one. Debug
     * builds only — see [ExcludedSettingsDiskCache].
     */
    fun readLastExcludedActivities(): List<ExcludedActivity> = excludedDiskCache.read() ?: emptyList()

    private fun publishLocked(
        labeledDiscovered: List<DiscoveredActivity>,
        hardcodedActionToComponent: Map<String, ComponentName>,
    ) {
        generation += 1
        _catalog.value =
            SettingsCatalog(
                generation = generation,
                labeledDiscovered = labeledDiscovered,
                overriddenComponents = labeledDiscovered.map { it.componentName }.toSet(),
                hardcodedActionToComponent = hardcodedActionToComponent,
            )
    }

    private fun resolveHardcodedActions(actions: List<String>): Map<String, ComponentName> =
        actions.mapNotNull { action ->
            try {
                Intent(action).resolveActivity(context.packageManager)?.let { resolved ->
                    action to ComponentName(resolved.packageName, resolved.className)
                }
            } catch (_: Exception) {
                null
            }
        }.toMap()

    companion object {
        @Volatile
        private var INSTANCE: SettingsCatalogRepository? = null

        fun getInstance(context: Context): SettingsCatalogRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsCatalogRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
