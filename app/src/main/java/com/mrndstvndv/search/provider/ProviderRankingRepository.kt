package com.mrndstvndv.search.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Manages provider display order/ranking.
 * Supports both manual ranking and algorithmic frequency-based ranking.
 * Ensures results are always sorted by user-configured provider order or frequency, not by load speed.
 *
 * @param context Application context
 * @param scope CoroutineScope for async initialization. Pass null for synchronous initialization
 *              (useful for Workers that are already on IO thread).
 */
class ProviderRankingRepository private constructor(
    context: Context,
    scope: CoroutineScope? = null,
) {
    companion object {
        private const val PREF_NAME = "provider_rankings"
        private const val KEY_PROVIDER_ORDER = "provider_order"
        private const val KEY_RESULT_FREQUENCY = "result_frequency"
        private const val KEY_USE_FREQUENCY_RANKING = "use_frequency_ranking"

        // Default provider order (used if not yet customized by user)
        private val DEFAULT_PROVIDER_ORDER =
            listOf(
                "app-list",
                "calculator",
                "text-utilities",
                "file-search",
                "termux",
                "web-search",
                "system-settings",
                "debug-long-operation",
            )

        @Volatile
        private var INSTANCE: ProviderRankingRepository? = null

        fun getInstance(
            context: Context,
            scope: CoroutineScope? = null,
        ): ProviderRankingRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProviderRankingRepository(context.applicationContext, scope).also { INSTANCE = it }
            }
    }

    private val preferences: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Initialize with defaults; actual values loaded async in init block
    private val _providerOrder = MutableStateFlow(DEFAULT_PROVIDER_ORDER)
    val providerOrder: StateFlow<List<String>> = _providerOrder

    private val _useFrequencyRanking = MutableStateFlow(true)
    val useFrequencyRanking: StateFlow<Boolean> = _useFrequencyRanking

    // Map<Query, Map<ResultId, Count>>
    private val _resultFrequency = MutableStateFlow(emptyMap<String, Map<String, Int>>())
    val resultFrequency: StateFlow<Map<String, Map<String, Int>>> = _resultFrequency

    init {
        if (scope != null) {
            scope.launch(Dispatchers.IO) {
                _providerOrder.value = loadProviderOrder()
                _useFrequencyRanking.value = loadUseFrequencyRanking()
                _resultFrequency.value = loadResultFrequency()
            }
        } else {
            // Synchronous load (for Workers already on IO thread)
            _providerOrder.value = loadProviderOrder()
            _useFrequencyRanking.value = loadUseFrequencyRanking()
            _resultFrequency.value = loadResultFrequency()
        }
    }

    /**
     * Get the rank/position of a provider (for provider-level sorting).
     * Provider ranking is always based on manual order, not frequency.
     */
    fun getProviderRank(providerId: String): Int = _providerOrder.value.indexOf(providerId).takeIf { it >= 0 } ?: Int.MAX_VALUE

    /**
     * Get the frequency rank of a result (for result-level sorting when frequency mode is enabled).
     * Higher frequency = higher priority (lower rank value).
     */
    fun getResultFrequencyRank(
        resultId: String,
        query: String,
    ): Int {
        val normalizedQuery = normalizeQuery(query)
        val queryCounts = _resultFrequency.value[normalizedQuery] ?: emptyMap()
        val freq = queryCounts[resultId] ?: 0
        
        // Invert frequency so higher frequency results get lower rank values (sort ascending)
        val maxFreq = queryCounts.values.maxOrNull() ?: 1
        return (maxFreq - freq)
    }

    /**
     * Get frequency count for a specific result.
     */
    fun getResultFrequency(
        resultId: String,
        query: String,
    ): Int {
        val normalizedQuery = normalizeQuery(query)
        return _resultFrequency.value[normalizedQuery]?.get(resultId) ?: 0
    }

    /**
     * Update the provider order
     */
    fun setProviderOrder(order: List<String>) {
        _providerOrder.value = order
        saveProviderOrder(order)
    }

    /**
     * Move provider up in ranking (higher priority).
     * Skips over disabled providers to swap with the nearest enabled one.
     */
    fun moveUp(
        providerId: String,
        isEnabled: (String) -> Boolean,
    ) {
        val current = _providerOrder.value.toMutableList()
        val index = current.indexOf(providerId)
        if (index <= 0) return

        // Find the nearest enabled provider above this one
        var targetIndex = -1
        for (i in index - 1 downTo 0) {
            if (isEnabled(current[i])) {
                targetIndex = i
                break
            }
        }

        if (targetIndex != -1) {
            val temp = current[index]
            current[index] = current[targetIndex]
            current[targetIndex] = temp
            setProviderOrder(current)
        }
    }

    /**
     * Move provider down in ranking (lower priority).
     * Skips over disabled providers to swap with the nearest enabled one.
     */
    fun moveDown(
        providerId: String,
        isEnabled: (String) -> Boolean,
    ) {
        val current = _providerOrder.value.toMutableList()
        val index = current.indexOf(providerId)
        if (index == -1 || index >= current.size - 1) return

        // Find the nearest enabled provider below this one
        var targetIndex = -1
        for (i in index + 1 until current.size) {
            if (isEnabled(current[i])) {
                targetIndex = i
                break
            }
        }

        if (targetIndex != -1) {
            val temp = current[index]
            current[index] = current[targetIndex]
            current[targetIndex] = temp
            setProviderOrder(current)
        }
    }

    /**
     * Increment the usage frequency of a result.
     * Call this when a user selects a specific result.
     */
    fun incrementResultUsage(
        resultId: String,
        query: String,
    ) {
        val normalizedQuery = normalizeQuery(query)
        val current = _resultFrequency.value.toMutableMap()
        val queryCounts = current[normalizedQuery]?.toMutableMap() ?: mutableMapOf()
        
        queryCounts[resultId] = (queryCounts[resultId] ?: 0) + 1
        current[normalizedQuery] = queryCounts
        
        _resultFrequency.value = current
        saveResultFrequency(current)
    }

    /**
     * Toggle between frequency-based and manual ranking modes.
     */
    fun setUseFrequencyRanking(enabled: Boolean) {
        _useFrequencyRanking.value = enabled
        saveUseFrequencyRanking(enabled)
    }

    /**
     * Reset result frequency counts.
     */
    fun resetResultFrequency() {
        _resultFrequency.value = emptyMap()
        saveResultFrequency(emptyMap())
    }

    private fun normalizeQuery(query: String): String = query.trim().lowercase()

    private fun loadProviderOrder(): List<String> =
        try {
            val json = preferences.getString(KEY_PROVIDER_ORDER, null)
            if (json != null) {
                val array = JSONArray(json)
                (0 until array.length()).map { array.getString(it) }
            } else {
                DEFAULT_PROVIDER_ORDER
            }
        } catch (e: JSONException) {
            DEFAULT_PROVIDER_ORDER
        }

    private fun saveProviderOrder(order: List<String>) {
        preferences.edit {
            val array = JSONArray(order)
            putString(KEY_PROVIDER_ORDER, array.toString())
        }
    }

    private fun loadUseFrequencyRanking(): Boolean = preferences.getBoolean(KEY_USE_FREQUENCY_RANKING, true)

    private fun saveUseFrequencyRanking(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_USE_FREQUENCY_RANKING, enabled)
        }
    }

    private fun loadResultFrequency(): Map<String, Map<String, Int>> =
        try {
            val json = preferences.getString(KEY_RESULT_FREQUENCY, null)
            if (json != null) {
                val obj = JSONObject(json)
                buildMap {
                    obj.keys().forEach { key ->
                        val value = obj.optJSONObject(key)
                        if (value != null) {
                            // New format: key is query, value is map of resultId -> count
                            val innerMap = buildMap {
                                value.keys().forEach { innerKey ->
                                    put(innerKey, value.getInt(innerKey))
                                }
                            }
                            put(key, innerMap)
                        }
                    }
                }
            } else {
                emptyMap()
            }
        } catch (e: JSONException) {
            emptyMap()
        }

    private fun saveResultFrequency(frequency: Map<String, Map<String, Int>>) {
        preferences.edit {
            val obj = JSONObject()
            frequency.forEach { (query, counts) ->
                val innerObj = JSONObject(counts)
                obj.put(query, innerObj)
            }
            putString(KEY_RESULT_FREQUENCY, obj.toString())
        }
    }
}
