package com.mrndstvndv.search.provider.termux

import org.json.JSONObject

/**
 * Represents a user-defined Termux command that can be executed via RUN_COMMAND intent.
 */
data class TermuxCommand(
    val id: String,
    val displayName: String,
    val executablePath: String,
    val arguments: String? = null,
    val workingDir: String? = null,
    val runInBackground: Boolean = false,
    val sessionAction: Int = SESSION_ACTION_NEW_AND_OPEN,
) {
    companion object {
        // Session action values per Termux RUN_COMMAND spec
        // https://github.com/termux/termux-app/blob/master/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java
        const val SESSION_ACTION_NEW_AND_OPEN = 0 // VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY
        const val SESSION_ACTION_CURRENT_AND_OPEN = 1 // VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY
        const val SESSION_ACTION_NEW_NO_OPEN = 2 // VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY
        const val SESSION_ACTION_CURRENT_NO_OPEN = 3 // VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY

        fun fromJson(json: JSONObject?): TermuxCommand? {
            if (json == null) return null
            val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
            val displayName = json.optString("displayName").takeIf { it.isNotBlank() } ?: return null
            val executablePath = json.optString("executablePath").takeIf { it.isNotBlank() } ?: return null
            return TermuxCommand(
                id = id,
                displayName = displayName,
                executablePath = executablePath,
                arguments = json.optString("arguments").takeIf { it.isNotBlank() },
                workingDir = json.optString("workingDir").takeIf { it.isNotBlank() },
                runInBackground = json.optBoolean("runInBackground", false),
                sessionAction = json.optInt("sessionAction", SESSION_ACTION_NEW_AND_OPEN),
            )
        }
    }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("displayName", displayName)
            put("executablePath", executablePath)
            arguments?.let { put("arguments", it) }
            workingDir?.let { put("workingDir", it) }
            put("runInBackground", runInBackground)
            put("sessionAction", sessionAction)
        }
}
