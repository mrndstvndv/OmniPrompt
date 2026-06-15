package com.mrndstvndv.search.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class ScaledDrawable(private val drawable: Drawable, private val scale: Float) : Drawable() {
    override fun draw(canvas: Canvas) {
        val bounds = bounds
        canvas.save()
        canvas.scale(scale, scale, bounds.exactCenterX(), bounds.exactCenterY())
        drawable.draw(canvas)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        drawable.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        drawable.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        @Suppress("DEPRECATION")
        return drawable.opacity
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        drawable.bounds = bounds
    }

    override fun getIntrinsicWidth(): Int = drawable.intrinsicWidth
    override fun getIntrinsicHeight(): Int = drawable.intrinsicHeight

    override fun getPadding(padding: Rect): Boolean = drawable.getPadding(padding)
    override fun isStateful(): Boolean = drawable.isStateful
    override fun onStateChange(state: IntArray): Boolean = drawable.setState(state)
    override fun onLevelChange(level: Int): Boolean = drawable.setLevel(level)
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return super.setVisible(visible, restart) || drawable.setVisible(visible, restart)
    }
}


private fun getForcedMonochromeDrawable(
    original: Drawable,
    iconSize: Int,
    primaryColor: Int,
    surfaceColor: Int
): Drawable {
    val bitmap = original.toBitmapOrNull(iconSize) ?: return original
    val width = bitmap.width
    val height = bitmap.height
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    
    var totalLuminance = 0L
    var nonTransparentPixels = 0
    for (i in pixels.indices) {
        val color = pixels[i]
        val alpha = (color ushr 24) and 0xFF
        if (alpha > 50) {
            val r = (color ushr 16) and 0xFF
            val g = (color ushr 8) and 0xFF
            val b = color and 0xFF
            val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            totalLuminance += lum
            nonTransparentPixels++
        }
    }
    
    val avgLuminance = if (nonTransparentPixels > 0) totalLuminance / nonTransparentPixels else 128
    val coverage = nonTransparentPixels.toFloat() / pixels.size
    val invert = when {
        avgLuminance > 140 && coverage > 0.60f -> true // light background
        avgLuminance < 80 -> true // dark logo on transparent background
        else -> false
    }
    
    val surfR = (surfaceColor ushr 16) and 0xFF
    val surfG = (surfaceColor ushr 8) and 0xFF
    val surfB = surfaceColor and 0xFF
    
    val primR = (primaryColor ushr 16) and 0xFF
    val primG = (primaryColor ushr 8) and 0xFF
    val primB = primaryColor and 0xFF
    
    for (i in pixels.indices) {
        val color = pixels[i]
        val alpha = (color ushr 24) and 0xFF
        if (alpha == 0) continue
        
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        
        var lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        if (invert) {
            lum = 255 - lum
        }
        
        val ratio = lum / 255f
        
        val red = ((1f - ratio) * surfR + ratio * primR).toInt().coerceIn(0, 255)
        val green = ((1f - ratio) * surfG + ratio * primG).toInt().coerceIn(0, 255)
        val blue = ((1f - ratio) * surfB + ratio * primB).toInt().coerceIn(0, 255)
        
        val newColor = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        pixels[i] = newColor
    }
    
    output.setPixels(pixels, 0, width, 0, 0, width, height)
    return BitmapDrawable(null, output)
}

