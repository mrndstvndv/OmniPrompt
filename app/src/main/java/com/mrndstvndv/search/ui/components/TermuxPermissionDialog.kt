package com.mrndstvndv.search.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TermuxPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ContentDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        },
        content = {
            Text(
                text = "To run Termux commands, you need to grant the RUN_COMMAND permission.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Go to App Info > Permissions > Additional permissions and enable 'Run commands in Termux environment'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
