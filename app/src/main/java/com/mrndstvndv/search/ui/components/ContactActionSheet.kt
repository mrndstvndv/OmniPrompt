package com.mrndstvndv.search.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mrndstvndv.search.provider.contacts.PhoneNumber

/**
 * Data class representing a contact for the action sheet.
 */
data class ContactActionData(
    val contactId: String?,
    val lookupKey: String?,
    val displayName: String,
    val phoneNumbers: List<PhoneNumber>,
    val isSimNumber: Boolean
)

/**
 * Bottom sheet showing actions for a selected contact.
 */
@Composable
fun ContactActionSheet(
    contact: ContactActionData,
    onDismiss: () -> Unit,
    onActionComplete: () -> Unit
) {
    val context = LocalContext.current

    BottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header with contact name
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.phoneNumbers.isNotEmpty()) {
                    val primaryNumber = contact.phoneNumbers.first()
                    Text(
                        text = primaryNumber.number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // If contact has multiple phone numbers, show each with its actions
            if (contact.phoneNumbers.size > 1) {
                contact.phoneNumbers.forEach { phoneNumber ->
                    PhoneNumberSection(
                        phoneNumber = phoneNumber,
                        context = context,
                        onActionComplete = onActionComplete
                    )
                }
            } else if (contact.phoneNumbers.isNotEmpty()) {
                // Single phone number - show actions directly
                val phoneNumber = contact.phoneNumbers.first()
                
                ActionRow(
                    icon = Icons.Outlined.Call,
                    label = "Call",
                    onClick = {
                        dialNumber(context, phoneNumber.number)
                        onActionComplete()
                    }
                )

                ActionRow(
                    icon = Icons.AutoMirrored.Outlined.Message,
                    label = "Message",
                    onClick = {
                        sendMessage(context, phoneNumber.number)
                        onActionComplete()
                    }
                )

                ActionRow(
                    icon = Icons.Outlined.ContentCopy,
                    label = "Copy number",
                    onClick = {
                        copyToClipboard(context, phoneNumber.number)
                        onActionComplete()
                    }
                )
            }

            // View contact option (only for real contacts, not SIM numbers)
            if (!contact.isSimNumber && contact.contactId != null && contact.lookupKey != null) {
                if (contact.phoneNumbers.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                ActionRow(
                    icon = Icons.Outlined.Person,
                    label = "View contact",
                    onClick = {
                        viewContact(context, contact.contactId, contact.lookupKey)
                        onActionComplete()
                    }
                )
            }
        }
    }
}

@Composable
private fun PhoneNumberSection(
    phoneNumber: PhoneNumber,
    context: Context,
    onActionComplete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Phone number header
        Text(
            text = "${phoneNumber.getTypeLabel()}: ${phoneNumber.number}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        ActionRow(
            icon = Icons.Outlined.Call,
            label = "Call",
            onClick = {
                dialNumber(context, phoneNumber.number)
                onActionComplete()
            }
        )

        ActionRow(
            icon = Icons.AutoMirrored.Outlined.Message,
            label = "Message",
            onClick = {
                sendMessage(context, phoneNumber.number)
                onActionComplete()
            }
        )

        ActionRow(
            icon = Icons.Outlined.ContentCopy,
            label = "Copy number",
            onClick = {
                copyToClipboard(context, phoneNumber.number)
                onActionComplete()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun dialNumber(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:${Uri.encode(number)}")
    }
    context.startActivity(intent)
}

private fun sendMessage(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${Uri.encode(number)}")
    }
    context.startActivity(intent)
}

private fun copyToClipboard(context: Context, number: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Phone number", number)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
}

private fun viewContact(context: Context, contactId: String, lookupKey: String) {
    val contactUri = ContactsContract.Contacts.getLookupUri(contactId.toLong(), lookupKey)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = contactUri
    }
    context.startActivity(intent)
}
