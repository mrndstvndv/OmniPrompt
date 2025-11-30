package com.mrndstvndv.search.provider.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.mrndstvndv.search.BuildConfig
import com.mrndstvndv.search.IUserService
import com.mrndstvndv.search.UserService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Manages developer settings toggle functionality with support for:
 * - Direct ADB-granted WRITE_SECURE_SETTINGS permission
 * - Shizuku service for elevated access
 * - Root access detection
 */
class DeveloperSettingsManager(private val context: Context) {

    companion object {
        private const val TAG = "DeveloperSettingsManager"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 101
        
        @Volatile
        private var instance: DeveloperSettingsManager? = null
        
        fun getInstance(context: Context): DeveloperSettingsManager {
            return instance ?: synchronized(this) {
                instance ?: DeveloperSettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    enum class PermissionMethod {
        NONE,
        ROOT,
        SHIZUKU,
        ADB
    }

    data class PermissionStatus(
        val availableMethod: PermissionMethod = PermissionMethod.NONE,
        val isShizukuAvailable: Boolean = false,
        val hasShizukuPermission: Boolean = false,
        val isReady: Boolean = false
    )

    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    private var userService: IUserService? = null
    private var isShizukuListenersRegistered = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, UserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "onServiceConnected: name=$name, binder=$binder, pingBinder=${binder?.pingBinder()}")
            if (binder != null && binder.pingBinder()) {
                userService = IUserService.Stub.asInterface(binder)
                Log.d(TAG, "userService bound successfully")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: name=$name")
            userService = null
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "binderReceivedListener triggered")
        updatePermissionStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "binderDeadListener triggered")
        userService = null
        updatePermissionStatus()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            updatePermissionStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                bindUserService()
            }
        }
    }

    /**
     * Register Shizuku listeners. Call this when the feature is enabled.
     */
    fun registerListeners() {
        if (isShizukuListenersRegistered) return
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            isShizukuListenersRegistered = true
        } catch (e: Exception) {
            // Shizuku not available
        }
        updatePermissionStatus()
    }

    /**
     * Unregister Shizuku listeners. Call this when the feature is disabled.
     */
    fun unregisterListeners() {
        if (!isShizukuListenersRegistered) return
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            unbindUserService()
        } catch (e: Exception) {
            // Ignore
        }
        isShizukuListenersRegistered = false
    }

    /**
     * Refresh the permission status. Call this onResume if needed.
     */
    fun refreshStatus() {
        updatePermissionStatus()
        val status = _permissionStatus.value
        if (status.isShizukuAvailable && status.hasShizukuPermission && userService == null) {
            bindUserService()
        }
    }

    /**
     * Request Shizuku permission if available.
     * @return true if request was initiated, false otherwise
     */
    fun requestShizukuPermission(): Boolean {
        try {
            if (Shizuku.isPreV11()) {
                return false
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                return false
            }
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Check if developer settings are currently enabled.
     */
    fun isDeveloperSettingsEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Toggle developer settings.
     * @return true if successful, false otherwise
     */
    fun setDeveloperSettingsEnabled(enabled: Boolean): Boolean {
        val status = _permissionStatus.value
        Log.d(TAG, "setDeveloperSettingsEnabled: enabled=$enabled, method=${status.availableMethod}")
        
        val result = when (status.availableMethod) {
            PermissionMethod.ADB -> toggleViaDirectPermission(enabled)
            PermissionMethod.ROOT -> toggleViaRoot(enabled)
            PermissionMethod.SHIZUKU -> toggleViaShizuku(enabled)
            PermissionMethod.NONE -> {
                Log.w(TAG, "No permission method available")
                false
            }
        }
        
        Log.d(TAG, "setDeveloperSettingsEnabled result: $result")
        return result
    }

    private fun updatePermissionStatus() {
        val hasAdbPermission = hasDirectPermission()
        val isRooted = isDeviceRooted()
        val isShizukuAvailable = isShizukuAvailable()
        val hasShizukuPermission = if (isShizukuAvailable) checkShizukuPermission() else false

        Log.d(TAG, "updatePermissionStatus: adb=$hasAdbPermission, root=$isRooted, shizuku=$isShizukuAvailable, shizukuPerm=$hasShizukuPermission")

        val method = when {
            hasAdbPermission -> PermissionMethod.ADB
            isRooted -> PermissionMethod.ROOT
            isShizukuAvailable && hasShizukuPermission -> PermissionMethod.SHIZUKU
            else -> PermissionMethod.NONE
        }

        val isReady = method != PermissionMethod.NONE

        _permissionStatus.value = PermissionStatus(
            availableMethod = method,
            isShizukuAvailable = isShizukuAvailable,
            hasShizukuPermission = hasShizukuPermission,
            isReady = isReady
        )
        
        Log.d(TAG, "Permission method: $method, isReady: $isReady")

        // Auto-bind Shizuku service if ready
        if (method == PermissionMethod.SHIZUKU && userService == null) {
            bindUserService()
        }
    }

    private fun hasDirectPermission(): Boolean {
        return try {
            val currentValue = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            )
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                currentValue
            )
            true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun isDeviceRooted(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkShizukuPermission(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun bindUserService() {
        Log.d(TAG, "bindUserService called, isShizukuListenersRegistered=$isShizukuListenersRegistered")
        try {
            val version = Shizuku.getVersion()
            Log.d(TAG, "Shizuku version: $version")
            if (version >= 10) {
                val className = UserService::class.java.name
                Log.d(TAG, "UserService class name: $className")
                Log.d(TAG, "Application ID: ${BuildConfig.APPLICATION_ID}")
                Log.d(TAG, "Binding user service...")
                Shizuku.bindUserService(userServiceArgs, userServiceConnection)
                Log.d(TAG, "bindUserService call completed successfully")
            } else {
                Log.w(TAG, "Shizuku version too old: $version")
            }
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed with exception", e)
        } catch (t: Throwable) {
            Log.e(TAG, "bindUserService failed with throwable", t)
        }
    }

    private fun unbindUserService() {
        try {
            Shizuku.unbindUserService(userServiceArgs, userServiceConnection, true)
        } catch (e: Exception) {
            // Ignore
        }
        userService = null
    }

    private fun toggleViaDirectPermission(enable: Boolean): Boolean {
        Log.d(TAG, "toggleViaDirectPermission: enable=$enable")
        return try {
            Settings.Global.putInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                if (enable) 1 else 0
            )
            Log.d(TAG, "toggleViaDirectPermission: success")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "toggleViaDirectPermission failed", e)
            false
        }
    }

    private fun toggleViaRoot(enable: Boolean): Boolean {
        Log.d(TAG, "toggleViaRoot: enable=$enable")
        return try {
            val value = if (enable) "1" else "0"
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "settings put global development_settings_enabled $value")
            )
            val exitCode = process.waitFor()
            Log.d(TAG, "toggleViaRoot: exitCode=$exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "toggleViaRoot failed", e)
            false
        }
    }

    fun toggleViaShizuku(enable: Boolean): Boolean {
        Log.d(TAG, "toggleViaShizuku: enable=$enable, userService=${userService != null}")
        return try {
            val service = userService
            if (service != null) {
                val result = service.setDeveloperSettingsEnabled(enable)
                Log.d(TAG, "toggleViaShizuku: result=$result")
                result
            } else {
                Log.w(TAG, "toggleViaShizuku: userService is null, binding...")
                bindUserService()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleViaShizuku failed", e)
            false
        }
    }

    /**
     * Attempts to launch Wireless Debugging settings via Root or Shizuku.
     */
    fun launchWirelessDebugging(): Boolean {
        return launchSettingsFragment(
            "com.android.settings.development.WirelessDebuggingFragment",
            null
        )
    }

    /**
     * Attempts to launch USB Debugging settings (Developer Options scrolled to key) via Root or Shizuku.
     */
    fun launchUsbDebugging(): Boolean {
        return launchSettingsFragment(
            "com.android.settings.development.DevelopmentSettingsDashboardFragment",
            "enable_adb"
        )
    }

    private fun launchSettingsFragment(fragmentName: String, highlightKey: String?): Boolean {
        val status = _permissionStatus.value
        
        return when (status.availableMethod) {
            PermissionMethod.ROOT -> {
                try {
                    val cmd = StringBuilder("am start -n com.android.settings/.SubSettings -e :settings:show_fragment $fragmentName")
                    if (!highlightKey.isNullOrEmpty()) {
                        cmd.append(" -e :settings:fragment_args_key $highlightKey")
                    }
                    
                    val process = Runtime.getRuntime().exec(
                        arrayOf("su", "-c", cmd.toString())
                    )
                    process.waitFor() == 0
                } catch (e: Exception) {
                    Log.e(TAG, "launchSettingsFragment via Root failed", e)
                    false
                }
            }
            PermissionMethod.SHIZUKU -> {
                try {
                    val service = userService
                    if (service != null) {
                        service.launchSettingsFragment(fragmentName, highlightKey)
                    } else {
                        Log.w(TAG, "launchSettingsFragment: userService is null, binding...")
                        bindUserService()
                        false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "launchSettingsFragment via Shizuku failed", e)
                    false
                }
            }
            else -> false
        }
    }
}
