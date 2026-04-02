package com.mrndstvndv.search.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.settings.FirstResultHighlightMode
import com.mrndstvndv.search.ui.theme.motionAwareTween

/**
 * Renders text with highlighted characters at specified indices.
 * Used for displaying fuzzy search matches with visual feedback.
 */
@Composable
fun HighlightedText(
    text: String,
    matchedIndices: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    highlightColor: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1
) {
    if (matchedIndices.isEmpty()) {
        Text(
            text = text,
            color = color,
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    } else {
        val matchedSet = matchedIndices.toSet()
        Text(
            text = buildAnnotatedString {
                text.forEachIndexed { index, char ->
                    if (index in matchedSet) {
                        withStyle(
                            SpanStyle(
                                color = highlightColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(char)
                        }
                    } else {
                        withStyle(SpanStyle(color = color)) {
                            append(char)
                        }
                    }
                }
            },
            style = style,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

@Composable
private fun TopResultBadge(
    modifier: Modifier = Modifier,
    cueProgress: Float = 0f,
) {
    Surface(
        modifier =
            modifier.graphicsLayer {
                val scale = 1f + (cueProgress * 0.05f)
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
    ) {
        Text(
            text = stringResource(R.string.result_default_action_badge),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun primaryActionContainerColor(
    highlightMode: FirstResultHighlightMode,
    translucentItems: Boolean,
): Color {
    val colorScheme = MaterialTheme.colorScheme

    return when (highlightMode) {
        FirstResultHighlightMode.SUBTLE -> {
            if (translucentItems) {
                colorScheme.surfaceContainerHighest.copy(alpha = 0.82f)
            } else {
                colorScheme.surfaceContainerHigh
            }
        }

        FirstResultHighlightMode.BALANCED -> {
            if (translucentItems) {
                lerp(
                    colorScheme.surfaceContainerHighest,
                    colorScheme.primaryContainer,
                    0.4f,
                ).copy(alpha = 0.72f)
            } else {
                lerp(colorScheme.surfaceContainerHigh, colorScheme.primaryContainer, 0.16f)
            }
        }

        FirstResultHighlightMode.STRONG -> {
            if (translucentItems) {
                colorScheme.primaryContainer.copy(alpha = 0.62f)
            } else {
                lerp(colorScheme.surfaceContainerHigh, colorScheme.primaryContainer, 0.3f)
            }
        }
    }
}

private fun Modifier.verticalEdgeFade(
    showTop: Boolean,
    showBottom: Boolean,
    fadeHeight: Dp = 10.dp,
): Modifier =
    if (!showTop && !showBottom) {
        this
    } else {
        graphicsLayer { alpha = 0.99f }.drawWithContent {
            drawContent()
            val fadeHeightPx = fadeHeight.toPx()
            if (showTop) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = fadeHeightPx,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (showBottom) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - fadeHeightPx,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItemsList(
    modifier: Modifier = Modifier,
    results: List<ProviderResult>,
    onItemClick: (ProviderResult) -> Unit,
    onItemLongPress: ((ProviderResult) -> Unit)? = null,
    translucentItems: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(2.dp),
    reverseOrder: Boolean = false,
    showEnterBadge: Boolean = true,
    firstResultHighlightEnabled: Boolean = true,
    firstResultHighlightMode: FirstResultHighlightMode = FirstResultHighlightMode.BALANCED,
    firstResultBorderThickness: Float = 1f,
    animateFirstResultChanges: Boolean = true,
    animateFirstResultColorPulse: Boolean = false,
) {
    if (results.isEmpty()) return

    val listState = rememberLazyListState()
    val primaryActionResultId = results.firstOrNull()?.id
    val primaryActionCue = remember { Animatable(0f) }
    var previousPrimaryActionResultId by remember { mutableStateOf(primaryActionResultId) }
    val cueExpandSpec = motionAwareTween<Float>(durationMillis = 120)
    val cueSettleSpec = motionAwareTween<Float>(durationMillis = 220)

    // Scroll to the "first" item (index 0) whenever results change.
    // In reverse layout, index 0 is at the bottom. In normal layout, it's at the top.
    LaunchedEffect(primaryActionResultId) {
        if (results.isNotEmpty()) {
            listState.scrollToItem(0)
        }

        val shouldAnimateCue =
            animateFirstResultChanges &&
                primaryActionResultId != null &&
                previousPrimaryActionResultId != null &&
                previousPrimaryActionResultId != primaryActionResultId

        previousPrimaryActionResultId = primaryActionResultId
        primaryActionCue.snapTo(0f)

        if (!shouldAnimateCue) return@LaunchedEffect

        primaryActionCue.animateTo(1f, animationSpec = cueExpandSpec)
        primaryActionCue.animateTo(0f, animationSpec = cueSettleSpec)
    }

    val showTopFade by remember(listState, reverseOrder) {
        derivedStateOf {
            if (reverseOrder) listState.canScrollForward else listState.canScrollBackward
        }
    }
    val showBottomFade by remember(listState, reverseOrder) {
        derivedStateOf {
            if (reverseOrder) listState.canScrollBackward else listState.canScrollForward
        }
    }
    val fadeModifier = Modifier.verticalEdgeFade(showTop = showTopFade, showBottom = showBottomFade)

    Box(modifier = modifier.then(fadeModifier)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
            reverseLayout = reverseOrder,
        ) {
            itemsIndexed(
                items = results,
                key = { _, item -> item.id }
            ) { index, item ->
                val singleItem = results.size == 1
                val isPrimaryActionItem = index == 0
                val showPrimaryActionHighlight = firstResultHighlightEnabled && isPrimaryActionItem
                val isVisualTopItem = if (reverseOrder) index == results.lastIndex else index == 0
                val isVisualBottomItem = if (reverseOrder) index == 0 else index == results.lastIndex

                val targetTopStart = when {
                    singleItem || isVisualTopItem -> 20.dp
                    else -> 5.dp
                }
                val targetTopEnd = when {
                    singleItem || isVisualTopItem -> 20.dp
                    else -> 5.dp
                }
                val targetBottomStart = when {
                    singleItem || isVisualBottomItem -> 20.dp
                    else -> 5.dp
                }
                val targetBottomEnd = when {
                    singleItem || isVisualBottomItem -> 20.dp
                    else -> 5.dp
                }

                val primaryActionCueProgress = if (isPrimaryActionItem && item.id == primaryActionResultId) {
                    primaryActionCue.value
                } else {
                    0f
                }
                val colorPulseProgress = if (animateFirstResultColorPulse && showPrimaryActionHighlight) {
                    primaryActionCueProgress
                } else {
                    0f
                }
                val itemScaleX = 1f + (primaryActionCueProgress * 0.008f)
                val itemScaleY = 1f + (primaryActionCueProgress * 0.018f)
                val itemTransformOrigin = when {
                    singleItem -> TransformOrigin.Center
                    isVisualBottomItem -> TransformOrigin(0.5f, 0f)
                    isVisualTopItem -> TransformOrigin(0.5f, 1f)
                    else -> TransformOrigin.Center
                }

                // Edge corner ownership should snap to the current visual slot.
                // Animating it per item makes the old top result "carry" rounded corners while moving away.
                val shape = RoundedCornerShape(
                    topStart = targetTopStart,
                    topEnd = targetTopEnd,
                    bottomEnd = targetBottomEnd,
                    bottomStart = targetBottomStart,
                )

                val baseContainerColor = if (translucentItems) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val highlightedContainerColor = if (showPrimaryActionHighlight) {
                    primaryActionContainerColor(
                        highlightMode = firstResultHighlightMode,
                        translucentItems = translucentItems,
                    )
                } else {
                    baseContainerColor
                }
                val cueTargetContainerColor = if (showPrimaryActionHighlight) {
                    if (translucentItems) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
                    } else {
                        lerp(highlightedContainerColor, MaterialTheme.colorScheme.primaryContainer, 0.35f)
                    }
                } else {
                    baseContainerColor
                }
                val containerColor = lerp(highlightedContainerColor, cueTargetContainerColor, colorPulseProgress)
                val baseBorderColor = if (showPrimaryActionHighlight) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (translucentItems) 0.5f else 0.22f)
                } else {
                    Color.Transparent
                }
                val cueBorderColor = if (showPrimaryActionHighlight) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (translucentItems) 0.82f else 0.38f)
                } else {
                    Color.Transparent
                }
                val borderColor = lerp(baseBorderColor, cueBorderColor, colorPulseProgress)
                val baseBorderWidth = if (showPrimaryActionHighlight) firstResultBorderThickness.dp else 0.dp
                val borderWidth = baseBorderWidth + (if (showPrimaryActionHighlight) {
                    (if (translucentItems) 0.75.dp else 0.5.dp) * primaryActionCueProgress
                } else {
                    0.dp
                })
                val tonalElevation = when {
                    translucentItems -> 0.dp
                    showPrimaryActionHighlight -> 3.dp
                    else -> 1.dp
                }
                val isDarkTheme = isSystemInDarkTheme()
                val primaryTextColor = if (translucentItems) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                val subtitleColor = if (translucentItems) {
                    val alpha = when {
                        showPrimaryActionHighlight && isDarkTheme -> 0.9f
                        showPrimaryActionHighlight -> 0.82f
                        isDarkTheme -> 0.85f
                        else -> 0.75f
                    }
                    primaryTextColor.copy(alpha = alpha)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                val iconBitmap by produceState<Bitmap?>(
                    initialValue = item.icon,
                    key1 = item.id,
                    key2 = item.iconLoader
                ) {
                    if (value != null) return@produceState
                    val loader = item.iconLoader ?: return@produceState
                    value = loader()
                }

                Surface(
                    modifier =
                        Modifier
                            .zIndex(if (primaryActionCueProgress > 0f) 1f else 0f)
                            .graphicsLayer {
                                transformOrigin = itemTransformOrigin
                                scaleX = itemScaleX
                                scaleY = itemScaleY
                            },
                    shape = shape,
                    tonalElevation = tonalElevation,
                    color = containerColor,
                    border = BorderStroke(borderWidth, borderColor),
                ) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val rippleIndication = LocalIndication.current
                    val clickModifier = if (onItemLongPress != null) {
                        Modifier.combinedClickable(
                            interactionSource = interactionSource,
                            indication = rippleIndication,
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongPress(item) }
                        )
                    } else {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = rippleIndication
                        ) { onItemClick(item) }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .then(clickModifier)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            iconBitmap != null -> {
                                val painter = remember(iconBitmap) { BitmapPainter(iconBitmap!!.asImageBitmap()) }
                                androidx.compose.foundation.Image(
                                    painter = painter,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            item.vectorIcon != null -> {
                                Icon(
                                    imageVector = item.vectorIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = primaryTextColor
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            item.defaultVectorIcon != null -> {
                                Icon(
                                    imageVector = item.defaultVectorIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = primaryTextColor
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            HighlightedText(
                                text = item.title,
                                matchedIndices = item.matchedTitleIndices,
                                color = primaryTextColor,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (showPrimaryActionHighlight) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                            )
                            item.subtitle?.let { subtitle ->
                                HighlightedText(
                                    text = subtitle,
                                    matchedIndices = item.matchedSubtitleIndices,
                                    color = subtitleColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if ((showEnterBadge && isPrimaryActionItem) || item.id.startsWith("alias:")) {
                            Spacer(Modifier.width(12.dp))
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // TODO: using the id prefix is still fragile.
                                // Add an explicit isAlias flag to ProviderResult.
                                if (item.id.startsWith("alias:")) {
                                    Icon(
                                        imageVector = Icons.Filled.Bookmark,
                                        contentDescription = stringResource(R.string.alias_content_description),
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    )
                                }

                                if (showEnterBadge && isPrimaryActionItem) {
                                    TopResultBadge(cueProgress = primaryActionCueProgress)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
