package com.mrndstvndv.search.provider.apps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class PinnedAppsRepository(
    private val context: Context,
    private val settingsRepository: ProviderSettingsRepository<AppSearchSettings>,
    private val appListRepository: AppListRepository,
) {
    private val packageManager = context.packageManager
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val mySerialNumber: Long by lazy {
        userManager.getSerialNumberForUser(Process.myUserHandle())
    }

    fun getPinnedApps(): Flow<List<RecentApp>> =
        settingsRepository.flow
            .map { settings ->
                settings.pinnedApps.mapNotNull { packageName ->
                    try {
                        val appInfo = packageManager.getApplicationInfo(packageName, 0)
                        val label = packageManager.getApplicationLabel(appInfo).toString()
                        val launchIntent =
                            packageManager.getLaunchIntentForPackage(packageName)
                                ?: return@mapNotNull null
                        RecentApp(
                            packageName = packageName,
                            label = label,
                            iconLoader = { appListRepository.getIcon(packageName, mySerialNumber) },
                            launchIntent = launchIntent,
                            userSerialNumber = mySerialNumber,
                        )
                    } catch (e: PackageManager.NameNotFoundException) {
                        null // App uninstalled
                    }
                }
            }.flowOn(Dispatchers.IO)
}
