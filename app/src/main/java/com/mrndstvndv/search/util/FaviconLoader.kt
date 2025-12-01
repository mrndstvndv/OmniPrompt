package com.mrndstvndv.search.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility for fetching, saving, and loading favicons for quicklinks.
 * Favicons are stored in the app's internal storage under files/favicons/{id}.png
 */
object FaviconLoader {

    private const val FAVICONS_DIR = "favicons"
    private const val FAVICON_SIZE = 64
    private const val CONNECTION_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    /**
     * Fetches a favicon for the given URL using Google's favicon service.
     * @param url The website URL to fetch favicon for
     * @param context Android context
     * @return Bitmap if successful, null otherwise
     */
    suspend fun fetchFavicon(url: String, context: Context): Bitmap? = withContext(Dispatchers.IO) {
        val domain = extractDomain(url) ?: return@withContext null
        val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=$FAVICON_SIZE"

        var connection: HttpURLConnection? = null
        try {
            val urlConnection = URL(faviconUrl).openConnection() as HttpURLConnection
            connection = urlConnection
            connection.connectTimeout = CONNECTION_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext null
            }

            connection.inputStream.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Saves a favicon bitmap to internal storage.
     * @param context Android context
     * @param id The quicklink ID (used as filename)
     * @param bitmap The favicon bitmap to save
     * @return true if saved successfully, false otherwise
     */
    fun saveFavicon(context: Context, id: String, bitmap: Bitmap): Boolean {
        return try {
            val dir = getFaviconsDir(context)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, "$id.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Loads a favicon from internal storage.
     * @param context Android context
     * @param id The quicklink ID
     * @return Bitmap if found, null otherwise
     */
    suspend fun loadFavicon(context: Context, id: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(getFaviconsDir(context), "$id.png")
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Deletes a favicon from internal storage.
     * @param context Android context
     * @param id The quicklink ID
     */
    fun deleteFavicon(context: Context, id: String) {
        try {
            val file = File(getFaviconsDir(context), "$id.png")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            // Ignore deletion errors
        }
    }

    /**
     * Extracts the domain from a URL.
     * Example: "https://github.com/user/repo" -> "github.com"
     */
    fun extractDomain(url: String): String? {
        val withoutProtocol = url
            .removePrefix("https://")
            .removePrefix("http://")
            .trimStart('/')

        if (withoutProtocol.isBlank()) return null

        return withoutProtocol
            .substringBefore("/")
            .substringBefore("?")
            .substringBefore("#")
            .takeIf { it.isNotBlank() }
    }

    private fun getFaviconsDir(context: Context): File {
        return File(context.filesDir, FAVICONS_DIR)
    }
}
