package com.omnistream.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Kotatsu-style individual chapter read tracking
 * Each read chapter gets its own entry for accurate tracking and AniList sync
 */
@Entity(tableName = "read_chapters")
data class ReadChaptersEntity(
    @PrimaryKey
    val id: String, // Format: "sourceId:mangaId:chapterId"

    @ColumnInfo(name = "manga_id")
    val mangaId: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "chapter_id")
    val chapterId: String,

    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Float, // Actual chapter number (1.0, 2.5, etc.)

    @ColumnInfo(name = "read_at")
    val readAt: Long = System.currentTimeMillis(), // When it was marked as read

    @ColumnInfo(name = "pages_read")
    val pagesRead: Int = 0, // Number of pages read in this chapter

    @ColumnInfo(name = "total_pages")
    val totalPages: Int = 0, // Total pages in chapter

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = true // Whether the chapter was fully read
)
