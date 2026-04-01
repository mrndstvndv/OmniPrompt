package com.mrndstvndv.search.provider.model

/**
 * Utility for parsing trigger-style input: "<firstToken> <payload>".
 */
object TriggerParser {

    /**
     * Extract the first token and remaining payload from input text.
     *
     * @param text The full input text.
     * @return A [ParsedTrigger] with the first token and payload.
     */
    fun parse(text: String): ParsedTrigger {
        val trimmed = text.trimStart()
        val spaceIndex = trimmed.indexOf(' ')
        val firstToken = if (spaceIndex == -1) trimmed else trimmed.substring(0, spaceIndex)
        val payload = if (spaceIndex == -1) "" else trimmed.substring(spaceIndex + 1).trim()
        return ParsedTrigger(firstToken = firstToken, payload = payload)
    }
}

/**
 * Result of parsing a trigger-style input.
 */
data class ParsedTrigger(
    /** The first whitespace-delimited token (potential trigger). */
    val firstToken: String,
    /** Everything after the first token, trimmed. Empty if no payload. */
    val payload: String,
)
