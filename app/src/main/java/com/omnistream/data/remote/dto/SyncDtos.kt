package com.omnistream.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SyncDataResponse(
    val library: List<LibraryEntryDto> = emptyList(),
    val history: List<HistoryEntryDto> = emptyList(),
    val lastSyncedAt: String? = null
)

@Serializable
data class LibraryEntryDto(
    val sourceId: String = "",
    val contentId: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val contentType: String = "",
    val category: String = "favorites",
    val progress: Float = 0f,
    val lastChapter: String = "",
    val unreadCount: Int = 0
)

@Serializable
data class HistoryEntryDto(
    val sourceId: String = "",
    val contentId: String = "",
    val title: String = "",
    val coverUrl: String = "",
    val contentType: String = "",
    val lastPosition: String = "",
    val timestamp: String = ""
)

@Serializable
data class SyncUpdateRequest(
    val library: List<LibraryEntryDto>,
    val history: List<HistoryEntryDto>
)
