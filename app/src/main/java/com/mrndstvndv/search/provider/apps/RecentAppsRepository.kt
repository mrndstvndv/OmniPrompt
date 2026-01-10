package com.mrndstvndv.search.provider.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Process
import com.mrndstvndv.search.util.loadAppIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Calendar

data class RecentApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val launchIntent: Intent,
)

class RecentAppsRepository(
    private val context: Context,
    private val iconSize: Int,
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager

    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode =
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getRecentApps(limit: Int = 5): Flow<List<RecentApp>> =
        flow {
            if (!hasPermission()) {
                emit(emptyList())
                return@flow
            }

            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis

            val usageStats =
                usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime,
                )

            if (usageStats.isNullOrEmpty()) {
                emit(emptyList())
                return@flow
            }

            val recentApps =
                usageStats
                    .asSequence()
                    .filter { it.totalTimeInForeground > 0 }
                    .sortedByDescending { it.lastTimeUsed }
                    .map { it.packageName }
                    .distinct()
                    .filter { it != context.packageName }
                    .mapNotNull { packageName ->
                        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
                        val appInfo =
                            try {
                                packageManager.getApplicationInfo(packageName, 0)
                            } catch (e: PackageManager.NameNotFoundException) {
                                return@mapNotNull null
                            }

                        val label = packageManager.getApplicationLabel(appInfo).toString()
                        val icon = loadAppIconBitmap(packageManager, packageName, iconSize)

                        RecentApp(packageName, label, icon, launchIntent)
                    }.take(limit)
                    .toList()

            emit(recentApps)
        }.flowOn(Dispatchers.IO)
}
