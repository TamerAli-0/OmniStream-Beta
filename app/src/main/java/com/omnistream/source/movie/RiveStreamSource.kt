package com.omnistream.source.movie

import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.HomeSection
import com.omnistream.domain.model.Video
import com.omnistream.domain.model.VideoLink
import com.omnistream.domain.model.VideoStatus
import com.omnistream.source.model.VideoSource
import com.omnistream.source.model.VideoType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * RiveStream - Direct scraper source for movies and TV shows.
 *
 * More reliable than embed-based sources. Uses direct CDN links.
 * Fallback to Consumet API if primary method fails.
 */
class RiveStreamSource(
    private val httpClient: OmniHttpClient
) : VideoSource {

    override val id = "rivestream"
    override val name = "RiveStream"
    override val baseUrl = "https://rivestream.live"
    override val lang = "en"
    override val supportedTypes = setOf(VideoType.MOVIE, VideoType.TV_SERIES)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // TMDB API for metadata
    private val tmdbApiKey = com.omnistream.BuildConfig.TMDB_API_KEY_PRIMARY
    private val tmdbBaseUrl = "https://api.themoviedb.org/3"

    /**
     * Extract TMDB ID from prefixed format (e.g., "movie-12345" -> "12345")
     */
    private fun extractTmdbId(prefixedId: String): String {
        return prefixedId.substringAfter("-", prefixedId)
    }

    override suspend fun getHomePage(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()

        try {
            android.util.Log.d("RiveStream", "Loading home page via TMDB")

            // Trending Movies
            val trending = fetchFromTmdb("trending/movie/day")
            if (trending.isNotEmpty()) {
                sections.add(HomeSection("Trending Movies", trending.take(20)))
            }

            // Popular Movies
            val popular = fetchFromTmdb("movie/popular")
            if (popular.isNotEmpty()) {
                sections.add(HomeSection("Popular Movies", popular.take(20)))
            }

            // Top Rated Movies
            val topRated = fetchFromTmdb("movie/top_rated")
            if (topRated.isNotEmpty()) {
                sections.add(HomeSection("Top Rated", topRated.take(20)))
            }

            // Popular TV Shows
            val tvPopular = fetchFromTmdb("tv/popular")
            if (tvPopular.isNotEmpty()) {
                sections.add(HomeSection("Popular TV Shows", tvPopular.take(20)))
            }

        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "Failed to load home page", e)
        }

        return sections
    }

    private suspend fun fetchFromTmdb(endpoint: String): List<Video> {
        return try {
            val url = "$tmdbBaseUrl/$endpoint?api_key=$tmdbApiKey&language=en-US&page=1"
            val response = httpClient.get(url)
            parseTmdbResponse(response, if (endpoint.contains("tv")) VideoType.TV_SERIES else VideoType.MOVIE)
        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "TMDB fetch failed: $endpoint", e)
            emptyList()
        }
    }

    private fun parseTmdbResponse(response: String, type: VideoType): List<Video> {
        return try {
            val jsonObj = json.parseToJsonElement(response).jsonObject
            val results = jsonObj["results"]?.jsonArray ?: return emptyList()

            results.mapNotNull { element ->
                try {
                    val item = element.jsonObject
                    val id = item["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val title = item["title"]?.jsonPrimitive?.content
                        ?: item["name"]?.jsonPrimitive?.content
                        ?: return@mapNotNull null
                    val posterPath = item["poster_path"]?.jsonPrimitive?.content
                    val releaseDate = item["release_date"]?.jsonPrimitive?.content
                        ?: item["first_air_date"]?.jsonPrimitive?.content
                    val voteAverage = item["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()

                    val typePrefix = if (type == VideoType.TV_SERIES) "tv" else "movie"
                    Video(
                        id = "$typePrefix-$id",
                        sourceId = this.id,
                        title = title,
                        url = "$baseUrl/$typePrefix/$id",
                        type = type,
                        posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
                        year = releaseDate?.take(4)?.toIntOrNull(),
                        rating = voteAverage
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "TMDB parse failed", e)
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<Video> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<Video>()

        try {
            // Search movies
            val movieUrl = "$tmdbBaseUrl/search/movie?api_key=$tmdbApiKey&language=en-US&query=$encodedQuery&page=$page"
            val movieResponse = httpClient.get(movieUrl)
            results.addAll(parseTmdbResponse(movieResponse, VideoType.MOVIE))

            // Search TV shows
            val tvUrl = "$tmdbBaseUrl/search/tv?api_key=$tmdbApiKey&language=en-US&query=$encodedQuery&page=$page"
            val tvResponse = httpClient.get(tvUrl)
            results.addAll(parseTmdbResponse(tvResponse, VideoType.TV_SERIES))

        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "Search failed", e)
        }

        return results.distinctBy { it.id }
    }

    override suspend fun getDetails(video: Video): Video {
        return try {
            val tmdbId = extractTmdbId(video.id)
            val isMovie = video.id.startsWith("movie-")
            val endpoint = if (isMovie) "movie" else "tv"

            val detailsUrl = "$tmdbBaseUrl/$endpoint/$tmdbId?api_key=$tmdbApiKey&language=en-US"
            val response = httpClient.get(detailsUrl)
            val jsonObj = json.parseToJsonElement(response).jsonObject

            val title = jsonObj["title"]?.jsonPrimitive?.content
                ?: jsonObj["name"]?.jsonPrimitive?.content
                ?: video.title
            val posterPath = jsonObj["poster_path"]?.jsonPrimitive?.content
            val backdropPath = jsonObj["backdrop_path"]?.jsonPrimitive?.content
            val overview = jsonObj["overview"]?.jsonPrimitive?.content
            val voteAverage = jsonObj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
            val releaseDate = jsonObj["release_date"]?.jsonPrimitive?.content
                ?: jsonObj["first_air_date"]?.jsonPrimitive?.content
            val runtime = jsonObj["runtime"]?.jsonPrimitive?.content?.toIntOrNull()

            val genresArray = jsonObj["genres"]?.jsonArray
            val genres = genresArray?.mapNotNull {
                it.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()

            val episodeCount = if (!isMovie) {
                jsonObj["number_of_episodes"]?.jsonPrimitive?.content?.toIntOrNull()
            } else null

            val seasonCount = if (!isMovie) {
                jsonObj["number_of_seasons"]?.jsonPrimitive?.content?.toIntOrNull()
            } else null

            val statusStr = jsonObj["status"]?.jsonPrimitive?.content
            val status = when (statusStr?.lowercase()) {
                "released", "ended" -> VideoStatus.COMPLETED
                "returning series", "in production" -> VideoStatus.ONGOING
                "planned", "post production" -> VideoStatus.UPCOMING
                else -> VideoStatus.UNKNOWN
            }

            video.copy(
                title = title,
                posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: video.posterUrl,
                backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" },
                description = overview,
                year = releaseDate?.take(4)?.toIntOrNull() ?: video.year,
                rating = voteAverage ?: video.rating,
                duration = runtime,
                genres = genres,
                status = status,
                episodeCount = episodeCount,
                seasonCount = seasonCount
            )
        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "Get details failed", e)
            video
        }
    }

    override suspend fun getEpisodes(video: Video): List<Episode> {
        // For movies, return single episode
        if (video.type == VideoType.MOVIE) {
            return listOf(
                Episode(
                    id = video.id,
                    videoId = video.id,
                    sourceId = id,
                    url = video.url,
                    title = video.title,
                    number = 1
                )
            )
        }

        // For TV series, fetch from TMDB
        return try {
            val tmdbId = extractTmdbId(video.id)
            val episodes = mutableListOf<Episode>()

            val detailsUrl = "$tmdbBaseUrl/tv/$tmdbId?api_key=$tmdbApiKey&language=en-US"
            val response = httpClient.get(detailsUrl)
            val jsonObj = json.parseToJsonElement(response).jsonObject
            val numberOfSeasons = jsonObj["number_of_seasons"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1

            for (seasonNum in 1..numberOfSeasons) {
                try {
                    val seasonUrl = "$tmdbBaseUrl/tv/$tmdbId/season/$seasonNum?api_key=$tmdbApiKey&language=en-US"
                    val seasonResponse = httpClient.get(seasonUrl)
                    val seasonObj = json.parseToJsonElement(seasonResponse).jsonObject
                    val episodesArray = seasonObj["episodes"]?.jsonArray ?: continue

                    episodesArray.forEach { epElement ->
                        val epObj = epElement.jsonObject
                        val epNum = epObj["episode_number"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                        val epTitle = epObj["name"]?.jsonPrimitive?.content

                        episodes.add(Episode(
                            id = "${video.id}_s${seasonNum}_e$epNum",
                            videoId = video.id,
                            sourceId = id,
                            url = "$baseUrl/tv/$tmdbId/$seasonNum/$epNum",
                            title = epTitle,
                            number = epNum,
                            season = seasonNum
                        ))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RiveStream", "Failed to fetch season $seasonNum", e)
                }
            }

            episodes.sortedWith(compareBy({ it.season }, { it.number }))
        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "Get episodes failed", e)
            emptyList()
        }
    }

    override suspend fun getLinks(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()
        val tmdbId = extractTmdbId(episode.videoId)
        val isMovie = episode.season == null || episode.season == 0

        try {
            android.util.Log.d("RiveStream", "Getting links for TMDB ID: $tmdbId, isMovie: $isMovie")

            // Method 1: Try RiveStream direct API (if it exists)
            // TODO: Research actual RiveStream API endpoint
            // For now, we'll use a placeholder that you can help me test with Firefox

            // Method 2: Try Consumet API (open-source, reliable)
            if (links.isEmpty()) {
                try {
                    val consumetUrl = if (isMovie) {
                        "https://api.consumet.org/movies/flixhq/watch?episodeId=$tmdbId&mediaId=$tmdbId"
                    } else {
                        "https://api.consumet.org/movies/flixhq/watch?episodeId=$tmdbId-${episode.season}-${episode.number}&mediaId=$tmdbId"
                    }

                    android.util.Log.d("RiveStream", "Trying Consumet API: $consumetUrl")
                    val response = httpClient.get(consumetUrl)
                    extractConsumetLinks(response, links)
                } catch (e: Exception) {
                    android.util.Log.e("RiveStream", "Consumet API failed", e)
                }
            }

            // Method 3: Fallback to VidSrc.pro (direct, not embed)
            if (links.isEmpty()) {
                try {
                    val vidsrcUrl = if (isMovie) {
                        "https://vidsrc.pro/embed/movie/$tmdbId"
                    } else {
                        "https://vidsrc.pro/embed/tv/$tmdbId/${episode.season}/${episode.number}"
                    }

                    android.util.Log.d("RiveStream", "Trying VidSrc.pro: $vidsrcUrl")
                    extractVidSrcProLinks(vidsrcUrl, links)
                } catch (e: Exception) {
                    android.util.Log.e("RiveStream", "VidSrc.pro failed", e)
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "Get links failed", e)
        }

        return links.also {
            android.util.Log.d("RiveStream", "Found ${it.size} links total")
        }
    }

    private fun extractConsumetLinks(response: String, links: MutableList<VideoLink>) {
        try {
            val jsonObj = json.parseToJsonElement(response).jsonObject
            val sources = jsonObj["sources"]?.jsonArray ?: return

            sources.forEach { sourceElement ->
                val source = sourceElement.jsonObject
                val url = source["url"]?.jsonPrimitive?.content ?: return@forEach
                val quality = source["quality"]?.jsonPrimitive?.content ?: "Auto"
                val isM3u8 = source["isM3U8"]?.jsonPrimitive?.content?.toBoolean() ?: url.contains(".m3u8")

                links.add(VideoLink(
                    url = url,
                    quality = quality,
                    extractorName = "Consumet",
                    isM3u8 = isM3u8,
                    referer = "https://flixhq.to/"
                ))
            }

            android.util.Log.d("RiveStream", "Extracted ${links.size} links from Consumet")
        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "Failed to parse Consumet response", e)
        }
    }

    private suspend fun extractVidSrcProLinks(url: String, links: MutableList<VideoLink>) {
        try {
            // This will need inspection with your modded Firefox
            // For now, placeholder implementation
            android.util.Log.d("RiveStream", "VidSrc.pro extraction needs implementation")

            // TODO: Use your Firefox to inspect the network requests
            // and extract the actual video URL pattern
        } catch (e: Exception) {
            android.util.Log.e("RiveStream", "VidSrc.pro extraction failed", e)
        }
    }

    override suspend fun ping(): Boolean {
        return try {
            httpClient.ping(tmdbBaseUrl) > 0
        } catch (e: Exception) {
            false
        }
    }
}
