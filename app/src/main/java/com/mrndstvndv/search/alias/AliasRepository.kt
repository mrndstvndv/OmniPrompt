package com.mrndstvndv.search.alias

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException

/**
 * Repository for alias settings.
 *
 * @param context Application context
 * @param scope CoroutineScope for async initialization. Pass null for synchronous initialization
 *              (useful for Workers that are already on IO thread).
 */
class AliasRepository(context: Context, scope: CoroutineScope? = null) {
    companion object {
        private const val PREF_NAME = "alias_settings"
        private const val KEY_ALIASES = "aliases"
    }

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Initialize with empty list; actual values loaded async in init block
    private val _aliases = MutableStateFlow(emptyList<AliasEntry>())
    val aliases: StateFlow<List<AliasEntry>> = _aliases

    init {
        if (scope != null) {
            scope.launch(Dispatchers.IO) {
                _aliases.value = loadAliases()
            }
        } else {
            // Synchronous load (for Workers already on IO thread)
            _aliases.value = loadAliases()
        }
    }

    enum class SaveResult {
        SUCCESS,
        DUPLICATE,
        INVALID_ALIAS,
    }

    fun addAlias(
        alias: String,
        target: AliasTarget,
    ): SaveResult {
        val normalized = alias.trim().lowercase()
        if (normalized.isBlank()) return SaveResult.INVALID_ALIAS
        if (_aliases.value.any { it.alias == normalized }) return SaveResult.DUPLICATE
        val updated = _aliases.value + AliasEntry(normalized, target)
        persist(updated)
        _aliases.value = updated
        return SaveResult.SUCCESS
    }

    fun removeAlias(alias: String) {
        val normalized = alias.trim().lowercase()
        val updated = _aliases.value.filterNot { it.alias == normalized }
        if (updated.size == _aliases.value.size) return
        persist(updated)
        _aliases.value = updated
    }

    fun matchAlias(query: String): AliasMatch? {
        val trimmed = query.trimStart()
        if (trimmed.isEmpty()) return null
        val trimmedLower = trimmed.lowercase()
        for (entry in _aliases.value) {
            val alias = entry.alias
            if (trimmedLower == alias) {
                return AliasMatch(entry, "")
            }
            if (trimmedLower.startsWith(alias)) {
                if (trimmed.length > alias.length) {
                    val boundary = trimmed[alias.length]
                    if (!boundary.isWhitespace() && boundary != ':') continue
                }
                val rest = trimmed.substring(alias.length).trimStart(' ', ':')
                return AliasMatch(entry, rest)
            }
        }
        return null
    }

    private fun persist(entries: List<AliasEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        preferences.edit { putString(KEY_ALIASES, array.toString()) }
    }

    private fun loadAliases(): List<AliasEntry> {
        val json = preferences.getString(KEY_ALIASES, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val entries = mutableListOf<AliasEntry>()
            for (i in 0 until array.length()) {
                AliasEntry.fromJson(array.optJSONObject(i))?.let { entries.add(it) }
            }
            entries
        } catch (ignored: JSONException) {
            emptyList()
        }
    }
}
