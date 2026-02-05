package com.omnistream.domain.model

import kotlinx.serialization.Serializable

/**
 * Manga/Manhwa/Manhua domain model.
 */
@Serializable
data class Manga(
    val id: String,
    val sourceId: String,
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: MangaStatus = MangaStatus.UNKNOWN,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val rating: Float? = null,
    val isNsfw: Boolean = false,
    val alternativeTitles: List<String> = emptyList()
)

enum class MangaStatus {
    ONGOING,
    COMPLETED,
    HIATUS,
    CANCELLED,
    UNKNOWN
}

/**
 * Chapter domain model.
 */
@Serializable
data class Chapter(
    val id: String,
    val mangaId: String,
    val sourceId: String,
    val url: String,
    val title: String? = null,
    val number: Float,
    val volume: Int? = null,
    val scanlator: String? = null,
    val uploadDate: Long? = null,  // Unix timestamp in milliseconds
    val pageCount: Int? = null
)

/**
 * Page domain model (for manga reader).
 */
@Serializable
data class Page(
    val index: Int,
    val imageUrl: String,
    val referer: String? = null  // Some sources need referer to load images
)