fun loadAppIconBitmap(
    pm: PackageManager,
    packageName: String,
    iconSize: Int,
    useThemedIcons: Boolean = false,
    forceThemedIcons: Boolean = false,
    selectedIconPack: String = "",
    context: android.content.Context? = null,
): Bitmap? {
    if (!isPackageInstalled(pm, packageName)) return null
    return runCatching {
        val app = pm.getApplicationInfo(packageName, 0)
        var loadedFromPack = false
        val iconDrawable = if (selectedIconPack.isNotEmpty() && context != null) {
            val pack = getIconPackInstance(context, selectedIconPack)
            val drawable = pack.getIconDrawable(packageName, pm)
            if (drawable != null) {
                loadedFromPack = true
                drawable
            } else {
                app.loadIcon(pm)
            }
        } else {
            app.loadIcon(pm)
        }
        if (useThemedIcons && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context != null) {
            val (primaryColor, onPrimaryColor, surfaceColor) = getThemeColors(context)
            val isDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val iconBgColor = if (isDark) surfaceColor else onPrimaryColor

            if (iconDrawable is AdaptiveIconDrawable) {
                val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    iconDrawable.monochrome
                } else {
                    null
                }
                if (monochrome != null) {
                    val mutatedMonochrome = monochrome.mutate().apply {
                        setTint(primaryColor)
                    }
                    val themedIcon = AdaptiveIconDrawable(ColorDrawable(iconBgColor), mutatedMonochrome)
                    return themedIcon.toBitmapOrNull(iconSize)
                }

                if (forceThemedIcons) {
                    val foreground = iconDrawable.foreground
                    val forcedMonochrome = getForcedMonochromeDrawable(foreground, iconSize, primaryColor, iconBgColor)
                    val scaledForeground = ScaledDrawable(forcedMonochrome, 1.5f)
                    val themedIcon = AdaptiveIconDrawable(ColorDrawable(iconBgColor), scaledForeground)
                    return themedIcon.toBitmapOrNull(iconSize)
                }
            } else {
                if (forceThemedIcons) {
                    val forcedMonochrome = getForcedMonochromeDrawable(iconDrawable, iconSize, primaryColor, iconBgColor)
                    val scaledForeground = ScaledDrawable(forcedMonochrome, 1.5f)
                    val themedIcon = AdaptiveIconDrawable(ColorDrawable(iconBgColor), scaledForeground)
                    return themedIcon.toBitmapOrNull(iconSize)
                }
            }
        }
        iconDrawable.toBitmapOrNull(iconSize)
    }.getOrNull()
}

fun isPackageInstalled(
    pm: PackageManager,
    packageName: String,
): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

internal fun Drawable.toBitmapOrNull(iconSize: Int): Bitmap? {
    val width = intrinsicWidth.takeIf { it > 0 } ?: iconSize
    val height = intrinsicHeight.takeIf { it > 0 } ?: iconSize
    setBounds(0, 0, width, height)
    return runCatching {
        toBitmap(width, height, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}

fun saveCustomIcon(
    context: android.content.Context,
    uri: android.net.Uri,
): String? {
    val dir = java.io.File(context.filesDir, "intent_icons")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    val file = java.io.File(dir, "${java.util.UUID.randomUUID()}.png")
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            java.io.FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        null
    }
}

fun getThemeColors(context: android.content.Context): Triple<Int, Int, Int> {
    val uiMode = context.resources.configuration.uiMode
    val isDark = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            val primary =
                context.getColor(
                    if (isDark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600,
                )
            val onPrimary =
                context.getColor(
                    if (isDark) android.R.color.system_accent1_800 else android.R.color.system_accent1_100,
                )
            val surface =
                context.getColor(
                    if (isDark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_100,
                )
            return Triple(primary, onPrimary, surface)
        } catch (_: Exception) {
            // fallback
        }
    }

    return if (isDark) {
        Triple(0xFFD0BCFF.toInt(), 0xFF381E72.toInt(), 0xFF1C1B1F.toInt())
    } else {
        Triple(0xFF6650A4.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
    }
}

fun createBadgedIcon(
    context: android.content.Context,
    baseIcon: Bitmap?,
    badgeResId: Int,
): Bitmap? {
    if (baseIcon == null) return null
    return try {
        val width = baseIcon.width
        val height = baseIcon.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)

        // Draw base app icon
        canvas.drawBitmap(baseIcon, 0f, 0f, null)

        // Draw badge in the bottom left
        val badgeDrawable = androidx.core.content.ContextCompat.getDrawable(context, badgeResId)
        if (badgeDrawable != null) {
            val badgeSize = (width * 0.35f).toInt()

            // Offset from bottom and left edges to avoid clipping
            // "+1 up and +1 left" relative to 0.05f
            val left = (width * 0.02f).toInt()
            val top = height - badgeSize - (width * 0.08f).toInt()

            // Get dynamic theme colors
            val (primaryColor, onPrimaryColor, surfaceColor) = getThemeColors(context)

            val centerX = left + badgeSize / 2f
            val centerY = top + badgeSize / 2f
            val innerRadius = badgeSize / 2f

            // Draw background circle following dynamic Material You theme colorSurface
            val bgPaint =
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = surfaceColor
                    style = android.graphics.Paint.Style.FILL
                }
            val bgRadius = innerRadius * 1.25f
            canvas.drawCircle(centerX, centerY, bgRadius, bgPaint)

            // Draw primary circle
            val innerPaint =
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = primaryColor
                    style = android.graphics.Paint.Style.FILL
                }
            canvas.drawCircle(centerX, centerY, innerRadius, innerPaint)

            // Draw white/onPrimary tinted share drawable inside the primary circle
            androidx.core.graphics.drawable.DrawableCompat.setTint(badgeDrawable, onPrimaryColor)

            val padding = (badgeSize * 0.20f).toInt()
            badgeDrawable.setBounds(
                (centerX - innerRadius + padding).toInt(),
                (centerY - innerRadius + padding).toInt(),
                (centerX + innerRadius - padding).toInt(),
                (centerY + innerRadius - padding).toInt(),
            )
            badgeDrawable.draw(canvas)
        }

        output
    } catch (e: Exception) {
        baseIcon
    }
}

