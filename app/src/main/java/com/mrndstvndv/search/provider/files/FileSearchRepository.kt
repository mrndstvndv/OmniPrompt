package com.mrndstvndv.search.provider.files

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.mrndstvndv.search.provider.files.index.FileSearchDatabase
import com.mrndstvndv.search.provider.files.index.IndexedDocumentEntity
import com.mrndstvndv.search.provider.files.indexing.FileSearchIndexWorker
import com.mrndstvndv.search.provider.files.indexing.IncrementalFileSyncWorker
import com.mrndstvndv.search.provider.settings.FileSearchRoot
import com.mrndstvndv.search.provider.settings.FileSearchSortMode
import com.mrndstvndv.search.util.FuzzyMatcher
import java.util.concurrent.TimeUnit

class FileSearchRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val database: FileSearchDatabase = FileSearchDatabase.get(appContext)
    private val workManager = WorkManager.getInstance(appContext)

    /**
     * Search for files using FTS + fuzzy scoring for Raycast-style matching.
     *
     * Strategy:
     * 1. For queries >= 2 chars: Use FTS4 prefix matching for fast candidate retrieval
     * 2. For single char queries: Fall back to LIKE search
     * 3. Apply fuzzy scoring to candidates for ranking
     * 4. Return results sorted by fuzzy score, then by user preference
     */
    suspend fun search(
        queryText: String,
        rootIds: List<String>,
        sortMode: FileSearchSortMode,
        sortAscending: Boolean,
        limit: Int = MAX_RESULTS,
    ): List<FileSearchMatch> {
        val cleaned = queryText.trim()
        if (cleaned.isEmpty() || rootIds.isEmpty()) return emptyList()

        val dao = database.indexedDocumentDao()
        val fetchLimit = limit * 3 // Fetch more candidates for fuzzy re-ranking

        // Choose search strategy based on query length
        val candidates =
            if (cleaned.length >= 2) {
                // Use FTS for 2+ character queries (more effective)
                val ftsQuery = buildFtsQuery(cleaned)
                try {
                    dao.searchFts(rootIds, ftsQuery, fetchLimit)
                } catch (e: Exception) {
                    // Fall back to LIKE if FTS fails (e.g., special characters)
                    val likeQuery = "%${escapeLikeWildcards(cleaned)}%"
                    dao.searchLike(rootIds, likeQuery, fetchLimit)
                }
            } else {
                // Fall back to LIKE for single characters
                val likeQuery = "%${escapeLikeWildcards(cleaned)}%"
                dao.searchLike(rootIds, likeQuery, fetchLimit)
            }

        // Apply fuzzy scoring and filter
        val scored =
            candidates.mapNotNull { entity ->
                val displayNameMatch = FuzzyMatcher.match(cleaned, entity.displayName)
                val relativePathMatch = FuzzyMatcher.match(cleaned, entity.relativePath)

                // Take the best match between displayName and relativePath for ranking
                val bestMatch =
                    listOfNotNull(displayNameMatch, relativePathMatch)
                        .maxByOrNull { it.score }

                bestMatch?.let { match ->
                    ScoredMatch(
                        entity = entity,
                        score = match.score,
                        // Highlight displayName if it matched
                        matchedTitleIndices = displayNameMatch?.matchedIndices ?: emptyList(),
                        // Highlight path if it matched
                        matchedSubtitleIndices = relativePathMatch?.matchedIndices ?: emptyList(),
                    )
                }
            }

        // Sort by fuzzy score (descending) first, then take top results
        val fuzzyRanked =
            scored
                .sortedByDescending { it.score }
                .take(limit)

        val mapped =
            fuzzyRanked.map { scoredMatch ->
                FileSearchMatch(
                    documentUri = scoredMatch.entity.documentUri,
                    displayName = scoredMatch.entity.displayName,
                    relativePath = scoredMatch.entity.relativePath,
                    rootDisplayName = scoredMatch.entity.rootDisplayName,
                    mimeType = scoredMatch.entity.mimeType,
                    isDirectory = scoredMatch.entity.isDirectory,
                    lastModified = scoredMatch.entity.lastModified,
                    sizeBytes = scoredMatch.entity.sizeBytes,
                    matchedTitleIndices = scoredMatch.matchedTitleIndices,
                    matchedSubtitleIndices = scoredMatch.matchedSubtitleIndices,
                )
            }

        return sortMatches(mapped, sortMode, sortAscending)
    }

    suspend fun deleteRootEntries(rootId: String) {
        database.indexedDocumentDao().deleteForRoot(rootId)
    }

    fun scheduleFullIndex(root: FileSearchRoot) {
        val request = buildIndexRequest(root)
        workManager.enqueueUniqueWork(uniqueWorkName(root.id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun buildIndexRequest(root: FileSearchRoot): OneTimeWorkRequest {
        val input =
            workDataOf(
                FileSearchIndexWorker.KEY_ROOT_ID to root.id,
                FileSearchIndexWorker.KEY_ROOT_URI to root.uri.toString(),
                FileSearchIndexWorker.KEY_ROOT_DISPLAY_NAME to root.displayName,
            )
        return OneTimeWorkRequestBuilder<FileSearchIndexWorker>()
            .setInputData(input)
            .addTag(indexTag(root.id))
            .build()
    }

    private fun uniqueWorkName(rootId: String): String = "file-search-index-$rootId"

    private fun indexTag(rootId: String): String = "file-search-index-tag-$rootId"

    private fun sortMatches(
        matches: List<FileSearchMatch>,
        sortMode: FileSearchSortMode,
        sortAscending: Boolean,
    ): List<FileSearchMatch> {
        val comparator =
            when (sortMode) {
                FileSearchSortMode.DATE -> compareBy<FileSearchMatch> { it.lastModified }
                FileSearchSortMode.NAME -> compareBy<FileSearchMatch> { it.displayName.lowercase() }
            }
        val effectiveComparator = if (sortAscending) comparator else comparator.reversed()
        val directories = matches.filter { it.isDirectory }.sortedWith(effectiveComparator)
        val files = matches.filterNot { it.isDirectory }.sortedWith(effectiveComparator)
        return directories + files
    }

    /**
     * Builds an FTS query with prefix matching for each token.
     * Example: "my doc" -> "my* doc*"
     */
    private fun buildFtsQuery(query: String): String {
        return query.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                val escaped = escapeFtsSpecialChars(token)
                "$escaped*"
            }
    }

    /**
     * Escapes FTS special characters by wrapping them in quotes.
     */
    private fun escapeFtsSpecialChars(token: String): String {
        return buildString {
            for (char in token) {
                when (char) {
                    '"', '\'', '(', ')', '-', ':', '*' -> {
                        append('"')
                        append(char)
                        append('"')
                    }
                    else -> append(char)
                }
            }
        }
    }

    private fun escapeLikeWildcards(input: String): String {
        return buildString(input.length) {
            input.forEach { char ->
                when (char) {
                    '%', '_', '\\' -> {
                        append('\\')
                        append(char)
                    }
                    else -> append(char)
                }
            }
        }
    }

    /**
     * Schedules periodic background sync for file changes.
     * @param intervalMinutes Sync interval in minutes. Pass 0 to disable.
     */
    fun schedulePeriodicSync(intervalMinutes: Int) {
        if (intervalMinutes <= 0) {
            cancelPeriodicSync()
            return
        }

        val request =
            PeriodicWorkRequestBuilder<IncrementalFileSyncWorker>(
                intervalMinutes.toLong(),
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Cancels the periodic sync worker.
     */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
    }

    /**
     * Triggers an immediate incremental sync.
     * Uses expedited work when possible for faster execution.
     */
    fun triggerImmediateSync() {
        val request =
            OneTimeWorkRequestBuilder<IncrementalFileSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        workManager.enqueue(request)
    }

    /**
     * Internal class for holding scored match results during fuzzy ranking.
     */
    private data class ScoredMatch(
        val entity: IndexedDocumentEntity,
        val score: Int,
        val matchedTitleIndices: List<Int>,
        val matchedSubtitleIndices: List<Int>,
    )

    companion object {
        private const val MAX_RESULTS = 40
        private const val PERIODIC_SYNC_WORK_NAME = "file-search-periodic-sync"

        @Volatile
        private var INSTANCE: FileSearchRepository? = null

        fun getInstance(context: Context): FileSearchRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FileSearchRepository(context).also { INSTANCE = it }
            }
        }
    }
}

data class FileSearchMatch(
    val documentUri: String,
    val displayName: String,
    val relativePath: String,
    val rootDisplayName: String,
    val mimeType: String?,
    val isDirectory: Boolean,
    val lastModified: Long,
    val sizeBytes: Long,
    val matchedTitleIndices: List<Int> = emptyList(),
    val matchedSubtitleIndices: List<Int> = emptyList(),
)
