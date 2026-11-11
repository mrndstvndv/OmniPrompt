package com.mrndstvndv.search.provider.apps

import android.content.Context
import com.mrndstvndv.search.provider.apps.models.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists a lightweight snapshot (package name, label, user serial number only —
 * never icons) of the app catalog so a cold-started process can paint a
 * near-instant, "last known good" app list before the authoritative
 * LauncherApps scan finishes.
 *
 * Written on every live catalog change (via [AppListRepository]'s
 * LauncherApps.Callback) and read once, on process start, as a provisional
 * seed — it is never treated as authoritative on its own. The real
 * LauncherApps scan always runs afterward and reconciles/corrects anything
 * that drifted while the process was dead.
 */
class AppCatalogDiskCache(context: Context) {
    private val cacheFile = File(context.filesDir, "app_catalog_cache.json")
    private val tempFile = File(context.filesDir, "app_catalog_cache.json.tmp")

    /** Reads the cached snapshot. Returns null if missing, corrupt, or empty. */
    fun read(): List<AppInfo>? =
        try {
            if (!cacheFile.exists()) {
                null
            } else {
                val text = cacheFile.readText()
                if (text.isBlank()) {
                    null
                } else {
                    val array = JSONArray(text)
                    (0 until array.length())
                        .mapNotNull { i -> array.optJSONObject(i)?.toAppInfoOrNull() }
                        .takeIf { it.isNotEmpty() }
                }
            }
        } catch (_: Exception) {
            // Corrupt/partial cache is never fatal — callers fall back to a
            // normal cold-start scan.
            null
        }

    /** Writes [apps] atomically: write to a temp file, then rename over the real one. */
    suspend fun write(apps: List<AppInfo>) {
        withContext(Dispatchers.IO) {
            try {
                val array = JSONArray()
                for (app in apps) {
                    array.put(
                        JSONObject().apply {
                            put(KEY_PACKAGE, app.packageName)
                            put(KEY_LABEL, app.label)
                            put(KEY_USER_SERIAL, app.userSerialNumber)
                        },
                    )
                }
                tempFile.writeText(array.toString())
                if (!tempFile.renameTo(cacheFile)) {
                    // Some filesystems refuse to rename over an existing file;
                    // this fallback isn't atomic, but read() validates on load
                    // so a torn write here just falls back to "no cache".
                    cacheFile.delete()
                    tempFile.renameTo(cacheFile)
                }
            } catch (_: Exception) {
                // Best-effort cache; never let a write failure affect search.
            }
        }
    }

    private fun JSONObject.toAppInfoOrNull(): AppInfo? {
        val packageName = optString(KEY_PACKAGE).takeIf { it.isNotBlank() } ?: return null
        val label = optString(KEY_LABEL).takeIf { it.isNotBlank() } ?: return null
        val userSerialNumber = optLong(KEY_USER_SERIAL, 0L)
        return AppInfo(packageName = packageName, label = label, userSerialNumber = userSerialNumber)
    }

    private companion object {
        const val KEY_PACKAGE = "p"
        const val KEY_LABEL = "l"
        const val KEY_USER_SERIAL = "u"
    }
}
