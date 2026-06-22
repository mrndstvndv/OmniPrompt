package com.mrndstvndv.search.provider.files.index

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "indexed_documents",
    indices = [
        Index(value = ["rootId", "relativePath"], unique = true),
        Index(value = ["displayName"]),
        Index(value = ["lastModified"]),
    ],
)
data class IndexedDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rootId: String,
    val rootDisplayName: String,
    val documentUri: String,
    val relativePath: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
)
