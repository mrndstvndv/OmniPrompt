package com.mrndstvndv.search.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.BuildConfig

@Composable
fun DebugRecompositionOverlay(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return

    val events by RecompositionDebugState.events.collectAsState()
    if (events.isEmpty()) return

    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(12.dp)
            .widthIn(max = 340.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = "Recompositions: ${events.size}",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (expanded) {
            Surface(
                modifier = Modifier.heightIn(max = 360.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = MaterialTheme.shapes.medium,
            ) {
                LazyColumn(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(events, key = { it.id }) { debugEvent ->
                        RecompositionDebugRow(debugEvent)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecompositionDebugRow(debugEvent: RecompositionDebugEvent) {
    val event = debugEvent.event
    val composableName = event.composableName.substringAfterLast('.')
    val changedParameters = event.parameterChanges
        .filter { it.changed }
        .joinToString { it.name }
    val unstableParameters = event.unstableParameters.joinToString()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = composableName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "#${event.recompositionCount}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (event.tag.isNotEmpty()) {
            Text(
                text = "tag: ${event.tag}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (changedParameters.isNotEmpty()) {
            Text(
                text = "changed: $changedParameters",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (unstableParameters.isNotEmpty()) {
            Text(
                text = "unstable: $unstableParameters",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
