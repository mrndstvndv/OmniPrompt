package com.mrndstvndv.search.provider.contacts

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
     * Returns a human-readable label for this phone number type.
     */
    fun getTypeLabel(): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Work Fax"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Home Fax"
            ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Pager"
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> label ?: "Custom"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "Work Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER -> "Work Pager"
            ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT -> "Assistant"
            ContactsContract.CommonDataKinds.Phone.TYPE_MMS -> "MMS"
            else -> "Phone"
        }
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
