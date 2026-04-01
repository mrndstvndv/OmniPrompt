package com.mrndstvndv.search.ui.components

import android.graphics.Bitmap
import android.view.KeyEvent as AndroidKeyEvent
import android.view.inputmethod.InputConnectionWrapper
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.model.SearchTrigger
import com.mrndstvndv.search.provider.model.TriggerMatch

/**
 * State representing an active trigger in the search field.
 */
data class TriggerState(
    /** The active trigger entry. */
    val trigger: SearchTrigger,
    /** The exact token that activated this trigger. */
    val matchedToken: String,
    /** The payload text (everything after the trigger + space). */
    val payload: String,
)

/**
 * Find the best trigger match across all trigger entries for a given first token.
 */
fun findTriggerMatch(
    firstToken: String,
    triggers: List<SearchTrigger>,
): TriggerMatch? {
    if (firstToken.isBlank() || triggers.isEmpty()) return null
    var bestMatch: TriggerMatch? = null
    for (trigger in triggers) {
        val match = trigger.match(firstToken) ?: continue
        if (bestMatch == null || match.score > bestMatch.score) {
            bestMatch = match
        }
    }
    return bestMatch
}

/**
 * Search field with optional trigger chip prefix.
 *
 * Always renders a single [BasicTextField] and applies Material3 text-field
 * decoration around it so the IME/editor state stays stable when the trigger
 * chip appears or disappears.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    onBackspaceAtStart: (() -> Unit)? = null,
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
    val latestValue = rememberUpdatedState(value)
    val latestOnBackspaceAtStart = rememberUpdatedState(onBackspaceAtStart)

    fun canDismissTriggerWithBackspace(currentValue: TextFieldValue): Boolean {
        if (currentValue.text.isNotEmpty()) return false
        val selection = currentValue.selection
        return selection.start == 0 && selection.end == 0
    }

    val effectiveModifier = modifier.onPreviewKeyEvent { event ->
        val callback = latestOnBackspaceAtStart.value ?: return@onPreviewKeyEvent false
        if (event.key != Key.Backspace) return@onPreviewKeyEvent false
        if (!canDismissTriggerWithBackspace(latestValue.value)) return@onPreviewKeyEvent false
        callback()
        true
    }

    val textField: @Composable () -> Unit = {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = effectiveModifier,
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

    // Keep the platform text input interceptor mounted at all times so the
    // underlying input connection stays stable when trigger mode toggles.
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            val wrappedRequest = PlatformTextInputMethodRequest { outAttributes ->
                val inputConnection = request.createInputConnection(outAttributes)
                val view = nextHandler.view

                object : InputConnectionWrapper(inputConnection, false) {
                    private fun handleBackspace(): Boolean {
                        val callback = latestOnBackspaceAtStart.value ?: return false
                        if (!canDismissTriggerWithBackspace(latestValue.value)) return false
                        view.post(callback)
                        return true
                    }

                    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                        if (beforeLength > 0 && afterLength == 0 && handleBackspace()) return true
                        return super.deleteSurroundingText(beforeLength, afterLength)
                    }

                    override fun deleteSurroundingTextInCodePoints(
                        beforeLength: Int,
                        afterLength: Int,
                    ): Boolean {
                        if (beforeLength > 0 && afterLength == 0 && handleBackspace()) return true
                        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                    }

                    override fun sendKeyEvent(event: AndroidKeyEvent): Boolean {
                        if (
                            event.action == AndroidKeyEvent.ACTION_DOWN &&
                                event.keyCode == AndroidKeyEvent.KEYCODE_DEL &&
                                handleBackspace()
                        ) {
                            return true
                        }
                        return super.sendKeyEvent(event)
                    }
                }
            }
            nextHandler.startInputMethod(wrappedRequest)
        }
    }

    InterceptPlatformTextInput(interceptor) {
        textField()
    }
}

/**
 * A chip displaying a trigger item, used as prefix in the search field.
 */
@Composable
fun TriggerChip(
    item: SearchTrigger,
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