data class IconPackInfo(
    val packageName: String,
    val label: String
)

fun getInstalledIconPacks(context: Context): List<IconPackInfo> {
    val pm = context.packageManager
    val iconPacks = mutableMapOf<String, IconPackInfo>()
    
    val intentActions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "solo.launcher.THEME"
    )
    val intentCategories = listOf(
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME"
    )
    
    for (action in intentActions) {
        val intent = android.content.Intent(action)
        val list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        for (info in list) {
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            iconPacks[packageName] = IconPackInfo(packageName, label)
        }
    }
    
    for (category in intentCategories) {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(category)
        }
        val list = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        for (info in list) {
            val packageName = info.activityInfo.packageName
            val label = info.loadLabel(pm).toString()
            iconPacks[packageName] = IconPackInfo(packageName, label)
        }
    }
    
    return iconPacks.values.toList().sortedBy { it.label }
}

class IconPack(private val context: Context, private val packageName: String) {
    private val iconMap = mutableMapOf<String, String>()
    private val packRes = try {
        context.packageManager.getResourcesForApplication(packageName)
    } catch (e: Exception) {
        null
    }

    init {
        loadAppFilter()
    }

    private fun loadAppFilter() {
        val packRes = this.packRes ?: return
        var parser: XmlPullParser? = null
        var inputStream: java.io.InputStream? = null
        try {
            try {
                inputStream = packRes.assets.open("appfilter.xml")
                val factory = XmlPullParserFactory.newInstance()
                parser = factory.newPullParser()
                parser.setInput(inputStream, "UTF-8")
            } catch (e: Exception) {
                val resId = packRes.getIdentifier("appfilter", "xml", packageName)
                if (resId != 0) {
                    parser = packRes.getXml(resId)
                }
            }

            if (parser != null) {
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val component = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (component != null && drawable != null) {
                            iconMap[component] = drawable
                        }
                    }
                    eventType = parser.next()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            inputStream?.close()
        }
    }

    fun getIconDrawable(appPackageName: String, pm: PackageManager): Drawable? {
        val packRes = this.packRes ?: return null
        val launchIntent = pm.getLaunchIntentForPackage(appPackageName)
        val componentName = launchIntent?.component
        val componentInfoStr = if (componentName != null) {
            "ComponentInfo{${componentName.packageName}/${componentName.className}}"
        } else {
            null
        }

        var drawableName: String? = null
        if (componentInfoStr != null) {
            drawableName = iconMap[componentInfoStr]
        }
        
        if (drawableName == null) {
            val keyPrefix = "ComponentInfo{$appPackageName/"
            val matchingKey = iconMap.keys.firstOrNull { it.startsWith(keyPrefix) }
            if (matchingKey != null) {
                drawableName = iconMap[matchingKey]
            }
        }

        if (drawableName == null) return null

        val resId = packRes.getIdentifier(drawableName, "drawable", packageName)
        return if (resId != 0) {
            try {
                androidx.core.content.res.ResourcesCompat.getDrawable(packRes, resId, null)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

private val iconPackCache = mutableMapOf<String, IconPack>()

@Synchronized
fun getIconPackInstance(context: Context, packageName: String): IconPack {
    return iconPackCache.getOrPut(packageName) {
        IconPack(context.applicationContext, packageName)
    }
}
