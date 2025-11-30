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

    private data class SettingsActionItem(
        val title: String,
        val action: String,
        val requiresElevatedPermission: Boolean = false,
        val onLaunch: (suspend () -> Unit)? = null
    )

    private val settingsActions by lazy {
        listOf(
            SettingsActionItem("Settings", Settings.ACTION_SETTINGS),
            SettingsActionItem("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
            SettingsActionItem("Access Point Names", Settings.ACTION_APN_SETTINGS),
            SettingsActionItem("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            SettingsActionItem("Apps", Settings.ACTION_APPLICATION_SETTINGS),
            SettingsActionItem("Battery Saver", Settings.ACTION_BATTERY_SAVER_SETTINGS),
            SettingsActionItem("Biometric Enrollment", Settings.ACTION_BIOMETRIC_ENROLL),
            SettingsActionItem("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
            SettingsActionItem("Charging Control", "org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS", onLaunch = {
                val intent = Intent("org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS")
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(intent)
                } else {
                    val fallback = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    if (fallback.resolveActivity(activity.packageManager) != null) {
                        activity.startActivity(fallback)
                    }
                }
            }),
            SettingsActionItem("Captioning", Settings.ACTION_CAPTIONING_SETTINGS),
            SettingsActionItem("Cast", Settings.ACTION_CAST_SETTINGS),
            SettingsActionItem("Data Roaming", Settings.ACTION_DATA_ROAMING_SETTINGS),
            SettingsActionItem("Data Usage", Settings.ACTION_DATA_USAGE_SETTINGS),
            SettingsActionItem("Date & time", Settings.ACTION_DATE_SETTINGS),
            SettingsActionItem("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS),
            SettingsActionItem("Display", Settings.ACTION_DISPLAY_SETTINGS),
            SettingsActionItem("Screen saver", Settings.ACTION_DREAM_SETTINGS),
            SettingsActionItem("Physical keyboard", Settings.ACTION_HARD_KEYBOARD_SETTINGS),
            SettingsActionItem("Default home app", Settings.ACTION_HOME_SETTINGS),
            SettingsActionItem("Battery optimization", Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            SettingsActionItem("On-screen keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS),
            SettingsActionItem("Input Method Subtype", Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS),
            SettingsActionItem("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            SettingsActionItem("Language", Settings.ACTION_LOCALE_SETTINGS),
            SettingsActionItem("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            SettingsActionItem("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            SettingsActionItem("Display over other apps", Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            SettingsActionItem("Install unknown apps", Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            SettingsActionItem("Modify system settings", Settings.ACTION_MANAGE_WRITE_SETTINGS),
            SettingsActionItem("Memory card", Settings.ACTION_MEMORY_CARD_SETTINGS),
            SettingsActionItem("Network operators", Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            SettingsActionItem("Android Beam", Settings.ACTION_NFCSHARING_SETTINGS),
            SettingsActionItem("Tap & pay", Settings.ACTION_NFC_PAYMENT_SETTINGS),
            SettingsActionItem("NFC", Settings.ACTION_NFC_SETTINGS),
            SettingsActionItem("Night Light", Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
            SettingsActionItem("Notification Assistant", Settings.ACTION_NOTIFICATION_ASSISTANT_SETTINGS),
            SettingsActionItem("Notification access", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            SettingsActionItem("Do Not Disturb access", Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            SettingsActionItem("Printing", Settings.ACTION_PRINT_SETTINGS),
            SettingsActionItem("Privacy", Settings.ACTION_PRIVACY_SETTINGS),
            SettingsActionItem("Quick Access Wallet", Settings.ACTION_QUICK_ACCESS_WALLET_SETTINGS),
            SettingsActionItem("Quick Launch", Settings.ACTION_QUICK_LAUNCH_SETTINGS),
            SettingsActionItem("Autofill Service", Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE),
            SettingsActionItem("Search", Settings.ACTION_SEARCH_SETTINGS),
            SettingsActionItem("Security", Settings.ACTION_SECURITY_SETTINGS),
            SettingsActionItem("Sound & vibration", Settings.ACTION_SOUND_SETTINGS),
            SettingsActionItem("Accounts", Settings.ACTION_SYNC_SETTINGS),
            SettingsActionItem("Usage access", Settings.ACTION_USAGE_ACCESS_SETTINGS),
            SettingsActionItem("Personal dictionary", Settings.ACTION_USER_DICTIONARY_SETTINGS),
            SettingsActionItem("Voice input", Settings.ACTION_VOICE_INPUT_SETTINGS),
            SettingsActionItem("VPN", Settings.ACTION_VPN_SETTINGS),
            SettingsActionItem("VR helper services", Settings.ACTION_VR_LISTENER_SETTINGS),
            SettingsActionItem("WebView implementation", Settings.ACTION_WEBVIEW_SETTINGS),
            SettingsActionItem("Wi-Fi IP Settings", Settings.ACTION_WIFI_IP_SETTINGS),
            SettingsActionItem("Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
            SettingsActionItem("Network & internet", Settings.ACTION_WIRELESS_SETTINGS),
            SettingsActionItem("Do Not Disturb", Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),

            // Privileged Actions
            SettingsActionItem(
                "Wireless Debugging", 
                "android.settings.ADB_WIFI_SETTINGS", 
                requiresElevatedPermission = true,
                onLaunch = {
                    val launched = withContext(Dispatchers.IO) {
                        developerSettingsManager.launchWirelessDebugging()
                    }
                    if (!launched) openDevOptionsFallback()
                }
            ),
            SettingsActionItem(
                "USB Debugging", 
                "action.custom.USB_DEBUGGING", 
                requiresElevatedPermission = true,
                onLaunch = {
                    val launched = withContext(Dispatchers.IO) {
                        developerSettingsManager.launchUsbDebugging()
                    }
                    if (!launched) openDevOptionsFallback()
                }
            )
        )
    }

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
            settingsActions.mapNotNull { item ->
                if (item.requiresElevatedPermission && currentPermissionStatus.availableMethod == DeveloperSettingsManager.PermissionMethod.NONE) {
                    null // Skip this action if neither root nor shizuku is available
                } else {
                    val matchResult = FuzzyMatcher.match(normalized, item.title)
                    if (matchResult != null) {
                        Pair(item, matchResult)
                    } else {
                        null
                    }
                }
            }.sortedByDescending { it.second.score }
            .map { (item, matchResult) ->
                ProviderResult(
                    id = "$id:${item.action}",
                    title = item.title,
                    subtitle = "System Settings",
                    defaultVectorIcon = Icons.Outlined.Settings,
                    providerId = id,
                    onSelect = {
                        withContext(Dispatchers.Main) {
                            try {
                                if (item.onLaunch != null) {
                                    item.onLaunch.invoke()
                                } else {
                                    val intent = Intent(item.action)
                                    if (intent.resolveActivity(activity.packageManager) != null) {
                                        activity.startActivity(intent)
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

    private fun openDevOptionsFallback() {
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        if (fallbackIntent.resolveActivity(activity.packageManager) != null) {
            Toast.makeText(activity, "Opening Developer Options", Toast.LENGTH_SHORT).show()
            activity.startActivity(fallbackIntent)
        }
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