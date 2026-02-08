package com.mrndstvndv.search.provider.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class RecentApp(
    val packageName: String,
    val label: String,
    val iconLoader: suspend () -> Bitmap?,
    val launchIntent: Intent,
)

class RecentAppsRepository(
    private val context: Context,
    private val appListRepository: AppListRepository,
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    @Volatile
    private var hasPermissionCache = checkPermission()

    private fun checkPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
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

    fun getRecentApps(limit: Int = 5): Flow<List<RecentApp>> = flow {
        if (!hasPermissionCache) {
            emit(emptyList())
            return@flow
        }

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 86400000 // 24 hours in ms

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime,
        )

        if (usageStats.isNullOrEmpty()) {
            emit(emptyList())
            return@flow
        }

        val selfPackage = context.packageName
        val recentApps = usageStats
            .asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.lastTimeUsed }
            .map { it.packageName }
            .distinct()
            .filter { it != selfPackage }
            .mapNotNull { packageName ->
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
                val appInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }

                RecentApp(
                    packageName = packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    iconLoader = { appListRepository.getIcon(packageName) },
                    launchIntent = launchIntent
                )
            }
            .take(limit)
            .toList()

        emit(recentApps)
    }.flowOn(Dispatchers.IO)
}
