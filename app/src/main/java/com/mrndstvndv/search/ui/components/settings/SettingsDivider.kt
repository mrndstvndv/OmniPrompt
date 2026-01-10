package com.mrndstvndv.search.ui.components.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.then(Modifier.padding(horizontal = 20.dp)),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
