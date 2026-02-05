package com.omnistream.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val id: String, // Format: "sourceId:contentId"
    val contentId: String,
    val sourceId: String,
    val contentType: String, // "manga" or "video"
    val title: String,
    val coverUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
