package com.mrndstvndv.search.util

import android.content.Context
import android.os.Build
import com.mrndstvndv.search.BuildConfig
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import org.json.JSONArray
import org.json.JSONObject

object SearchDebugCollector {
    @Volatile
    var lastQueryText: String = ""

    @Volatile
    var lastNormalizedQueryText: String = ""

    @Volatile
    var lastResults: List<ProviderResult> = emptyList()

    @Volatile
    var lastRawResultsByProvider: Map<String, List<ProviderResult>> = emptyMap()

    @Volatile
    var providerDisplayNames: Map<String, String> = emptyMap()

    @Volatile
    var providerTriggers: Map<String, List<String>> = emptyMap()

    fun generateDebugJson(
        context: Context,
        rankingRepository: ProviderRankingRepository,
        settingsRepository: ProviderSettingsRepository
    ): String {
        val root = JSONObject()

        // 1. App and Device Metadata
        val metadata = JSONObject()
        metadata.put("appVersionName", BuildConfig.VERSION_NAME)
        metadata.put("appVersionCode", BuildConfig.VERSION_CODE)
        metadata.put("sdkInt", Build.VERSION.SDK_INT)
        metadata.put("device", Build.DEVICE)
        metadata.put("model", Build.MODEL)
        metadata.put("timestamp", System.currentTimeMillis())
        root.put("metadata", metadata)

        // 2. Search Query
        val queryInfo = JSONObject()
        queryInfo.put("queryText", lastQueryText)
        queryInfo.put("normalizedQueryText", lastNormalizedQueryText)
        root.put("searchQuery", queryInfo)

        // 3. Merged Results
        val resultsArray = JSONArray()
        lastResults.forEach { result ->
            resultsArray.put(serializeProviderResult(result))
        }
        root.put("mergedResults", resultsArray)

        // 4. Provider Items (raw results per provider before deduplication/merging)
        val providerItemsObj = JSONObject()
        lastRawResultsByProvider.forEach { (providerId, results) ->
            val providerResultsArray = JSONArray()
            results.forEach { result ->
                providerResultsArray.put(serializeProviderResult(result))
            }
            providerItemsObj.put(providerId, providerResultsArray)
        }
        root.put("providerItems", providerItemsObj)

        // 5. Providers Status and Triggers
        val providersArray = JSONArray()
        val enabledProviders = settingsRepository.enabledProviders.value
        providerDisplayNames.forEach { (id, name) ->
            val providerObj = JSONObject()
            providerObj.put("id", id)
            providerObj.put("displayName", name)
            providerObj.put("enabled", enabledProviders[id] ?: true)
            
            val triggersArray = JSONArray()
            providerTriggers[id]?.forEach { trigger ->
                triggersArray.put(trigger)
            }
            providerObj.put("triggers", triggersArray)
            
            providersArray.put(providerObj)
        }
        root.put("providers", providersArray)

        // 6. Frequency Data
        val frequencyObj = JSONObject()
        frequencyObj.put("useFrequencyRanking", rankingRepository.useFrequencyRanking.value)
        frequencyObj.put("queryBasedRankingEnabled", rankingRepository.queryBasedRankingEnabled.value)
        frequencyObj.put("decayAmount", rankingRepository.decayAmount.value)

        // Add raw resultFrequency
        val frequencyMapObj = JSONObject()
        rankingRepository.resultFrequency.value.forEach { (query, counts) ->
            val countsObj = JSONObject()
            counts.forEach { (resultId, score) ->
                countsObj.put(resultId, score.toDouble())
            }
            frequencyMapObj.put(query, countsObj)
        }
        frequencyObj.put("resultFrequencyMap", frequencyMapObj)
        root.put("frequencyData", frequencyObj)

        // 7. Other Relevant Settings
        val otherSettings = JSONObject()
        
        val providerOrderArray = JSONArray()
        rankingRepository.providerOrder.value.forEach { providerId ->
            providerOrderArray.put(providerId)
        }
        otherSettings.put("providerOrder", providerOrderArray)
        otherSettings.put("translucentResultsEnabled", settingsRepository.translucentResultsEnabled.value)
        otherSettings.put("backgroundOpacity", settingsRepository.backgroundOpacity.value.toDouble())
        otherSettings.put("backgroundBlurStrength", settingsRepository.backgroundBlurStrength.value.toDouble())
        otherSettings.put("activityIndicatorDelayMs", settingsRepository.activityIndicatorDelayMs.value)
        
        root.put("otherSettings", otherSettings)

        return root.toString(4) // 4 spaces indentation for readable formatted text
    }

    private fun serializeProviderResult(result: ProviderResult): JSONObject {
        val obj = JSONObject()
        obj.put("id", result.id)
        obj.put("title", result.title)
        obj.put("subtitle", result.subtitle ?: JSONObject.NULL)
        obj.put("providerId", result.providerId)
        obj.put("score", result.score.toDouble())
        obj.put("frequencyKey", result.frequencyKey)
        obj.put("frequencyQuery", result.frequencyQuery ?: JSONObject.NULL)
        obj.put("excludeFromFrequencyRanking", result.excludeFromFrequencyRanking)
        
        val extrasObj = JSONObject()
        result.extras.forEach { (key, value) ->
            if (value is String || value is Number || value is Boolean) {
                extrasObj.put(key, value)
            }
        }
        obj.put("extras", extrasObj)
        return obj
    }
}
