package com.mrndstvndv.search.provider.files.index

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [IndexedDocumentEntity::class, IndexedDocumentFtsEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FileSearchDatabase : RoomDatabase() {
    abstract fun indexedDocumentDao(): IndexedDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: FileSearchDatabase? = null

        /**
         * Migration from version 1 to 2: Adds FTS4 virtual table for fast full-text search.
         */
        private val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Create FTS4 virtual table linked to indexed_documents
                    db.execSQL(
                        """
                        CREATE VIRTUAL TABLE IF NOT EXISTS indexed_documents_fts 
                        USING fts4(
                            displayName, 
                            relativePath, 
                            content=`indexed_documents`
                        )
                        """.trimIndent(),
                    )
                    // Rebuild FTS index from existing data
                    db.execSQL(
                        """
                        INSERT INTO indexed_documents_fts(indexed_documents_fts) 
                        VALUES('rebuild')
                        """.trimIndent(),
                    )
                }
            }

        fun get(context: Context): FileSearchDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FileSearchDatabase::class.java,
                    "file_search_index.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
