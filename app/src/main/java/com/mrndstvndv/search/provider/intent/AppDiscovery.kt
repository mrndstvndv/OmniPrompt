package com.mrndstvndv.search.provider.intent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import com.mrndstvndv.search.provider.apps.AppListRepository
import kotlinx.coroutines.flow.first

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Bitmap? = null
)

data class IntentOption(
    val action: String,
    val label: String,
    val mimeTypes: List<String> = emptyList()
)

class AppDiscovery(
    private val context: Context,
    private val appListRepository: AppListRepository
) {
    private val packageManager: PackageManager = context.packageManager

    suspend fun getTargetApps(): List<AppInfo> {
        // Ensure repository is initialized
        appListRepository.initialize()
        
        // Get all apps from repository
        val allApps = appListRepository.apps.value
        
        val targetActions = listOf(
            Intent.ACTION_SEND,
            Intent.ACTION_VIEW,
            Intent.ACTION_SENDTO
        )

        val filteredApps = mutableListOf<AppInfo>()

        for (app in allApps) {
            if (appHasAnyTargetIntent(app.packageName, targetActions)) {
                val icon = appListRepository.getIcon(app.packageName)
                filteredApps.add(AppInfo(app.packageName, app.label, icon))
            }
        }

        return filteredApps.sortedBy { it.name }
    }

    private fun appHasAnyTargetIntent(packageName: String, actions: List<String>): Boolean {
        for (action in actions) {
            val intent = Intent(action).apply {
                setPackage(packageName)
                if (action == Intent.ACTION_SEND) type = "*/*"
            }
            
            val resolves = try {
                packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            } catch (e: Exception) {
                emptyList()
            }
            
            if (resolves.isNotEmpty()) return true
            
            // Try again with common schemes for VIEW
            if (action == Intent.ACTION_VIEW) {
                val viewHttp = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://")).setPackage(packageName)
                if (packageManager.queryIntentActivities(viewHttp, 0).isNotEmpty()) return true
            }
            
            if (action == Intent.ACTION_SENDTO) {
                val sendtoMail = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).setPackage(packageName)
                if (packageManager.queryIntentActivities(sendtoMail, 0).isNotEmpty()) return true
            }
        }
        return false
    }

    fun getIntentsForApp(packageName: String): List<IntentOption> {
        val actions = mapOf(
            Intent.ACTION_SEND to "Share content",
            Intent.ACTION_VIEW to "Open URL / View content",
            Intent.ACTION_SENDTO to "Send to address"
        )

        val options = mutableListOf<IntentOption>()

        actions.forEach { (action, label) ->
            val intent = Intent(action).apply {
                setPackage(packageName)
                if (action == Intent.ACTION_SEND) type = "*/*"
            }
            
            var resolveInfos = try {
                packageManager.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
            } catch (e: Exception) {
                emptyList()
            }

            if (resolveInfos.isEmpty() && action == Intent.ACTION_VIEW) {
                val viewHttp = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://")).setPackage(packageName)
                resolveInfos = packageManager.queryIntentActivities(viewHttp, PackageManager.GET_RESOLVED_FILTER)
            }

            if (resolveInfos.isEmpty() && action == Intent.ACTION_SENDTO) {
                val sendtoMail = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).setPackage(packageName)
                resolveInfos = packageManager.queryIntentActivities(sendtoMail, PackageManager.GET_RESOLVED_FILTER)
            }
            
            if (resolveInfos.isNotEmpty()) {
                val mimeTypes = mutableSetOf<String>()
                resolveInfos.forEach { info ->
                    info.filter?.let { filter ->
                        for (i in 0 until filter.countDataTypes()) {
                            mimeTypes.add(filter.getDataType(i))
                        }
                    }
                }
                options.add(IntentOption(action, label, mimeTypes.toList().sorted()))
            }
        }

        if (options.isEmpty()) {
            options.add(IntentOption(Intent.ACTION_SEND, "Share content (Generic)"))
        }

        return options
    }
}
