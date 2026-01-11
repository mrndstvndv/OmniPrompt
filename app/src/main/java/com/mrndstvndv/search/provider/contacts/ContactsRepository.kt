package com.mrndstvndv.search.provider.contacts

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Repository for accessing device contacts and SIM card information.
 */
class ContactsRepository(
    private val context: Context,
) {
    private val contentResolver = context.contentResolver
    private var cachedContacts: List<ContactEntry>? = null
    private val cacheMutex = Mutex()
    private val photoCache = mutableMapOf<String, Bitmap?>()

    /**
     * Checks if the app has READ_CONTACTS permission.
     */
    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Checks if the app has phone state permissions for reading SIM numbers.
     */
    fun hasPhoneStatePermission(): Boolean {
        val hasReadPhoneState =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) == PackageManager.PERMISSION_GRANTED

        // On Android 8.0+, also need READ_PHONE_NUMBERS
        val hasReadPhoneNumbers =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_NUMBERS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        return hasReadPhoneState && hasReadPhoneNumbers
    }

    /**
     * Loads all contacts from the device. Results are cached.
     */
    suspend fun loadContacts(forceRefresh: Boolean = false): List<ContactEntry> {
        if (!hasContactsPermission()) return emptyList()

        cacheMutex.withLock {
            if (!forceRefresh && cachedContacts != null) {
                return cachedContacts!!
            }
        }

        val contacts =
            withContext(Dispatchers.IO) {
                val contactsMap = mutableMapOf<String, ContactEntry>()

                // First, query all contacts
                val contactsCursor =
                    contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        arrayOf(
                            ContactsContract.Contacts._ID,
                            ContactsContract.Contacts.LOOKUP_KEY,
                            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                            ContactsContract.Contacts.PHOTO_URI,
                            ContactsContract.Contacts.STARRED,
                            ContactsContract.Contacts.HAS_PHONE_NUMBER,
                        ),
                        null,
                        null,
                        "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
                    )

                contactsCursor?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val lookupKeyIndex = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                    val displayNameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val photoUriIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    val starredIndex = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
                    val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex) ?: continue
                        val displayName = cursor.getString(displayNameIndex) ?: continue
                        val lookupKey = cursor.getString(lookupKeyIndex) ?: ""
                        val photoUri = cursor.getString(photoUriIndex)
                        val isStarred = cursor.getInt(starredIndex) == 1
                        val hasPhone = cursor.getInt(hasPhoneIndex) > 0

                        contactsMap[id] =
                            ContactEntry(
                                id = id,
                                lookupKey = lookupKey,
                                displayName = displayName,
                                phoneNumbers = emptyList(),
                                photoUri = photoUri,
                                isStarred = isStarred,
                            )
                    }
                }

                // Now query phone numbers for all contacts
                val phonesCursor =
                    contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.LABEL,
                        ),
                        null,
                        null,
                        null,
                    )

                val phoneNumbersMap = mutableMapOf<String, MutableList<PhoneNumber>>()

                phonesCursor?.use { cursor ->
                    val contactIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                    val labelIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                    while (cursor.moveToNext()) {
                        val contactId = cursor.getString(contactIdIndex) ?: continue
                        val number = cursor.getString(numberIndex) ?: continue
                        val type = cursor.getInt(typeIndex)
                        val label = cursor.getString(labelIndex)

                        val phoneNumber =
                            PhoneNumber(
                                number = number,
                                type = type,
                                label = label,
                            )

                        phoneNumbersMap.getOrPut(contactId) { mutableListOf() }.add(phoneNumber)
                    }
                }

                // Merge phone numbers into contacts
                contactsMap
                    .mapValues { (id, contact) ->
                        contact.copy(phoneNumbers = phoneNumbersMap[id] ?: emptyList())
                    }.values
                    .toList()
            }

        cacheMutex.withLock {
            cachedContacts = contacts
        }

        return contacts
    }

    /**
     * Retrieves SIM card numbers if permissions are granted and available.
     * Note: Many carriers don't populate the phone number field, so this may return empty.
     */
    suspend fun getSimNumbers(): List<SimNumber> {
        if (!hasPhoneStatePermission()) return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val subscriptionManager =
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                            ?: return@withContext emptyList()

                    val subscriptions =
                        try {
                            subscriptionManager.activeSubscriptionInfoList ?: emptyList()
                        } catch (e: SecurityException) {
                            emptyList()
                        }

                    subscriptions.mapNotNull { info ->
                        val number =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                subscriptionManager.getPhoneNumber(info.subscriptionId)
                            } else {
                                @Suppress("DEPRECATION")
                                info.number
                            }
                        if (number.isNullOrBlank()) return@mapNotNull null

                        SimNumber(
                            number = number,
                            displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                            slotIndex = info.simSlotIndex,
                            subscriptionId = info.subscriptionId,
                        )
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * Loads a contact's photo as a Bitmap.
     */
    suspend fun loadContactPhoto(photoUri: String?): Bitmap? {
        if (photoUri == null) return null

        // Check cache first
        photoCache[photoUri]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(photoUri)
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                photoCache[photoUri] = bitmap
                bitmap
            } catch (e: Exception) {
                photoCache[photoUri] = null
                null
            }
        }
    }

    /**
     * Loads a contact's photo thumbnail using the contact ID.
     */
    suspend fun loadContactThumbnail(contactId: String): Bitmap? {
        val cacheKey = "thumb:$contactId"
        photoCache[cacheKey]?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val contactUri =
                    ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contactId.toLong(),
                    )
                val inputStream =
                    ContactsContract.Contacts.openContactPhotoInputStream(
                        contentResolver,
                        contactUri,
                        false, // preferHighRes = false for thumbnail
                    )
                val bitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                photoCache[cacheKey] = bitmap
                bitmap
            } catch (e: Exception) {
                photoCache[cacheKey] = null
                null
            }
        }
    }

    /**
     * Clears the cached contacts, forcing a refresh on next load.
     */
    suspend fun invalidateCache() {
        cacheMutex.withLock {
            cachedContacts = null
        }
    }

    /**
     * Clears the photo cache.
     */
    fun clearPhotoCache() {
        photoCache.clear()
    }

    companion object {
        @Volatile
        private var instance: ContactsRepository? = null

        fun getInstance(context: Context): ContactsRepository =
            instance ?: synchronized(this) {
                instance ?: ContactsRepository(context.applicationContext).also { instance = it }
            }
    }
}
