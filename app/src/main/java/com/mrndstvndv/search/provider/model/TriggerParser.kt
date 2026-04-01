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
        val separatorIndex = trimmed.indexOfFirst { it.isWhitespace() }
        if (separatorIndex == -1) {
            return ParsedTrigger(
                firstToken = trimmed,
                payload = "",
                hasPayloadSeparator = false,
            )
        }

        return ParsedTrigger(
            firstToken = trimmed.substring(0, separatorIndex),
            payload = trimmed.substring(separatorIndex + 1).trimStart(),
            hasPayloadSeparator = true,
        )
    }
}

/**
 * Result of parsing a trigger-style input.
 */
data class ParsedTrigger(
    /** The first whitespace-delimited token (potential trigger). */
    val firstToken: String,
    /** Everything after the first token, with only leading separator whitespace removed. */
    val payload: String,
    /** True when the original text contained whitespace after the first token. */
    val hasPayloadSeparator: Boolean,
)
