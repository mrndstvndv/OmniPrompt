package com.mrndstvndv.search.provider.termux

import org.json.JSONArray
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
        const val SESSION_ACTION_NEW_AND_OPEN = 0 // Switch to new session and open Termux
        const val SESSION_ACTION_NEW_NO_OPEN = 1 // Switch to new session but don't open
        const val SESSION_ACTION_CURRENT_AND_OPEN = 2 // Keep current session and open Termux
        const val SESSION_ACTION_CURRENT_NO_OPEN = 3 // Keep current session, don't open

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

/**
 * Settings container for the Termux provider.
 */
data class TermuxSettings(
    val commands: List<TermuxCommand>,
) {
    companion object {
        fun default(): TermuxSettings = TermuxSettings(commands = emptyList())

        fun fromJson(json: JSONObject?): TermuxSettings? {
            if (json == null) return null
            val commandsArray = json.optJSONArray("commands") ?: JSONArray()
            val commands =
                buildList {
                    for (i in 0 until commandsArray.length()) {
                        TermuxCommand.fromJson(commandsArray.optJSONObject(i))?.let { add(it) }
                    }
                }
            return TermuxSettings(commands = commands)
        }
    }

    fun toJson(): JSONObject =
        JSONObject().apply {
            val commandsArray = JSONArray()
            commands.forEach { commandsArray.put(it.toJson()) }
            put("commands", commandsArray)
        }

    fun toJsonString(): String = toJson().toString()
}
