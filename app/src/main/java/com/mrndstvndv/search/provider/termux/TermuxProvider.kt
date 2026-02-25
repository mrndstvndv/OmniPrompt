package com.mrndstvndv.search.provider.termux

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.core.content.ContextCompat
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import com.mrndstvndv.search.provider.settings.SettingsRepository
import com.mrndstvndv.search.util.FuzzyMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider for executing user-defined Termux commands.
 *
 * This provider is only active when Termux is installed on the device.
 * Commands are executed via Termux's RUN_COMMAND intent.
 */
class TermuxProvider(
    private val activity: ComponentActivity,
    private val globalSettingsRepository: ProviderSettingsRepository,
    private val settingsRepository: SettingsRepository<TermuxSettings>,
) : Provider {
    override val id: String = "termux"
    override val displayName: String = "Termux Commands"

    private val isTermuxInstalled: Boolean by lazy {
        activity.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) != null
    }

    override fun canHandle(query: Query): Boolean {
        if (!isTermuxInstalled) return false
        val isEnabled = globalSettingsRepository.enabledProviders.value[id] ?: true
        if (!isEnabled) return false
        val cleaned = query.trimmedText
        return cleaned.isNotBlank()
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        if (!isTermuxInstalled) return emptyList()

        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return emptyList()

        val settings = settingsRepository.value
        val commands = settings.commands
        if (commands.isEmpty()) return emptyList()

        // Parse query: "ytdl url_link" -> commandPart="ytdl", argsPart="url_link"
        val spaceIndex = cleaned.indexOf(' ')
        val commandPart = if (spaceIndex > 0) cleaned.substring(0, spaceIndex) else cleaned
        val argsPart = if (spaceIndex > 0) cleaned.substring(spaceIndex + 1).trim() else ""
        val queryArgs = if (argsPart.isBlank()) emptyList() else argsPart.split(' ')

        data class ScoredCommand(
            val command: TermuxCommand,
            val score: Int,
            val matchedTitleIndices: List<Int>,
            val matchedSubtitleIndices: List<Int>,
            val queryArgs: List<String>,
            val argsText: String,
        )

        val scored =
            commands
                .mapNotNull { command ->
                    val titleMatch = FuzzyMatcher.match(commandPart, command.displayName)
                    val pathMatch = FuzzyMatcher.match(commandPart, command.executablePath)

                    // Apply penalty to path matches
                    val pathScoreWithPenalty = pathMatch?.let { it.score - PATH_MATCH_PENALTY }

                    val titleIsBest =
                        when {
                            titleMatch == null -> false
                            pathScoreWithPenalty == null -> true
                            else -> titleMatch.score >= pathScoreWithPenalty
                        }

                    when {
                        titleIsBest && titleMatch != null -> {
                            ScoredCommand(
                                command = command,
                                score = titleMatch.score,
                                matchedTitleIndices = titleMatch.matchedIndices,
                                matchedSubtitleIndices = pathMatch?.matchedIndices ?: emptyList(),
                                queryArgs = queryArgs,
                                argsText = argsPart,
                            )
                        }

                        pathMatch != null -> {
                            ScoredCommand(
                                command = command,
                                score = pathScoreWithPenalty!!,
                                matchedTitleIndices = emptyList(),
                                matchedSubtitleIndices = pathMatch.matchedIndices,
                                queryArgs = queryArgs,
                                argsText = argsPart,
                            )
                        }

                        else -> {
                            null
                        }
                    }
                }.sortedByDescending { it.score }

        return scored.map { (command, _, matchedTitleIndices, matchedSubtitleIndices, queryArgs, argsText) ->
            val resolvedArgs = resolveArguments(command.arguments, queryArgs, argsText)
            val preview = buildCommandPreview(command.executablePath, resolvedArgs)
            ProviderResult(
                id = "$id:${command.id}",
                title = if (argsText.isBlank()) command.displayName else "${command.displayName} \"$argsText\"",
                subtitle = preview,
                vectorIcon = Icons.Outlined.Terminal,
                providerId = id,
                onSelect = { executeTermuxCommand(command, queryArgs, argsText) },
                keepOverlayUntilExit = true,
                matchedTitleIndices = matchedTitleIndices,
                matchedSubtitleIndices = matchedSubtitleIndices,
            )
        }
    }

    /**
     * Resolves dynamic argument placeholders like $1, $2, $* with actual query arguments.
     * Supports inline placeholders within arguments (e.g., "shep $1").
     */
    private fun resolveArguments(
        arguments: String?,
        queryArgs: List<String>,
        argsText: String,
    ): List<String> {
        if (arguments.isNullOrBlank()) return emptyList()

        return arguments.split(",").map { it.trim() }.map { arg ->
            resolvePlaceholders(arg, queryArgs, argsText)
        }
    }

    /**
     * Replaces $1, $2, $* placeholders within a string.
     */
    private fun resolvePlaceholders(
        input: String,
        queryArgs: List<String>,
        argsText: String,
    ): String {
        var result = input

        // Replace $* first (all remaining text)
        result = result.replace("$*", argsText)

        // Replace $1, $2, etc. with corresponding query arguments
        val placeholderPattern = Regex("\\$([0-9]+)")
        result = placeholderPattern.replace(result) { matchResult ->
            val index = matchResult.groupValues[1].toIntOrNull()
            if (index != null && index > 0) {
                queryArgs.getOrNull(index - 1) ?: matchResult.value
            } else {
                matchResult.value
            }
        }

        return result
    }

    /**
     * Builds a command preview string showing the executable and resolved arguments.
     */
    private fun buildCommandPreview(executablePath: String, resolvedArgs: List<String>): String {
        return if (resolvedArgs.isEmpty()) {
            executablePath
        } else {
            "$executablePath ${resolvedArgs.joinToString(" ")}"
        }
    }

    private suspend fun executeTermuxCommand(
        command: TermuxCommand,
        queryArgs: List<String>,
        argsText: String,
    ) {
        withContext(Dispatchers.Main) {
            // 1. Check permission first to provide clear feedback
            if (!hasRunCommandPermission(activity)) {
                Toast.makeText(
                    activity, 
                    "Permission denied: Grant 'Run commands in Termux environment' in App Info > Permissions > Additional permissions.", 
                    Toast.LENGTH_LONG
                ).show()
                activity.finish()
                return@withContext
            }

            val resolvedArgs = resolveArguments(command.arguments, queryArgs, argsText)
            
            // 2. Create a valid PendingIntent for results (mandatory for Android 12+ background start)
            val resultIntent = Intent("com.mrndstvndv.search.TERMUX_RESULT")
            resultIntent.setPackage(activity.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(activity, 0, resultIntent, flags)

            // 3. Prepare the base intent
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                setPackage(TERMUX_PACKAGE)
                putExtra(EXTRA_COMMAND_PATH, command.executablePath)
                if (resolvedArgs.isNotEmpty()) {
                    putExtra(EXTRA_COMMAND_ARGUMENTS, resolvedArgs.toTypedArray())
                }
                command.workingDir?.let { workDir ->
                    putExtra(EXTRA_COMMAND_WORKDIR, workDir)
                }
                putExtra(EXTRA_COMMAND_BACKGROUND, command.runInBackground)
                // sessionAction must be String: "0", "1", "2", "3"
                putExtra(EXTRA_COMMAND_SESSION_ACTION, command.sessionAction.toString())
                putExtra(EXTRA_COMMAND_PENDING_INTENT, pendingIntent)
            }

            try {
                // 4. Ensure Termux is awake. Launching its activity is the most reliable way 
                // to gain the "foreground privilege" needed to start its service.
                if (!command.runInBackground) {
                    val launchIntent = activity.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                        activity.startActivity(launchIntent)
                        kotlinx.coroutines.delay(250) // Brief pause to let process start
                    }
                }

                // 5. Deliver the command. Try startService first, fallback to broadcast.
                val serviceIntent = Intent(intent).setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                try {
                    // On Android 12+, PendingIntent.send() can sometimes bypass background restrictions
                    // that startService() fails on.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val pi = android.app.PendingIntent.getService(activity, 0, serviceIntent, 
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                        pi.send()
                    } else {
                        activity.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    // Fallback: Use the Receiver which forwards to the Service internally
                    val broadcastIntent = Intent(intent).setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_RECEIVER)
                    broadcastIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    activity.sendBroadcast(broadcastIntent)
                }
            } catch (e: Exception) {
                Toast.makeText(activity, "Failed to send command: ${e.message}", Toast.LENGTH_LONG).show()
            }

            // 6. CRITICAL: Stay in foreground long enough for delivery to complete.
            kotlinx.coroutines.delay(600)
            activity.finish()
        }
    }

    /**
     * Checks if Termux is installed on the device.
     * This can be called from settings UI to determine if the provider should be enabled.
     */
    fun checkTermuxInstalled(): Boolean = isTermuxInstalled

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val TERMUX_RUN_COMMAND_RECEIVER = "com.termux.app.TermuxReceiver"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        private const val EXTRA_COMMAND_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

        private const val PATH_MATCH_PENALTY = 10

        /**
         * Static helper to check if Termux is installed without needing a provider instance.
         */
        fun isTermuxInstalled(activity: ComponentActivity): Boolean =
            activity.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) != null

        /**
         * Static helper to check if RUN_COMMAND permission is granted.
         */
        fun hasRunCommandPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                TERMUX_RUN_COMMAND_PERMISSION,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
