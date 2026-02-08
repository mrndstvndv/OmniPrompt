package com.mrndstvndv.search.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.apps.PinnedAppsRepository
import com.mrndstvndv.search.provider.apps.RecentApp
import com.mrndstvndv.search.provider.apps.RecentAppsRepository
import com.mrndstvndv.search.provider.settings.AppListType
import com.mrndstvndv.search.ui.theme.motionAwareVisibility

@Composable
fun RecentAppsList(
    repository: RecentAppsRepository,
    isReversed: Boolean,
    modifier: Modifier = Modifier,
    shouldCenter: Boolean = false,
    visible: Boolean = true,
    excludePackages: Set<String> = emptySet(),
) {
    val context = LocalContext.current
    val hasPermission = remember(repository) { repository.hasPermission() }

    BoxWithConstraints(modifier = modifier) {
        if (!hasPermission) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Enable Recent Apps",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clickable {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                )
            }
        } else {
            val width = maxWidth
            val iconSizeDp = 40.dp
            val paddingDp = 8.dp
            val itemWidth = iconSizeDp + paddingDp

            val maxItems = (width / itemWidth).toInt().coerceAtLeast(0)

            if (maxItems > 0) {
                val fetchLimit = if (excludePackages.isEmpty()) maxItems else maxItems + excludePackages.size
                val recentApps by remember(maxItems, excludePackages) {
                    repository.getRecentApps(limit = fetchLimit)
                }.collectAsState(initial = emptyList())

                val filteredApps = recentApps.filterNot { app -> app.packageName in excludePackages }.take(maxItems)
                val displayApps = if (isReversed) filteredApps else filteredApps.asReversed()
                val scrollState = rememberScrollState()

                LaunchedEffect(recentApps, isReversed) {
                    scrollState.scrollTo(0)
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 8.dp),
                    horizontalArrangement = if (shouldCenter) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    displayApps.forEachIndexed { index, app ->
                        key(app.packageName) {
                            AppIconItem(
                                app = app,
                                iconSizeDp = iconSizeDp,
                                index = index,
                                onClick = {
                                    context.startActivity(app.launchIntent)
                                    (context as ComponentActivity).finish()
                                },
                                visible = visible,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual app icon with fade-in + scale animation.
 */
@Composable
fun AppIconItem(
    app: RecentApp,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    index: Int,
    onClick: () -> Unit,
    visible: Boolean = true,
) {
    if (app.icon != null) {
        // Staggered fade-in and scale animation
        val animationDelay = (index * 30).coerceAtMost(150)
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 300, delayMillis = animationDelay),
            label = "appIconAlpha_${app.packageName}"
        )
        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 300, delayMillis = animationDelay),
            label = "appIconScale_${app.packageName}"
        )

        Image(
            bitmap = app.icon.asImageBitmap(),
            contentDescription = app.label,
            modifier =
                Modifier
                    .size(iconSizeDp)
                    .clip(CircleShape)
                    .alpha(alpha)
                    .scale(scale)
                    .clickable(onClick = onClick),
        )
    }
}

/**
 * A reusable component for displaying a list of apps (either recent or pinned).
 */
@Composable
fun AppListRow(
    apps: List<RecentApp>,
    isReversed: Boolean,
    modifier: Modifier = Modifier,
    shouldCenter: Boolean = false,
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val displayApps = if (isReversed) apps else apps.asReversed()
    val iconSizeDp = 40.dp
    val scrollState = rememberScrollState()
    val listKey = remember(apps) { apps.joinToString("|") { it.packageName } }

    LaunchedEffect(listKey, isReversed) {
        if (isReversed) {
            scrollState.scrollTo(0)
        } else {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (shouldCenter) Alignment.CenterHorizontally else if (isReversed) Alignment.Start else Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        displayApps.forEachIndexed { index, app ->
            key(app.packageName) {
                AppIconItem(
                    app = app,
                    iconSizeDp = iconSizeDp,
                    index = index,
                    onClick = {
                        context.startActivity(app.launchIntent)
                    },
                    visible = visible,
                )
            }
        }
    }
}

/**
 * Container for app list with visibility animation and optional settings icon.
 */
private enum class FadeEdge {
    START,
    END,
}

private fun Modifier.edgeFade(edge: FadeEdge, fadeWidth: Dp = 24.dp): Modifier =
    graphicsLayer { alpha = 0.99f }.drawWithContent {
        drawContent()
        val fadeWidthPx = fadeWidth.toPx()
        val (startX, endX, colors) =
            when (edge) {
                FadeEdge.START -> Triple(0f, fadeWidthPx, listOf(Color.Transparent, Color.Black))
                FadeEdge.END -> Triple(size.width - fadeWidthPx, size.width, listOf(Color.Black, Color.Transparent))
            }
        drawRect(
            brush = Brush.horizontalGradient(
                colors = colors,
                startX = startX,
                endX = endX,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
fun AppListContainer(
    visible: Boolean,
    enterDuration: Int,
    exitDuration: Int,
    shouldCenter: Boolean,
    showSettingsIcon: Boolean,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    motionAwareVisibility(
        visible = visible,
        modifier = modifier.fillMaxWidth(),
        enter = fadeIn(animationSpec = tween(durationMillis = enterDuration)) +
            expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = tween(durationMillis = enterDuration),
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = exitDuration)) +
            shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(durationMillis = exitDuration),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (shouldCenter) Arrangement.Center else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()

            if (showSettingsIcon) {
                VerticalDivider(
                    modifier = Modifier
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

/**
 * Unified app list section that handles both Recent and Pinned apps with animations.
 */
@Composable
fun AppListSection(
    appListType: AppListType,
    recentAppsRepository: RecentAppsRepository,
    pinnedAppsRepository: PinnedAppsRepository,
    isReversedRecent: Boolean,
    isReversedPinned: Boolean,
    pinnedOnLeft: Boolean,
    filterPinnedFromRecentsInBoth: Boolean,
    shouldCenter: Boolean,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    when (appListType) {
        AppListType.RECENT -> {
            RecentAppsList(
                repository = recentAppsRepository,
                isReversed = isReversedRecent,
                shouldCenter = shouldCenter,
                modifier = modifier,
                visible = visible,
            )
        }

        AppListType.PINNED -> {
            val pinnedApps by pinnedAppsRepository.getPinnedApps().collectAsState(initial = emptyList())
            if (pinnedApps.isNotEmpty()) {
                BoxWithConstraints(modifier = modifier) {
                    val itemWidth = 48.dp
                    val contentWidth = (itemWidth * pinnedApps.size) + 16.dp
                    val shouldFade = contentWidth > maxWidth
                    val fadeEdge = if (isReversedPinned) FadeEdge.START else FadeEdge.END
                    val fadeModifier = if (shouldFade) Modifier.edgeFade(fadeEdge) else Modifier
                    val allowCenter = shouldCenter && !shouldFade
                    Box(modifier = Modifier.fillMaxWidth().then(fadeModifier)) {
                        AppListRow(
                            apps = pinnedApps,
                            isReversed = isReversedPinned,
                            shouldCenter = allowCenter,
                            modifier = Modifier.fillMaxWidth(),
                            visible = visible,
                        )
                    }
                }
            }
        }

        AppListType.BOTH -> {
            val pinnedApps by pinnedAppsRepository.getPinnedApps().collectAsState(initial = emptyList())
            val excludePackages = if (filterPinnedFromRecentsInBoth) {
                pinnedApps.map { it.packageName }.toSet()
            } else {
                emptySet()
            }
            BoxWithConstraints(modifier = modifier) {
                val itemWidth = 48.dp
                val minRecentWidth = 120.dp
                val estimatedPinnedWidth = (itemWidth * pinnedApps.size) + 16.dp
                val maxPinnedWidth = (maxWidth - minRecentWidth).coerceAtLeast(itemWidth)
                val pinnedWidth =
                    if (pinnedApps.isEmpty()) {
                        minOf(maxPinnedWidth, 120.dp)
                    } else {
                        minOf(maxPinnedWidth, estimatedPinnedWidth)
                    }
                val recentPaddingStart = if (pinnedOnLeft) 4.dp else 0.dp
                val recentPaddingEnd = if (pinnedOnLeft) 0.dp else 4.dp
                val pinnedPaddingStart = if (pinnedOnLeft) 0.dp else 4.dp
                val pinnedPaddingEnd = if (pinnedOnLeft) 4.dp else 0.dp
                val pinnedModifier =
                    Modifier
                        .width(pinnedWidth)
                        .padding(
                            start = pinnedPaddingStart,
                            end = pinnedPaddingEnd,
                        )
                val shouldFadePinned = estimatedPinnedWidth > pinnedWidth
                val recentContent: @Composable RowScope.() -> Unit = {
                    RecentAppsList(
                        repository = recentAppsRepository,
                        isReversed = isReversedRecent,
                        shouldCenter = false,
                        modifier = Modifier.weight(1f).padding(start = recentPaddingStart, end = recentPaddingEnd),
                        visible = visible,
                        excludePackages = excludePackages,
                    )
                }
                val pinnedContent: @Composable RowScope.() -> Unit = {
                    if (pinnedApps.isNotEmpty()) {
                        val fadeEdge = if (pinnedOnLeft) FadeEdge.START else FadeEdge.END
                        val fadeModifier = if (shouldFadePinned) Modifier.edgeFade(fadeEdge) else Modifier
                        Box(modifier = pinnedModifier.then(fadeModifier)) {
                            AppListRow(
                                apps = pinnedApps,
                                isReversed = isReversedPinned,
                                shouldCenter = false,
                                modifier = Modifier.fillMaxWidth(),
                                visible = visible,
                            )
                        }
                    } else {
                        Box(modifier = pinnedModifier) {
                            Text(
                                text = "No pinned apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (pinnedOnLeft) {
                        pinnedContent()

                        VerticalDivider(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        recentContent()
                    } else {
                        recentContent()

                        VerticalDivider(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )

                        pinnedContent()
                    }
                }
            }
        }
    }
}
