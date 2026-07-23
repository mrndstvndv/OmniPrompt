package com.mrndstvndv.search.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PerformanceLogger {
    private const val TAG = "PerfEvidence"
    private const val LOG_FILE_NAME = "performance_evidence.log"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(context: Context, logTag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val formattedLine = "[$timestamp] [$logTag] $message\n"
        
        // Print to logcat for immediate visibility
        Log.d("$TAG:$logTag", message)

        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            logFile.appendText(formattedLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    fun clearLog(context: Context) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) {
                logFile.writeText("=== Performance Evidence Log Reset at ${dateFormat.format(Date())} ===\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log file", e)
        }
    }

    fun shareLogFile(context: Context) {
        val logFile = getLogFile(context)
        if (!logFile.exists() || logFile.length() == 0L) {
            log(context, "INFO", "=== Performance Evidence Log Started ===")
        }

        val authority = "${context.packageName}.fileprovider"
        val contentUri: Uri = FileProvider.getUriForFile(context, authority, logFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "OmniPrompt Performance Evidence Log")
            putExtra(Intent.EXTRA_TEXT, "Attached performance evidence log file from OmniPrompt Search.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share or Save Performance Log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
    }
}
