package com.mrndstvndv.search.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.model.ProviderResult
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
) {
    if (results.isEmpty()) return

    val displayResults = if (reverseOrder) results.reversed() else results
    val listState = rememberLazyListState()
    
    LaunchedEffect(displayResults.firstOrNull()?.id) {
        if (reverseOrder && displayResults.isNotEmpty()) {
            listState.scrollToItem(displayResults.lastIndex)
        } else {
            listState.scrollToItem(0)
        }
    }

    val showTopFade by remember(listState) {
        derivedStateOf { listState.canScrollBackward }
    }
    val showBottomFade by remember(listState) {
        derivedStateOf { listState.canScrollForward }
    }
    val fadeModifier = Modifier.verticalEdgeFade(showTop = showTopFade, showBottom = showBottomFade)

    Box(modifier = modifier.then(fadeModifier)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = verticalArrangement,
        ) {
            itemsIndexed(
                items = displayResults,
                key = { _, item -> item.id }
            ) { index, item ->
                val singleItem = displayResults.size == 1
                val targetTopStart = when {
                    singleItem -> 20.dp
                    index == 0 -> 20.dp
                    else -> 5.dp
                }
                val targetTopEnd = when {
                    singleItem -> 20.dp
                    index == 0 -> 20.dp
                    else -> 5.dp
                }
                val targetBottomStart = when {
                    singleItem -> 20.dp
                    index == displayResults.lastIndex -> 20.dp
                    else -> 5.dp
                }
                val targetBottomEnd = when {
                    singleItem -> 20.dp
                    index == displayResults.lastIndex -> 20.dp
                    else -> 5.dp
                }

                val cornerAnimationSpec = motionAwareTween<Dp>(durationMillis = 250)
                val animatedTopStart by animateDpAsState(targetTopStart, animationSpec = cornerAnimationSpec, label = "shapeTopStart")
                val animatedTopEnd by animateDpAsState(targetTopEnd, animationSpec = cornerAnimationSpec, label = "shapeTopEnd")
                val animatedBottomStart by animateDpAsState(targetBottomStart, animationSpec = cornerAnimationSpec, label = "shapeBottomStart")
                val animatedBottomEnd by animateDpAsState(targetBottomEnd, animationSpec = cornerAnimationSpec, label = "shapeBottomEnd")

                val shape = RoundedCornerShape(
                    topStart = animatedTopStart,
                    topEnd = animatedTopEnd,
                    bottomEnd = animatedBottomEnd,
                    bottomStart = animatedBottomStart,
                )

                val containerColor = if (translucentItems) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
                val isDarkTheme = isSystemInDarkTheme()
                val primaryTextColor = if (translucentItems) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
                val subtitleColor = if (translucentItems) {
                    val alpha = if (isDarkTheme) 0.85f else 0.75f
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
                    shape = shape,
                    tonalElevation = if (translucentItems) 0.dp else 1.dp,
                    color = containerColor
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
                                color = primaryTextColor
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
                    }
                }
            }
        }
    }
}
