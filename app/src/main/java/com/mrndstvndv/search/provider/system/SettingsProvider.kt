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
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.SystemSettingsSettings
import com.mrndstvndv.search.util.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import com.mrndstvndv.search.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

class SettingsProvider(
    private val activity: ComponentActivity,
    private val globalSettingsRepository: ProviderSettingsRepository,
    private val settingsRepository: SettingsRepository<SystemSettingsSettings>,
    private val developerSettingsManager: DeveloperSettingsManager
) : Provider {

    override val id: String = "system-settings"
    override val displayName: String = activity.getString(R.string.provider_system_settings)

    private val _refreshSignal = MutableSharedFlow<Unit>()
    override val refreshSignal: SharedFlow<Unit> = _refreshSignal

    private data class SettingsActionItem(
        val title: String,
        val action: String,
        val requiresElevatedPermission: Boolean = false,
        val onLaunch: (suspend () -> Unit)? = null
    )

    private fun string(resId: Int): String = activity.getString(resId)

    private val settingsActions by lazy {
        listOf(
            SettingsActionItem(string(R.string.system_action_settings), Settings.ACTION_SETTINGS),
            SettingsActionItem(string(R.string.system_action_accessibility), Settings.ACTION_ACCESSIBILITY_SETTINGS),
            SettingsActionItem(string(R.string.system_action_access_point_names), Settings.ACTION_APN_SETTINGS),
            SettingsActionItem(string(R.string.system_action_developer_options), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            SettingsActionItem(string(R.string.system_action_apps), Settings.ACTION_APPLICATION_SETTINGS),
            SettingsActionItem(string(R.string.system_action_battery_saver), Settings.ACTION_BATTERY_SAVER_SETTINGS),
            SettingsActionItem(string(R.string.system_action_biometric_enrollment), Settings.ACTION_BIOMETRIC_ENROLL),
            SettingsActionItem(string(R.string.system_action_bluetooth), Settings.ACTION_BLUETOOTH_SETTINGS),
            SettingsActionItem(string(R.string.system_action_charging_control), "org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS", onLaunch = {
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
            SettingsActionItem(string(R.string.system_action_captioning), Settings.ACTION_CAPTIONING_SETTINGS),
            SettingsActionItem(string(R.string.system_action_cast), Settings.ACTION_CAST_SETTINGS),
            SettingsActionItem(string(R.string.system_action_data_roaming), Settings.ACTION_DATA_ROAMING_SETTINGS),
            SettingsActionItem(string(R.string.system_action_data_usage), Settings.ACTION_DATA_USAGE_SETTINGS),
            SettingsActionItem(string(R.string.system_action_date_time), Settings.ACTION_DATE_SETTINGS),
            SettingsActionItem(string(R.string.system_action_about_phone), Settings.ACTION_DEVICE_INFO_SETTINGS),
            SettingsActionItem(string(R.string.system_action_display), Settings.ACTION_DISPLAY_SETTINGS),
            SettingsActionItem(string(R.string.system_action_screen_saver), Settings.ACTION_DREAM_SETTINGS),
            SettingsActionItem(string(R.string.system_action_physical_keyboard), Settings.ACTION_HARD_KEYBOARD_SETTINGS),
            SettingsActionItem(string(R.string.system_action_default_home_app), Settings.ACTION_HOME_SETTINGS),
            SettingsActionItem(string(R.string.system_action_battery_optimization), Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            SettingsActionItem(string(R.string.system_action_on_screen_keyboard), Settings.ACTION_INPUT_METHOD_SETTINGS),
            SettingsActionItem(string(R.string.system_action_input_method_subtype), Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS),
            SettingsActionItem(string(R.string.system_action_storage), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            SettingsActionItem(string(R.string.system_action_language), Settings.ACTION_LOCALE_SETTINGS),
            SettingsActionItem(string(R.string.system_action_location), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            SettingsActionItem(string(R.string.system_action_default_apps), Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            SettingsActionItem(string(R.string.system_action_display_over_other_apps), Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            SettingsActionItem(string(R.string.system_action_install_unknown_apps), Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            SettingsActionItem(string(R.string.system_action_modify_system_settings), Settings.ACTION_MANAGE_WRITE_SETTINGS),
            SettingsActionItem(string(R.string.system_action_memory_card), Settings.ACTION_MEMORY_CARD_SETTINGS),
            SettingsActionItem(string(R.string.system_action_network_operators), Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            SettingsActionItem(string(R.string.system_action_android_beam), Settings.ACTION_NFCSHARING_SETTINGS),
            SettingsActionItem(string(R.string.system_action_tap_pay), Settings.ACTION_NFC_PAYMENT_SETTINGS),
            SettingsActionItem(string(R.string.system_action_nfc), Settings.ACTION_NFC_SETTINGS),
            SettingsActionItem(string(R.string.system_action_night_light), Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
            SettingsActionItem(string(R.string.system_action_notification_assistant), Settings.ACTION_NOTIFICATION_ASSISTANT_SETTINGS),
            SettingsActionItem(string(R.string.system_action_notification_access), Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            SettingsActionItem(string(R.string.system_action_do_not_disturb_access), Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            SettingsActionItem(string(R.string.system_action_printing), Settings.ACTION_PRINT_SETTINGS),
            SettingsActionItem(string(R.string.system_action_privacy), Settings.ACTION_PRIVACY_SETTINGS),
            SettingsActionItem(string(R.string.system_action_quick_access_wallet), Settings.ACTION_QUICK_ACCESS_WALLET_SETTINGS),
            SettingsActionItem(string(R.string.system_action_quick_launch), Settings.ACTION_QUICK_LAUNCH_SETTINGS),
            SettingsActionItem(string(R.string.system_action_autofill_service), Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE),
            SettingsActionItem(string(R.string.system_action_search), Settings.ACTION_SEARCH_SETTINGS),
            SettingsActionItem(string(R.string.system_action_security), Settings.ACTION_SECURITY_SETTINGS),
            SettingsActionItem(string(R.string.system_action_sound_vibration), Settings.ACTION_SOUND_SETTINGS),
            SettingsActionItem(string(R.string.system_action_accounts), Settings.ACTION_SYNC_SETTINGS),
            SettingsActionItem(string(R.string.system_action_usage_access), Settings.ACTION_USAGE_ACCESS_SETTINGS),
            SettingsActionItem(string(R.string.system_action_personal_dictionary), Settings.ACTION_USER_DICTIONARY_SETTINGS),
            SettingsActionItem(string(R.string.system_action_voice_input), Settings.ACTION_VOICE_INPUT_SETTINGS),
            SettingsActionItem(string(R.string.system_action_vpn), Settings.ACTION_VPN_SETTINGS),
            SettingsActionItem(string(R.string.system_action_vr_helper_services), Settings.ACTION_VR_LISTENER_SETTINGS),
            SettingsActionItem(string(R.string.system_action_webview_implementation), Settings.ACTION_WEBVIEW_SETTINGS),
            SettingsActionItem(string(R.string.system_action_wifi_ip_settings), Settings.ACTION_WIFI_IP_SETTINGS),
            SettingsActionItem(string(R.string.system_action_wifi), Settings.ACTION_WIFI_SETTINGS),
            SettingsActionItem(string(R.string.system_action_network_internet), Settings.ACTION_WIRELESS_SETTINGS),
            SettingsActionItem(string(R.string.system_action_do_not_disturb), Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),

            // Privileged Actions
            SettingsActionItem(
                string(R.string.system_action_wireless_debugging),
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
                string(R.string.system_action_usb_debugging),
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
        val enabledProviders = globalSettingsRepository.enabledProviders.value
        if (enabledProviders[id] == false) return emptyList()

        val normalized = query.trimmedText
        
        if (normalized.isBlank()) return emptyList()

        val results = mutableListOf<ProviderResult>()

        // Add developer toggle result if feature is enabled and query matches
        val systemSettings = settingsRepository.value
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
                    subtitle = activity.getString(R.string.provider_system_settings),
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
            Toast.makeText(activity, activity.getString(R.string.toast_developer_options), Toast.LENGTH_SHORT).show()
            activity.startActivity(fallbackIntent)
        }
    }

    private fun buildDeveloperToggleResult(isCurrentlyEnabled: Boolean, isReady: Boolean, query: String): ProviderResult? {
        val title =
            if (isCurrentlyEnabled) {
                activity.getString(R.string.system_developer_options_disable)
            } else {
                activity.getString(R.string.system_developer_options_enable)
            }
        val permissionStatus = developerSettingsManager.permissionStatus.value
        val subtitle = when {
            !isReady && permissionStatus.isShizukuAvailable && !permissionStatus.hasShizukuPermission ->
                activity.getString(R.string.system_developer_options_grant_shizuku)
            !isReady ->
                activity.getString(R.string.system_developer_options_no_permission, activity.packageName)
            isCurrentlyEnabled -> activity.getString(R.string.system_developer_options_turn_off)
            else -> activity.getString(R.string.system_developer_options_turn_on)
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
                                val message = if (success) {
                                    if (newState) {
                                        activity.getString(R.string.system_developer_options_enabled)
                                    } else {
                                        activity.getString(R.string.system_developer_options_disabled)
                                    }
                                } else {
                                    activity.getString(R.string.system_developer_options_toggle_failed)
                                }
                                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                            }
                            if (success) {
                                _refreshSignal.emit(Unit)
                            }
                        }
                    }
                    currentStatus.isShizukuAvailable && !currentStatus.hasShizukuPermission -> {
                        // Request Shizuku permission
                        withContext(Dispatchers.Main) {
                            val requested = developerSettingsManager.requestShizukuPermission()
                            if (!requested) {
                                Toast.makeText(activity, activity.getString(R.string.toast_shizuku_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.system_developer_options_grant_adb, activity.packageName),
                                Toast.LENGTH_LONG,
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