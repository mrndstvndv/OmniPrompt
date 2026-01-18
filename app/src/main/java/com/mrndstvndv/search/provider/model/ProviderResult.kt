package com.mrndstvndv.search.provider.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.mrndstvndv.search.alias.AliasTarget

data class ProviderResult(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val icon: Bitmap? = null,
    val vectorIcon: ImageVector? = null,
    val defaultVectorIcon: ImageVector? = null,
    val iconLoader: (suspend () -> Bitmap?)? = null,
    val providerId: String,
    val score: Float = 0f,
    val extras: Map<String, Any?> = emptyMap(),
    val onSelect: (suspend () -> Unit)? = null,
    val aliasTarget: AliasTarget? = null,
    val keepOverlayUntilExit: Boolean = false,
    /** Indices of matched characters in [title] for fuzzy search highlighting */
    val matchedTitleIndices: List<Int> = emptyList(),
    /** Indices of matched characters in [subtitle] for fuzzy search highlighting */
    val matchedSubtitleIndices: List<Int> = emptyList(),
    /** When true, this result will not be tracked for frequency-based ranking */
    val excludeFromFrequencyRanking: Boolean = false,
)
