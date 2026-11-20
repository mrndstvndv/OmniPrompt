package com.mrndstvndv.search.provider.system

import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Debug-only diagnostic record of settings activities the explorer chose not
 * to surface, and why — lets you review false positives from the dialog
 * filter, or activities with no usable label. Never read in release builds;
 * writes are gated by BuildConfig.DEBUG at the call site so nothing is
 * persisted for users who'll never see the screen that reads it.
 */
internal class ExcludedSettingsDiskCache(context: Context) {
    private val cacheFile = File(context.filesDir, "excluded_settings_debug_cache.json")
    private val tempFile = File(context.filesDir, "excluded_settings_debug_cache.json.tmp")

    fun read(): List<ExcludedActivity>? =
        try {
            if (!cacheFile.exists()) {
                null
            } else {
                val text = cacheFile.readText()
                if (text.isBlank()) {
                    null
                } else {
                    val array = JSONArray(text)
                    (0 until array.length()).mapNotNull { i ->
                        array.optJSONObject(i)?.toExcludedActivityOrNull()
                    }
                }
            }
        } catch (_: Exception) {
            null
        }

    suspend fun write(excluded: List<ExcludedActivity>) {
        withContext(Dispatchers.IO) {
            try {
                val array = JSONArray()
                excluded.forEach { activity ->
                    array.put(
                        JSONObject().apply {
                            put(KEY_COMPONENT, activity.componentName.flattenToString())
                            put(KEY_REASON, activity.reason.name)
                        },
                    )
                }
                tempFile.writeText(array.toString())
                if (!tempFile.renameTo(cacheFile)) {
                    cacheFile.delete()
                    tempFile.renameTo(cacheFile)
                }
            } catch (_: Exception) {
                // Diagnostic-only cache; never let a write failure matter.
            }
        }
    }

    private fun JSONObject.toExcludedActivityOrNull(): ExcludedActivity? {
        val componentString = optString(KEY_COMPONENT).takeIf { it.isNotBlank() } ?: return null
        val component = ComponentName.unflattenFromString(componentString) ?: return null
        val reason =
            try {
                SettingsExclusionReason.valueOf(optString(KEY_REASON))
            } catch (_: Exception) {
                return null
            }
        return ExcludedActivity(component, reason)
    }

    private companion object {
        const val KEY_COMPONENT = "c"
        const val KEY_REASON = "r"
    }
}
