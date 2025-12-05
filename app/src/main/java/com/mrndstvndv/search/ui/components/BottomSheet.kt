package com.mrndstvndv.search.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mrndstvndv.search.ui.theme.LocalMotionPreferences
import com.mrndstvndv.search.ui.theme.SearchTheme
import kotlin.math.roundToInt

/**
 * A custom bottom sheet composable that properly inherits the app theme.
 * 
 * This is used instead of Material3's ModalBottomSheet which has issues
 * with theme inheritance in certain scenarios (Dialog creates a new window).
 * 
 * The component captures the current motion preferences and re-applies
 * SearchTheme inside the dialog to ensure proper theming.
 *
 * @param onDismiss Called when the sheet should be dismissed (scrim tap, swipe down, or back press)
 * @param content The composable content to display inside the bottom sheet
 */
@Composable
fun BottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // Capture motion preferences from parent composition before entering Dialog
    val motionPreferences = LocalMotionPreferences.current
    
    var isVisible by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    
    // Dismiss threshold: if dragged more than 30% of sheet height, dismiss
    val dismissThreshold = sheetHeight * 0.3f
    
    // Animate the sheet entrance
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    val animatedOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else sheetHeight,
        animationSpec = tween(durationMillis = 250),
        label = "sheetOffset"
    )
    
    // Scrim alpha animation
    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.5f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrimAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Re-apply SearchTheme inside the dialog to inherit proper colors
        SearchTheme(motionPreferences = motionPreferences) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            sheetHeight = size.height.toFloat()
                        }
                        .offset {
                            IntOffset(0, (animatedOffset + dragOffset).roundToInt())
                        }
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (dragOffset > dismissThreshold) {
                                        onDismiss()
                                    } else {
                                        dragOffset = 0f
                                    }
                                },
                                onDragCancel = {
                                    dragOffset = 0f
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    // Only allow dragging down (positive dragAmount)
                                    val newOffset = dragOffset + dragAmount
                                    dragOffset = newOffset.coerceAtLeast(0f)
                                }
                            )
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            enabled = false
                        ) { },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(width = 32.dp, height = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = MaterialTheme.shapes.extraLarge
                            ) {}
                        }
                        
                        // Sheet content
                        content()
                    }
                }
            }
        }
    }
}
