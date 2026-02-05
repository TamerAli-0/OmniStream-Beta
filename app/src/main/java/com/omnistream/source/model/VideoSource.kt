package com.omnistream.source.model

import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.HomeSection
import com.omnistream.domain.model.Video
import com.omnistream.domain.model.VideoLink

/**
 * Interface for video sources (anime, movies, TV shows).
 * Inspired by CloudStream's MainAPI.
 */
interface VideoSource {
    /** Unique identifier for this source */
    val id: String

    /** Display name */
    val name: String

    /** Base URL of the source */
    val baseUrl: String

    /** Language code */
    val lang: String

    /** Types of content this source provides */
    val supportedTypes: Set<VideoType>

    /**
     * Get homepage sections (featured, trending, etc.)
     * @return List of sections with their content
     */
    suspend fun getHomePage(): List<HomeSection>

    /**
     * Search for videos.
     * @param query Search query
     * @param page Page number (1-indexed)
     * @return List of matching videos
     */
    suspend fun search(query: String, page: Int): List<Video>

    /**
     * Get full details for a video.
     * @param video Video with at least url populated
     * @return Video with all details filled in
     */
    suspend fun getDetails(video: Video): Video

    /**
     * Get episode list for a video (for series).
     * For movies, returns single episode.
     * @param video Video to get episodes for
     * @return List of episodes
     */
    suspend fun getEpisodes(video: Video): List<Episode>

    /**
     * Get playable video links for an episode.
     * @param episode Episode to get links for
     * @return List of video links from different servers/qualities
     */
    suspend fun getLinks(episode: Episode): List<VideoLink>

    /**
     * Quick connectivity test.
     */
    suspend fun ping(): Boolean = try {
        getHomePage().isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

/**
 * Video content types
 */
enum class VideoType {
    ANIME,
    MOVIE,
    TV_SERIES,
    DOCUMENTARY,
    LIVE
}
