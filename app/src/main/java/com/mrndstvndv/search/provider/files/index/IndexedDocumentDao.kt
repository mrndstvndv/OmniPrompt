package com.mrndstvndv.search.provider.files.index

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface IndexedDocumentDao {
    @Query("DELETE FROM indexed_documents WHERE rootId = :rootId")
    suspend fun deleteForRoot(rootId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(documents: List<IndexedDocumentEntity>)

    @Upsert
    suspend fun upsertAll(entities: List<IndexedDocumentEntity>)

    @Query("SELECT * FROM indexed_documents WHERE rootId = :rootId")
    suspend fun getAllForRoot(rootId: String): List<IndexedDocumentEntity>

    @Query("DELETE FROM indexed_documents WHERE documentUri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    /**
     * FTS4 search for fast prefix/token matching.
     * The FTS table is kept in sync with the main table via Room's contentEntity.
     */
    @Query(
        """
        SELECT d.* FROM indexed_documents d
        INNER JOIN indexed_documents_fts fts ON d.rowid = fts.rowid
        WHERE fts.indexed_documents_fts MATCH :ftsQuery
        AND d.rootId IN (:rootIds)
        ORDER BY d.isDirectory DESC, d.lastModified DESC
        LIMIT :limit
        """,
    )
    suspend fun searchFts(
        rootIds: List<String>,
        ftsQuery: String,
        limit: Int,
    ): List<IndexedDocumentEntity>

    /**
     * Fallback LIKE search for single-character or special queries where FTS isn't effective.
     */
    @Query(
        """
        SELECT * FROM indexed_documents
        WHERE rootId IN (:rootIds)
        AND (displayName LIKE :query ESCAPE '\' OR relativePath LIKE :query ESCAPE '\')
        ORDER BY isDirectory DESC, lastModified DESC
        LIMIT :limit
        """,
    )
    suspend fun searchLike(
        rootIds: List<String>,
        query: String,
        limit: Int,
    ): List<IndexedDocumentEntity>

    /**
     * Legacy search method - kept for compatibility.
     * @deprecated Use searchFts or searchLike instead.
     */
    @Deprecated(
        "Use searchFts or searchLike for better performance",
        ReplaceWith("searchLike(rootIds, query, limit)"),
    )
    @Query(
        """
        SELECT * FROM indexed_documents
        WHERE rootId IN (:rootIds)
        AND (displayName LIKE :query ESCAPE '\' OR relativePath LIKE :query ESCAPE '\')
        ORDER BY isDirectory DESC, lastModified DESC
        LIMIT :limit
        """,
    )
    suspend fun search(
        rootIds: List<String>,
        query: String,
        limit: Int,
    ): List<IndexedDocumentEntity>
}
