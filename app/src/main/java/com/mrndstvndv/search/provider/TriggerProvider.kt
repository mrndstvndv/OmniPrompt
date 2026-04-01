package com.mrndstvndv.search.provider

import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.TriggerItem
import com.mrndstvndv.search.provider.model.TriggerMatch
import com.mrndstvndv.search.util.FuzzyMatcher

/**
 * A provider that supports trigger-based execution.
 *
 * Trigger providers expose a set of [TriggerItem]s that can be matched
 * against the first token of user input. When matched, the remaining
 * input becomes a payload passed to [executeTrigger].
 *
 * This allows consumers (e.g., UI) to:
 * - Query all available trigger items across providers
 * - Detect when the first token matches a trigger
 * - Replace the trigger text with a chip/annotation
 * - Forward only the payload to the provider
 */
interface TriggerProvider : Provider {

    /**
     * The trigger items this provider exposes.
     * May change dynamically (e.g., when settings change).
     */
    val triggerItems: List<TriggerItem>

    /**
     * Match the first token against this provider's trigger items.
     *
     * @param firstToken The first whitespace-delimited token from user input.
     * @return A [TriggerMatch] if any item matches, or null.
     */
    fun matchTrigger(firstToken: String): TriggerMatch? {
        if (firstToken.isBlank()) return null

        var bestMatch: TriggerMatch? = null

        for (item in triggerItems) {
            val labelMatch = FuzzyMatcher.match(firstToken, item.label)
            val aliasMatch = item.aliases
                .mapNotNull { alias -> FuzzyMatcher.match(firstToken, alias) }
                .maxByOrNull { it.score }

            // Pick the best between label and alias matches
            val best = listOfNotNull(labelMatch, aliasMatch).maxByOrNull { it.score } ?: continue

            if (bestMatch == null || best.score > bestMatch.score) {
                bestMatch = TriggerMatch(
                    item = item,
                    score = best.score,
                    matchedIndices = if (best === labelMatch) best.matchedIndices else emptyList(),
                )
            }
        }

        return bestMatch
    }

    /**
     * Execute this trigger with the given payload.
     *
     * @param item The matched trigger item.
     * @param payload The remaining input after the trigger token (may be empty).
     * @return Results to display to the user.
     */
    suspend fun executeTrigger(item: TriggerItem, payload: String): List<ProviderResult>
}
