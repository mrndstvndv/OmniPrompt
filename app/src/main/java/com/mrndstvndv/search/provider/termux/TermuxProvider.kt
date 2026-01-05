package com.mrndstvndv.search.provider.termux

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
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
    private val settingsRepository: ProviderSettingsRepository,
) : Provider {
    override val id: String = "termux"
    override val displayName: String = "Termux Commands"

    private val isTermuxInstalled: Boolean by lazy {
        activity.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) != null
    }

    override fun canHandle(query: Query): Boolean {
        if (!isTermuxInstalled) return false
        val isEnabled = settingsRepository.enabledProviders.value[id] ?: true
        if (!isEnabled) return false
        val cleaned = query.trimmedText
        return cleaned.isNotBlank()
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        if (!isTermuxInstalled) return emptyList()

        val cleaned = query.trimmedText
        if (cleaned.isBlank()) return emptyList()

        val settings = settingsRepository.termuxSettings.value
        val commands = settings.commands
        if (commands.isEmpty()) return emptyList()

        data class ScoredCommand(
            val command: TermuxCommand,
            val score: Int,
            val matchedTitleIndices: List<Int>,
            val matchedSubtitleIndices: List<Int>,
        )

        val scored =
            commands
                .mapNotNull { command ->
                    val titleMatch = FuzzyMatcher.match(cleaned, command.displayName)
                    val pathMatch = FuzzyMatcher.match(cleaned, command.executablePath)

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
                            )
                        }

                        pathMatch != null -> {
                            ScoredCommand(
                                command = command,
                                score = pathScoreWithPenalty!!,
                                matchedTitleIndices = emptyList(),
                                matchedSubtitleIndices = pathMatch.matchedIndices,
                            )
                        }

                        else -> {
                            null
                        }
                    }
                }.sortedByDescending { it.score }

        return scored.map { (command, _, matchedTitleIndices, matchedSubtitleIndices) ->
            ProviderResult(
                id = "$id:${command.id}",
                title = command.displayName,
                subtitle = command.executablePath,
                vectorIcon = Icons.Outlined.Terminal,
                providerId = id,
                onSelect = { executeTermuxCommand(command) },
                keepOverlayUntilExit = true,
                matchedTitleIndices = matchedTitleIndices,
                matchedSubtitleIndices = matchedSubtitleIndices,
            )
        }
    }

    private suspend fun executeTermuxCommand(command: TermuxCommand) {
        withContext(Dispatchers.Main) {
            val intent =
                Intent().apply {
                    setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
                    action = ACTION_RUN_COMMAND
                    putExtra(EXTRA_COMMAND_PATH, command.executablePath)

                    // Arguments are comma-separated
                    command.arguments?.let { args ->
                        val argArray = args.split(",").map { it.trim() }.toTypedArray()
                        if (argArray.isNotEmpty()) {
                            putExtra(EXTRA_COMMAND_ARGUMENTS, argArray)
                        }
                    }

                    command.workingDir?.let { workDir ->
                        putExtra(EXTRA_COMMAND_WORKDIR, workDir)
                    }

                    putExtra(EXTRA_COMMAND_BACKGROUND, command.runInBackground)
                    putExtra(EXTRA_COMMAND_SESSION_ACTION, command.sessionAction.toString())
                }

            try {
                activity.startService(intent)
            } catch (e: Exception) {
                // Handle potential security exceptions if permission not granted
                e.printStackTrace()
            }

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
        private const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        private const val EXTRA_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        private const val EXTRA_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        private const val EXTRA_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        private const val EXTRA_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"

        private const val PATH_MATCH_PENALTY = 10

        /**
         * Static helper to check if Termux is installed without needing a provider instance.
         */
        fun isTermuxInstalled(activity: ComponentActivity): Boolean =
            activity.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE) != null
    }
}
