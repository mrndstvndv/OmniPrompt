package com.mrndstvndv.search.provider.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.Settings

/** A settings screen discovered by scanning the settings app's manifest. */
data class DiscoveredActivity(
    val componentName: ComponentName,
    val hasOwnLabel: Boolean,
    val label: String,
)

enum class SettingsExclusionReason {
    /** No android:label on the activity itself, and no hardcoded action resolves to it. */
    NO_LABEL_NO_HARDCODED_MATCH,

    /** Theme resolves as floating/translucent/no-display — almost certainly a dialog/popup, not a destination screen. */
    DIALOG_STYLED,
}

data class ExcludedActivity(
    val componentName: ComponentName,
    val reason: SettingsExclusionReason,
)

internal data class ExplorerScanResult(
    val settingsPackageName: String,
    val settingsPackageVersionCode: Long,
    val settingsPackageLastUpdateTime: Long,
    val discovered: List<DiscoveredActivity>,
    val excluded: List<ExcludedActivity>,
)

/**
 * Enumerates the exported activities of whichever package handles
 * [Settings.ACTION_SETTINGS] on this device, filtering out dialog/popup-styled
 * activities along the way.
 *
 * This is a manifest scan, not a documented public API for "all settings
 * screens" — most modern AOSP-derived sub-screens are fragments hosted inside
 * one shared container activity and simply won't appear here. What it *does*
 * reliably find: any genuinely standalone exported Activity, including
 * OEM-only screens with no Settings.ACTION_* equivalent.
 */
internal object SettingsActivityExplorer {
    fun scan(context: Context): ExplorerScanResult? {
        val packageManager = context.packageManager
        val settingsPackageName = resolveSettingsPackageName(packageManager) ?: return null

        val packageInfo =
            try {
                packageManager.getPackageInfo(
                    settingsPackageName,
                    PackageManager.GET_ACTIVITIES,
                )
            } catch (_: Exception) {
                return null
            }

        val activities = packageInfo.activities ?: emptyArray()

        // All activities being scanned belong to the same package, so the
        // Resources needed to resolve any of their themes are identical —
        // fetch once instead of once per activity (there can be 100+).
        val settingsResources: Resources? =
            packageInfo.applicationInfo?.let { appInfo ->
                try {
                    packageManager.getResourcesForApplication(appInfo)
                } catch (_: Exception) {
                    null
                }
            }

        // Keyed by the *canonical* launch target (an alias's real target, or
        // the activity itself if it isn't an alias) — collapses the common
        // OEM pattern of several <activity-alias> entries all pointing at one
        // real screen (e.g. multiple Wi-Fi aliases on Samsung) into a single
        // result instead of showing duplicates. This key is ONLY used for
        // grouping: an alias's own exported/permission attributes govern
        // whether it can be launched independently of its target's, so the
        // component we actually keep and launch must stay whichever specific
        // entry (alias or real activity) we found — never the target's raw
        // name substituted in.
        val discoveredByCanonicalTarget = linkedMapOf<ComponentName, DiscoveredActivity>()
        val excluded = mutableListOf<ExcludedActivity>()

        for (activityInfo in activities) {
            if (!activityInfo.exported) continue

            val ownComponentName = ComponentName(activityInfo.packageName, activityInfo.name)
            val canonicalTarget =
                activityInfo.targetActivity?.let { ComponentName(activityInfo.packageName, it) }
                    ?: ownComponentName

            if (isDialogStyled(settingsResources, activityInfo)) {
                excluded.add(ExcludedActivity(ownComponentName, SettingsExclusionReason.DIALOG_STYLED))
                continue
            }

            val hasOwnLabel = activityInfo.labelRes != 0 || activityInfo.nonLocalizedLabel != null
            val label =
                if (hasOwnLabel) {
                    activityInfo.loadLabel(packageManager).toString().takeIf { it.isNotBlank() }
                } else {
                    null
                }
            // componentName here is deliberately the entry we actually found
            // (ownComponentName) — see the comment on discoveredByCanonicalTarget.
            val candidate =
                if (label != null) {
                    DiscoveredActivity(ownComponentName, hasOwnLabel = true, label = label)
                } else {
                    DiscoveredActivity(ownComponentName, hasOwnLabel = false, label = "")
                }

            // Several aliases (or an alias plus its real activity) can map to
            // the same canonical target — keep one entry per target, favoring
            // whichever candidate actually has a usable label.
            val existing = discoveredByCanonicalTarget[canonicalTarget]
            if (existing == null || (!existing.hasOwnLabel && candidate.hasOwnLabel)) {
                discoveredByCanonicalTarget[canonicalTarget] = candidate
            }
        }

        val discovered = discoveredByCanonicalTarget.values.toList()

        return ExplorerScanResult(
            settingsPackageName = settingsPackageName,
            settingsPackageVersionCode = packageInfoVersionCode(packageInfo),
            settingsPackageLastUpdateTime = packageInfo.lastUpdateTime,
            discovered = discovered,
            excluded = excluded,
        )
    }

    /**
     * Cheap check (no GET_ACTIVITIES) for whether the settings app has changed
     * since a previous scan, so callers can skip the full enumeration when
     * nothing changed.
     */
    fun peekPackageMeta(context: Context): Triple<String, Long, Long>? {
        val packageManager = context.packageManager
        val settingsPackageName = resolveSettingsPackageName(packageManager) ?: return null
        return try {
            val info = packageManager.getPackageInfo(settingsPackageName, 0)
            Triple(settingsPackageName, packageInfoVersionCode(info), info.lastUpdateTime)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveSettingsPackageName(packageManager: PackageManager): String? =
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.resolveActivity(packageManager)?.packageName
        } catch (_: Exception) {
            null
        }

    @Suppress("DEPRECATION")
    private fun packageInfoVersionCode(packageInfo: android.content.pm.PackageInfo): Long =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    /**
     * Resolves the activity's theme and checks for floating/translucent/no-display
     * attributes — the same mechanism dialog-styled activities (e.g. a Wi-Fi
     * network picker popup) declare themselves with. Public API only, no
     * reflection. Unresolvable themes are treated as "not a dialog" — we'd
     * rather risk a false include than over-exclude on a lookup failure.
     */
    private fun isDialogStyled(
        settingsResources: Resources?,
        activityInfo: ActivityInfo,
    ): Boolean {
        val themeResId = activityInfo.theme.takeIf { it != 0 } ?: activityInfo.applicationInfo?.theme ?: 0
        if (themeResId == 0 || settingsResources == null) return false

        return try {
            val theme = settingsResources.newTheme()
            theme.applyStyle(themeResId, true)
            val attrs =
                theme.obtainStyledAttributes(
                    intArrayOf(
                        android.R.attr.windowIsFloating,
                        android.R.attr.windowIsTranslucent,
                        android.R.attr.windowNoDisplay,
                    ),
                )
            try {
                val floating = attrs.getBoolean(0, false)
                val translucent = attrs.getBoolean(1, false)
                val noDisplay = attrs.getBoolean(2, false)
                floating || translucent || noDisplay
            } finally {
                attrs.recycle()
            }
        } catch (_: Exception) {
            false
        }
    }
}
