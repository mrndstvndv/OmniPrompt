package com.mrndstvndv.search.provider.contacts

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.util.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider for searching device contacts.
 */
class ContactsProvider(
    private val settingsRepository: ProviderSettingsRepository,
    private val contactsRepository: ContactsRepository
) : Provider {

    override val id: String = "contacts"
    override val displayName: String = "Contacts"

    override fun canHandle(query: Query): Boolean {
        // Only handle if provider is enabled and has permission
        val isEnabled = settingsRepository.enabledProviders.value[id] ?: false
        return isEnabled && contactsRepository.hasContactsPermission()
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        val normalized = query.trimmedText
        val settings = settingsRepository.contactsSettings.value
        val includePhoneNumbers = settings.includePhoneNumbers

        val contacts = contactsRepository.loadContacts()
        if (contacts.isEmpty()) return emptyList()

        val results = mutableListOf<ScoredContact>()

        // Also include SIM numbers if enabled and we have permission
        if (settings.showSimNumbers && contactsRepository.hasPhoneStatePermission()) {
            val simNumbers = contactsRepository.getSimNumbers()
            for (sim in simNumbers) {
                // Use full title for matching so "my number" matches all SIMs
                val fullTitle = "My Number (${sim.displayName})"
                val match = if (normalized.isBlank()) {
                    ScoredContact(
                        contact = null,
                        simNumber = sim,
                        score = 0,
                        matchedTitleIndices = emptyList(),
                        matchedSubtitleIndices = emptyList()
                    )
                } else {
                    val nameMatch = FuzzyMatcher.match(normalized, fullTitle)
                    val numberMatch = FuzzyMatcher.match(normalized, sim.number)

                    val bestMatch = listOfNotNull(nameMatch, numberMatch).maxByOrNull { it.score }
                    if (bestMatch != null) {
                        val isNameMatch = nameMatch != null && nameMatch.score >= (numberMatch?.score ?: 0)
                        ScoredContact(
                            contact = null,
                            simNumber = sim,
                            score = bestMatch.score,
                            matchedTitleIndices = if (isNameMatch) nameMatch?.matchedIndices ?: emptyList() else emptyList(),
                            matchedSubtitleIndices = if (!isNameMatch) numberMatch?.matchedIndices ?: emptyList() else emptyList()
                        )
                    } else null
                }
                if (match != null) results.add(match)
            }
        }

        // Search contacts
        for (contact in contacts) {
            val match = if (normalized.isBlank()) {
                // Return all contacts with zero score when query is empty
                ScoredContact(
                    contact = contact,
                    simNumber = null,
                    score = 0,
                    matchedTitleIndices = emptyList(),
                    matchedSubtitleIndices = emptyList()
                )
            } else {
                // Fuzzy match on name
                val nameMatch = FuzzyMatcher.match(normalized, contact.displayName)

                // Optionally fuzzy match on phone numbers
                val phoneMatch = if (includePhoneNumbers && contact.phoneNumbers.isNotEmpty()) {
                    contact.phoneNumbers
                        .mapNotNull { phone -> FuzzyMatcher.match(normalized, phone.number) }
                        .maxByOrNull { it.score }
                } else null

                // Calculate the best match
                val bestMatch = listOfNotNull(nameMatch, phoneMatch).maxByOrNull { it.score }

                if (bestMatch != null) {
                    val isNameMatch = nameMatch != null && nameMatch.score >= (phoneMatch?.score ?: 0)
                    ScoredContact(
                        contact = contact,
                        simNumber = null,
                        score = bestMatch.score,
                        matchedTitleIndices = if (isNameMatch) nameMatch?.matchedIndices ?: emptyList() else emptyList(),
                        matchedSubtitleIndices = if (!isNameMatch) phoneMatch?.matchedIndices ?: emptyList() else emptyList()
                    )
                } else null
            }

            if (match != null) results.add(match)
        }

        // Sort by score (descending), then by starred status, then alphabetically
        val sorted = results.sortedWith(
            compareByDescending<ScoredContact> { it.score }
                .thenByDescending { it.contact?.isStarred == true }
                .thenBy { it.contact?.displayName ?: it.simNumber?.displayName ?: "" }
        )

        return sorted.take(MAX_RESULTS).map { scored ->
            if (scored.simNumber != null) {
                buildSimNumberResult(scored)
            } else {
                buildContactResult(scored)
            }
        }
    }

    private fun buildContactResult(scored: ScoredContact): ProviderResult {
        val contact = scored.contact!!
        val primaryNumber = contact.phoneNumbers.firstOrNull()?.number
        val subtitle = primaryNumber ?: "No phone number"

        return ProviderResult(
            id = "$id:${contact.id}",
            title = contact.displayName,
            subtitle = subtitle,
            icon = null,
            defaultVectorIcon = Icons.Outlined.Person,
            iconLoader = { contactsRepository.loadContactThumbnail(contact.id) },
            providerId = id,
            extras = mapOf(
                EXTRA_CONTACT_ID to contact.id,
                EXTRA_LOOKUP_KEY to contact.lookupKey,
                EXTRA_PHONE_NUMBERS to contact.phoneNumbers,
                EXTRA_DISPLAY_NAME to contact.displayName,
                EXTRA_IS_SIM_NUMBER to false
            ),
            onSelect = null, // Handled specially in MainActivity to show action sheet
            matchedTitleIndices = scored.matchedTitleIndices,
            matchedSubtitleIndices = scored.matchedSubtitleIndices
        )
    }

    private fun buildSimNumberResult(scored: ScoredContact): ProviderResult {
        val sim = scored.simNumber!!

        return ProviderResult(
            id = "$id:sim:${sim.subscriptionId}",
            title = "My Number (${sim.displayName})",
            subtitle = sim.number,
            icon = null,
            defaultVectorIcon = Icons.Outlined.Person,
            iconLoader = null,
            providerId = id,
            extras = mapOf(
                EXTRA_PHONE_NUMBERS to listOf(
                    PhoneNumber(
                        number = sim.number,
                        type = 0,
                        label = sim.displayName
                    )
                ),
                EXTRA_DISPLAY_NAME to "My Number (${sim.displayName})",
                EXTRA_IS_SIM_NUMBER to true
            ),
            onSelect = null, // Handled specially in MainActivity to show action sheet
            matchedTitleIndices = scored.matchedTitleIndices,
            matchedSubtitleIndices = scored.matchedSubtitleIndices
        )
    }

    private data class ScoredContact(
        val contact: ContactEntry?,
        val simNumber: SimNumber?,
        val score: Int,
        val matchedTitleIndices: List<Int>,
        val matchedSubtitleIndices: List<Int>
    )

    companion object {
        private const val MAX_RESULTS = 30
        const val EXTRA_CONTACT_ID = "contacts.contactId"
        const val EXTRA_LOOKUP_KEY = "contacts.lookupKey"
        const val EXTRA_PHONE_NUMBERS = "contacts.phoneNumbers"
        const val EXTRA_DISPLAY_NAME = "contacts.displayName"
        const val EXTRA_IS_SIM_NUMBER = "contacts.isSimNumber"
    }
}
