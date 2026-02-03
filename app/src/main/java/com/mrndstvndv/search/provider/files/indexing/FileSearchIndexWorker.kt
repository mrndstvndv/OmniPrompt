package com.mrndstvndv.search.provider.files.indexing

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mrndstvndv.search.provider.files.createFileSearchSettingsRepository
import com.mrndstvndv.search.provider.files.index.FileSearchDatabase
import com.mrndstvndv.search.provider.files.index.IndexedDocumentDao
import com.mrndstvndv.search.provider.files.index.IndexedDocumentEntity
import com.mrndstvndv.search.provider.settings.FileSearchScanMetadata
import com.mrndstvndv.search.provider.settings.FileSearchScanState
import com.mrndstvndv.search.provider.settings.FileSearchSettings
import com.mrndstvndv.search.provider.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileSearchIndexWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val rootId = inputData.getString(KEY_ROOT_ID)
        val rootUri = inputData.getString(KEY_ROOT_URI)
        val rootDisplayName = inputData.getString(KEY_ROOT_DISPLAY_NAME)
        if (rootId.isNullOrBlank() || rootUri.isNullOrBlank() || rootDisplayName.isNullOrBlank()) {
            return@withContext Result.failure()
        }
        val settingsRepository = createFileSearchSettingsRepository(applicationContext)
        updateScanState(settingsRepository, rootId, FileSearchScanState.INDEXING)
        val parsedUri = Uri.parse(rootUri)
        val documentTree = when (parsedUri.scheme) {
            "file" -> {
                val file = parsedUri.path?.let { File(it) }
                if (file != null && file.exists()) {
                    DocumentFile.fromFile(file)
                } else null
            }
            else -> DocumentFile.fromTreeUri(applicationContext, parsedUri)
        } ?: return@withContext Result.failure()
        val dao = FileSearchDatabase.get(applicationContext).indexedDocumentDao()
        return@withContext try {
            dao.deleteForRoot(rootId)
            val batch = ArrayList<IndexedDocumentEntity>(BATCH_SIZE)
            val counter = Counter()
            indexDirectory(
                document = documentTree,
                rootId = rootId,
                rootDisplayName = rootDisplayName,
                currentPath = "",
                dao = dao,
                batch = batch,
                counter = counter,
                settingsRepository = settingsRepository
            )
            flushBatch(
                dao = dao,
                batch = batch,
                settingsRepository = settingsRepository,
                rootId = rootId,
                counter = counter
            )
            updateScanState(
                settingsRepository = settingsRepository,
                rootId = rootId,
                state = FileSearchScanState.SUCCESS,
                itemCount = counter.value,
                errorMessage = null
            )
            Result.success(workDataOf(KEY_INDEXED_COUNT to counter.value))
        } catch (error: Exception) {
            updateScanState(
                settingsRepository = settingsRepository,
                rootId = rootId,
                state = FileSearchScanState.ERROR,
                errorMessage = error.message ?: "Unknown error"
            )
            Result.failure()
        }
    }

    private suspend fun indexDirectory(
        document: DocumentFile,
        rootId: String,
        rootDisplayName: String,
        currentPath: String,
        dao: IndexedDocumentDao,
        batch: MutableList<IndexedDocumentEntity>,
        counter: Counter,
        settingsRepository: SettingsRepository<FileSearchSettings>
    ) {
        if (isStopped || counter.value >= MAX_INDEXED_ITEMS) return
        val children = document.listFiles()
        children.forEach { child ->
            if (isStopped || counter.value >= MAX_INDEXED_ITEMS) return
            val name = child.name ?: child.uri.lastPathSegment ?: UNKNOWN_NAME
            val relativePath = if (currentPath.isEmpty()) name else "$currentPath/$name"
            val entity = IndexedDocumentEntity(
                rootId = rootId,
                rootDisplayName = rootDisplayName,
                documentUri = child.uri.toString(),
                relativePath = relativePath,
                displayName = name,
                mimeType = child.type,
                sizeBytes = child.length(),
                lastModified = child.lastModified(),
                isDirectory = child.isDirectory
            )
            batch.add(entity)
            counter.increment()
            if (batch.size >= BATCH_SIZE) {
                flushBatch(
                    dao = dao,
                    batch = batch,
                    settingsRepository = settingsRepository,
                    rootId = rootId,
                    counter = counter
                )
            }
            if (child.isDirectory) {
                indexDirectory(
                    document = child,
                    rootId = rootId,
                    rootDisplayName = rootDisplayName,
                    currentPath = relativePath,
                    dao = dao,
                    batch = batch,
                    counter = counter,
                    settingsRepository = settingsRepository
                )
            }
        }
    }

    private suspend fun flushBatch(
        dao: IndexedDocumentDao,
        batch: MutableList<IndexedDocumentEntity>,
        settingsRepository: SettingsRepository<FileSearchSettings>,
        rootId: String,
        counter: Counter
    ) {
        if (batch.isEmpty()) return
        dao.insertAll(batch.toList())
        updateScanState(
            settingsRepository = settingsRepository,
            rootId = rootId,
            state = FileSearchScanState.INDEXING,
            itemCount = counter.value,
            errorMessage = null
        )
        batch.clear()
    }

    private fun updateScanState(
        settingsRepository: SettingsRepository<FileSearchSettings>,
        rootId: String,
        state: FileSearchScanState,
        itemCount: Int = 0,
        errorMessage: String? = null
    ) {
        settingsRepository.update { currentSettings ->
            val currentMetadata = currentSettings.scanMetadata.toMutableMap()
            currentMetadata[rootId] = FileSearchScanMetadata(
                state = state,
                indexedItemCount = itemCount,
                updatedAtMillis = System.currentTimeMillis(),
                errorMessage = errorMessage
            )
            currentSettings.copy(scanMetadata = currentMetadata)
        }
    }

    private class Counter {
        var value: Int = 0
            private set

        fun increment() {
            value++
        }
    }

    companion object {
        const val KEY_ROOT_ID = "root_id"
        const val KEY_ROOT_URI = "root_uri"
        const val KEY_ROOT_DISPLAY_NAME = "root_display_name"
        const val KEY_INDEXED_COUNT = "indexed_count"

        private const val UNKNOWN_NAME = "(unnamed)"
        private const val BATCH_SIZE = 50
        private const val MAX_INDEXED_ITEMS = 20_000
    }
}
