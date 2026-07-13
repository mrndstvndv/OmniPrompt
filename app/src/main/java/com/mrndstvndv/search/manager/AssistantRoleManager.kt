package com.mrndstvndv.search.manager

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.mrndstvndv.search.SearchActivity

class AssistantRoleManager(private val context: Context) {
    fun isDefaultAssistant(): Boolean {
        val assistantComponent =
            Settings.Secure.getString(context.contentResolver, ASSISTANT_SETTING_KEY)
                ?: return false
        val defaultComponent = ComponentName.unflattenFromString(assistantComponent) ?: return false
        val appComponent = ComponentName(context, SearchActivity::class.java)
        return defaultComponent.packageName == appComponent.packageName
    }

    fun launchDefaultAssistantSettings() {
        val packageManager: PackageManager = context.packageManager
        val intents =
            listOf(
                Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )
        val resolvedIntent = intents.firstOrNull { it.resolveActivity(packageManager) != null } ?: return
        context.startActivity(resolvedIntent)
    }

    companion object {
        private const val ASSISTANT_SETTING_KEY = "assistant"
    }
}
