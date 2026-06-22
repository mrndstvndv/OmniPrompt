package com.mrndstvndv.search.provider.apps

import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.UserHandle
import android.os.UserManager
import com.mrndstvndv.search.SearchApplication
import com.mrndstvndv.search.provider.apps.models.AppInfo
import com.mrndstvndv.search.provider.settings.AppSearchSettings
import com.mrndstvndv.search.util.getThemeColors
import com.mrndstvndv.search.util.loadAppIconBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppListRepository private constructor(
    private val context: Context,
    private val iconSize: Int,
) {
    private val packageManager = context.packageManager
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val cacheMutex = Mutex()
    private var cachedApps: List<AppInfo>? = null
    private val iconCache = ConcurrentHashMap<String, Bitmap>()
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    // ponytail: reads theme settings internally so iconLoader lambdas stay parameterless.
    private val settingsRepository by lazy {
        (context.applicationContext as SearchApplication).container.appSearchSettingsRepo
    }
    private var currentSettings: AppSearchSettings? = null

    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            iconCache.clear()
        }

        override fun onLowMemory() {
            iconCache.clear()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            scope.launch { refresh() }
        }

        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            scope.launch { refresh() }
        }

        override fun onPackageChanged(packageName: String, user: UserHandle) {
            scope.launch { refresh() }
        }

        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            scope.launch { refresh() }
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            scope.launch { refresh() }
        }

        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<android.content.pm.ShortcutInfo>, user: UserHandle) {
        }
    }

    init {
        launcherApps.registerCallback(launcherAppsCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        context.registerComponentCallbacks(componentCallbacks)

        // Watch theme settings to invalidate icon cache without full app list refresh.
        scope.launch {
            settingsRepository.flow.collectLatest { settings ->
                val prev = currentSettings
                currentSettings = settings
                if (prev != null &&
                    (prev.themedIconsEnabled != settings.themedIconsEnabled ||
                     prev.themeAllIcons != settings.themeAllIcons ||
                     prev.iconPackPackageName != settings.iconPackPackageName)
                ) {
                    iconCache.clear()
                }
            }
        }
    }

    fun dispose() {
        try {
            launcherApps.unregisterCallback(launcherAppsCallback)
        } catch (_: Exception) { }
        try {
            context.unregisterComponentCallbacks(componentCallbacks)
        } catch (_: Exception) { }
    }

    suspend fun initialize() {
        val needsLoad = cacheMutex.withLock { cachedApps == null }
        if (needsLoad) {
            loadApps()
        }
    }

    fun getAllApps(): StateFlow<List<AppInfo>> = _apps

    /** Loads icon for the given package using current theme settings. */
    suspend fun getIcon(packageName: String, userSerialNumber: Long = 0L): Bitmap? {
        val s = currentSettings ?: settingsRepository.value
        val colors = getThemeColors(context)
        val cacheKey = buildString {
            append(packageName)
            append(":")
            append(userSerialNumber)
            append(":c=${colors.first}_${colors.third}")
            if (s.iconPackPackageName.isNotEmpty()) append(":pack=${s.iconPackPackageName}")
            if (s.themedIconsEnabled) {
                append(":themed")
                if (s.themeAllIcons) append(":all")
            }
        }

        val cached = iconCache[cacheKey]
        if (cached != null) return cached

        val icon = getIcon(packageName, s.themedIconsEnabled, s.themeAllIcons, s.iconPackPackageName, userSerialNumber)
        if (icon != null) {
            iconCache[cacheKey] = icon
        }
        return icon
    }

    /** Loads icon with explicit theme settings. Used by composables that need to key on settings. */
    suspend fun getIcon(
        packageName: String,
        themedIconsEnabled: Boolean,
        themeAllIcons: Boolean,
        iconPackPackageName: String,
        userSerialNumber: Long = 0L,
    ): Bitmap? {
        // ponytail: composite cache key so toggling themes doesn't serve stale icons.
        val colors = getThemeColors(context)
        val cacheKey = buildString {
            append(packageName)
            append(":")
            append(userSerialNumber)
            append(":c=${colors.first}_${colors.third}")
            if (iconPackPackageName.isNotEmpty()) append(":pack=$iconPackPackageName")
            if (themedIconsEnabled) {
                append(":themed")
                if (themeAllIcons) append(":all")
            }
        }

        val cached = iconCache[cacheKey]
        if (cached != null) return cached

        val icon =
            withContext(Dispatchers.IO) {
                loadAppIconBitmap(
                    context, packageName, iconSize,
                    themedIconsEnabled, themeAllIcons, iconPackPackageName,
                    userSerialNumber,
                )
            }

        if (icon != null) {
            iconCache[cacheKey] = icon
        }
        return icon
    }

    suspend fun refresh() {
        cacheMutex.withLock {
            cachedApps = null
            iconCache.clear()
        }
        loadApps()
    }

    private suspend fun loadApps() {
        val apps =
            withContext(Dispatchers.IO) {
                userManager.userProfiles.flatMap { user ->
                    val serialNumber = userManager.getSerialNumberForUser(user)
                    launcherApps.getActivityList(null, user).mapNotNull { activityInfo ->
                        val packageName = activityInfo.componentName.packageName
                        val label = activityInfo.label?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        AppInfo(packageName, label, serialNumber)
                    }
                }.distinctBy { "${it.packageName}:${it.userSerialNumber}" }
                 .sortedBy { it.label.lowercase() }
            }

        cacheMutex.withLock {
            cachedApps = apps
            _apps.value = apps
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppListRepository? = null

        fun getInstance(
            context: Context,
            iconSize: Int,
        ): AppListRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppListRepository(context.applicationContext, iconSize).also { INSTANCE = it }
            }
    }
}
