package com.omnistream.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val contentId: String,
    val sourceId: String,
    val contentType: String,
    val title: String,
    val coverUrl: String? = null,
    @ColumnInfo(name = "episode_id") val episodeId: String? = null,
    @ColumnInfo(name = "chapter_id") val chapterId: String? = null,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,
    val status: String = "pending",
    val progress: Float = 0f,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
