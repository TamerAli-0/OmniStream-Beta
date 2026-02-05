package com.omnistream.domain.model

import com.omnistream.source.model.VideoType
import kotlinx.serialization.Serializable

/**
 * Video content domain model (anime, movie, TV show).
 */
@Serializable
data class Video(
    val id: String,
    val sourceId: String,
    val title: String,
    val url: String,
    val type: VideoType,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val description: String? = null,
    val year: Int? = null,
    val rating: Float? = null,  // 0-10 scale
    val duration: Int? = null,  // Minutes
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: String? = null,
    val studio: String? = null,
    val status: VideoStatus = VideoStatus.UNKNOWN,
    val episodeCount: Int? = null,
    val seasonCount: Int? = null
)

enum class VideoStatus {
    ONGOING,
    COMPLETED,
    UPCOMING,
    UNKNOWN
}

/**
 * Episode domain model.
 */
@Serializable
data class Episode(
    val id: String,
    val videoId: String,
    val sourceId: String,
    val url: String,
    val title: String? = null,
    val number: Int,
    val season: Int? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val airDate: Long? = null,  // Unix timestamp
    val duration: Int? = null   // Minutes
)

/**
 * Playable video link with quality info.
 */
@Serializable
data class VideoLink(
    val url: String,
    val quality: String,  // "1080p", "720p", "480p", "Auto"
    val extractorName: String,
    val referer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isM3u8: Boolean = false,
    val isDash: Boolean = false,
    val subtitles: List<Subtitle> = emptyList()
)

/**
 * Subtitle track.
 */
@Serializable
data class Subtitle(
    val url: String,
    val language: String,
    val label: String? = null,
    val isDefault: Boolean = false
)

/**
 * Homepage section for video sources.
 */
@Serializable
data class HomeSection(
    val name: String,
    val items: List<Video>,
    val hasMore: Boolean = false,
    val moreUrl: String? = null
)
