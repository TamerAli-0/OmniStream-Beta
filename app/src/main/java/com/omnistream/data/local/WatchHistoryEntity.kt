package com.omnistream.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey
    val id: String, // Format: "sourceId:contentId"
    val contentId: String,
    val sourceId: String,
    val contentType: String, // "video" or "manga"
    val title: String,
    val coverUrl: String? = null,
    @ColumnInfo(name = "episode_id") val episodeId: String? = null,
    @ColumnInfo(name = "chapter_id") val chapterId: String? = null,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int = 0,
    @ColumnInfo(name = "total_chapters") val totalChapters: Int = 0,
    @ColumnInfo(name = "progress_position") val progressPosition: Long = 0L,
    @ColumnInfo(name = "total_duration") val totalDuration: Long = 0L,
    @ColumnInfo(name = "progress_percentage") val progressPercentage: Float = 0f,
    @ColumnInfo(name = "last_watched_at") val lastWatchedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false
)
