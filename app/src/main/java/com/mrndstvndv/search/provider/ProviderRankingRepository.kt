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
        private const val KEY_QUERY_BASED_RANKING = "query_based_ranking"
        private const val KEY_DECAY_AMOUNT = "decay_amount"

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

    private val _queryBasedRankingEnabled = MutableStateFlow(true)
    val queryBasedRankingEnabled: StateFlow<Boolean> = _queryBasedRankingEnabled

    // Map<Query, Map<ResultId, Score>>
    private val _resultFrequency = MutableStateFlow(emptyMap<String, Map<String, Float>>())
    val resultFrequency: StateFlow<Map<String, Map<String, Float>>> = _resultFrequency

    // Aggregated global frequency map (cached for performance)
    // Map<ResultId, Score>
    private var _globalFrequencyMap: Map<String, Float> = emptyMap()

    private val _decayAmount = MutableStateFlow(1.0f)
    val decayAmount: StateFlow<Float> = _decayAmount

    init {
        if (scope != null) {
            scope.launch(Dispatchers.IO) {
                _providerOrder.value = loadProviderOrder()
                _useFrequencyRanking.value = loadUseFrequencyRanking()
                _queryBasedRankingEnabled.value = loadQueryBasedRankingEnabled()
                _resultFrequency.value = loadResultFrequency()
                _decayAmount.value = loadDecayAmount()
                rebuildGlobalFrequency()
            }
        } else {
            // Synchronous load (for Workers already on IO thread)
            _providerOrder.value = loadProviderOrder()
            _useFrequencyRanking.value = loadUseFrequencyRanking()
            _queryBasedRankingEnabled.value = loadQueryBasedRankingEnabled()
            _resultFrequency.value = loadResultFrequency()
            _decayAmount.value = loadDecayAmount()
            rebuildGlobalFrequency()
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
    ): Float {
        val count =
            if (_queryBasedRankingEnabled.value) {
                val normalizedQuery = normalizeQuery(query)
                val queryCounts = _resultFrequency.value[normalizedQuery] ?: emptyMap()
                queryCounts[resultId] ?: 0f
            } else {
                _globalFrequencyMap[resultId] ?: 0f
            }

        // To calculate rank correctly, we need the max frequency in the current context
        val maxFreq =
            if (_queryBasedRankingEnabled.value) {
                val normalizedQuery = normalizeQuery(query)
                _resultFrequency.value[normalizedQuery]?.values?.maxOrNull() ?: 1f
            } else {
                _globalFrequencyMap.values.maxOrNull() ?: 1f
            }

        // Invert frequency so higher frequency results get lower rank values (sort ascending)
        return (maxFreq - count)
    }

    /**
     * Get frequency count for a specific result.
     */
    fun getResultFrequency(
        resultId: String,
        query: String,
    ): Float =
        if (_queryBasedRankingEnabled.value) {
            val normalizedQuery = normalizeQuery(query)
            _resultFrequency.value[normalizedQuery]?.get(resultId) ?: 0f
        } else {
            _globalFrequencyMap[resultId] ?: 0f
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
     * Applies competitive decay: selected score += 1; current top score (excluding selected) -= decayAmount, clamped at 0.
     */
    fun incrementResultUsage(
        resultId: String,
        query: String,
    ) {
        val normalizedQuery = normalizeQuery(query)
        val current = _resultFrequency.value.toMutableMap()
        val queryCounts = current[normalizedQuery]?.toMutableMap() ?: mutableMapOf()

        // Increment selected result score
        val newScore = (queryCounts[resultId] ?: 0f) + 1.0f
        queryCounts[resultId] = newScore
        current[normalizedQuery] = queryCounts

        // Find current top score result (excluding selected) and apply decay
        val decayAmount = _decayAmount.value
        var topResultId: String? = null
        var topScore = Float.MIN_VALUE
        queryCounts.forEach { (id, score) ->
            if (id != resultId && score > topScore) {
                topScore = score
                topResultId = id
            }
        }

        if (topResultId != null && topScore > 0f) {
            val decayedScore = (queryCounts[topResultId] ?: 0f) - decayAmount
            queryCounts[topResultId] = decayedScore.coerceAtLeast(0f)
        }

        // Update global frequency map (apply the same changes)
        val currentGlobal = _globalFrequencyMap.toMutableMap()
        currentGlobal[resultId] = (currentGlobal[resultId] ?: 0f) + 1.0f
        if (topResultId != null) {
            val globalTopScore = currentGlobal[topResultId] ?: 0f
            currentGlobal[topResultId] = (globalTopScore - decayAmount).coerceAtLeast(0f)
        }
        _globalFrequencyMap = currentGlobal

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
     * Toggle between query-based (specific) and global (generic) frequency ranking.
     */
    fun setQueryBasedRankingEnabled(enabled: Boolean) {
        _queryBasedRankingEnabled.value = enabled
        saveQueryBasedRankingEnabled(enabled)
    }

    /**
     * Get the decay amount applied to the current top-scoring result when another is selected.
     */
    fun getDecayAmount(): Float = _decayAmount.value

    /**
     * Set the decay amount applied to the current top-scoring result when another is selected.
     */
    fun setDecayAmount(amount: Float) {
        _decayAmount.value = amount.coerceIn(0f, 10f)
        saveDecayAmount(_decayAmount.value)
    }

    /**
     * Reset result frequency counts.
     */
    fun resetResultFrequency() {
        _resultFrequency.value = emptyMap()
        _globalFrequencyMap = emptyMap()
        saveResultFrequency(emptyMap())
    }

    private fun rebuildGlobalFrequency() {
        val global = mutableMapOf<String, Float>()
        _resultFrequency.value.values.forEach { queryMap ->
            queryMap.forEach { (resultId, count) ->
                global[resultId] = (global[resultId] ?: 0f) + count
            }
        }
        _globalFrequencyMap = global
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

    private fun loadQueryBasedRankingEnabled(): Boolean = preferences.getBoolean(KEY_QUERY_BASED_RANKING, true)

    private fun saveQueryBasedRankingEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_QUERY_BASED_RANKING, enabled)
        }
    }

    private fun loadDecayAmount(): Float = preferences.getFloat(KEY_DECAY_AMOUNT, 1.0f)

    private fun saveDecayAmount(amount: Float) {
        preferences.edit {
            putFloat(KEY_DECAY_AMOUNT, amount)
        }
    }

    private fun loadResultFrequency(): Map<String, Map<String, Float>> =
        try {
            val json = preferences.getString(KEY_RESULT_FREQUENCY, null)
            if (json != null) {
                val obj = JSONObject(json)
                buildMap {
                    obj.keys().forEach { key ->
                        val value = obj.optJSONObject(key)
                        if (value != null) {
                            // New format: key is query, value is map of resultId -> score
                            val innerMap =
                                buildMap {
                                    value.keys().forEach { innerKey ->
                                        val jsonValue = value.opt(innerKey)
                                        val score =
                                            when {
                                                jsonValue is Double -> jsonValue.toFloat()
                                                jsonValue is Int -> jsonValue.toFloat()
                                                jsonValue is Long -> jsonValue.toFloat()
                                                else -> 0f
                                            }
                                        put(innerKey, score)
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

    private fun saveResultFrequency(frequency: Map<String, Map<String, Float>>) {
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
