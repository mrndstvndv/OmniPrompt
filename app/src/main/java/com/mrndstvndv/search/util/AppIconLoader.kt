package com.mrndstvndv.search.util

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap

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
