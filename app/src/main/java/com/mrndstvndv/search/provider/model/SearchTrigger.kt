package com.mrndstvndv.search.provider.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.mrndstvndv.search.util.FuzzyMatcher

enum class TriggerResultPolicy {
    EXCLUSIVE,
    INCLUDE_OWNER_RESULTS,
    INCLUDE_ALL_RESULTS,
}

data class TriggerInvocation(
    val matchedToken: String,
    val payload: String,
) {
    val frequencyQuery: String
        get() = matchedToken
}

class SearchTrigger private constructor(
    val id: String,
    val ownerProviderId: String,
    val label: String,
    val aliases: Set<String> = emptySet(),
    val vectorIcon: ImageVector? = null,
    val iconLoader: (suspend () -> Bitmap?)? = null,
    val resultPolicy: TriggerResultPolicy = TriggerResultPolicy.EXCLUSIVE,
    private val matchLogic: (SearchTrigger, String) -> TriggerMatch?,
    private val executeLogic: suspend (TriggerInvocation) -> List<ProviderResult>,
) {
    fun match(firstToken: String): TriggerMatch? = matchLogic(this, firstToken)

    suspend fun execute(
        matchedToken: String,
        payload: String,
    ): List<ProviderResult> = executeLogic(TriggerInvocation(matchedToken = matchedToken, payload = payload))

    companion object {
        fun create(
            id: String,
            ownerProviderId: String,
            label: String,
            aliases: Set<String> = emptySet(),
            vectorIcon: ImageVector? = null,
            iconLoader: (suspend () -> Bitmap?)? = null,
            resultPolicy: TriggerResultPolicy = TriggerResultPolicy.EXCLUSIVE,
            matchLogic: ((SearchTrigger, String) -> TriggerMatch?)? = null,
            execute: suspend (TriggerInvocation) -> List<ProviderResult>,
        ): SearchTrigger =
            SearchTrigger(
                id = id,
                ownerProviderId = ownerProviderId,
                label = label,
                aliases = aliases,
                vectorIcon = vectorIcon,
                iconLoader = iconLoader,
                resultPolicy = resultPolicy,
                matchLogic = matchLogic ?: ::defaultMatch,
                executeLogic = execute,
            )

        private fun defaultMatch(
            trigger: SearchTrigger,
            firstToken: String,
        ): TriggerMatch? {
            if (firstToken.isBlank()) return null

            val labelMatch = FuzzyMatcher.match(firstToken, trigger.label)
            val aliasMatch = trigger.aliases
                .mapNotNull { alias -> FuzzyMatcher.match(firstToken, alias) }
                .maxByOrNull { it.score }

            val best = listOfNotNull(labelMatch, aliasMatch).maxByOrNull { it.score } ?: return null
            val matchedIndices = if (best === labelMatch) best.matchedIndices else emptyList()
            return TriggerMatch(
                trigger = trigger,
                score = best.score,
                matchedIndices = matchedIndices,
            )
        }
    }
}

data class TriggerMatch(
    val trigger: SearchTrigger,
    val score: Int,
    val matchedIndices: List<Int>,
)
