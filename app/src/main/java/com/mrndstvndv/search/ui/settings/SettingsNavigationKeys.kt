package com.mrndstvndv.search.ui.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Sealed interface for all settings navigation routes.
 *
 * Each data object represents a distinct settings sub-screen.
 * All keys are @Serializable to support rememberNavBackStack
 * (which persists across config changes and process death).
 */
sealed interface SettingsKey : NavKey

// ── Top-level settings ──────────────────────────────────────
@Serializable data object Home : SettingsKey
@Serializable data object Appearance : SettingsKey
@Serializable data object Behavior : SettingsKey
@Serializable data object Aliases : SettingsKey
@Serializable data object Ranking : SettingsKey
@Serializable data object BackupRestore : SettingsKey
@Serializable data object Updates : SettingsKey
@Serializable data object About : SettingsKey
@Serializable data object Licenses : SettingsKey

// ── Provider settings ───────────────────────────────────────
@Serializable data object Providers : SettingsKey
@Serializable data object WebSearch : SettingsKey
@Serializable data object FileSearch : SettingsKey
@Serializable data object TextUtilities : SettingsKey
@Serializable data object AppSearch : SettingsKey
@Serializable data object SystemSettings : SettingsKey
@Serializable data object ContactsSettings : SettingsKey
@Serializable data object TermuxSettings : SettingsKey
@Serializable data object IntentSettings : SettingsKey
