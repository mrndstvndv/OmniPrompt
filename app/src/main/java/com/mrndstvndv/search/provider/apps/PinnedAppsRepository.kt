package com.mrndstvndv.search.provider.apps

import android.content.Context
import android.content.pm.PackageManager
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.util.loadAppIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class PinnedAppsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository<AppSearchSettings>,
    private val iconSize: Int,
) {
    private val packageManager = context.packageManager

    fun getPinnedApps(): Flow<List<RecentApp>> =
        settingsRepository.flow
            .map { settings ->
                settings.pinnedApps.mapNotNull { packageName ->
                    try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        val label = packageManager.getApplicationLabel(appInfo).toString()
                        val icon = loadAppIconBitmap(packageManager, packageName, iconSize)
                        val launchIntent =
                            packageManager.getLaunchIntentForPackage(packageName)
                                ?: return@mapNotNull null
                        RecentApp(packageName, label, icon, launchIntent)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null // App uninstalled
                    }
                }
            }.flowOn(Dispatchers.IO)
}
