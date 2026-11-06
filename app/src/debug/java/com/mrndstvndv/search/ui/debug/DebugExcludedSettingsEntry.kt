package com.mrndstvndv.search.ui.debug

import androidx.compose.runtime.Composable
import com.mrndstvndv.search.ui.components.settings.SettingsGroup
import com.mrndstvndv.search.ui.components.settings.SettingsNavigationRow

/** Row shown only in debug builds, linking to [ExcludedSettingsScreen]. */
@Composable
fun DebugExcludedSettingsEntry(onOpen: () -> Unit) {
    SettingsGroup {
        SettingsNavigationRow(
            title = "Excluded settings screens (debug)",
            subtitle = "Screens the scan hid, and why",
            onClick = onOpen,
        )
    }
}
