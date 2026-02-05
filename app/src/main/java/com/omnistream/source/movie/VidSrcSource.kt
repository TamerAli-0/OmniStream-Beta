package com.omnistream.source.movie

import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.HomeSection
import com.omnistream.domain.model.Video
import com.omnistream.domain.model.VideoLink
import com.omnistream.domain.model.VideoStatus
import com.omnistream.source.model.VideoSource
import com.omnistream.source.model.VideoType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * VidSrc source for movies and TV shows.
 * Uses TMDB for metadata and VidSrc for streaming links.
 *
 * VidSrc embed format: https://vidsrc.to/embed/movie/{tmdb_id}
 *                      https://vidsrc.to/embed/tv/{tmdb_id}/{season}/{episode}
 */
class VidSrcSource(
    private val httpClient: OmniHttpClient
) : VideoSource {

    override val id = "vidsrc"
    override val name = "VidSrc"
    override val baseUrl = "https://vidsrc.to"
    override val lang = "en"
    override val supportedTypes = setOf(VideoType.MOVIE, VideoType.TV_SERIES)

    private val tmdbBaseUrl = "https://api.themoviedb.org/3"
    private val tmdbImageUrl = "https://image.tmdb.org/t/p"

    private val tmdbApiKey = com.omnistream.BuildConfig.TMDB_API_KEY_SECONDARY

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun getHomePage(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()

        try {
            // Trending Movies
            val trendingMovies = fetchTMDB("/trending/movie/week")
            if (trendingMovies.isNotEmpty()) {
                sections.add(HomeSection("Trending Movies", trendingMovies))
            }

            // Trending TV Shows
            val trendingTV = fetchTMDB("/trending/tv/week")
            if (trendingTV.isNotEmpty()) {
                sections.add(HomeSection("Trending TV Shows", trendingTV))
            }

            // Popular Movies
            val popularMovies = fetchTMDB("/movie/popular")
            if (popularMovies.isNotEmpty()) {
                sections.add(HomeSection("Popular Movies", popularMovies))
            }

            // Top Rated Movies
            val topRatedMovies = fetchTMDB("/movie/top_rated")
            if (topRatedMovies.isNotEmpty()) {
                sections.add(HomeSection("Top Rated", topRatedMovies))
            }

            // Now Playing
            val nowPlaying = fetchTMDB("/movie/now_playing")
            if (nowPlaying.isNotEmpty()) {
                sections.add(HomeSection("Now Playing", nowPlaying))
            }
        } catch (e: Exception) {
            // Return empty sections on error
        }

        return sections
    }

    private suspend fun fetchTMDB(endpoint: String, page: Int = 1): List<Video> {
        val url = "$tmdbBaseUrl$endpoint?api_key=$tmdbApiKey&page=$page&language=en-US"

        return try {
            // Use API-specific headers for TMDB
            val response = httpClient.get(url, headers = mapOf(
                "Accept" to "application/json"
            ))
            val tmdbResponse = json.decodeFromString<TMDBResponse>(response)

            tmdbResponse.results.mapNotNull { item ->
                tmdbItemToVideo(item)
            }
        } catch (e: Exception) {
            android.util.Log.e("VidSrcSource", "Failed to fetch TMDB $endpoint", e)
            emptyList()
        }
    }

    private fun tmdbItemToVideo(item: TMDBItem): Video? {
        val title = item.title ?: item.name ?: return null
        val isMovie = item.mediaType == "movie" || item.title != null

        return Video(
            id = "${if (isMovie) "movie" else "tv"}-${item.id}",
            sourceId = id,
            title = title,
            url = "$baseUrl/embed/${if (isMovie) "movie" else "tv"}/${item.id}",
            type = if (isMovie) VideoType.MOVIE else VideoType.TV_SERIES,
            posterUrl = item.posterPath?.let { "$tmdbImageUrl/w500$it" },
            backdropUrl = item.backdropPath?.let { "$tmdbImageUrl/original$it" },
            description = item.overview,
            year = (item.releaseDate ?: item.firstAirDate)?.take(4)?.toIntOrNull(),
            rating = item.voteAverage?.toFloat(),
            genres = emptyList() // Would need another API call for genres
        )
    }

    override suspend fun search(query: String, page: Int): List<Video> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$tmdbBaseUrl/search/multi?api_key=$tmdbApiKey&query=$encodedQuery&page=$page&language=en-US"

        return try {
            val response = httpClient.get(url, headers = mapOf("Accept" to "application/json"))
            val tmdbResponse = json.decodeFromString<TMDBResponse>(response)

            tmdbResponse.results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .mapNotNull { tmdbItemToVideo(it) }
        } catch (e: Exception) {
            android.util.Log.e("VidSrcSource", "Search failed", e)
            emptyList()
        }
    }

    override suspend fun getDetails(video: Video): Video {
        val (type, tmdbId) = parseVideoId(video.id)
        val endpoint = if (type == "movie") "/movie/$tmdbId" else "/tv/$tmdbId"
        val url = "$tmdbBaseUrl$endpoint?api_key=$tmdbApiKey&language=en-US"

        return try {
            val response = httpClient.get(url, headers = mapOf("Accept" to "application/json"))

            if (type == "movie") {
                val details = json.decodeFromString<TMDBMovieDetails>(response)
                video.copy(
                    title = details.title,
                    description = details.overview,
                    posterUrl = details.posterPath?.let { "$tmdbImageUrl/w500$it" },
                    backdropUrl = details.backdropPath?.let { "$tmdbImageUrl/original$it" },
                    year = details.releaseDate?.take(4)?.toIntOrNull(),
                    rating = details.voteAverage?.toFloat(),
                    duration = details.runtime,
                    genres = details.genres?.map { it.name } ?: emptyList(),
                    status = if (details.status == "Released") VideoStatus.COMPLETED else VideoStatus.UPCOMING
                )
            } else {
                val details = json.decodeFromString<TMDBTVDetails>(response)
                video.copy(
                    title = details.name,
                    description = details.overview,
                    posterUrl = details.posterPath?.let { "$tmdbImageUrl/w500$it" },
                    backdropUrl = details.backdropPath?.let { "$tmdbImageUrl/original$it" },
                    year = details.firstAirDate?.take(4)?.toIntOrNull(),
                    rating = details.voteAverage?.toFloat(),
                    genres = details.genres?.map { it.name } ?: emptyList(),
                    status = when (details.status) {
                        "Returning Series" -> VideoStatus.ONGOING
                        "Ended", "Canceled" -> VideoStatus.COMPLETED
                        else -> VideoStatus.UNKNOWN
                    },
                    seasonCount = details.numberOfSeasons,
                    episodeCount = details.numberOfEpisodes
                )
            }
        } catch (e: Exception) {
            video
        }
    }

    override suspend fun getEpisodes(video: Video): List<Episode> {
        val (type, tmdbId) = parseVideoId(video.id)

        // For movies, return single "episode"
        if (type == "movie") {
            return listOf(
                Episode(
                    id = "movie-$tmdbId",
                    videoId = video.id,
                    sourceId = id,
                    url = "$baseUrl/embed/movie/$tmdbId",
                    title = video.title,
                    number = 1
                )
            )
        }

        // For TV shows, fetch seasons and episodes
        val episodes = mutableListOf<Episode>()

        try {
            val url = "$tmdbBaseUrl/tv/$tmdbId?api_key=$tmdbApiKey&language=en-US"
            val response = httpClient.get(url, headers = mapOf("Accept" to "application/json"))
            val tvDetails = json.decodeFromString<TMDBTVDetails>(response)

            tvDetails.seasons?.filter { it.seasonNumber > 0 }?.forEach { season ->
                try {
                    val seasonUrl = "$tmdbBaseUrl/tv/$tmdbId/season/${season.seasonNumber}?api_key=$tmdbApiKey&language=en-US"
                    val seasonResponse = httpClient.get(seasonUrl, headers = mapOf("Accept" to "application/json"))
                    val seasonDetails = json.decodeFromString<TMDBSeasonDetails>(seasonResponse)

                    seasonDetails.episodes?.forEach { ep ->
                        episodes.add(
                            Episode(
                                id = "tv-$tmdbId-${season.seasonNumber}-${ep.episodeNumber}",
                                videoId = video.id,
                                sourceId = id,
                                url = "$baseUrl/embed/tv/$tmdbId/${season.seasonNumber}/${ep.episodeNumber}",
                                title = ep.name,
                                number = ep.episodeNumber,
                                season = season.seasonNumber,
                                description = ep.overview,
                                airDate = ep.airDate?.let { parseDate(it) },
                                duration = ep.runtime,
                                thumbnailUrl = ep.stillPath?.let { "$tmdbImageUrl/w300$it" }
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Continue with other seasons
                }
            }
        } catch (e: Exception) {
            // Return empty list on error
        }

        return episodes.sortedWith(compareBy({ it.season }, { it.number }))
    }

    override suspend fun getLinks(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        // VidSrc primary embed
        links.addAll(extractVidSrc(episode.url))

        // VidSrc.me alternative
        val vidsrcMeUrl = episode.url.replace("vidsrc.to", "vidsrc.me")
        links.addAll(extractVidSrc(vidsrcMeUrl))

        // VidSrc.pro alternative
        val vidsrcProUrl = episode.url.replace("vidsrc.to", "vidsrc.pro")
        links.addAll(extractVidSrc(vidsrcProUrl))

        return links.distinctBy { it.url }
    }

    private suspend fun extractVidSrc(embedUrl: String): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            val response = httpClient.get(embedUrl, headers = mapOf(
                "Referer" to baseUrl
            ))

            // Look for iframe sources
            val iframePattern = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            iframePattern.findAll(response).forEach { match ->
                val iframeSrc = match.groupValues[1]
                links.addAll(extractFromIframe(iframeSrc))
            }

            // Look for direct sources in script tags
            val sourcesPattern = Regex("""sources\s*[:=]\s*\[([^\]]+)\]""")
            sourcesPattern.find(response)?.let { match ->
                val sourcesContent = match.groupValues[1]

                // Extract file URLs
                val filePattern = Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""")
                filePattern.findAll(sourcesContent).forEach { fileMatch ->
                    val fileUrl = fileMatch.groupValues[1].replace("\\/", "/")
                    links.add(VideoLink(
                        url = fileUrl,
                        quality = extractQuality(fileUrl),
                        extractorName = "VidSrc",
                        isM3u8 = fileUrl.contains(".m3u8"),
                        referer = embedUrl
                    ))
                }
            }

            // Look for m3u8/mp4 URLs directly
            val mediaPattern = Regex("""["']?(https?://[^"'\s]+\.(m3u8|mp4)[^"'\s]*)["']?""")
            mediaPattern.findAll(response).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                if (!links.any { it.url == url }) {
                    links.add(VideoLink(
                        url = url,
                        quality = extractQuality(url),
                        extractorName = "VidSrc",
                        isM3u8 = url.contains(".m3u8"),
                        referer = embedUrl
                    ))
                }
            }
        } catch (e: Exception) {
            // Extraction failed
        }

        return links
    }

    private suspend fun extractFromIframe(iframeSrc: String): List<VideoLink> {
        val links = mutableListOf<VideoLink>()
        val fullUrl = when {
            iframeSrc.startsWith("//") -> "https:$iframeSrc"
            iframeSrc.startsWith("/") -> "$baseUrl$iframeSrc"
            else -> iframeSrc
        }

        try {
            val response = httpClient.get(fullUrl, headers = mapOf(
                "Referer" to baseUrl
            ))

            // Similar extraction logic
            val mediaPattern = Regex("""["']?(https?://[^"'\s]+\.(m3u8|mp4)[^"'\s]*)["']?""")
            mediaPattern.findAll(response).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                links.add(VideoLink(
                    url = url,
                    quality = extractQuality(url),
                    extractorName = "VidSrc",
                    isM3u8 = url.contains(".m3u8"),
                    referer = fullUrl
                ))
            }

            // Look for encoded/encrypted sources
            val encodedPattern = Regex("""atob\(["']([^"']+)["']\)""")
            encodedPattern.findAll(response).forEach { match ->
                try {
                    val decoded = java.util.Base64.getDecoder().decode(match.groupValues[1]).toString(Charsets.UTF_8)
                    if (decoded.contains(".m3u8") || decoded.contains(".mp4")) {
                        links.add(VideoLink(
                            url = decoded,
                            quality = extractQuality(decoded),
                            extractorName = "VidSrc",
                            isM3u8 = decoded.contains(".m3u8"),
                            referer = fullUrl
                        ))
                    }
                } catch (e: Exception) {
                    // Decode failed
                }
            }
        } catch (e: Exception) {
            // Iframe extraction failed
        }

        return links
    }

    private fun parseVideoId(videoId: String): Pair<String, String> {
        val parts = videoId.split("-", limit = 2)
        return if (parts.size == 2) {
            Pair(parts[0], parts[1])
        } else {
            Pair("movie", videoId)
        }
    }

    private fun extractQuality(url: String): String {
        return when {
            url.contains("4k", ignoreCase = true) || url.contains("2160") -> "4K"
            url.contains("1080") -> "1080p"
            url.contains("720") -> "720p"
            url.contains("480") -> "480p"
            url.contains("360") -> "360p"
            url.contains("master") || url.contains("index") -> "Auto"
            else -> "Unknown"
        }
    }

    private fun parseDate(dateStr: String): Long? {
        return try {
            java.time.LocalDate.parse(dateStr).atStartOfDay()
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun ping(): Boolean {
        return try {
            val latency = httpClient.ping(baseUrl)
            latency > 0
        } catch (e: Exception) {
            false
        }
    }

    // TMDB API models
    @Serializable
    private data class TMDBResponse(
        val page: Int = 1,
        val results: List<TMDBItem> = emptyList(),
        @SerialName("total_pages") val totalPages: Int = 0,
        @SerialName("total_results") val totalResults: Int = 0
    )

    @Serializable
    private data class TMDBItem(
        val id: Int,
        val title: String? = null,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("media_type") val mediaType: String? = null
    )

    @Serializable
    private data class TMDBMovieDetails(
        val id: Int,
        val title: String,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        val runtime: Int? = null,
        val status: String? = null,
        val genres: List<TMDBGenre>? = null
    )

    @Serializable
    private data class TMDBTVDetails(
        val id: Int,
        val name: String,
        val overview: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        val status: String? = null,
        val genres: List<TMDBGenre>? = null,
        val seasons: List<TMDBSeason>? = null,
        @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
        @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null
    )

    @Serializable
    private data class TMDBGenre(
        val id: Int,
        val name: String
    )

    @Serializable
    private data class TMDBSeason(
        val id: Int,
        @SerialName("season_number") val seasonNumber: Int,
        val name: String? = null,
        @SerialName("episode_count") val episodeCount: Int? = null
    )

    @Serializable
    private data class TMDBSeasonDetails(
        val id: Int,
        @SerialName("season_number") val seasonNumber: Int,
        val episodes: List<TMDBEpisode>? = null
    )

    @Serializable
    private data class TMDBEpisode(
        val id: Int,
        val name: String? = null,
        val overview: String? = null,
        @SerialName("episode_number") val episodeNumber: Int,
        @SerialName("season_number") val seasonNumber: Int,
        @SerialName("air_date") val airDate: String? = null,
        val runtime: Int? = null,
        @SerialName("still_path") val stillPath: String? = null
    )
}
