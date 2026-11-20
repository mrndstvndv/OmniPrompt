package com.mrndstvndv.search.provider.system

import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class SettingsCatalogSnapshot(
    val settingsPackageName: String,
    val settingsPackageVersionCode: Long,
    val settingsPackageLastUpdateTime: Long,
    /** Discovered activities with their own manifest label — always shown, overriding a hardcoded entry on the same target. */
    val labeledDiscovered: List<DiscoveredActivity>,
)

/**
 * Persists the manifest-discovered, labeled settings screens so a cold start
 * doesn't have to redo the (comparatively heavy) full activity + theme scan
 * before showing results — mirrors [com.mrndstvndv.search.provider.apps.AppCatalogDiskCache].
 *
 * Only the labeled-discovered set is persisted here; hardcoded-action
 * resolvability is cheap enough to recompute fresh on every cold start and
 * isn't cached at all.
 */
internal class SettingsCatalogDiskCache(context: Context) {
    private val cacheFile = File(context.filesDir, "settings_catalog_cache.json")
    private val tempFile = File(context.filesDir, "settings_catalog_cache.json.tmp")

    fun read(): SettingsCatalogSnapshot? =
        try {
            if (!cacheFile.exists()) {
                null
            } else {
                val text = cacheFile.readText()
                if (text.isBlank()) {
                    null
                } else {
                    val root = JSONObject(text)
                    val labeled = root.getJSONArray(KEY_LABELED)
                    val labeledDiscovered =
                        (0 until labeled.length()).mapNotNull { i ->
                            labeled.optJSONObject(i)?.toDiscoveredActivityOrNull()
                        }
                    SettingsCatalogSnapshot(
                        settingsPackageName = root.optString(KEY_PACKAGE),
                        settingsPackageVersionCode = root.optLong(KEY_VERSION_CODE, -1L),
                        settingsPackageLastUpdateTime = root.optLong(KEY_LAST_UPDATE, -1L),
                        labeledDiscovered = labeledDiscovered,
                    )
                }
            }
        } catch (_: Exception) {
            null
        }

    suspend fun write(snapshot: SettingsCatalogSnapshot) {
        withContext(Dispatchers.IO) {
            try {
                val root =
                    JSONObject().apply {
                        put(KEY_PACKAGE, snapshot.settingsPackageName)
                        put(KEY_VERSION_CODE, snapshot.settingsPackageVersionCode)
                        put(KEY_LAST_UPDATE, snapshot.settingsPackageLastUpdateTime)
                        put(
                            KEY_LABELED,
                            JSONArray().apply {
                                snapshot.labeledDiscovered.forEach { activity ->
                                    put(
                                        JSONObject().apply {
                                            put(KEY_COMPONENT, activity.componentName.flattenToString())
                                            put(KEY_LABEL, activity.label)
                                        },
                                    )
                                }
                            },
                        )
                    }
                tempFile.writeText(root.toString())
                if (!tempFile.renameTo(cacheFile)) {
                    cacheFile.delete()
                    tempFile.renameTo(cacheFile)
                }
            } catch (_: Exception) {
                // Best-effort cache; never let a write failure affect search.
            }
        }
    }

    private fun JSONObject.toDiscoveredActivityOrNull(): DiscoveredActivity? {
        val componentString = optString(KEY_COMPONENT).takeIf { it.isNotBlank() } ?: return null
        val component = ComponentName.unflattenFromString(componentString) ?: return null
        val label = optString(KEY_LABEL).takeIf { it.isNotBlank() } ?: return null
        return DiscoveredActivity(component, hasOwnLabel = true, label = label)
    }

    private companion object {
        const val KEY_PACKAGE = "pkg"
        const val KEY_VERSION_CODE = "vc"
        const val KEY_LAST_UPDATE = "lu"
        const val KEY_LABELED = "labeled"
        const val KEY_COMPONENT = "c"
        const val KEY_LABEL = "l"
    }
}
