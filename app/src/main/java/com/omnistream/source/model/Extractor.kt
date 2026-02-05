package com.omnistream.source.model

import com.omnistream.domain.model.VideoLink

/**
 * Interface for video link extractors.
 * Handles extracting actual video URLs from embed pages.
 *
 * Common hosts: VidCloud, StreamTape, MixDrop, DoodStream, etc.
 */
interface Extractor {
    /** Extractor name for display */
    val name: String

    /** List of domains this extractor can handle */
    val domains: List<String>

    /**
     * Check if this extractor can handle the given URL.
     */
    fun canHandle(url: String): Boolean {
        return domains.any { domain ->
            url.contains(domain, ignoreCase = true)
        }
    }

    /**
     * Extract video links from an embed URL.
     *
     * @param url The embed/player URL
     * @param referer Optional referer header (often required)
     * @return List of video links with different qualities
     */
    suspend fun extract(url: String, referer: String? = null): List<VideoLink>
}

/**
 * Result of extraction attempt
 */
sealed class ExtractionResult {
    data class Success(val links: List<VideoLink>) : ExtractionResult()
    data class Error(val message: String) : ExtractionResult()
    data object NotSupported : ExtractionResult()
}
