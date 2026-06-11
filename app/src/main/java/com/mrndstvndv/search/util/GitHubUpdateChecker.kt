package com.mrndstvndv.search.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdateChecker {
    private const val TAG = "GitHubUpdateChecker"
    private const val RELEASES_URL = "https://api.github.com/repos/mrndstvndv/OmniPrompt/releases"

    data class UpdateResult(
        val version: String,
        val changelog: String,
        val downloadUrl: String,
        val isPrerelease: Boolean
    )

    sealed class CheckResult {
        data class NewUpdate(val update: UpdateResult) : CheckResult()
        object UpToDate : CheckResult()
        data class Error(val errorMsg: String) : CheckResult()
    }

    suspend fun checkForUpdates(currentVersion: String, checkPrerelease: Boolean): CheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "OmniPrompt-App")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Failed to fetch releases: HTTP ${connection.responseCode}")
                return@withContext CheckResult.Error("HTTP Error: ${connection.responseCode}")
            }

            val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
            val releasesArray = JSONArray(jsonText)

            for (i in 0 until releasesArray.length()) {
                val releaseObj = releasesArray.getJSONObject(i)
                val tagName = releaseObj.optString("tag_name", "")
                val isPrerelease = releaseObj.optBoolean("prerelease", false)
                val body = releaseObj.optString("body", "")
                val htmlUrl = releaseObj.optString("html_url", "")

                // Filter based on pre-release preference:
                if (!checkPrerelease && isPrerelease) {
                    continue
                }

                if (VersionComparator.isNewer(currentVersion, tagName)) {
                    return@withContext CheckResult.NewUpdate(
                        UpdateResult(
                            version = tagName,
                            changelog = body,
                            downloadUrl = htmlUrl,
                            isPrerelease = isPrerelease
                        )
                    )
                }
                
                // Since releases are returned in reverse chronological order (newest first),
                // the first release matching the criteria (stable for release build, any for debug build)
                // is the latest release we care about. If it is NOT newer than current, then there are no updates.
                break
            }
            return@withContext CheckResult.UpToDate
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext CheckResult.Error(e.message ?: "Unknown error")
        }
    }
}
