package com.mrndstvndv.search.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.TriggerProvider
import com.mrndstvndv.search.provider.model.TriggerItem

/**
 * State representing an active trigger in the search field.
 */
data class TriggerState(
    /** The provider that owns this trigger. */
    val provider: TriggerProvider,
    /** The matched trigger item. */
    val item: TriggerItem,
    /** The payload text (everything after the trigger + space). */
    val payload: String,
)

/**
 * Find the best trigger match across all providers for a given first token.
 * Returns (provider, item) pair or null.
 */
fun findTriggerMatch(
    firstToken: String,
    triggerProviders: List<TriggerProvider>,
): Pair<TriggerProvider, TriggerItem>? {
    if (firstToken.isBlank() || triggerProviders.isEmpty()) return null
    var best: Pair<TriggerProvider, TriggerItem>? = null
    var bestScore = 0
    for (provider in triggerProviders) {
        val match = provider.matchTrigger(firstToken) ?: continue
        if (match.score > bestScore) {
            bestScore = match.score
            best = provider to match.item
        }
    }
    return best
}

/**
 * Search field with optional trigger chip prefix.
 *
 * Always renders a single [BasicTextField] and applies Material3 text-field
 * decoration around it so the IME/editor state stays stable when the trigger
 * chip appears or disappears.
 */
@Composable
fun SearchField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    triggerChip: (@Composable () -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    val colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )
    val isFocused by interactionSource.collectIsFocusedAsState()
    val textColor = colors.textColor(enabled = true, isError = false, focused = isFocused)
    val placeholderColor = colors.placeholderColor(enabled = true, isError = false, focused = isFocused)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = textColor,
        ),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                TextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interactionSource,
                    modifier = Modifier.fillMaxSize(),
                    colors = colors,
                    shape = shape,
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    triggerChip?.let { chip ->
                        chip()
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.text.isEmpty()) {
                            CompositionLocalProvider(LocalContentColor provides placeholderColor) {
                                placeholder?.invoke()
                            }
                        }
                        innerTextField()
                    }

                    trailingIcon?.let {
                        Spacer(modifier = Modifier.width(4.dp))
                        it()
                    }
                }
            }
        },
    )
}

/**
 * A chip displaying a trigger item, used as prefix in the search field.
 */
@Composable
fun TriggerChip(
    item: TriggerItem,
    onDismiss: () -> Unit,
) {
    var iconBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.id) {
        iconBitmap = item.iconLoader?.invoke()
    }

    Surface(
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon
            when {
                iconBitmap != null -> {
                    Image(
                        bitmap = iconBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                item.vectorIcon != null -> {
                    Icon(
                        imageVector = item.vectorIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }

            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
            )

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Remove trigger",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
