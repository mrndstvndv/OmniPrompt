package com.mrndstvndv.search.provider.files.index

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table for fast full-text search on indexed documents.
 *
 * This table is automatically kept in sync with [IndexedDocumentEntity] via
 * the contentEntity parameter. FTS4 provides efficient prefix matching which
 * is then combined with in-memory fuzzy scoring for Raycast-style search.
 */
@Fts4(contentEntity = IndexedDocumentEntity::class)
@Entity(tableName = "indexed_documents_fts")
data class IndexedDocumentFtsEntity(
    val displayName: String,
    val relativePath: String,
)
