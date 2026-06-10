package com.mrndstvndv.search.util

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap

fun loadAppIconBitmap(pm: PackageManager, packageName: String, iconSize: Int): Bitmap? {
    if (!isPackageInstalled(pm, packageName)) return null
    return runCatching {
        val app = pm.getApplicationInfo(packageName, 0)
        app.loadIcon(pm).toBitmapOrNull(iconSize)
    }.getOrNull()
}

fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
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

fun saveCustomIcon(context: android.content.Context, uri: android.net.Uri): String? {
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

fun createBadgedIcon(context: android.content.Context, baseIcon: Bitmap?, badgeResId: Int): Bitmap? {
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
            val left = 0
            val top = height - badgeSize
            val right = badgeSize
            val bottom = height
            
            // Draw background white circle for the badge to stand out
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
            val centerX = left + badgeSize / 2f
            val centerY = top + badgeSize / 2f
            val radius = (badgeSize / 2f) * 1.2f
            canvas.drawCircle(centerX, centerY, radius, bgPaint)
            
            val innerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
            }
            val primaryColor = resolveThemeColor(context, "colorPrimary", 0xFF6200EE.toInt())
            innerPaint.color = primaryColor
            canvas.drawCircle(centerX, centerY, badgeSize / 2f, innerPaint)

            // Draw white tinted share drawable inside the primary circle
            val tintColor = resolveThemeColor(context, "colorOnPrimary", android.graphics.Color.WHITE)
            androidx.core.graphics.drawable.DrawableCompat.setTint(badgeDrawable, tintColor)
            
            val padding = (badgeSize * 0.15f).toInt()
            badgeDrawable.setBounds(left + padding, top + padding, right - padding, bottom - padding)
            badgeDrawable.draw(canvas)
        }
        
        output
    } catch (e: Exception) {
        baseIcon
    }
}

fun resolveThemeColor(context: android.content.Context, attrName: String, fallbackColor: Int): Int {
    val resId = context.resources.getIdentifier(attrName, "attr", context.packageName)
    if (resId == 0) return fallbackColor
    val typedValue = android.util.TypedValue()
    return if (context.theme.resolveAttribute(resId, typedValue, true)) {
        typedValue.data
    } else {
        fallbackColor
    }
}

