package com.mrndstvndv.search.provider.apps

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Process
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import com.mrndstvndv.search.R
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "RecentApps"
private const val TRACKED_PREFS = "recent_apps_launches"
private const val TRACK_PREFIX = "recent|"
private const val MAX_TRACKED = 100

data class RecentApp(
    val packageName: String,
    val label: String,
    val iconLoader: suspend () -> Bitmap?,
    val launchIntent: Intent?,
    val userSerialNumber: Long = 0L,
) {
    /** Stable identity across profiles: one package can exist in personal + work. */
    val profileKey: String get() = "$packageName:$userSerialNumber"
}

/** One recency signal, either from system usage stats or locally tracked launches. */
internal data class RecentEntry(
    val packageName: String,
    val userSerialNumber: Long,
    val label: String?,
    val lastUsed: Long,
)

/** Merges usage-stats and tracked entries: freshest timestamp wins per profile app. */
internal fun mergeRecentEntries(
    usage: List<RecentEntry>,
    tracked: List<RecentEntry>,
): List<RecentEntry> =
    (usage + tracked)
        .groupBy { it.packageName to it.userSerialNumber }
        .map { (_, group) -> group.maxBy { it.lastUsed } }
        .sortedByDescending { it.lastUsed }

class RecentAppsRepository(
    private val context: Context,
    private val appListRepository: AppListRepository,
    private val settingsRepository: ProviderSettingsRepository<AppSearchSettings>,
) {
    private val usageStatsManager =
        context.getSystemService(
            Context.USAGE_STATS_SERVICE,
        ) as UsageStatsManager
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val trackedPrefs = context.getSharedPreferences(TRACKED_PREFS, Context.MODE_PRIVATE)

    private val mySerialNumber: Long by lazy {
        userManager.getSerialNumberForUser(Process.myUserHandle())
    }

    @Volatile
    private var hasPermissionCache = checkPermission()

    private fun checkPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode =
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasPermission(): Boolean = hasPermissionCache

    fun refreshPermissionState(): Boolean {
        hasPermissionCache = checkPermission()
        return hasPermissionCache
    }

    /**
     * Records an app launch. UsageStatsManager only reports the caller's profile, so
     * work-profile apps can never appear in system usage stats — launches made through
     * Search are tracked locally to fill that gap.
     */
    fun recordLaunch(packageName: String, userSerialNumber: Long) {
        if (userSerialNumber < 0) return
        val key = trackKey(packageName, userSerialNumber)
        val snapshot = trackedPrefs.all.toMutableMap()
        snapshot[key] = System.currentTimeMillis()
        trackedPrefs.edit {
            putLong(key, snapshot[key] as Long)
            if (snapshot.size > MAX_TRACKED) {
                snapshot.entries
                    .sortedBy { (it.value as? Long) ?: 0L }
                    .take(snapshot.size - MAX_TRACKED)
                    .forEach { remove(it.key) }
            }
        }
    }
    /** Launches the app on its own profile and records the launch for recents. */
    fun launchApp(context: Context, app: RecentApp) {
        try {
            val user =
                userManager.getUserForSerialNumber(app.userSerialNumber)
                    ?: Process.myUserHandle()
            if (app.userSerialNumber != mySerialNumber) {
                val activity =
                    runCatching {
                        launcherApps.getActivityList(app.packageName, user).firstOrNull()
                    }.getOrNull()
                if (activity != null) {
                    launcherApps.startMainActivity(activity.componentName, user, null, null)
                    recordLaunch(app.packageName, app.userSerialNumber)
                    (context as? ComponentActivity)?.finish()
                    return
                }
                // Work target no longer resolvable (quiet mode, uninstalled): fall through
                // to launchIntent if present, else report unavailable below.
            }
            val intent = app.launchIntent ?: throw ActivityNotFoundException()
            context.startActivity(intent)
            recordLaunch(app.packageName, app.userSerialNumber)
            (context as? ComponentActivity)?.finish()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                context,
                context.getString(R.string.app_list_app_unavailable),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch app", e)
        }
    }

    fun getRecentApps(limit: Int = 5): Flow<List<RecentApp>> =
        flow {
            val includeWorkApps = settingsRepository.value.includeWorkApps
            val usageEntries =
                if (hasPermissionCache) {
                    queryUsageEntries()
                } else {
                    emptyList()
                }
            val trackedEntries = readTrackedEntries(includeWorkApps)
            val recentApps =
                mergeRecentEntries(usageEntries, trackedEntries)
                    .mapNotNull { it.toRecentApp() }
                    .take(limit)
            emit(recentApps)
        }.flowOn(Dispatchers.IO)

    private fun queryUsageEntries(): List<RecentEntry> {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 86400000 // 24 hours in ms

        val usageStats =
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime,
            ) ?: return emptyList()

        val selfPackage = context.packageName
        val seen = LinkedHashSet<String>()
        val entries = mutableListOf<RecentEntry>()
        usageStats.asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .forEach {
                if (it.packageName != selfPackage && seen.add(it.packageName)) {
                    entries +=
                        RecentEntry(
                            packageName = it.packageName,
                            userSerialNumber = mySerialNumber,
                            label = null,
                            lastUsed = it.lastTimeUsed,
                        )
                }
            }
        return entries
    }

    private fun readTrackedEntries(includeWorkApps: Boolean): List<RecentEntry> {
        val all = trackedPrefs.all
        if (all.isEmpty()) return emptyList()
        val entries = mutableListOf<RecentEntry>()
        val staleKeys = mutableListOf<String>()
        for ((key, value) in all) {
            val parsed = parseTrackKey(key) ?: continue
            val (packageName, serial) = parsed
            val lastUsed = (value as? Long) ?: continue
            if (!includeWorkApps && serial != mySerialNumber) continue
            val user = userManager.getUserForSerialNumber(serial)
            if (user == null) {
                staleKeys += key
                continue
            }
            val label =
                runCatching {
                    launcherApps.getActivityList(packageName, user)
                        .firstOrNull()?.label?.toString()
                }.getOrNull()
            if (label.isNullOrBlank()) {
                staleKeys += key
                continue
            }
            entries += RecentEntry(packageName, serial, label, lastUsed)
        }
        if (staleKeys.isNotEmpty()) {
            trackedPrefs.edit { staleKeys.forEach { remove(it) } }
        }
        return entries
    }

    private fun RecentEntry.toRecentApp(): RecentApp? {
        if (userSerialNumber == mySerialNumber) {
            val launchIntent =
                packageManager.getLaunchIntentForPackage(packageName) ?: return null
            val resolvedLabel =
                label ?: try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    return null
                }
            return RecentApp(
                packageName = packageName,
                label = resolvedLabel,
                iconLoader = { appListRepository.getIcon(packageName, userSerialNumber) },
                launchIntent = launchIntent,
                userSerialNumber = userSerialNumber,
            )
        }
        // Work-profile entry: only launchable via LauncherApps; icon is work-badged.
        return RecentApp(
            packageName = packageName,
            label = label ?: return null,
            iconLoader = { appListRepository.getIcon(packageName, userSerialNumber) },
            launchIntent = null,
            userSerialNumber = userSerialNumber,
        )
    }

    private fun trackKey(packageName: String, userSerialNumber: Long): String =
        "$TRACK_PREFIX$packageName|$userSerialNumber"

    private fun parseTrackKey(key: String): Pair<String, Long>? {
        if (!key.startsWith(TRACK_PREFIX)) return null
        val body = key.removePrefix(TRACK_PREFIX)
        val split = body.lastIndexOf('|')
        if (split <= 0) return null
        val serial = body.substring(split + 1).toLongOrNull() ?: return null
        return body.substring(0, split) to serial
    }
}
