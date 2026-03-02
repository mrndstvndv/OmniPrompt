package com.mrndstvndv.search.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ContentDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScrimDialog(
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val maxDialogHeight = maxHeight * 0.9f
            val contentMaxHeight = (maxDialogHeight - 200.dp).coerceAtLeast(0.dp)
            val scrollState = rememberScrollState()
            val canScroll = contentMaxHeight > 0.dp
            val contentModifier =
                if (canScroll) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = contentMaxHeight)
                        .verticalScroll(scrollState)
                } else {
                    Modifier.fillMaxWidth()
                }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxDialogHeight)
                        .padding(24.dp),
            ) {
                title()
                Spacer(modifier = Modifier.height(16.dp))

                Box {
                    Column(modifier = contentModifier, content = content)

                    if (canScroll && scrollState.maxValue > 0) {
                        if (scrollState.value > 0) {
                            HorizontalDivider(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }

                        if (scrollState.value < scrollState.maxValue) {
                            HorizontalDivider(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter),
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    content = buttons,
                )
            }
        }
    }
}
