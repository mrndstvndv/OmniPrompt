package com.mrndstvndv.search.provider.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.vector.ImageVector
import com.mrndstvndv.search.alias.AliasTarget

fun createTriggerResult(
    invocation: TriggerInvocation,
    id: String,
    title: String,
    subtitle: String? = null,
    providerId: String,
    triggerId: String,
    icon: Bitmap? = null,
    vectorIcon: ImageVector? = null,
    defaultVectorIcon: ImageVector? = null,
    iconLoader: (suspend () -> Bitmap?)? = null,
    extras: Map<String, Any?> = emptyMap(),
    onSelect: (suspend () -> Unit)? = null,
    aliasTarget: AliasTarget? = null,
    keepOverlayUntilExit: Boolean = onSelect != null,
    matchedTitleIndices: List<Int> = emptyList(),
    matchedSubtitleIndices: List<Int> = emptyList(),
    excludeFromFrequencyRanking: Boolean = false,
    frequencyKey: String = "$providerId:$triggerId",
    frequencyQuery: String? = invocation.frequencyQuery,
): ProviderResult =
    ProviderResult(
        id = id,
        title = title,
        subtitle = subtitle,
        icon = icon,
        vectorIcon = vectorIcon,
        defaultVectorIcon = defaultVectorIcon,
        iconLoader = iconLoader,
        providerId = providerId,
        extras = extras,
        onSelect = onSelect,
        aliasTarget = aliasTarget,
        keepOverlayUntilExit = keepOverlayUntilExit,
        matchedTitleIndices = matchedTitleIndices,
        matchedSubtitleIndices = matchedSubtitleIndices,
        frequencyKey = frequencyKey,
        frequencyQuery = frequencyQuery,
        excludeFromFrequencyRanking = excludeFromFrequencyRanking,
    )
