package com.mrndstvndv.search.provider.files

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.mrndstvndv.search.provider.Provider
import com.mrndstvndv.search.provider.files.FileSearchMatch
import com.mrndstvndv.search.provider.files.FileSearchRepository
import com.mrndstvndv.search.provider.files.FileThumbnailRepository
import com.mrndstvndv.search.provider.files.ThumbnailType
import com.mrndstvndv.search.provider.model.ProviderResult
import com.mrndstvndv.search.provider.model.Query
import com.mrndstvndv.search.provider.settings.FileSearchSettings
import com.mrndstvndv.search.provider.settings.FileSearchThumbnailCropMode
import com.mrndstvndv.search.provider.settings.ProviderSettingsRepository
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FileSearchProvider(
    private val activity: ComponentActivity,
    private val settingsRepository: ProviderSettingsRepository,
    private val repository: FileSearchRepository,
    private val thumbnailRepository: FileThumbnailRepository
) : Provider {

    private val fileProviderAuthority: String = "${activity.packageName}.fileprovider"

    override val id: String = "file-search"
    override val displayName: String = "Files & Folders"

    override fun canHandle(query: Query): Boolean {
        if (query.isBlank) return false
        val settings = settingsRepository.fileSearchSettings.value
        val hasRoots = settings.enabledRoots().isNotEmpty()
        val hasDownloads = settings.includeDownloads && hasDownloadsPermission()
        return hasRoots || hasDownloads
    }

    override suspend fun query(query: Query): List<ProviderResult> {
        val normalized = query.trimmedText
        if (normalized.isBlank()) return emptyList()
        val settings = settingsRepository.fileSearchSettings.value
        val thumbnailsEnabled = settings.loadThumbnails
        val potentialRoots = settings.enabledRoots().map { it.id }.toMutableList()
        if (settings.includeDownloads && hasDownloadsPermission()) {
            potentialRoots += FileSearchSettings.DOWNLOADS_ROOT_ID
        }
        if (potentialRoots.isEmpty()) return emptyList()
        val matches = repository.search(
            queryText = normalized,
            rootIds = potentialRoots,
            sortMode = settings.sortMode,
            sortAscending = settings.sortAscending
        )
        if (matches.isEmpty()) return emptyList()
        val lowerQuery = normalized.lowercase()
        val results = mutableListOf<ProviderResult>()
        val uniqueMatches = matches.distinctBy { it.documentUri }
        for (match in uniqueMatches) {
            val iconDescriptor = resolveIcons(match, thumbnailsEnabled, settings.thumbnailCropMode)
            // Calculate offset for subtitle indices due to prefix "${rootDisplayName} • "
            val subtitlePrefix = "${match.rootDisplayName} • "
            val adjustedSubtitleIndices = match.matchedSubtitleIndices.map { it + subtitlePrefix.length }
            results += ProviderResult(
                id = "$id:${match.documentUri}",
                title = match.displayName,
                subtitle = describeMatch(match),
                icon = null,
                vectorIcon = iconDescriptor.vectorIcon,
                iconLoader = iconDescriptor.iconLoader,
                providerId = id,
                score = computeScore(match, lowerQuery),
                extras = mapOf(
                    EXTRA_ROOT_NAME to match.rootDisplayName,
                    EXTRA_RELATIVE_PATH to match.relativePath
                ),
                onSelect = { openDocument(match) },
                matchedTitleIndices = match.matchedTitleIndices,
                matchedSubtitleIndices = adjustedSubtitleIndices
            )
        }
        return results
    }

    private fun hasDownloadsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun resolveIcons(
        match: FileSearchMatch,
        thumbnailsEnabled: Boolean,
        cropMode: FileSearchThumbnailCropMode
    ): IconDescriptor {
        if (match.isDirectory) return IconDescriptor(folderIcon, null)
        val mime = match.mimeType?.lowercase()
        val extension = match.displayName.substringAfterLast('.', "").lowercase()
        if (isImageFile(mime, extension)) {
            return IconDescriptor(
                vectorIcon = imageIcon,
                iconLoader = if (thumbnailsEnabled) {
                    { thumbnailRepository.loadThumbnail(match.documentUri, match.lastModified, cropMode, ThumbnailType.IMAGE) }
                } else null
            )
        }
        if (isVideoFile(mime, extension)) {
            return IconDescriptor(
                vectorIcon = videoIcon,
                iconLoader = if (thumbnailsEnabled) {
                    { thumbnailRepository.loadThumbnail(match.documentUri, match.lastModified, cropMode, ThumbnailType.VIDEO) }
                } else null
            )
        }
        if (isAudioFile(mime, extension)) {
            return IconDescriptor(
                vectorIcon = musicIcon,
                iconLoader = if (thumbnailsEnabled) {
                    { thumbnailRepository.loadThumbnail(match.documentUri, match.lastModified, cropMode, ThumbnailType.AUDIO) }
                } else null
            )
        }
        if (isApkFile(mime, extension)) return IconDescriptor(apkIcon, null)
        if (isArchiveFile(extension)) return IconDescriptor(archiveIcon, null)
        return IconDescriptor(documentIcon, null)
    }

    private fun isImageFile(mime: String?, extension: String): Boolean {
        if (mime?.startsWith("image/") == true) return true
        return extension in IMAGE_EXTENSIONS
    }

    private fun isAudioFile(mime: String?, extension: String): Boolean {
        if (mime?.startsWith("audio/") == true) return true
        return extension in AUDIO_EXTENSIONS
    }

    private fun isApkFile(mime: String?, extension: String): Boolean {
        if (mime == APK_MIME) return true
        return extension == "apk"
    }

    private fun isVideoFile(mime: String?, extension: String): Boolean {
        if (mime?.startsWith("video/") == true) return true
        return extension in VIDEO_EXTENSIONS
    }

    private fun isArchiveFile(extension: String): Boolean {
        return extension in ARCHIVE_EXTENSIONS
    }

    private val folderIcon: ImageVector = Icons.Outlined.Folder
    private val musicIcon: ImageVector = Icons.Outlined.LibraryMusic
    private val documentIcon: ImageVector = Icons.Outlined.Description
    private val apkIcon: ImageVector = Icons.Outlined.Android
    private val imageIcon: ImageVector = Icons.Outlined.Image
    private val videoIcon: ImageVector = Icons.Outlined.Movie
    private val archiveIcon: ImageVector = Icons.Outlined.FolderZip

    private suspend fun openDocument(match: FileSearchMatch) {
        val originalUri = Uri.parse(match.documentUri)
        val targetMime = when {
            match.isDirectory -> DocumentsContract.Document.MIME_TYPE_DIR
            !match.mimeType.isNullOrBlank() -> match.mimeType
            else -> DEFAULT_MIME
        }
        withContext(Dispatchers.Main) {
            val shareableUri = resolveSharableUri(originalUri)
            if (shareableUri == null) {
                Toast.makeText(activity, "Can't open this item", Toast.LENGTH_SHORT).show()
                return@withContext
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(shareableUri, targetMime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                activity.startActivity(intent)
                activity.finish()
            } catch (error: ActivityNotFoundException) {
                Toast.makeText(activity, "No app can open this item", Toast.LENGTH_SHORT).show()
            } catch (error: SecurityException) {
                Toast.makeText(activity, "Permission denied for this file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun describeMatch(match: FileSearchMatch): String {
        val path = if (match.relativePath.isBlank()) match.displayName else match.relativePath
        return "${match.rootDisplayName} • $path"
    }

    private fun computeScore(match: FileSearchMatch, query: String): Float {
        val lowerName = match.displayName.lowercase()
        var score = 0f
        if (lowerName.startsWith(query)) score += 2f
        if (lowerName.contains(query)) score += 1f
        if (!match.isDirectory) score += 0.1f
        return score
    }

    private fun resolveSharableUri(original: Uri): Uri? {
        if (original.scheme != ContentResolver.SCHEME_FILE) return original
        val path = original.path ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(activity, fileProviderAuthority, file)
        }.getOrNull()
    }

    companion object {
        private const val DEFAULT_MIME = "*/*"
        const val EXTRA_ROOT_NAME = "fileSearch.rootName"
        const val EXTRA_RELATIVE_PATH = "fileSearch.relativePath"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private val AUDIO_EXTENSIONS = setOf("mp3", "aac", "wav", "flac", "m4a", "ogg", "opus")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "flv", "wmv")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "xz")
    }

    private data class IconDescriptor(
        val vectorIcon: ImageVector?,
        val iconLoader: (suspend () -> Bitmap?)?
    )
}
