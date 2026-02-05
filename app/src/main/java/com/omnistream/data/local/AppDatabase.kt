package com.omnistream.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        ReadChaptersEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun readChaptersDao(): ReadChaptersDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS watch_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        contentId TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        coverUrl TEXT,
                        episode_id TEXT,
                        chapter_id TEXT,
                        chapter_index INTEGER NOT NULL DEFAULT 0,
                        total_chapters INTEGER NOT NULL DEFAULT 0,
                        progress_position INTEGER NOT NULL DEFAULT 0,
                        total_duration INTEGER NOT NULL DEFAULT 0,
                        progress_percentage REAL NOT NULL DEFAULT 0,
                        last_watched_at INTEGER NOT NULL DEFAULT 0,
                        is_completed INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS search_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        query TEXT NOT NULL,
                        searched_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS downloads (
                        id TEXT NOT NULL PRIMARY KEY,
                        contentId TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        contentType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        coverUrl TEXT,
                        episode_id TEXT,
                        chapter_id TEXT,
                        file_path TEXT NOT NULL,
                        file_size INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'pending',
                        progress REAL NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create read_chapters table for Kotatsu-style tracking
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS read_chapters (
                        id TEXT NOT NULL PRIMARY KEY,
                        manga_id TEXT NOT NULL,
                        source_id TEXT NOT NULL,
                        chapter_id TEXT NOT NULL,
                        chapter_number REAL NOT NULL,
                        read_at INTEGER NOT NULL DEFAULT 0,
                        pages_read INTEGER NOT NULL DEFAULT 0,
                        total_pages INTEGER NOT NULL DEFAULT 0,
                        is_completed INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )

                // Create index for faster queries
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_read_chapters_manga
                    ON read_chapters(manga_id, source_id)
                    """.trimIndent()
                )
            }
        }
    }
}
