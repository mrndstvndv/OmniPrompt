package com.mrndstvndv.search.provider.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import com.mrndstvndv.search.SearchApplication
import com.mrndstvndv.search.provider.apps.models.AppInfo
import com.mrndstvndv.search.provider.settings.AppSearchSettings
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

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val packageChangeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_REMOVED,
                    Intent.ACTION_PACKAGE_REPLACED,
                    -> {
                        scope.launch { refresh() }
                    }
                }
            }
        }

    @Volatile
    private var isReceiverRegistered = true

    init {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        context.registerReceiver(packageChangeReceiver, filter)

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
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(packageChangeReceiver)
                isReceiverRegistered = false
            } catch (_: IllegalArgumentException) { }
        }
    }

    suspend fun initialize() {
        val needsLoad = cacheMutex.withLock { cachedApps == null }
        if (needsLoad) {
            loadApps()
        }
    }

    fun getAllApps(): StateFlow<List<AppInfo>> = _apps

    /** Loads icon for the given package using current theme settings. */
    suspend fun getIcon(packageName: String): Bitmap? {
        val s = currentSettings ?: settingsRepository.value
        return getIcon(packageName, s.themedIconsEnabled, s.themeAllIcons, s.iconPackPackageName)
    }

    /** Loads icon with explicit theme settings. Used by composables that need to key on settings. */
    suspend fun getIcon(
        packageName: String,
        themedIconsEnabled: Boolean,
        themeAllIcons: Boolean,
        iconPackPackageName: String,
    ): Bitmap? {
        // ponytail: composite cache key so toggling themes doesn't serve stale icons.
        val cacheKey = buildString {
            append(packageName)
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
                val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
                packageManager
                    .queryIntentActivities(intent, 0)
                    .mapNotNull { resolveInfo ->
                        val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                        val label =
                            resolveInfo.loadLabel(packageManager).toString().takeIf { it.isNotBlank() }
                                ?: return@mapNotNull null
                        AppInfo(packageName, label)
                    }.distinctBy { it.packageName }
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
