package com.mrndstvndv.search.provider.files.indexing

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mrndstvndv.search.provider.settings.FileSearchRoot
import com.mrndstvndv.search.provider.settings.FileSearchScanMetadata
import com.mrndstvndv.search.provider.settings.FileSearchScanState
import com.mrndstvndv.search.provider.settings.FileSearchSettings
import com.mrndstvndv.search.provider.files.createFileSearchSettingsRepository
import com.mrndstvndv.search.provider.files.index.FileSearchDatabase
import com.mrndstvndv.search.provider.files.index.IndexedDocumentDao
import com.mrndstvndv.search.provider.files.index.IndexedDocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A worker that performs incremental file synchronization.
 * 
 * Instead of re-indexing everything, this worker:
 * 1. Loads all indexed entries for each enabled root from Room
 * 2. Traverses the SAF document tree
 * 3. Compares lastModified timestamps to detect changes
 * 4. Applies incremental updates (new, modified, deleted files)
 */
class IncrementalFileSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dao = FileSearchDatabase.get(applicationContext).indexedDocumentDao()
        val settingsRepository = createFileSearchSettingsRepository(applicationContext)
        val settings = settingsRepository.value

        // Get all enabled roots (including Downloads if enabled)
        val roots = getEnabledRoots(settings)

        if (roots.isEmpty()) {
            return@withContext Result.success()
        }

        try {
            roots.forEach { root ->
                if (isStopped) return@withContext Result.retry()
                val itemCount = syncRoot(root, dao)

                // Update scan metadata for this root to refresh "Updated xx ago"
                val currentMetadata = settingsRepository.value.scanMetadata.toMutableMap()
                currentMetadata[root.id] = FileSearchScanMetadata(
                    state = FileSearchScanState.SUCCESS,
                    indexedItemCount = itemCount,
                    updatedAtMillis = System.currentTimeMillis(),
                    errorMessage = null
                )
                settingsRepository.update { it.copy(scanMetadata = currentMetadata) }
            }

            // Update last sync timestamp
            settingsRepository.update { it.copy(lastSyncTimestamp = System.currentTimeMillis()) }

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun getEnabledRoots(settings: FileSearchSettings): List<SyncableRoot> {
        val roots = mutableListOf<SyncableRoot>()

        // Add Downloads folder if enabled
        if (settings.includeDownloads) {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                roots.add(
                    SyncableRoot(
                        id = FileSearchSettings.DOWNLOADS_ROOT_ID,
                        displayName = "Downloads",
                        uri = Uri.fromFile(downloadsDir),
                        isFileUri = true
                    )
                )
            }
        }

        // Add custom SAF roots
        settings.enabledRoots().forEach { root ->
            roots.add(
                SyncableRoot(
                    id = root.id,
                    displayName = root.displayName,
                    uri = root.uri,
                    isFileUri = root.uri.scheme == "file"
                )
            )
        }

        return roots
    }

    private suspend fun syncRoot(root: SyncableRoot, dao: IndexedDocumentDao): Int {
        val docFile = resolveDocumentFile(root) ?: return 0

        // Get current indexed state
        val indexed = dao.getAllForRoot(root.id).associateBy { it.documentUri }
        val seenUris = mutableSetOf<String>()
        val batch = mutableListOf<IndexedDocumentEntity>()

        // Scan and diff
        scanAndDiff(docFile, root, "", indexed, seenUris, batch, dao)

        // Flush remaining batch
        if (batch.isNotEmpty()) {
            dao.upsertAll(batch)
        }

        // Delete removed files
        val deleted = indexed.keys - seenUris
        if (deleted.isNotEmpty()) {
            // Delete in batches to avoid SQLite variable limit
            deleted.chunked(BATCH_SIZE).forEach { chunk ->
                dao.deleteByUris(chunk)
            }
        }

        // Return total item count after sync
        return seenUris.size
    }

    private fun resolveDocumentFile(root: SyncableRoot): DocumentFile? {
        return if (root.isFileUri) {
            val file = root.uri.path?.let { File(it) }
            if (file != null && file.exists()) {
                DocumentFile.fromFile(file)
            } else null
        } else {
            DocumentFile.fromTreeUri(applicationContext, root.uri)
        }
    }

    private suspend fun scanAndDiff(
        doc: DocumentFile,
        root: SyncableRoot,
        currentPath: String,
        indexed: Map<String, IndexedDocumentEntity>,
        seenUris: MutableSet<String>,
        batch: MutableList<IndexedDocumentEntity>,
        dao: IndexedDocumentDao
    ) {
        if (isStopped) return

        doc.listFiles().forEach { child ->
            if (isStopped) return

            val uri = child.uri.toString()
            seenUris += uri

            val existing = indexed[uri]
            val name = child.name ?: UNKNOWN_NAME
            val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"

            // Check if new or modified
            if (existing == null || existing.lastModified != child.lastModified()) {
                batch += IndexedDocumentEntity(
                    id = existing?.id ?: 0, // Use existing ID for updates
                    rootId = root.id,
                    rootDisplayName = root.displayName,
                    documentUri = uri,
                    relativePath = relativePath,
                    displayName = name,
                    mimeType = child.type,
                    sizeBytes = child.length(),
                    lastModified = child.lastModified(),
                    isDirectory = child.isDirectory
                )

                if (batch.size >= BATCH_SIZE) {
                    dao.upsertAll(batch.toList())
                    batch.clear()
                }
            }

            if (child.isDirectory) {
                scanAndDiff(child, root, relativePath, indexed, seenUris, batch, dao)
            }
        }
    }

    private data class SyncableRoot(
        val id: String,
        val displayName: String,
        val uri: Uri,
        val isFileUri: Boolean
    )

    companion object {
        private const val BATCH_SIZE = 100
        private const val UNKNOWN_NAME = "(unnamed)"
    }
}
