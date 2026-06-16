package com.mrndstvndv.search.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.mrndstvndv.search.R
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(
    val packageName: String,
    val label: String,
)

object IconPackManager {
    private val iconPackCache = ConcurrentHashMap<String, Map<String, String>>()

    fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val packs = mutableMapOf<String, IconPackInfo>()

        val intentActions = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "solo.launcher.THEME",
        )
        val intentCategories = listOf(
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
        )

        for (action in intentActions) {
            val apps = pm.queryIntentActivities(
                Intent(action), PackageManager.GET_META_DATA,
            )
            for (app in apps) {
                val packageName = app.activityInfo.packageName
                val label = app.activityInfo.loadLabel(pm).toString()
                packs[packageName] = IconPackInfo(packageName, label)
            }
        }

        for (category in intentCategories) {
            val apps = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).apply { addCategory(category) },
                PackageManager.GET_META_DATA,
            )
            for (app in apps) {
                val packageName = app.activityInfo.packageName
                val label = app.activityInfo.loadLabel(pm).toString()
                packs[packageName] = IconPackInfo(packageName, label)
            }
        }

        return packs.values.toList().sortedBy { it.label }
    }

    fun getIconPackLabel(context: Context, packageName: String): String {
        if (packageName.isEmpty()) return context.getString(R.string.settings_icon_pack_default)
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun getIconFromPack(
        context: Context,
        iconPackPackage: String,
        packageName: String,
        launcherActivity: String?,
    ): Drawable? {
        if (iconPackPackage.isEmpty()) return null
        val pm = context.packageManager

        val map = getAppFilterMap(context, iconPackPackage)
        if (map.isEmpty()) return null

        var drawableName = if (launcherActivity != null) {
            map["$packageName/$launcherActivity"]
        } else {
            null
        }

        if (drawableName == null) {
            drawableName = map[packageName]
        }

        if (drawableName == null) return null

        return runCatching {
            val res = pm.getResourcesForApplication(iconPackPackage)
            val resId = res.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (resId != 0) {
                ResourcesCompat.getDrawable(res, resId, null)
            } else {
                null
            }
        }.getOrNull()
    }

    private val appFilterLoadingMutex = Any()

    private fun getAppFilterMap(context: Context, iconPackPackage: String): Map<String, String> {
        val cached = iconPackCache[iconPackPackage]
        if (cached != null) return cached

        return synchronized(appFilterLoadingMutex) {
            iconPackCache.getOrPut(iconPackPackage) {
                runCatching {
                    val pm = context.packageManager
                    val appFilterMap = mutableMapOf<String, String>()
                    var loaded = false

                    try {
                        val res = pm.getResourcesForApplication(iconPackPackage)
                        val resId = res.getIdentifier("appfilter", "xml", iconPackPackage)
                        if (resId != 0) {
                            val parser = res.getXml(resId)
                            appFilterMap.putAll(parseAppFilter(parser))
                            loaded = true
                        }
                    } catch (_: Exception) { }

                    if (!loaded) {
                        try {
                            val iconPackContext = context.createPackageContext(
                                iconPackPackage, Context.CONTEXT_IGNORE_SECURITY,
                            )
                            val inputStream = iconPackContext.assets.open("appfilter.xml")
                            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                            val parser = factory.newPullParser()
                            parser.setInput(inputStream, "UTF-8")
                            appFilterMap.putAll(parseAppFilter(parser))
                            inputStream.close()
                        } catch (_: Exception) { }
                    }

                    appFilterMap
                }.getOrDefault(emptyMap())
            }
        }
    }

    private fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (component != null && drawable != null) {
                    val cleaned = cleanComponent(component)
                    if (cleaned != null) {
                        map[cleaned] = drawable
                        val pkg = cleaned.substringBefore("/")
                        if (!map.containsKey(pkg)) {
                            map[pkg] = drawable
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return map
    }

    private fun cleanComponent(component: String): String? {
        val startIndex = component.indexOf('{')
        val endIndex = component.indexOf('}')
        val inner = if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            component.substring(startIndex + 1, endIndex)
        } else {
            component
        }
        val parts = inner.trim().split('/')
        if (parts.size == 2) {
            val pkg = parts[0].trim()
            var cls = parts[1].trim()
            if (cls.startsWith(".")) {
                cls = pkg + cls
            }
            return "$pkg/$cls"
        } else if (parts.size == 1 && parts[0].isNotBlank()) {
            return parts[0].trim()
        }
        return null
    }
}
