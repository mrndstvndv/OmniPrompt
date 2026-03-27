package com.mrndstvndv.search.provider.contacts

import android.content.Context
import android.provider.ContactsContract

/**
 * Represents a contact entry from the device's contact list.
 */
data class ContactEntry(
    val id: String,
    val lookupKey: String,
    val displayName: String,
    val phoneNumbers: List<PhoneNumber>,
    val photoUri: String?,
    val isStarred: Boolean
)

/**
 * Represents a phone number associated with a contact.
 */
data class PhoneNumber(
    val number: String,
    val type: Int,
    val label: String?
) {
    /**
     * Returns the platform-localized label for this phone number type.
     */
    fun getTypeLabel(context: Context): String {
        return ContactsContract.CommonDataKinds.Phone.getTypeLabel(
            context.resources,
            type,
            label,
        ).toString()
    }
}

/**
 * Represents a SIM card number from the device.
 */
data class SimNumber(
    val number: String,
    val displayName: String,
    val slotIndex: Int,
    val subscriptionId: Int
)
