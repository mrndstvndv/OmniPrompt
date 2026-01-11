package com.mrndstvndv.search.provider.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import com.mrndstvndv.search.provider.apps.models.AppInfo
import com.mrndstvndv.search.util.loadAppIconBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppListRepository private constructor(
    private val context: Context,
    private val iconSize: Int,
) {
    private val packageManager = context.packageManager
    private val cacheMutex = Mutex()
    private var cachedApps: List<AppInfo>? = null
    private val iconCache = mutableMapOf<String, Bitmap?>()
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

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

    init {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        context.registerReceiver(packageChangeReceiver, filter)
    }

    suspend fun initialize() {
        if (cachedApps == null) {
            loadApps()
        }
    }

    fun getAllApps(): StateFlow<List<AppInfo>> = _apps

    suspend fun getIcon(packageName: String): Bitmap? {
        iconCache[packageName]?.let { return it }

        return cacheMutex.withLock {
            iconCache[packageName]?.let { return it }

            val icon =
                withContext(Dispatchers.IO) {
                    loadAppIconBitmap(packageManager, packageName, iconSize)
                }
            iconCache[packageName] = icon
            icon
        }
    }

    suspend fun refresh() {
        cachedApps = null
        iconCache.clear()
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
