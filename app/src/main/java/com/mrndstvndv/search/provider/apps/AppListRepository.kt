package com.mrndstvndv.search.provider.apps

import android.content.ComponentCallbacks
import android.content.Context
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

data class AppCatalog(
    val generation: Long,
    val apps: List<AppInfo>,
    val packageNames: Set<String>,
) {
    companion object {
        val EMPTY = AppCatalog(generation = 0L, apps = emptyList(), packageNames = emptySet())
    }
}

@Suppress("OVERRIDE_DEPRECATION")
class AppListRepository private constructor(
    private val context: Context,
    private val iconSize: Int,
) {
    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    private val cacheMutex = Mutex()
    private val iconCache = ConcurrentHashMap<String, Bitmap>()
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps
    private val _catalog = MutableStateFlow(AppCatalog.EMPTY)
    val catalog: StateFlow<AppCatalog> = _catalog

    private data class AppKey(
        val packageName: String,
        val userSerialNumber: Long,
    )

    private var appsByKey: MutableMap<AppKey, AppInfo> = linkedMapOf()
    private var catalogGeneration: Long = 0L
    private var isInitialized = false

    // ponytail: reads theme settings internally so iconLoader lambdas stay parameterless.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val settingsRepository by lazy {
        (context.applicationContext as SearchApplication).container.appSearchSettingsRepo
    }
    private var currentSettings: AppSearchSettings? = null

    @Volatile
    private var cachedColors: Triple<Int, Int, Int>? = null

    private fun getCachedThemeColors(): Triple<Int, Int, Int> {
        val existing = cachedColors
        if (existing != null) return existing
        return getThemeColors(context).also { cachedColors = it }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    private val componentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            cachedColors = null
            iconCache.clear()
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {
            iconCache.clear()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val launcherAppsCallback = object : LauncherApps.Callback() {
        @Deprecated("Deprecated in Java")
        override fun onPackageAdded(packageName: String, user: UserHandle) {
            scope.launch { upsertPackage(packageName, user) }
        }

        @Deprecated("Deprecated in Java")
        override fun onPackageRemoved(packageName: String, user: UserHandle) {
            scope.launch { removePackage(packageName, user) }
        }

        @Deprecated("Deprecated in Java")
        override fun onPackageChanged(packageName: String, user: UserHandle) {
            scope.launch { upsertPackage(packageName, user) }
        }

        @Deprecated("Deprecated in Java")
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            scope.launch { upsertPackages(packageNames, user) }
        }

        @Deprecated("Deprecated in Java")
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
            scope.launch { removePackages(packageNames, user) }
        }

        @Deprecated("Deprecated in Java")
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
                if (prev != null) {
                    if (prev.themedIconsEnabled != settings.themedIconsEnabled ||
                        prev.themeAllIcons != settings.themeAllIcons ||
                        prev.iconPackPackageName != settings.iconPackPackageName
                    ) {
                        cachedColors = null
                        iconCache.clear()
                    }
                    if (prev.includeWorkApps != settings.includeWorkApps) {
                        refresh()
                    }
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
        val needsLoad = cacheMutex.withLock { !isInitialized }
        if (needsLoad) {
            reloadAllApps()
        }
    }

    fun getAllApps(): StateFlow<List<AppInfo>> = _apps

    /** Loads icon for the given package using current theme settings. */
    suspend fun getIcon(packageName: String, userSerialNumber: Long = 0L): Bitmap? {
        val s = currentSettings ?: settingsRepository.value
        val colors = getCachedThemeColors()
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
        val colors = getCachedThemeColors()
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
        iconCache.clear()
        reloadAllApps()
    }

    private suspend fun reloadAllApps() {
        val settings = settingsRepository.value
        val includeWorkApps = settings.includeWorkApps
        val apps =
            withContext(Dispatchers.IO) {
                userManager.userProfiles.filter { user ->
                    includeWorkApps || !isWorkProfile(user)
                }.flatMap { user ->
                    val serialNumber = userManager.getSerialNumberForUser(user)
                    launcherApps.getActivityList(null, user).mapNotNull { activityInfo ->
                        val packageName = activityInfo.componentName.packageName
                        val label = activityInfo.label?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        AppInfo(packageName, label, serialNumber)
                    }
                }.distinctBy { "${it.packageName}:${it.userSerialNumber}" }
                 .sortedBy { it.label.lowercase() }
            }

        val newMap =
            apps.associateBy {
                AppKey(
                    packageName = it.packageName,
                    userSerialNumber = it.userSerialNumber,
                )
            }

        cacheMutex.withLock {
            isInitialized = true
            if (appsByKey == newMap) {
                return
            }

            appsByKey = newMap.toMutableMap()
            publishCatalogLocked()
        }
    }

    private suspend fun upsertPackage(
        packageName: String,
        user: UserHandle,
    ) {
        if (!isUserIncluded(user)) return
        val userSerialNumber = userManager.getSerialNumberForUser(user)
        if (userSerialNumber < 0) return

        val appInfo =
            withContext(Dispatchers.IO) {
                resolveAppInfoForUserPackage(packageName, user, userSerialNumber)
            }

        cacheMutex.withLock {
            val key = AppKey(packageName, userSerialNumber)
            val previous = appsByKey[key]

            val changed =
                if (appInfo == null) {
                    appsByKey.remove(key) != null
                } else {
                    if (previous != appInfo) {
                        appsByKey[key] = appInfo
                        true
                    } else {
                        false
                    }
                }

            if (!changed) return
            evictIconCacheForPackage(packageName)
            publishCatalogLocked()
        }
    }

    private suspend fun upsertPackages(
        packageNames: Array<out String>,
        user: UserHandle,
    ) {
        if (!isUserIncluded(user) || packageNames.isEmpty()) return
        val userSerialNumber = userManager.getSerialNumberForUser(user)
        if (userSerialNumber < 0) return

        val updates =
            withContext(Dispatchers.IO) {
                packageNames.associateWith { packageName ->
                    resolveAppInfoForUserPackage(packageName, user, userSerialNumber)
                }
            }

        cacheMutex.withLock {
            var changed = false
            updates.forEach { (packageName, appInfo) ->
                val key = AppKey(packageName, userSerialNumber)
                val previous = appsByKey[key]
                if (appInfo == null) {
                    if (appsByKey.remove(key) != null) {
                        changed = true
                    }
                } else if (previous != appInfo) {
                    appsByKey[key] = appInfo
                    changed = true
                }
            }

            if (!changed) return
            packageNames.forEach { evictIconCacheForPackage(it) }
            publishCatalogLocked()
        }
    }

    private suspend fun removePackage(
        packageName: String,
        user: UserHandle,
    ) {
        val userSerialNumber = userManager.getSerialNumberForUser(user)
        if (userSerialNumber < 0) return

        cacheMutex.withLock {
            val removed = appsByKey.remove(AppKey(packageName, userSerialNumber)) != null
            if (!removed) return
            evictIconCacheForPackage(packageName)
            publishCatalogLocked()
        }
    }

    private suspend fun removePackages(
        packageNames: Array<out String>,
        user: UserHandle,
    ) {
        if (packageNames.isEmpty()) return
        val userSerialNumber = userManager.getSerialNumberForUser(user)
        if (userSerialNumber < 0) return

        cacheMutex.withLock {
            var changed = false
            packageNames.forEach { packageName ->
                val removed = appsByKey.remove(AppKey(packageName, userSerialNumber)) != null
                if (removed) {
                    changed = true
                    evictIconCacheForPackage(packageName)
                }
            }

            if (!changed) return
            publishCatalogLocked()
        }
    }

    private fun resolveAppInfoForUserPackage(
        packageName: String,
        user: UserHandle,
        userSerialNumber: Long,
    ): AppInfo? {
        val activityInfo =
            launcherApps.getActivityList(packageName, user).firstOrNull { activity ->
                activity.label?.toString()?.isNotBlank() == true
            } ?: return null

        val label = activityInfo.label.toString().takeIf { it.isNotBlank() } ?: return null
        return AppInfo(packageName = packageName, label = label, userSerialNumber = userSerialNumber)
    }

    private fun publishCatalogLocked() {
        val sortedApps = appsByKey.values.sortedBy { it.labelLower }
        catalogGeneration += 1
        _apps.value = sortedApps
        _catalog.value =
            AppCatalog(
                generation = catalogGeneration,
                apps = sortedApps,
                packageNames = sortedApps.map { it.packageName }.toSet(),
            )
    }

    private fun evictIconCacheForPackage(packageName: String) {
        val prefix = "$packageName:"
        iconCache.keys.removeIf { key -> key.startsWith(prefix) }
    }

    private fun isUserIncluded(user: UserHandle): Boolean {
        val includeWorkApps = settingsRepository.value.includeWorkApps
        return includeWorkApps || !isWorkProfile(user)
    }

    private fun isWorkProfile(user: UserHandle): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val crossProfileApps = context.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE) as? android.content.pm.CrossProfileApps
            if (crossProfileApps?.isManagedProfile(user) == true) {
                return true
            }
        }
        return user != android.os.Process.myUserHandle()
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
