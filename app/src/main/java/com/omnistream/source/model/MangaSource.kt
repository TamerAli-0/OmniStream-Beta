package com.omnistream.source.model

import com.omnistream.domain.model.Chapter
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.Page

/**
 * Interface for manga/manhwa/manhua sources.
 * Inspired by Kotatsu's MangaParser interface.
 */
interface MangaSource {
    /** Unique identifier for this source */
    val id: String

    /** Display name */
    val name: String

    /** Base URL of the source */
    val baseUrl: String

    /** Language code (en, ja, ko, zh, etc.) */
    val lang: String

    /** Whether source contains NSFW content */
    val isNsfw: Boolean get() = false

    /**
     * Get popular/trending manga with pagination.
     * @param page Page number (1-indexed)
     * @return List of manga for this page
     */
    suspend fun getPopular(page: Int): List<Manga>

    /**
     * Get latest updated manga with pagination.
     * @param page Page number (1-indexed)
     * @return List of manga for this page
     */
    suspend fun getLatest(page: Int): List<Manga>

    /**
     * Search for manga.
     * @param query Search query
     * @param page Page number (1-indexed)
     * @return List of matching manga
     */
    suspend fun search(query: String, page: Int): List<Manga>

    /**
     * Get full details for a manga.
     * @param manga Manga with at least url populated
     * @return Manga with all details filled in
     */
    suspend fun getDetails(manga: Manga): Manga

    /**
     * Get chapter list for a manga.
     * @param manga Manga to get chapters for
     * @return List of chapters (newest first typically)
     */
    suspend fun getChapters(manga: Manga): List<Chapter>

    /**
     * Get page images for a chapter.
     * @param chapter Chapter to get pages for
     * @return List of pages with image URLs
     */
    suspend fun getPages(chapter: Chapter): List<Page>

    /**
     * Quick connectivity test for speed benchmarking.
     * @return true if source is reachable
     */
    suspend fun ping(): Boolean = try {
        getPopular(1).isNotEmpty()
    } catch (e: Exception) {
        false
    }
}

/**
 * Source metadata for display in source manager.
 */
data class SourceMetadata(
    val id: String,
    val name: String,
    val lang: String,
    val isNsfw: Boolean,
    val iconUrl: String? = null,
    val description: String? = null
)

/**
 * Source type categories
 */
enum class SourceType {
    MANGA,      // Japanese manga
    MANHWA,     // Korean manhwa
    MANHUA,     // Chinese manhua
    COMIC,      // Western comics
    ALL         // Multi-type source
}
