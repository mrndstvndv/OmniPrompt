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
    private val _catalog = MutableStateFlow(AppCatalog.EMPTY)
    val catalog: StateFlow<AppCatalog> = _catalog

    private data class AppKey(
        val packageName: String,
        val userSerialNumber: Long,
    )

    private val diskCache = AppCatalogDiskCache(context)
    private var provisionalLoadAttempted = false

    private var appsByKey: MutableMap<AppKey, AppInfo> = linkedMapOf()
    private var catalogGeneration: Long = 0L
    private var isInitialized = false

    // ponytail: reads theme settings internally so iconLoader lambdas stay parameterless.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private val settingsRepository by lazy {
        (context.applicationContext as SearchApplication).container.appSearchSettingsRepo
    }
    private val appearanceSettingsRepository by lazy {
        (context.applicationContext as SearchApplication).container.settingsRepository
    }
    private var currentSettings: AppSearchSettings? = null

    @Volatile
    private var cachedColors: Triple<Int, Int, Int>? = null

    private fun getCachedThemeColors(): Triple<Int, Int, Int> {
        val existing = cachedColors
        if (existing != null) return existing
        return getThemeColors(context).also { cachedColors = it }
    }

    private fun getIconBackgroundAlpha(): Float =
        appearanceSettingsRepository.appListIconBackgroundTransparency.value.coerceIn(0f, 1f)

    @Suppress("OVERRIDE_DEPRECATION")
    private val componentCallbacks =
        object : ComponentCallbacks {
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

    private val launcherAppsCallback =
        object : LauncherApps.Callback() {
            @Deprecated("Deprecated in Java")
            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) {
                scope.launch { upsertPackage(packageName, user) }
            }

            @Deprecated("Deprecated in Java")
            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) {
                scope.launch { removePackage(packageName, user) }
            }

            @Deprecated("Deprecated in Java")
            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) {
                scope.launch { upsertPackage(packageName, user, forceRefresh = true) }
            }

            @Deprecated("Deprecated in Java")
            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                scope.launch { upsertPackages(packageNames, user, forceRefresh = true) }
            }

            @Deprecated("Deprecated in Java")
            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                scope.launch {
                    if (replacing) {
                        invalidatePackages(packageNames, user)
                    } else {
                        removePackages(packageNames, user)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<android.content.pm.ShortcutInfo>,
                user: UserHandle,
            ) {
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
        } catch (_: Exception) {
        }
        try {
            context.unregisterComponentCallbacks(componentCallbacks)
        } catch (_: Exception) {
        }
    }

    suspend fun initialize() {
        loadProvisionalCacheIfNeeded()
        reloadAllApps(onlyIfUninitialized = true)
    }

    /**
     * Seeds the catalog from disk on cold start so the first search has
     * *something* correct to show while the real LauncherApps scan runs.
     * No-ops once the real scan has already published (isInitialized) or if
     * this has already been attempted this process lifetime.
     */
    private suspend fun loadProvisionalCacheIfNeeded() {
        cacheMutex.withLock {
            if (isInitialized || provisionalLoadAttempted) return
            provisionalLoadAttempted = true
            val cached = withContext(Dispatchers.IO) { diskCache.read() } ?: return
            appsByKey =
                cached.associateBy { AppKey(it.packageName, it.userSerialNumber) }.toMutableMap()
            publishCatalogLocked()
        }
    }

    /** Loads icon for the given package using current theme settings. */
    suspend fun getIcon(
        packageName: String,
        userSerialNumber: Long = 0L,
    ): Bitmap? {
        val s = currentSettings ?: settingsRepository.value
        val colors = getCachedThemeColors()
        val backgroundAlpha = getIconBackgroundAlpha()
        val cacheKey =
            buildString {
                append(packageName)
                append(":")
                append(userSerialNumber)
                append(":c=${colors.first}_${colors.third}")
                append(":ba=$backgroundAlpha")
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
        val backgroundAlpha = getIconBackgroundAlpha()
        val cacheKey =
            buildString {
                append(packageName)
                append(":")
                append(userSerialNumber)
                append(":c=${colors.first}_${colors.third}")
                append(":ba=$backgroundAlpha")
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
                    backgroundAlpha = backgroundAlpha,
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

    private suspend fun reloadAllApps(onlyIfUninitialized: Boolean = false) {
        cacheMutex.withLock {
            if (onlyIfUninitialized && isInitialized) return

            val includeWorkApps = settingsRepository.value.includeWorkApps
            val apps =
                withContext(Dispatchers.IO) {
                    userManager.userProfiles
                        .filter { user -> includeWorkApps || !isWorkProfile(user) }
                        .flatMap { user ->
                            val serialNumber = userManager.getSerialNumberForUser(user)
                            launcherApps.getActivityList(null, user).mapNotNull { activityInfo ->
                                val packageName = activityInfo.componentName.packageName
                                val label =
                                    activityInfo.label
                                        ?.toString()
                                        ?.takeIf { it.isNotBlank() }
                                        ?: return@mapNotNull null
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
        forceRefresh: Boolean = false,
    ) {
        cacheMutex.withLock {
            val update =
                withContext(Dispatchers.IO) {
                    if (!isUserIncluded(user)) return@withContext null
                    val userSerialNumber = userManager.getSerialNumberForUser(user)
                    if (userSerialNumber < 0) return@withContext null
                    resolveAppInfoForUserPackage(packageName, user, userSerialNumber)
                        .let { userSerialNumber to it }
                }
                    ?: return
            val (userSerialNumber, appInfo) = update
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

            if (!changed && !forceRefresh) return
            evictIconCacheForPackage(packageName)
            publishCatalogLocked()
        }
    }

    private suspend fun upsertPackages(
        packageNames: Array<out String>,
        user: UserHandle,
        forceRefresh: Boolean = false,
    ) {
        cacheMutex.withLock {
            if (packageNames.isEmpty()) return
            val resolved =
                withContext(Dispatchers.IO) {
                    if (!isUserIncluded(user)) return@withContext null
                    val userSerialNumber = userManager.getSerialNumberForUser(user)
                    if (userSerialNumber < 0) return@withContext null
                    val updates =
                        packageNames.associateWith { packageName ->
                            resolveAppInfoForUserPackage(packageName, user, userSerialNumber)
                        }
                    userSerialNumber to updates
                }
                    ?: return
            val (userSerialNumber, updates) = resolved
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

            if (!changed && !forceRefresh) return
            packageNames.forEach { evictIconCacheForPackage(it) }
            publishCatalogLocked()
        }
    }

    private suspend fun removePackage(
        packageName: String,
        user: UserHandle,
    ) {
        cacheMutex.withLock {
            val userSerialNumber =
                withContext(Dispatchers.IO) { userManager.getSerialNumberForUser(user) }
            if (userSerialNumber < 0) return
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
        cacheMutex.withLock {
            if (packageNames.isEmpty()) return
            val userSerialNumber =
                withContext(Dispatchers.IO) { userManager.getSerialNumberForUser(user) }
            if (userSerialNumber < 0) return
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

    private suspend fun invalidatePackages(
        packageNames: Array<out String>,
        user: UserHandle,
    ) {
        cacheMutex.withLock {
            if (packageNames.isEmpty()) return
            val isValidUser =
                withContext(Dispatchers.IO) {
                    isUserIncluded(user) && userManager.getSerialNumberForUser(user) >= 0
                }
            if (!isValidUser) return

            packageNames.forEach { evictIconCacheForPackage(it) }
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
        val sortedApps =
            appsByKey.values.sortedWith(
                compareBy<AppInfo> { it.labelLower }
                    .thenBy { it.packageNameLower }
                    .thenBy { it.userSerialNumber },
            )
        catalogGeneration += 1
        _catalog.value =
            AppCatalog(
                generation = catalogGeneration,
                apps = sortedApps,
                packageNames = sortedApps.map { it.packageName }.toSet(),
            )
        persistCatalogAsync(sortedApps)
    }

    /**
     * Fire-and-forget disk write, kept off [cacheMutex] since [sortedApps] is
     * already an immutable snapshot — writing it doesn't need the lock, and
     * not holding the lock during file I/O keeps queries/upserts unblocked.
     */
    private fun persistCatalogAsync(sortedApps: List<AppInfo>) {
        scope.launch(Dispatchers.IO) { diskCache.write(sortedApps) }
    }

    /**
     * Immediately drops one entry — used when a search result turns out to be
     * stale (the user tapped an app that's no longer installed) so the list
     * self-heals without waiting for the next full reconciliation pass.
     */
    suspend fun removeStaleEntry(
        packageName: String,
        userSerialNumber: Long,
    ) {
        cacheMutex.withLock {
            val removed = appsByKey.remove(AppKey(packageName, userSerialNumber)) != null
            if (!removed) return
            evictIconCacheForPackage(packageName)
            publishCatalogLocked()
        }
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
