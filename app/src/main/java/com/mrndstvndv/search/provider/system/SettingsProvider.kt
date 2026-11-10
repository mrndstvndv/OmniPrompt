package com.mrndstvndv.search.provider.system

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Settings
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.ProviderRankingRepository
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.settings.SystemSettingsSettings
import com.mrndstvndv.search.util.FuzzyMatcher
import com.mrndstvndv.search.util.TypoFallbackMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsProvider(
    private val context: Context,
    private val globalSettingsRepository: SettingsRepository,
    private val settingsRepository: ProviderSettingsRepository<SystemSettingsSettings>,
    private val developerSettingsManager: DeveloperSettingsManager,
    private val rankingRepository: ProviderRankingRepository,
    private val settingsCatalogRepository: SettingsCatalogRepository,
    private val scope: CoroutineScope,
) : Provider {
    override val id: String = "system-settings"
    override val displayName: String = context.getString(R.string.provider_system_settings)

    private val _refreshSignal = MutableSharedFlow<Unit>()
    override val refreshSignal: SharedFlow<Unit> = _refreshSignal

    init {
        // Re-run the active search whenever the discovered-screens catalog
        // changes (initial scan finishing, a manual/automatic rescan, or a
        // stale entry being self-healed away).
        scope.launch {
            settingsCatalogRepository.catalog.drop(1).collect { _refreshSignal.emit(Unit) }
        }
    }

    override fun initialize() {
        scope.launch {
            settingsCatalogRepository.initialize(settingsActions.map { it.action })
        }
    }

    /** Bypasses the version-change gate — for a manual "refresh" action in settings. */
    suspend fun forceRescanSettingsCatalog() {
        settingsCatalogRepository.forceRescan(settingsActions.map { it.action })
    }

    private data class SettingsActionItem(
        val title: String,
        val action: String,
        val requiresElevatedPermission: Boolean = false,
        val onLaunch: (suspend () -> Unit)? = null,
    )

    private fun string(resId: Int): String = context.getString(resId)

    private val settingsActions by lazy {
        listOf(
            SettingsActionItem(string(R.string.system_action_settings), Settings.ACTION_SETTINGS),
            SettingsActionItem(
                string(R.string.system_action_accessibility),
                Settings.ACTION_ACCESSIBILITY_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_access_point_names),
                Settings.ACTION_APN_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_developer_options),
                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_apps),
                Settings.ACTION_APPLICATION_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_battery_saver),
                Settings.ACTION_BATTERY_SAVER_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_biometric_enrollment),
                Settings.ACTION_BIOMETRIC_ENROLL,
            ),
            SettingsActionItem(
                string(R.string.system_action_bluetooth),
                Settings.ACTION_BLUETOOTH_SETTINGS,
            ),
            SettingsActionItem(
                string(
                    R.string.system_action_charging_control,
                ),
                "org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS",
                onLaunch = {
                    val intent = Intent("org.lineageos.lineageparts.CHARGING_CONTROL_SETTINGS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        val fallback = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        if (fallback.resolveActivity(context.packageManager) != null) {
                            context.startActivity(fallback)
                        }
                    }
                },
            ),
            SettingsActionItem(
                string(R.string.system_action_captioning),
                Settings.ACTION_CAPTIONING_SETTINGS,
            ),
            SettingsActionItem(string(R.string.system_action_cast), Settings.ACTION_CAST_SETTINGS),
            SettingsActionItem(
                string(R.string.system_action_data_roaming),
                Settings.ACTION_DATA_ROAMING_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_data_usage),
                Settings.ACTION_DATA_USAGE_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_date_time),
                Settings.ACTION_DATE_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_about_phone),
                Settings.ACTION_DEVICE_INFO_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_display),
                Settings.ACTION_DISPLAY_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_screen_saver),
                Settings.ACTION_DREAM_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_physical_keyboard),
                Settings.ACTION_HARD_KEYBOARD_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_default_home_app),
                Settings.ACTION_HOME_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_battery_optimization),
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_on_screen_keyboard),
                Settings.ACTION_INPUT_METHOD_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_input_method_subtype),
                Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_storage),
                Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_language),
                Settings.ACTION_LOCALE_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_location),
                Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_default_apps),
                Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_display_over_other_apps),
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            ),
            SettingsActionItem(
                string(R.string.system_action_install_unknown_apps),
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            ),
            SettingsActionItem(
                string(R.string.system_action_modify_system_settings),
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_memory_card),
                Settings.ACTION_MEMORY_CARD_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_network_operators),
                Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_android_beam),
                Settings.ACTION_NFCSHARING_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_tap_pay),
                Settings.ACTION_NFC_PAYMENT_SETTINGS,
            ),
            SettingsActionItem(string(R.string.system_action_nfc), Settings.ACTION_NFC_SETTINGS),
            SettingsActionItem(
                string(R.string.system_action_night_light),
                Settings.ACTION_NIGHT_DISPLAY_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_notification_assistant),
                Settings.ACTION_NOTIFICATION_ASSISTANT_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_notification_access),
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_do_not_disturb_access),
                Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_printing),
                Settings.ACTION_PRINT_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_privacy),
                Settings.ACTION_PRIVACY_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_quick_access_wallet),
                Settings.ACTION_QUICK_ACCESS_WALLET_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_quick_launch),
                Settings.ACTION_QUICK_LAUNCH_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_autofill_service),
                Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE,
            ),
            SettingsActionItem(
                string(R.string.system_action_search),
                Settings.ACTION_SEARCH_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_security),
                Settings.ACTION_SECURITY_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_sound_vibration),
                Settings.ACTION_SOUND_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_accounts),
                Settings.ACTION_SYNC_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_usage_access),
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_personal_dictionary),
                Settings.ACTION_USER_DICTIONARY_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_voice_input),
                Settings.ACTION_VOICE_INPUT_SETTINGS,
            ),
            SettingsActionItem(string(R.string.system_action_vpn), Settings.ACTION_VPN_SETTINGS),
            SettingsActionItem(
                string(R.string.system_action_vr_helper_services),
                Settings.ACTION_VR_LISTENER_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_webview_implementation),
                Settings.ACTION_WEBVIEW_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_wifi_ip_settings),
                Settings.ACTION_WIFI_IP_SETTINGS,
            ),
            SettingsActionItem(string(R.string.system_action_wifi), Settings.ACTION_WIFI_SETTINGS),
            SettingsActionItem(
                string(R.string.system_action_network_internet),
                Settings.ACTION_WIRELESS_SETTINGS,
            ),
            SettingsActionItem(
                string(R.string.system_action_do_not_disturb),
                Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS,
            ),
            // Privileged Actions
            SettingsActionItem(
                string(R.string.system_action_wireless_debugging),
                "android.settings.ADB_WIFI_SETTINGS",
                requiresElevatedPermission = true,
                onLaunch = {
                    val launched =
                        withContext(Dispatchers.IO) {
                            developerSettingsManager.launchWirelessDebugging()
                        }
                    if (!launched) openDevOptionsFallback()
                },
            ),
            SettingsActionItem(
                string(R.string.system_action_usb_debugging),
                "action.custom.USB_DEBUGGING",
                requiresElevatedPermission = true,
                onLaunch = {
                    val launched =
                        withContext(Dispatchers.IO) {
                            developerSettingsManager.launchUsbDebugging()
                        }
                    if (!launched) openDevOptionsFallback()
                },
            ),
        )
    }

    // Keywords that trigger the developer toggle result
    private val developerToggleKeywords =
        listOf(
            "developer",
            "dev mode",
            "developer mode",
            "dev options",
            "toggle developer",
            "enable developer",
            "disable developer",
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
            val matchesDeveloperToggle =
                developerToggleKeywords.any { keyword ->
                    keyword.contains(normalized, ignoreCase = true) ||
                        normalized.contains(keyword, ignoreCase = true)
                }

            if (matchesDeveloperToggle) {
                val permissionStatus = developerSettingsManager.permissionStatus.value
                val isCurrentlyEnabled = developerSettingsManager.isDeveloperSettingsEnabled()
                val toggleResult =
                    buildDeveloperToggleResult(
                        isCurrentlyEnabled,
                        permissionStatus.isReady,
                        normalized,
                    )
                if (toggleResult != null) {
                    results.add(toggleResult)
                }
            }
        }

        val currentPermissionStatus = developerSettingsManager.permissionStatus.value
        val settingsCatalog = settingsCatalogRepository.catalog.value

        // Manifest-discovered activities with their own label always take
        // priority over a hardcoded entry resolving to the same target — an
        // OEM's own name for a screen is more useful than a generic curated
        // one. Precomputed once per catalog publish (not per keystroke) in
        // SettingsCatalogRepository.publishLocked().
        val overriddenComponents = settingsCatalog.overriddenComponents

        // Add regular settings results with fuzzy matching. Hardcoded entries
        // are filtered to ones that actually resolve on this device (via the
        // catalog's cheap resolvability check) and aren't overridden above.
        val eligibleHardcoded =
            settingsActions.filter { item ->
                if (item.requiresElevatedPermission && currentPermissionStatus.availableMethod == DeveloperSettingsManager.PermissionMethod.NONE) {
                    return@filter false
                }
                if (item.onLaunch == null) {
                    val resolvedComponent = settingsCatalog.hardcodedActionToComponent[item.action]
                    if (resolvedComponent == null || resolvedComponent in overriddenComponents) {
                        return@filter false
                    }
                }
                true
            }
        val hardcodedResults =
            eligibleHardcoded.mapNotNull { item ->
                val matchResult = FuzzyMatcher.match(normalized, item.title)
                if (matchResult != null) Pair(item, matchResult) else null
            }.sortedByDescending { it.second.score }
                .map { (item, matchResult) -> buildHardcodedResult(item, matchResult.matchedIndices) }

        // Add manifest-discovered settings screens with their own label.
        val discoveredResults =
            settingsCatalog.labeledDiscovered.mapNotNull { activity ->
                val matchResult = FuzzyMatcher.match(normalized, activity.label)
                matchResult?.let { activity to it }
            }.sortedByDescending { it.second.score }
                .map { (activity, matchResult) -> buildDiscoveredResult(activity, matchResult.matchedIndices) }

        // A canonical-target dedup already collapses activity aliases in the
        // explorer, but distinct components can still end up with visually
        // identical titles (a curated hardcoded label matching a discovered
        // OEM label word-for-word, or two separate screens an OEM names the
        // same thing). De-dupe on the visible title as a final safety net —
        // discovered (manifest/skin-specific) entries win ties, since that's
        // the whole point of preferring them in the first place.
        val deduped = linkedMapOf<String, ProviderResult>()
        (discoveredResults + hardcodedResults).forEach { result ->
            deduped.putIfAbsent(result.title.trim().lowercase(), result)
        }
        results.addAll(deduped.values)

        if (results.isEmpty()) {
            // Nothing matched at all — try a typo-tolerant fallback over the
            // same candidates (discovered first so they win ties, as above).
            val fallbackCandidates =
                settingsCatalog.labeledDiscovered.map { activity ->
                    TypoFallbackCandidate(
                        label = activity.label,
                        resultId = "$id:${activity.componentName.flattenToString()}",
                    ) { buildDiscoveredResult(activity, emptyList()) }
                } +
                    eligibleHardcoded.map { item ->
                        TypoFallbackCandidate(
                            label = item.title,
                            resultId = "$id:${item.action}",
                        ) { buildHardcodedResult(item, emptyList()) }
                    }
            results.addAll(
                TypoFallbackMatcher.match(
                    queryLower = normalized.lowercase(),
                    candidates = fallbackCandidates,
                    labelOf = { it.label.lowercase() },
                    frequencyOf = { rankingRepository.getGlobalResultFrequency(it.resultId) },
                ).distinctBy { it.item.label.trim().lowercase() }
                    .map { it.item.build() },
            )
        }

        return results
    }

    private class TypoFallbackCandidate(
        val label: String,
        val resultId: String,
        val build: () -> ProviderResult,
    )

    private fun buildHardcodedResult(
        item: SettingsActionItem,
        matchedIndices: List<Int>,
    ): ProviderResult =
        ProviderResult(
            id = "$id:${item.action}",
            title = item.title,
            subtitle = context.getString(R.string.provider_system_settings),
            defaultVectorIcon = Icons.Outlined.Settings,
            providerId = id,
            onSelect = {
                withContext(Dispatchers.Main) {
                    try {
                        if (item.onLaunch != null) {
                            item.onLaunch.invoke()
                        } else {
                            val intent = Intent(item.action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.settings_screen_no_longer_available, item.title),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_screen_no_longer_available, item.title),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            keepOverlayUntilExit = true,
            matchedTitleIndices = matchedIndices,
        )

    private fun buildDiscoveredResult(
        activity: DiscoveredActivity,
        matchedIndices: List<Int>,
    ): ProviderResult =
        ProviderResult(
            id = "$id:${activity.componentName.flattenToString()}",
            title = activity.label,
            subtitle = context.getString(R.string.provider_system_settings),
            defaultVectorIcon = Icons.Outlined.Settings,
            providerId = id,
            onSelect = {
                withContext(Dispatchers.Main) {
                    try {
                        val intent =
                            Intent().apply {
                                component = activity.componentName
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Discovered via the manifest but not actually
                        // launchable anymore (removed/permission-gated since
                        // the last scan) — self-heal instead of leaving a
                        // silent dead entry in search.
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_screen_no_longer_available, activity.label),
                            Toast.LENGTH_SHORT,
                        ).show()
                        settingsCatalogRepository.removeStaleDiscoveredEntry(activity.componentName)
                    }
                }
            },
            keepOverlayUntilExit = true,
            matchedTitleIndices = matchedIndices,
        )

    private fun openDevOptionsFallback() {
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (fallbackIntent.resolveActivity(context.packageManager) != null) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_developer_options),
                Toast.LENGTH_SHORT,
            ).show()
            context.startActivity(fallbackIntent)
        }
    }

    private fun buildDeveloperToggleResult(
        isCurrentlyEnabled: Boolean,
        isReady: Boolean,
        query: String,
    ): ProviderResult? {
        val title =
            if (isCurrentlyEnabled) {
                context.getString(R.string.system_developer_options_disable)
            } else {
                context.getString(R.string.system_developer_options_enable)
            }
        val permissionStatus = developerSettingsManager.permissionStatus.value
        val subtitle =
            when {
                !isReady && permissionStatus.isShizukuAvailable && !permissionStatus.hasShizukuPermission ->
                    context.getString(R.string.system_developer_options_grant_shizuku)
                !isReady ->
                    context.getString(
                        R.string.system_developer_options_no_permission,
                        context.packageName,
                    )
                isCurrentlyEnabled -> context.getString(R.string.system_developer_options_turn_off)
                else -> context.getString(R.string.system_developer_options_turn_on)
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
                            val success =
                                developerSettingsManager.setDeveloperSettingsEnabled(
                                    newState,
                                )
                            withContext(Dispatchers.Main) {
                                val message =
                                    if (success) {
                                        if (newState) {
                                            context.getString(
                                                R.string.system_developer_options_enabled,
                                            )
                                        } else {
                                            context.getString(
                                                R.string.system_developer_options_disabled,
                                            )
                                        }
                                    } else {
                                        context.getString(
                                            R.string.system_developer_options_toggle_failed,
                                        )
                                    }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_shizuku_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    else -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.system_developer_options_grant_adb,
                                    context.packageName,
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
            keepOverlayUntilExit = true,
            matchedTitleIndices = titleMatch?.matchedIndices ?: emptyList(),
            matchedSubtitleIndices = subtitleMatch?.matchedIndices ?: emptyList(),
        )
    }
}
