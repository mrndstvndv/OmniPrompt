package com.mrndstvndv.search.ui.components.settings

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun <T> SettingsSingleChoiceSegmentedButtons(
    options: List<T>,
    selectedOption: T,
    enabled: Boolean,
    label: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    showSelectedIcon: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selectedOption,
                onClick = {
                    if (enabled && option != selectedOption) {
                        onOptionSelected(option)
                    }
                },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = if (showSelectedIcon) {
                    { SegmentedButtonDefaults.Icon(active = option == selectedOption) }
                } else {
                    {}
                },
                label = { Text(text = label(option)) },
            )
        }
    }
}
