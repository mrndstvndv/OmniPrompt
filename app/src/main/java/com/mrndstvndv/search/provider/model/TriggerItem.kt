package com.mrndstvndv.search.provider.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * A single trigger item exposed by a [TriggerProvider].
 *
 * Trigger items are matched against the first token of user input.
 * When matched, the remaining input becomes the payload passed to execution.
 */
data class TriggerItem(
    /** Stable unique identifier for this trigger item. */
    val id: String,
    /** The label shown in the chip and used for fuzzy matching. */
    val label: String,
    /** Optional extra aliases that can also trigger this item (fuzzy matched). */
    val aliases: Set<String> = emptySet(),
    /** Optional vector icon for the chip. */
    val vectorIcon: ImageVector? = null,
    /** Optional async icon loader for the chip. */
    val iconLoader: (suspend () -> Bitmap?)? = null,
)

/**
 * Result of matching a trigger token against available trigger items.
 */
data class TriggerMatch(
    /** The matched trigger item. */
    val item: TriggerItem,
    /** The fuzzy match score (higher is better). */
    val score: Int,
    /** Matched character indices in the label (for highlighting). */
    val matchedIndices: List<Int>,
)
