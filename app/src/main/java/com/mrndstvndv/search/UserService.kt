package com.mrndstvndv.search

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import kotlin.system.exitProcess

@Keep
class UserService : IUserService.Stub {
    
    companion object {
        private const val TAG = "UserService"
    }
    
    // Default constructor required by Shizuku
    constructor() {
        Log.d(TAG, "UserService created (default constructor)")
    }
    
    // Constructor with Context available from Shizuku v13
    @Keep
    constructor(context: Context) {
        Log.d(TAG, "UserService created with context: $context")
    }
    
    override fun destroy() {
        Log.d(TAG, "destroy called")
        exitProcess(0)
    }
    
    override fun setDeveloperSettingsEnabled(enabled: Boolean): Boolean {
        Log.d(TAG, "setDeveloperSettingsEnabled: enabled=$enabled")
        return try {
            val value = if (enabled) "1" else "0"
            val process = Runtime.getRuntime().exec(
                arrayOf("settings", "put", "global", "development_settings_enabled", value)
            )
            val exitCode = process.waitFor()
            Log.d(TAG, "setDeveloperSettingsEnabled: exitCode=$exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "setDeveloperSettingsEnabled failed", e)
            false
        }
    }

    override fun launchSettingsFragment(fragmentName: String, highlightKey: String?): Boolean {
        Log.d(TAG, "launchSettingsFragment called: fragment=$fragmentName, key=$highlightKey")
        return try {
            val cmd = mutableListOf(
                "am", 
                "start", 
                "-n", 
                "com.android.settings/.SubSettings", 
                "-e", 
                ":settings:show_fragment", 
                fragmentName
            )
            
            if (!highlightKey.isNullOrEmpty()) {
                cmd.add("-e")
                cmd.add(":settings:fragment_args_key")
                cmd.add(highlightKey)
            }
            
            val process = Runtime.getRuntime().exec(cmd.toTypedArray())
            val exitCode = process.waitFor()
            Log.d(TAG, "launchSettingsFragment: exitCode=$exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "launchSettingsFragment failed", e)
            false
        }
    }
}
