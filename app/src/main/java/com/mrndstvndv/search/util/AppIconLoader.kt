package com.mrndstvndv.search.util

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap

// ponytail: simple extractAlpha + PorterDuff forced theme, no ScaledDrawable wrapper.
// Upgrade to per-pixel luminance analysis if specific icons look off.

fun loadAppIconBitmap(
    pm: PackageManager,
    packageName: String,
    iconSize: Int,
): Bitmap? {
    if (!isPackageInstalled(pm, packageName)) return null
    return runCatching {
        val app = pm.getApplicationInfo(packageName, 0)
        app.loadIcon(pm).toBitmapOrNull(iconSize)
    }.getOrNull()
}

fun loadAppIconBitmap(
    context: android.content.Context,
    packageName: String,
    iconSize: Int,
    themedIconsEnabled: Boolean,
    themeAllIcons: Boolean,
    iconPackPackageName: String,
): Bitmap? {
    val pm = context.packageManager
    val launcherActivity = pm.getLaunchIntentForPackage(packageName)?.component?.className
    var drawable = if (iconPackPackageName.isNotEmpty()) {
        IconPackManager.getIconFromPack(context, iconPackPackageName, packageName, launcherActivity)
    } else {
        null
    }

    if (drawable == null) {
        if (!isPackageInstalled(pm, packageName)) return null
        drawable = runCatching {
            val app = pm.getApplicationInfo(packageName, 0)
            app.loadIcon(pm)
        }.getOrNull()
    }

    if (drawable == null) return null

    var themedBitmap: Bitmap? = null
    if (themedIconsEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && drawable is AdaptiveIconDrawable) {
            val monochrome = drawable.monochrome
            if (monochrome != null) {
                val (primaryColor, _, surfaceColor) = getThemeColors(context)
                themedBitmap = createThemedAdaptiveIcon(monochrome, primaryColor, surfaceColor, iconSize)
            }
        }

        if (themedBitmap == null && themeAllIcons) {
            val (primaryColor, _, surfaceColor) = getThemeColors(context)
            val originalBitmap = drawable.toBitmapOrNull(iconSize)
            if (originalBitmap != null) {
                themedBitmap = createForcedThemedIcon(originalBitmap, primaryColor, surfaceColor, iconSize)
            }
        }
    }

    return themedBitmap ?: drawable.toBitmapOrNull(iconSize)
}

private fun createThemedAdaptiveIcon(
    monochrome: Drawable,
    primaryColor: Int,
    surfaceColor: Int,
    iconSize: Int,
): Bitmap? {
    return runCatching {
        val output = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = surfaceColor
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(iconSize / 2f, iconSize / 2f, iconSize / 2f, paint)

        val tintedMonochrome = monochrome.mutate()
        DrawableCompat.setTint(tintedMonochrome, primaryColor)

        // Native monochrome layer of an adaptive icon already has safety margins built-in.
        tintedMonochrome.setBounds(0, 0, iconSize, iconSize)
        tintedMonochrome.draw(canvas)
        output
    }.getOrNull()
}

private fun createForcedThemedIcon(
    originalBitmap: Bitmap,
    primaryColor: Int,
    surfaceColor: Int,
    iconSize: Int,
): Bitmap? {
    return runCatching {
        val output = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = surfaceColor
            style = android.graphics.Paint.Style.FILL
        }
        canvas.drawCircle(iconSize / 2f, iconSize / 2f, iconSize / 2f, paint)

        val alphaBitmap = originalBitmap.extractAlpha()
        val paintTint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.PorterDuffColorFilter(primaryColor, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        // Scale non-adaptive icons slightly down to fit inside the background circle.
        val targetSize = (iconSize * 0.72f).toInt()
        val offset = (iconSize - targetSize) / 2f
        val srcRect = android.graphics.Rect(0, 0, alphaBitmap.width, alphaBitmap.height)
        val destRect = android.graphics.RectF(offset, offset, offset + targetSize, offset + targetSize)
        canvas.drawBitmap(alphaBitmap, srcRect, destRect, paintTint)
        alphaBitmap.recycle()
        output
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
    } catch (_: Exception) {
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
        } catch (_: Exception) { }
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

        canvas.drawBitmap(baseIcon, 0f, 0f, null)

        val badgeDrawable = androidx.core.content.ContextCompat.getDrawable(context, badgeResId)
        if (badgeDrawable != null) {
            val badgeSize = (width * 0.35f).toInt()
            val left = (width * 0.02f).toInt()
            val top = height - badgeSize - (width * 0.08f).toInt()
            val (primaryColor, onPrimaryColor, surfaceColor) = getThemeColors(context)

            val centerX = left + badgeSize / 2f
            val centerY = top + badgeSize / 2f
            val innerRadius = badgeSize / 2f

            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = surfaceColor
                style = android.graphics.Paint.Style.FILL
            }
            val bgRadius = innerRadius * 1.25f
            canvas.drawCircle(centerX, centerY, bgRadius, bgPaint)

            val innerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = primaryColor
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, innerRadius, innerPaint)

            DrawableCompat.setTint(badgeDrawable, onPrimaryColor)
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
    } catch (_: Exception) {
        baseIcon
    }
}
