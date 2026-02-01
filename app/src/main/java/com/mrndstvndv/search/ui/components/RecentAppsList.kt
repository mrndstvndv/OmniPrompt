package com.mrndstvndv.search.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.apps.RecentApp
import com.mrndstvndv.search.provider.apps.RecentAppsRepository

@Composable
fun RecentAppsList(
    repository: RecentAppsRepository,
    isReversed: Boolean,
    modifier: Modifier = Modifier,
    shouldCenter: Boolean = false,
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
                val recentApps by remember(maxItems) {
                    repository.getRecentApps(limit = maxItems)
                }.collectAsState(initial = emptyList())

                val displayApps = if (isReversed) recentApps else recentApps.asReversed()

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
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
private fun AppIconItem(
    app: RecentApp,
    iconSizeDp: androidx.compose.ui.unit.Dp,
    index: Int,
    onClick: () -> Unit,
) {
    if (app.icon != null) {
        // Staggered fade-in and scale animation
        val animationDelay = (index * 30).coerceAtMost(150)
        val alpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, delayMillis = animationDelay),
            label = "appIconAlpha_${app.packageName}"
        )
        val scale by animateFloatAsState(
            targetValue = 1f,
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
) {
    val context = LocalContext.current
    val displayApps = if (isReversed) apps else apps.asReversed()
    val iconSizeDp = 40.dp

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
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
                )
            }
        }
    }
}
