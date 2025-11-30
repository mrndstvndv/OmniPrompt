package com.mrndstvndv.search.provider.system

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Settings
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.util.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: ProviderSettingsRepository,
    private val developerSettingsManager: DeveloperSettingsManager
) : Provider {

    override val id: String = "system-settings"
    override val displayName: String = "System Settings"

    @Suppress("DEPRECATION")
    private val settingsActions = listOf(
        "Settings" to Settings.ACTION_SETTINGS,
        "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        "Access Point Names" to Settings.ACTION_APN_SETTINGS,
        "Developer options" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
        "Apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "Battery Saver" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "Biometric Enrollment" to Settings.ACTION_BIOMETRIC_ENROLL,
        "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "Charging Control" to "org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS",
        "Captioning" to Settings.ACTION_CAPTIONING_SETTINGS,
        "Cast" to Settings.ACTION_CAST_SETTINGS,
        "Data Roaming" to Settings.ACTION_DATA_ROAMING_SETTINGS,
        "Data Usage" to Settings.ACTION_DATA_USAGE_SETTINGS,
        "Date & time" to Settings.ACTION_DATE_SETTINGS,
        "About phone" to Settings.ACTION_DEVICE_INFO_SETTINGS,
        "Display" to Settings.ACTION_DISPLAY_SETTINGS,
        "Screen saver" to Settings.ACTION_DREAM_SETTINGS,
        "Physical keyboard" to Settings.ACTION_HARD_KEYBOARD_SETTINGS,
        "Default home app" to Settings.ACTION_HOME_SETTINGS,
        "Battery optimization" to Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
        "On-screen keyboard" to Settings.ACTION_INPUT_METHOD_SETTINGS,
        "Input Method Subtype" to Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS,
        "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
        "Language" to Settings.ACTION_LOCALE_SETTINGS,
        "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "Default apps" to Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
        "Display over other apps" to Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "Install unknown apps" to Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "Modify system settings" to Settings.ACTION_MANAGE_WRITE_SETTINGS,
        "Memory card" to Settings.ACTION_MEMORY_CARD_SETTINGS,
        "Network operators" to Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
        "Android Beam" to Settings.ACTION_NFCSHARING_SETTINGS,
        "Tap & pay" to Settings.ACTION_NFC_PAYMENT_SETTINGS,
        "NFC" to Settings.ACTION_NFC_SETTINGS,
        "Night Light" to Settings.ACTION_NIGHT_DISPLAY_SETTINGS,
        "Notification Assistant" to Settings.ACTION_NOTIFICATION_ASSISTANT_SETTINGS,
        "Notification access" to Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        "Do Not Disturb access" to Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
        "Printing" to Settings.ACTION_PRINT_SETTINGS,
        "Privacy" to Settings.ACTION_PRIVACY_SETTINGS,
        "Quick Access Wallet" to Settings.ACTION_QUICK_ACCESS_WALLET_SETTINGS,
        "Quick Launch" to Settings.ACTION_QUICK_LAUNCH_SETTINGS,
        "Autofill Service" to Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
        "Search" to Settings.ACTION_SEARCH_SETTINGS,
        "Security" to Settings.ACTION_SECURITY_SETTINGS,
        "Sound & vibration" to Settings.ACTION_SOUND_SETTINGS,
        "Storage Volume Access" to Settings.ACTION_STORAGE_VOLUME_ACCESS_SETTINGS,
        "Accounts" to Settings.ACTION_SYNC_SETTINGS,
        "Usage access" to Settings.ACTION_USAGE_ACCESS_SETTINGS,
        "Personal dictionary" to Settings.ACTION_USER_DICTIONARY_SETTINGS,
        "Voice input" to Settings.ACTION_VOICE_INPUT_SETTINGS,
        "VPN" to Settings.ACTION_VPN_SETTINGS,
        "VR helper services" to Settings.ACTION_VR_LISTENER_SETTINGS,
        "WebView implementation" to Settings.ACTION_WEBVIEW_SETTINGS,
        "Wi-Fi IP Settings" to Settings.ACTION_WIFI_IP_SETTINGS,
        "Wi-Fi" to Settings.ACTION_WIFI_SETTINGS,
        "Wireless Debugging" to "android.settings.ADB_WIFI_SETTINGS",
        "Network & internet" to Settings.ACTION_WIRELESS_SETTINGS,
        "Do Not Disturb" to Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
    )

    // Keywords that trigger the developer toggle result
    private val developerToggleKeywords = listOf(
        "developer", "dev mode", "developer mode", "dev options", 
        "toggle developer", "enable developer", "disable developer"
    )

    override fun canHandle(query: Query): Boolean = true

    override suspend fun query(query: Query): List<ProviderResult> {
        val enabledProviders = settingsRepository.enabledProviders.value
        if (enabledProviders[id] == false) return emptyList()

        val normalized = query.trimmedText
        
        if (normalized.isBlank()) return emptyList()

        val results = mutableListOf<ProviderResult>()

        // Add developer toggle result if feature is enabled and query matches
        val systemSettings = settingsRepository.systemSettingsSettings.value
        if (systemSettings.developerToggleEnabled) {
            val matchesDeveloperToggle = developerToggleKeywords.any { keyword ->
                keyword.contains(normalized, ignoreCase = true) || 
                normalized.contains(keyword, ignoreCase = true)
            }
            
            if (matchesDeveloperToggle) {
                val permissionStatus = developerSettingsManager.permissionStatus.value
                val isCurrentlyEnabled = developerSettingsManager.isDeveloperSettingsEnabled()
                val toggleResult = buildDeveloperToggleResult(isCurrentlyEnabled, permissionStatus.isReady, normalized)
                if (toggleResult != null) {
                    results.add(toggleResult)
                }
            }
        }

        val currentPermissionStatus = developerSettingsManager.permissionStatus.value

        // Add regular settings results with fuzzy matching
        results.addAll(
            settingsActions.mapNotNull { (title, action) ->
                if (action == "android.settings.ADB_WIFI_SETTINGS" && currentPermissionStatus.availableMethod == DeveloperSettingsManager.PermissionMethod.NONE) {
                    null // Skip this action if neither root nor shizuku is available
                } else {
                    val matchResult = FuzzyMatcher.match(normalized, title)
                    if (matchResult != null) {
                        Triple(title, action, matchResult)
                    } else {
                        null
                    }
                }
            }.sortedByDescending { it.third.score }
            .map { (title, action, matchResult) ->
                ProviderResult(
                    id = "$id:$action",
                    title = title,
                    subtitle = "System Settings",
                    defaultVectorIcon = Icons.Outlined.Settings,
                    providerId = id,
                    onSelect = {
                        withContext(Dispatchers.Main) {
                            try {
                                val intent = Intent(action)
                                if (intent.resolveActivity(activity.packageManager) != null) {
                                    activity.startActivity(intent)
                                } else {
                                    // Fallback for Charging Control to Battery Saver settings
                                    if (action == "org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS") {
                                        val fallbackIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                        if (fallbackIntent.resolveActivity(activity.packageManager) != null) {
                                            activity.startActivity(fallbackIntent)
                                        }
                                    } else if (action == "android.settings.ADB_WIFI_SETTINGS") {
                                        val launched = withContext(Dispatchers.IO) {
                                            developerSettingsManager.launchWirelessDebugging()
                                        }
                                        
                                        if (!launched) {
                                            val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                            if (fallbackIntent.resolveActivity(activity.packageManager) != null) {
                                                Toast.makeText(activity, "Opening Developer Options", Toast.LENGTH_SHORT).show()
                                                activity.startActivity(fallbackIntent)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    keepOverlayUntilExit = true,
                    matchedTitleIndices = matchResult.matchedIndices
                )
            }
        )

        return results
    }

    private fun buildDeveloperToggleResult(isCurrentlyEnabled: Boolean, isReady: Boolean, query: String): ProviderResult? {
        val title = if (isCurrentlyEnabled) "Disable Developer Options" else "Enable Developer Options"
        val permissionStatus = developerSettingsManager.permissionStatus.value
        val subtitle = when {
            !isReady && permissionStatus.isShizukuAvailable && !permissionStatus.hasShizukuPermission -> 
                "Tap to grant Shizuku permission"
            !isReady -> 
                "No permission - run: adb shell pm grant ${activity.packageName} android.permission.WRITE_SECURE_SETTINGS"
            isCurrentlyEnabled -> "Turn off developer mode"
            else -> "Turn on developer mode"
        }
        
        // Get match indices for highlighting
        val titleMatch = FuzzyMatcher.match(query, title)
        val subtitleMatch = FuzzyMatcher.match(query, subtitle)
        
        return ProviderResult(
            id = "$id:developer-toggle",
            title = title,
            subtitle = subtitle,
            defaultVectorIcon = Icons.Outlined.DeveloperMode,
            providerId = id,
            onSelect = {
                val currentStatus = developerSettingsManager.permissionStatus.value
                when {
                    currentStatus.isReady -> {
                        withContext(Dispatchers.IO) {
                            val newState = !isCurrentlyEnabled
                            val success = developerSettingsManager.setDeveloperSettingsEnabled(newState)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    val message = if (newState) "Developer options enabled" else "Developer options disabled"
                                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                                    activity.finish()
                                } else {
                                    Toast.makeText(activity, "Failed to toggle developer options", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    currentStatus.isShizukuAvailable && !currentStatus.hasShizukuPermission -> {
                        // Request Shizuku permission
                        withContext(Dispatchers.Main) {
                            val requested = developerSettingsManager.requestShizukuPermission()
                            if (!requested) {
                                Toast.makeText(activity, "Could not request Shizuku permission", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                activity,
                                "Grant permission via ADB: adb shell pm grant ${activity.packageName} android.permission.WRITE_SECURE_SETTINGS",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            keepOverlayUntilExit = true,
            matchedTitleIndices = titleMatch?.matchedIndices ?: emptyList(),
            matchedSubtitleIndices = subtitleMatch?.matchedIndices ?: emptyList()
        )
    }
}
