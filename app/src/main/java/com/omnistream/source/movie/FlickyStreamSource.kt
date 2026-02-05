package com.omnistream.source.movie

import com.omnistream.core.crypto.CryptoUtils
import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.HomeSection
import com.omnistream.domain.model.Video
import com.omnistream.domain.model.VideoLink
import com.omnistream.domain.model.VideoStatus
import com.omnistream.source.model.VideoSource
import com.omnistream.source.model.VideoType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import org.jsoup.Jsoup

/**
 * FlickyStream source for movies and TV shows.
 * Site: https://flickystream.ru
 */
class FlickyStreamSource(
    private val httpClient: OmniHttpClient
) : VideoSource {

    override val id = "flickystream"
    override val name = "FlickyStream"
    override val baseUrl = "https://flickystream.ru"
    override val lang = "en"
    override val supportedTypes = setOf(VideoType.MOVIE, VideoType.TV_SERIES)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Vidzee TMDB proxy API
    private val tmdbProxyUrl = "https://mid.vidzee.wtf/tmdb"
    private val tmdbApiKey = com.omnistream.BuildConfig.TMDB_API_KEY_PRIMARY

    // Vidzee decryption key (cached)
    private var vidzeeKey: String? = null

    /**
     * Extract the actual TMDB ID from prefixed format (e.g., "tv-12345" -> "12345")
     */
    private fun extractTmdbId(prefixedId: String): String {
        return prefixedId.substringAfter("-", prefixedId)
    }

    /**
     * Parse stream URL and extract actual source if it's a smashystream proxy URL.
     *
     * Smashystream proxy format:
     * https://streams.smashystream.top/proxy/m3u8/{url_encoded_source}/{json_headers}
     *
     * Returns Triple of (finalUrl, referer, headers)
     */
    private fun parseStreamUrl(url: String): Triple<String, String, Map<String, String>> {
        android.util.Log.d("FlickyStream", "parseStreamUrl input: ${url.take(100)}...")

        // Check if it's a smashystream proxy URL
        if (url.contains("smashystream.top/proxy/")) {
            try {
                android.util.Log.d("FlickyStream", "Detected smashystream proxy URL, extracting source...")

                // Extract parts after /proxy/m3u8/
                val proxyPath = url.substringAfter("/proxy/m3u8/")
                android.util.Log.d("FlickyStream", "Proxy path: ${proxyPath.take(100)}...")

                // The format is: {encoded_source_url}/{encoded_headers}
                // Find the last / followed by encoded JSON (starts with %7B which is {)
                val lastJsonStart = proxyPath.lastIndexOf("/%7B")
                android.util.Log.d("FlickyStream", "lastJsonStart position: $lastJsonStart")

                if (lastJsonStart > 0) {
                    val encodedSourceUrl = proxyPath.substring(0, lastJsonStart)
                    val encodedHeaders = proxyPath.substring(lastJsonStart + 1)

                    // Decode the source URL
                    val sourceUrl = java.net.URLDecoder.decode(encodedSourceUrl, "UTF-8")

                    // Decode and parse the headers JSON
                    val headersJson = java.net.URLDecoder.decode(encodedHeaders, "UTF-8")
                    android.util.Log.d("FlickyStream", "EXTRACTED source URL: $sourceUrl")
                    android.util.Log.d("FlickyStream", "EXTRACTED headers JSON: $headersJson")

                    // Parse headers from JSON
                    val headersMap = mutableMapOf<String, String>()
                    var referer = "https://rapidairmax.site/"

                    try {
                        val jsonObj = json.parseToJsonElement(headersJson) as? kotlinx.serialization.json.JsonObject
                        jsonObj?.forEach { (key, value) ->
                            val strValue = (value as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                            if (key.equals("referer", ignoreCase = true)) {
                                referer = strValue
                            }
                            headersMap[key.replaceFirstChar { it.uppercase() }] = strValue
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FlickyStream", "Failed to parse headers JSON", e)
                    }

                    // Add Origin if we have Referer
                    if (!headersMap.containsKey("Origin") && referer.isNotEmpty()) {
                        headersMap["Origin"] = referer.trimEnd('/')
                    }

                    android.util.Log.d("FlickyStream", "Returning extracted URL with referer: $referer")
                    return Triple(sourceUrl, referer, headersMap)
                } else {
                    android.util.Log.w("FlickyStream", "Could not find /%7B in proxy path, using original URL")
                }
            } catch (e: Exception) {
                android.util.Log.e("FlickyStream", "Failed to parse smashystream proxy URL", e)
            }
        }

        // Default handling for non-proxy URLs
        val (referer, origin) = when {
            url.contains("rapidairmax") -> Pair("https://rapidairmax.site/", "https://rapidairmax.site")
            url.contains("khdiamondcdn") -> Pair("https://player.vidzee.wtf/", "https://player.vidzee.wtf")
            url.contains("core36link") -> Pair("https://rapidairmax.site/", "https://rapidairmax.site")
            url.contains("smart32grid") -> Pair("https://rapidairmax.site/", "https://rapidairmax.site")
            else -> Pair("https://player.vidzee.wtf/", "https://player.vidzee.wtf")
        }

        android.util.Log.d("FlickyStream", "Using default headers for URL, referer: $referer")
        return Triple(url, referer, mapOf("Origin" to origin))
    }

    /**
     * Fetch and decrypt the Vidzee API key used for decrypting video links.
     * Key is fetched from https://core.vidzee.wtf/api-key and decrypted with AES-GCM.
     */
    private suspend fun getVidzeeKey(): String {
        vidzeeKey?.let { return it }

        return try {
            val response = httpClient.get("https://core.vidzee.wtf/api-key")
            val decryptedKey = CryptoUtils.vidzeeDecryptApiKey(response.trim())
            android.util.Log.d("FlickyStream", "Fetched vidzee key: $decryptedKey")
            vidzeeKey = decryptedKey
            decryptedKey
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Failed to fetch vidzee key", e)
            ""
        }
    }

    override suspend fun getHomePage(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()

        try {
            android.util.Log.d("FlickyStream", "Loading home page via TMDB proxy")

            // Trending
            val trending = fetchFromTmdbProxy("trending/movie/day")
            if (trending.isNotEmpty()) {
                sections.add(HomeSection("Trending", trending.take(20)))
            }

            // Popular Movies
            val popular = fetchFromTmdbProxy("movie/popular")
            if (popular.isNotEmpty()) {
                sections.add(HomeSection("Popular Movies", popular.take(20)))
            }

            // Top Rated
            val topRated = fetchFromTmdbProxy("movie/top_rated")
            if (topRated.isNotEmpty()) {
                sections.add(HomeSection("Top Rated", topRated.take(20)))
            }

            // Upcoming
            val upcoming = fetchFromTmdbProxy("movie/upcoming")
            if (upcoming.isNotEmpty()) {
                sections.add(HomeSection("Coming Soon", upcoming.take(20)))
            }

            // Popular TV Shows
            val tvPopular = fetchFromTmdbProxy("tv/popular")
            if (tvPopular.isNotEmpty()) {
                sections.add(HomeSection("Popular TV Shows", tvPopular.take(20)))
            }

        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Failed to load home page", e)
        }

        return sections
    }

    private suspend fun fetchFromTmdbProxy(endpoint: String): List<Video> {
        return try {
            val url = "$tmdbProxyUrl/$endpoint?api_key=$tmdbApiKey&language=en-US&page=1"
            android.util.Log.d("FlickyStream", "Fetching: $url")

            val response = httpClient.get(url)
            android.util.Log.d("FlickyStream", "TMDB response length: ${response.length}")

            parseTmdbResponse(response, if (endpoint.contains("tv")) VideoType.TV_SERIES else VideoType.MOVIE)
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "TMDB proxy fetch failed: $endpoint", e)
            emptyList()
        }
    }

    private fun parseTmdbResponse(response: String, type: VideoType): List<Video> {
        return try {
            val jsonObj = json.parseToJsonElement(response)
            if (jsonObj !is JsonObject) return emptyList()

            val results = jsonObj["results"]
            if (results !is JsonArray) return emptyList()

            results.mapNotNull { element ->
                try {
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val id = (item["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
                    val rawTitle = (item["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (item["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: return@mapNotNull null
                    // Decode URL-encoded characters (+ to space, %20 to space, etc.)
                    val title = java.net.URLDecoder.decode(rawTitle.replace("+", " "), "UTF-8")
                    val posterPath = (item["poster_path"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val releaseDate = (item["release_date"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (item["first_air_date"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val voteAverage = (item["vote_average"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()

                    // Prefix ID with type for VideoDetailViewModel type detection
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
            }.also {
                android.util.Log.d("FlickyStream", "Parsed ${it.size} items from TMDB")
            }
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "TMDB parse failed", e)
            emptyList()
        }
    }

    private fun parseApiResponse(response: String): List<Video> {
        return try {
            // Try parsing as array
            if (response.trimStart().startsWith("[")) {
                val items = json.decodeFromString<List<FlickyItem>>(response)
                return items.mapNotNull { parseFlickyItem(it) }
            }

            // Try parsing as object with results/data array
            val jsonObj = json.parseToJsonElement(response)
            if (jsonObj is kotlinx.serialization.json.JsonObject) {
                val results = jsonObj["results"] ?: jsonObj["data"] ?: jsonObj["movies"] ?: jsonObj["items"]
                if (results is kotlinx.serialization.json.JsonArray) {
                    return results.mapNotNull { element ->
                        try {
                            val item = json.decodeFromJsonElement<FlickyItem>(element)
                            parseFlickyItem(item)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "API parse failed", e)
            emptyList()
        }
    }

    private fun parseFlickyItem(item: FlickyItem): Video? {
        val videoId = item.id?.toString() ?: item.slug ?: return null
        val rawTitle = item.title ?: item.name ?: return null
        // Decode URL-encoded characters (+ to space, %20 to space, etc.)
        val title = java.net.URLDecoder.decode(rawTitle.replace("+", " "), "UTF-8")

        return Video(
            id = videoId,
            sourceId = id,
            title = title,
            url = "$baseUrl/movie/$videoId",
            type = VideoType.MOVIE,
            posterUrl = item.poster ?: item.image ?: if (item.poster_path != null) "https://image.tmdb.org/t/p/w342${item.poster_path}" else null,
            year = item.year ?: item.release_date?.take(4)?.toIntOrNull(),
            rating = item.rating?.toFloatOrNull() ?: item.vote_average
        )
    }

    private fun parseNextData(nextData: String, type: VideoType): List<Video> {
        return try {
            val jsonObj = json.parseToJsonElement(nextData)
            if (jsonObj !is JsonObject) return emptyList()

            val props = jsonObj["props"] as? JsonObject ?: return emptyList()
            val pageProps = props["pageProps"] as? JsonObject ?: return emptyList()

            // Try different possible keys for movie/tv data
            val possibleKeys = listOf("movies", "trending", "popular", "results", "data", "items", "shows", "tvShows")
            for (key in possibleKeys) {
                val items = pageProps[key]
                if (items is JsonArray && items.isNotEmpty()) {
                    android.util.Log.d("FlickyStream", "Found ${items.size} items in __NEXT_DATA__.$key")
                    return items.mapNotNull { element ->
                        try {
                            val item = json.decodeFromJsonElement<FlickyItem>(element)
                            parseFlickyItem(item)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }
            emptyList()
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Failed to parse __NEXT_DATA__", e)
            emptyList()
        }
    }

    private suspend fun fetchContent(url: String, type: VideoType): List<Video> {
        return try {
            val response = httpClient.get(url)
            android.util.Log.d("FlickyStream", "Fetching: $url, response length: ${response.length}")

            // Try JSON API first
            if (response.trimStart().startsWith("[") || response.trimStart().startsWith("{")) {
                return parseJsonResponse(response, type)
            }

            // Fall back to HTML parsing
            val doc = Jsoup.parse(response, url)

            // Try to find __NEXT_DATA__ script (Next.js apps embed initial data here)
            val nextDataScript = doc.selectFirst("script#__NEXT_DATA__")
            if (nextDataScript != null) {
                val nextData = nextDataScript.data()
                android.util.Log.d("FlickyStream", "Found __NEXT_DATA__: ${nextData.take(500)}")
                val movies = parseNextData(nextData, type)
                if (movies.isNotEmpty()) return movies
            }

            // Also check for self.__next_f.push patterns (React Server Components)
            val rscPattern = Regex("""self\.__next_f\.push\(\[([\d]+),"(.+?)"\]\)""")
            doc.select("script").forEach { script ->
                val content = script.data()
                if (content.contains("__next_f")) {
                    android.util.Log.d("FlickyStream", "Found RSC data")
                }
            }

            // FlickyStream uses a[href^="/movie/"] or a[href^="/tv/"] for cards
            val movieLinks = doc.select("a[href^='/movie/'], a[href^='/tv/']")
            android.util.Log.d("FlickyStream", "Found ${movieLinks.size} movie/tv links")

            movieLinks.mapNotNull { element ->
                try {
                    val href = element.attr("href")
                    if (href.isBlank()) return@mapNotNull null

                    val videoId = href.substringAfterLast("/").substringBefore("?")
                    if (videoId.isBlank()) return@mapNotNull null

                    // Title from h3 or img alt attribute
                    val title = element.selectFirst("h3")?.text()?.trim()
                        ?: element.selectFirst("img")?.attr("alt")?.trim()
                        ?: return@mapNotNull null
                    if (title.isBlank()) return@mapNotNull null

                    // Poster from img src (TMDB images)
                    val posterUrl = element.selectFirst("img")?.attr("src")

                    // Year from span.line-clamp-1
                    val year = element.selectFirst("span.line-clamp-1")?.text()?.trim()?.toIntOrNull()

                    // Rating from the amber div (text after SVG)
                    val ratingText = element.selectFirst("div.text-amber-400, div[class*='amber']")?.text()?.trim()
                    val rating = ratingText?.toFloatOrNull()

                    val isMovie = href.contains("/movie/")

                    Video(
                        id = videoId,
                        sourceId = id,
                        title = title,
                        url = "$baseUrl$href",
                        type = if (isMovie) VideoType.MOVIE else VideoType.TV_SERIES,
                        posterUrl = posterUrl,
                        year = year,
                        rating = rating
                    )
                } catch (e: Exception) {
                    null
                }
            }.distinctBy { it.id }.also {
                android.util.Log.d("FlickyStream", "Parsed ${it.size} videos")
            }
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Fetch failed: $url", e)
            emptyList()
        }
    }

    private fun parseJsonResponse(response: String, type: VideoType): List<Video> {
        return try {
            val items = json.decodeFromString<List<FlickyItem>>(response)
            items.mapNotNull { item ->
                Video(
                    id = item.id?.toString() ?: item.slug ?: return@mapNotNull null,
                    sourceId = id,
                    title = item.title ?: item.name ?: return@mapNotNull null,
                    url = "$baseUrl/${if (type == VideoType.MOVIE) "movie" else "show"}/${item.slug ?: item.id}",
                    type = type,
                    posterUrl = item.poster ?: item.image,
                    year = item.year,
                    rating = item.rating?.toFloatOrNull()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "JSON parse failed", e)
            emptyList()
        }
    }

    private fun extractId(href: String): String {
        return href
            .substringAfterLast("/")
            .substringBefore("?")
            .substringBefore("#")
    }

    override suspend fun search(query: String, page: Int): List<Video> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<Video>()

        try {
            // Search movies via TMDB proxy
            val movieUrl = "$tmdbProxyUrl/search/movie?api_key=$tmdbApiKey&language=en-US&query=$encodedQuery&page=$page"
            android.util.Log.d("FlickyStream", "Searching movies: $movieUrl")
            val movieResponse = httpClient.get(movieUrl)
            results.addAll(parseTmdbResponse(movieResponse, VideoType.MOVIE))

            // Search TV shows via TMDB proxy
            val tvUrl = "$tmdbProxyUrl/search/tv?api_key=$tmdbApiKey&language=en-US&query=$encodedQuery&page=$page"
            android.util.Log.d("FlickyStream", "Searching TV: $tvUrl")
            val tvResponse = httpClient.get(tvUrl)
            results.addAll(parseTmdbResponse(tvResponse, VideoType.TV_SERIES))

        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Search failed", e)
        }

        return results.distinctBy { it.id }.also {
            android.util.Log.d("FlickyStream", "Search returned ${it.size} results")
        }
    }

    override suspend fun getDetails(video: Video): Video {
        return try {
            val tmdbId = extractTmdbId(video.id)
            val isMovie = video.id.startsWith("movie-") || video.type == VideoType.MOVIE
            val endpoint = if (isMovie) "movie" else "tv"

            android.util.Log.d("FlickyStream", "Getting details from TMDB: $endpoint/$tmdbId")

            val detailsUrl = "$tmdbProxyUrl/$endpoint/$tmdbId?api_key=$tmdbApiKey&language=en-US"
            val response = httpClient.get(detailsUrl)

            val jsonObj = json.parseToJsonElement(response) as? kotlinx.serialization.json.JsonObject
                ?: return video

            val title = (jsonObj["title"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (jsonObj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: video.title

            val posterPath = (jsonObj["poster_path"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val backdropPath = (jsonObj["backdrop_path"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val overview = (jsonObj["overview"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val voteAverage = (jsonObj["vote_average"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toFloatOrNull()
            val releaseDate = (jsonObj["release_date"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                ?: (jsonObj["first_air_date"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val runtime = (jsonObj["runtime"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

            // Parse genres
            val genresArray = jsonObj["genres"] as? kotlinx.serialization.json.JsonArray
            val genres = genresArray?.mapNotNull { genreItem ->
                val genreObj = genreItem as? kotlinx.serialization.json.JsonObject
                (genreObj?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content
            } ?: emptyList()

            // Get episode count for TV shows
            val episodeCount = if (!isMovie) {
                (jsonObj["number_of_episodes"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            } else null

            val seasonCount = if (!isMovie) {
                (jsonObj["number_of_seasons"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            } else null

            // Get status
            val statusStr = (jsonObj["status"] as? kotlinx.serialization.json.JsonPrimitive)?.content
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
            ).also {
                android.util.Log.d("FlickyStream", "Details loaded: ${it.title}, poster: ${it.posterUrl}, backdrop: ${it.backdropUrl}")
            }
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Get details failed", e)
            video
        }
    }

    override suspend fun getEpisodes(video: Video): List<Episode> {
        // For movies, return single episode
        if (video.type == VideoType.MOVIE || video.url.contains("/movie/")) {
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

        // For TV series, fetch seasons and episodes from TMDB
        return try {
            val tmdbId = extractTmdbId(video.id)
            val episodes = mutableListOf<Episode>()
            val detailsUrl = "$tmdbProxyUrl/tv/$tmdbId?api_key=$tmdbApiKey&language=en-US"
            android.util.Log.d("FlickyStream", "Getting episodes from TMDB: $detailsUrl")

            val response = httpClient.get(detailsUrl)
            val jsonObj = json.parseToJsonElement(response) as? kotlinx.serialization.json.JsonObject ?: return emptyList()

            val numberOfSeasons = (jsonObj["number_of_seasons"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 1

            // Fetch episodes for each season
            for (seasonNum in 1..numberOfSeasons) {
                try {
                    val seasonUrl = "$tmdbProxyUrl/tv/$tmdbId/season/$seasonNum?api_key=$tmdbApiKey&language=en-US"
                    val seasonResponse = httpClient.get(seasonUrl)
                    val seasonObj = json.parseToJsonElement(seasonResponse) as? kotlinx.serialization.json.JsonObject ?: continue

                    val episodesArray = seasonObj["episodes"] as? kotlinx.serialization.json.JsonArray ?: continue
                    episodesArray.forEach { epElement ->
                        val epObj = epElement as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val epNum = (epObj["episode_number"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: return@forEach
                        val epTitle = (epObj["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content

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
                    android.util.Log.e("FlickyStream", "Failed to fetch season $seasonNum", e)
                }
            }

            episodes.sortedWith(compareBy({ it.season }, { it.number })).also {
                android.util.Log.d("FlickyStream", "Found ${it.size} episodes from TMDB")
            }
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Get episodes failed", e)
            emptyList()
        }
    }

    override suspend fun getLinks(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()
        // Extract actual TMDB ID from videoId (e.g., "tv-66732" -> "66732")
        val tmdbId = extractTmdbId(episode.videoId)
        // Determine if movie or TV based on episode data
        val isMovie = episode.season == null || episode.season == 0
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        try {
            android.util.Log.d("FlickyStream", "Getting links for TMDB ID: $tmdbId, isMovie: $isMovie")

            // Method 1: Try player.vidzee.wtf API with proper decryption
            try {
                // First, get the decryption key
                val decryptionKey = getVidzeeKey()
                android.util.Log.d("FlickyStream", "Using vidzee decryption key: $decryptionKey")

                for (server in listOf(2, 0, 1)) { // Try server 2 first - it usually works!
                    if (links.isNotEmpty()) break

                    // Build API URL based on movie or TV
                    val apiUrl = if (isMovie) {
                        "https://player.vidzee.wtf/api/server?id=$tmdbId&sr=$server"
                    } else {
                        "https://player.vidzee.wtf/api/server?id=$tmdbId&sr=$server&ss=${episode.season}&ep=${episode.number}"
                    }
                    val refererType = if (isMovie) "movie" else "tv"
                    android.util.Log.d("FlickyStream", "Trying vidzee API server $server: $apiUrl")

                    try {
                        val response = httpClient.get(apiUrl, headers = mapOf(
                            "User-Agent" to userAgent,
                            "Referer" to "https://player.vidzee.wtf/embed/$refererType/$tmdbId",
                            "Origin" to "https://player.vidzee.wtf",
                            "Accept" to "application/json, text/plain, */*",
                            "Accept-Language" to "en-US,en;q=0.9",
                            "sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
                            "sec-ch-ua-mobile" to "?0",
                            "sec-ch-ua-platform" to "\"Windows\"",
                            "sec-fetch-dest" to "empty",
                            "sec-fetch-mode" to "cors",
                            "sec-fetch-site" to "same-origin"
                        ))
                        android.util.Log.d("FlickyStream", "Vidzee API server $server response: ${response.take(500)}")

                        // Check for error response
                        if (response.contains("\"error\"")) {
                            android.util.Log.d("FlickyStream", "Server $server returned error, trying next...")
                            continue
                        }

                        // Parse JSON response - format: {"provider":"Glory","url":[{"lang":"English","link":"encrypted..."}]}
                        if (response.contains("\"url\"") && response.contains("\"link\"")) {
                            try {
                                val jsonObj = json.parseToJsonElement(response) as? kotlinx.serialization.json.JsonObject
                                val urlArray = jsonObj?.get("url") as? kotlinx.serialization.json.JsonArray
                                val provider = (jsonObj?.get("provider") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Unknown"

                                // Extract subtitles first so we can add them to each link
                                val subtitlesList = mutableListOf<com.omnistream.domain.model.Subtitle>()
                                val tracksArray = jsonObj?.get("tracks") as? kotlinx.serialization.json.JsonArray
                                tracksArray?.forEach { trackItem ->
                                    val trackObj = trackItem as? kotlinx.serialization.json.JsonObject
                                    val trackUrl = (trackObj?.get("url") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                    val trackLang = (trackObj?.get("lang") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                    val trackLabel = (trackObj?.get("label") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                    if (trackUrl != null && trackLang != null) {
                                        android.util.Log.d("FlickyStream", "Found subtitle: $trackLang - $trackUrl")
                                        subtitlesList.add(com.omnistream.domain.model.Subtitle(
                                            url = trackUrl,
                                            language = trackLang,
                                            label = trackLabel ?: trackLang,
                                            isDefault = trackLang.lowercase().contains("english")
                                        ))
                                    }
                                }
                                android.util.Log.d("FlickyStream", "Total subtitles found: ${subtitlesList.size}")

                                urlArray?.forEach { urlItem ->
                                    val urlObj = urlItem as? kotlinx.serialization.json.JsonObject
                                    val encryptedLink = (urlObj?.get("link") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                    val lang = (urlObj?.get("lang") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "Unknown"
                                    val linkType = (urlObj?.get("type") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "hls"

                                    if (encryptedLink != null && decryptionKey.isNotEmpty()) {
                                        android.util.Log.d("FlickyStream", "Decrypting link for $lang (provider: $provider)...")

                                        // Decrypt the link using AES-CBC
                                        val decryptedUrl = CryptoUtils.vidzeeDecryptLink(encryptedLink, decryptionKey)

                                        if (decryptedUrl.isNotEmpty() && decryptedUrl.startsWith("http")) {
                                            android.util.Log.d("FlickyStream", "Decrypted URL: $decryptedUrl")
                                            // Determine stream type - prioritize URL extension over API type
                                            val isHls = when {
                                                decryptedUrl.contains(".mp4") -> false
                                                decryptedUrl.contains(".m3u8") -> true
                                                else -> linkType == "hls"
                                            }
                                            // Check if this is a smashystream proxy URL and parse it
                                            val (finalUrl, linkReferer, linkHeaders) = parseStreamUrl(decryptedUrl)

                                            android.util.Log.d("FlickyStream", "Stream type: ${if (isHls) "HLS" else "Progressive"}, finalUrl: $finalUrl, referer: $linkReferer")
                                            links.add(VideoLink(
                                                url = finalUrl,
                                                quality = "$lang - $provider",
                                                extractorName = "Vidzee",
                                                isM3u8 = isHls || finalUrl.contains(".m3u8"),
                                                referer = linkReferer,
                                                headers = linkHeaders,
                                                subtitles = subtitlesList
                                            ))
                                        } else {
                                            android.util.Log.d("FlickyStream", "Decryption produced invalid URL: ${decryptedUrl.take(100)}")
                                        }
                                    }
                                }

                            } catch (e: Exception) {
                                android.util.Log.e("FlickyStream", "JSON parse failed: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("FlickyStream", "Vidzee API server $server failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FlickyStream", "Vidzee API failed", e)
            }

            // Method 1b: Try vidsrc.xyz as fallback
            if (links.isEmpty()) {
                try {
                    val vidsrcXyzUrl = "https://vidsrc.xyz/embed/movie/$tmdbId"
                    android.util.Log.d("FlickyStream", "Trying vidsrc.xyz: $vidsrcXyzUrl")
                    extractVidsrcLinks(vidsrcXyzUrl, links, userAgent)
                } catch (e: Exception) {
                    android.util.Log.e("FlickyStream", "vidsrc.xyz failed", e)
                }
            }

            // Method 2: Try vidsrc.me
            if (links.isEmpty()) {
                try {
                    val vidsrcMeUrl = "https://vidsrc.me/embed/movie?tmdb=$tmdbId"
                    android.util.Log.d("FlickyStream", "Trying vidsrc.me: $vidsrcMeUrl")
                    extractVidsrcLinks(vidsrcMeUrl, links, userAgent)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "vidsrc.me failed")
                }
            }

            // Method 3: Try vidsrc.net
            if (links.isEmpty()) {
                try {
                    val vidsrcNetUrl = "https://vidsrc.net/embed/movie/$tmdbId"
                    android.util.Log.d("FlickyStream", "Trying vidsrc.net: $vidsrcNetUrl")
                    extractVidsrcLinks(vidsrcNetUrl, links, userAgent)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "vidsrc.net failed")
                }
            }

            // Method 4: Try embedsu
            if (links.isEmpty()) {
                try {
                    val embedsuUrl = "https://embed.su/embed/movie/$tmdbId"
                    android.util.Log.d("FlickyStream", "Trying embed.su: $embedsuUrl")
                    val response = httpClient.get(embedsuUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    android.util.Log.d("FlickyStream", "embed.su response: ${response.take(500)}")
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, embedsuUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "embed.su failed")
                }
            }

            // Method 5: Try 2embed.org (different from 2embed.cc)
            if (links.isEmpty()) {
                try {
                    val twoEmbedUrl = "https://2embed.org/embed/movie/$tmdbId"
                    android.util.Log.d("FlickyStream", "Trying 2embed.org: $twoEmbedUrl")
                    val response = httpClient.get(twoEmbedUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, twoEmbedUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "2embed.org failed")
                }
            }

            // Method 6: Try moviesapi
            if (links.isEmpty()) {
                try {
                    val moviesApiUrl = "https://moviesapi.club/movie/$tmdbId"
                    android.util.Log.d("FlickyStream", "Trying moviesapi: $moviesApiUrl")
                    val response = httpClient.get(moviesApiUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, moviesApiUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "moviesapi failed")
                }
            }

            // Method 7: Try smashystream endpoints
            if (links.isEmpty()) {
                val smashyEndpoints = listOf(
                    "https://player.smashy.stream/movie/$tmdbId",
                    "https://embed.smashystream.com/playere.php?tmdb=$tmdbId",
                    "https://player.smashystream.com/movie/$tmdbId",
                    "https://smashystream.com/e/movie/$tmdbId"
                )

                for (smashyUrl in smashyEndpoints) {
                    if (links.isNotEmpty()) break
                    try {
                        android.util.Log.d("FlickyStream", "Trying smashy: $smashyUrl")
                        val response = httpClient.get(smashyUrl, headers = mapOf(
                            "User-Agent" to userAgent,
                            "Referer" to "https://rapidairmax.site/"
                        ))
                        android.util.Log.d("FlickyStream", "Smashy response (${smashyUrl.substringAfterLast("/")}): ${response.take(800)}")

                        // Look for the proxy m3u8 pattern
                        val proxyPattern = Regex("""(https?://streams\.smashystream\.top/proxy/m3u8/[^"'\s<>]+)""")
                        proxyPattern.findAll(response).forEach { match ->
                            val url = match.groupValues[1].replace("\\u002F", "/").replace("\\/", "/")
                            android.util.Log.d("FlickyStream", "Found smashy proxy URL: $url")
                            links.add(VideoLink(
                                url = url,
                                quality = "Auto",
                                extractorName = "SmashyStream",
                                isM3u8 = true,
                                referer = "https://rapidairmax.site/"
                            ))
                        }

                        // Look for sources array in JS
                        val sourcesMatch = Regex("""sources\s*[:=]\s*\[([^\]]+)\]""").find(response)
                        if (sourcesMatch != null) {
                            android.util.Log.d("FlickyStream", "Found sources: ${sourcesMatch.groupValues[1].take(200)}")
                            Regex("""["']?(https?://[^"'\s,\]]+)["']?""").findAll(sourcesMatch.groupValues[1]).forEach { urlMatch ->
                                val url = urlMatch.groupValues[1].replace("\\/", "/")
                                if ((url.contains(".m3u8") || url.contains("proxy")) && !links.any { it.url == url }) {
                                    links.add(VideoLink(
                                        url = url,
                                        quality = extractQuality(url),
                                        extractorName = "SmashyStream",
                                        isM3u8 = true,
                                        referer = "https://rapidairmax.site/"
                                    ))
                                }
                            }
                        }

                        // Look for file: pattern
                        val fileMatch = Regex("""["']?file["']?\s*[:=]\s*["']([^"']+)["']""").find(response)
                        if (fileMatch != null) {
                            val url = fileMatch.groupValues[1].replace("\\/", "/")
                            android.util.Log.d("FlickyStream", "Found file URL: $url")
                            if (!links.any { it.url == url }) {
                                links.add(VideoLink(
                                    url = url,
                                    quality = "Auto",
                                    extractorName = "SmashyStream",
                                    isM3u8 = url.contains(".m3u8") || url.contains("proxy"),
                                    referer = "https://rapidairmax.site/"
                                ))
                            }
                        }

                        extractLinksFromHtml(response, links)
                        followIframeChain(response, smashyUrl, links, userAgent, 0)
                    } catch (e: Exception) {
                        android.util.Log.d("FlickyStream", "Smashy endpoint failed: ${smashyUrl.substringAfterLast("/")}")
                    }
                }
            }

            // Method 8: Try nontongo
            if (links.isEmpty()) {
                try {
                    val nontongoUrl = "https://www.nontongo.win/embed/movie/$tmdbId"
                    android.util.Log.d("FlickyStream", "Trying nontongo: $nontongoUrl")
                    val response = httpClient.get(nontongoUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, nontongoUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "nontongo failed")
                }
            }

            // Method 9: Try autoembed.cc (often has direct m3u8)
            if (links.isEmpty()) {
                try {
                    val autoembedUrl = if (isMovie) {
                        "https://autoembed.cc/embed/movie/$tmdbId"
                    } else {
                        "https://autoembed.cc/embed/tv/$tmdbId/${episode.season}/${episode.number}"
                    }
                    android.util.Log.d("FlickyStream", "Trying autoembed: $autoembedUrl")
                    val response = httpClient.get(autoembedUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    android.util.Log.d("FlickyStream", "Autoembed response: ${response.take(500)}")
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, autoembedUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "autoembed failed: ${e.message}")
                }
            }

            // Method 10: Try vidsrc.cc (newer vidsrc domain)
            if (links.isEmpty()) {
                try {
                    val vidsrcCcUrl = if (isMovie) {
                        "https://vidsrc.cc/v2/embed/movie/$tmdbId"
                    } else {
                        "https://vidsrc.cc/v2/embed/tv/$tmdbId/${episode.season}/${episode.number}"
                    }
                    android.util.Log.d("FlickyStream", "Trying vidsrc.cc: $vidsrcCcUrl")
                    val response = httpClient.get(vidsrcCcUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    android.util.Log.d("FlickyStream", "vidsrc.cc response: ${response.take(500)}")
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, vidsrcCcUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "vidsrc.cc failed: ${e.message}")
                }
            }

            // Method 11: Try vidsrc.icu (sometimes provides direct m3u8)
            if (links.isEmpty()) {
                try {
                    val vidsrcIcuUrl = if (isMovie) {
                        "https://vidsrc.icu/embed/movie/$tmdbId"
                    } else {
                        "https://vidsrc.icu/embed/tv/$tmdbId/${episode.season}/${episode.number}"
                    }
                    android.util.Log.d("FlickyStream", "Trying vidsrc.icu: $vidsrcIcuUrl")
                    val response = httpClient.get(vidsrcIcuUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    android.util.Log.d("FlickyStream", "vidsrc.icu response: ${response.take(500)}")
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, vidsrcIcuUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "vidsrc.icu failed: ${e.message}")
                }
            }

            // Method 12: Try superembed (often has direct sources)
            if (links.isEmpty()) {
                try {
                    val superembedUrl = if (isMovie) {
                        "https://multiembed.mov/directstream.php?video_id=$tmdbId&tmdb=1"
                    } else {
                        "https://multiembed.mov/directstream.php?video_id=$tmdbId&tmdb=1&s=${episode.season}&e=${episode.number}"
                    }
                    android.util.Log.d("FlickyStream", "Trying multiembed: $superembedUrl")
                    val response = httpClient.get(superembedUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to "https://flickystream.ru/"
                    ))
                    android.util.Log.d("FlickyStream", "multiembed response: ${response.take(500)}")
                    extractLinksFromHtml(response, links)
                    followIframeChain(response, superembedUrl, links, userAgent, 0)
                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "multiembed failed: ${e.message}")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Get links failed", e)
        }

        return links.distinctBy { it.url }.also {
            android.util.Log.d("FlickyStream", "Found ${it.size} links total")
        }
    }

    private suspend fun extractVidsrcLinks(url: String, links: MutableList<VideoLink>, userAgent: String) {
        val response = httpClient.get(url, headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to "https://flickystream.ru/"
        ))
        android.util.Log.d("FlickyStream", "Vidsrc response length: ${response.length}")

        // Extract direct video links
        extractLinksFromHtml(response, links)

        // Look for data-hash or encoded sources
        val hashMatch = Regex("""data-hash=["']([^"']+)["']""").find(response)
        if (hashMatch != null) {
            val hash = hashMatch.groupValues[1]
            android.util.Log.d("FlickyStream", "Found data-hash: $hash")
            // Try to decode or fetch from API
            try {
                val apiUrl = url.substringBefore("/embed") + "/api/source/$hash"
                val apiResponse = httpClient.get(apiUrl, headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to url
                ))
                android.util.Log.d("FlickyStream", "Hash API response: ${apiResponse.take(500)}")
                extractLinksFromHtml(apiResponse, links)
            } catch (e: Exception) {
                android.util.Log.d("FlickyStream", "Hash API failed")
            }
        }

        // Look for rcpUrl pattern (common in vidsrc)
        val rcpMatch = Regex("""["']?rcp["']?\s*[:=]\s*["']([^"']+)["']""").find(response)
        if (rcpMatch != null) {
            val rcpUrl = rcpMatch.groupValues[1]
            android.util.Log.d("FlickyStream", "Found rcp URL: $rcpUrl")
            try {
                val fullRcpUrl = if (rcpUrl.startsWith("//")) "https:$rcpUrl"
                    else if (rcpUrl.startsWith("/")) url.substringBefore("/", "").substringBeforeLast("/") + rcpUrl
                    else rcpUrl
                val rcpResponse = httpClient.get(fullRcpUrl, headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to url
                ))
                extractLinksFromHtml(rcpResponse, links)
            } catch (e: Exception) {
                android.util.Log.d("FlickyStream", "RCP URL failed")
            }
        }

        // Follow iframe chain
        followIframeChain(response, url, links, userAgent, 0)
    }

    private suspend fun followIframeChain(html: String, referer: String, links: MutableList<VideoLink>, userAgent: String, depth: Int) {
        if (depth > 3) return // Prevent infinite recursion

        // Find all iframe sources
        val iframePattern = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        iframePattern.findAll(html).forEach { match ->
            var iframeSrc = match.groupValues[1]
            if (iframeSrc.isBlank() || iframeSrc.contains("ads") || iframeSrc.contains("banner")) return@forEach

            // Normalize URL
            iframeSrc = when {
                iframeSrc.startsWith("//") -> "https:$iframeSrc"
                iframeSrc.startsWith("/") -> {
                    val baseUrl = referer.substringBefore("/", "https://").let {
                        if (it == referer) referer.substringBefore("/", referer.take(50))
                        else it
                    }
                    "$baseUrl$iframeSrc"
                }
                else -> iframeSrc
            }

            android.util.Log.d("FlickyStream", "Following iframe (depth $depth): $iframeSrc")

            try {
                val iframeResponse = httpClient.get(iframeSrc, headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to referer
                ))

                // Log response for cloudnestra debugging
                if (iframeSrc.contains("cloudnestra")) {
                    android.util.Log.d("FlickyStream", "Cloudnestra response (${iframeResponse.length} chars): ${iframeResponse.take(1000)}")

                    // Try to extract prorcp URL from cloudnestra response
                    extractFromCloudnestra(iframeResponse, iframeSrc, links, userAgent)
                }

                // Extract links from iframe content
                extractLinksFromHtml(iframeResponse, links)

                // Look for video sources in JS
                extractJsVideoSources(iframeResponse, links, iframeSrc)

                // Recursively follow nested iframes
                if (links.isEmpty()) {
                    followIframeChain(iframeResponse, iframeSrc, links, userAgent, depth + 1)
                }
            } catch (e: Exception) {
                android.util.Log.d("FlickyStream", "Iframe failed: $iframeSrc - ${e.message}")
            }
        }
    }

    /**
     * Extract video URL from cloudnestra pages.
     * Cloudnestra uses a two-step process:
     * 1. /rcp/{hash} -> returns page with prorcp hash
     * 2. /prorcp/{hash2} -> returns page with file: URL containing CDN placeholders
     */
    private suspend fun extractFromCloudnestra(response: String, referer: String, links: MutableList<VideoLink>, userAgent: String) {
        // CDN placeholder replacements
        // Placeholders are the BASE domain, subdomains are in the URL itself
        // e.g., "tmstr3.{v1}/pl/..." so {v1} = "quibblezoomfable.com"
        val cdnReplacements = mapOf(
            "{v1}" to "quibblezoomfable.com",
            "{v2}" to "quibblezoomfable.com",
            "{v3}" to "quibblezoomfable.com",
            "{v4}" to "quibblezoomfable.com",
            "{v5}" to "quibblezoomfable.com"
        )

        // Look for prorcp hash in the response - pattern: /prorcp/{hash} or loadIframe('/prorcp/{hash}')
        val prorcpPatterns = listOf(
            Regex("""/prorcp/([a-zA-Z0-9]+)"""),
            Regex("""loadIframe\s*\(\s*['"]([^'"]+)['"]"""),
            Regex("""data-src=["']([^"']*prorcp[^"']*)["']""")
        )

        for (pattern in prorcpPatterns) {
            val match = pattern.find(response)
            if (match != null) {
                val matchValue = match.groupValues[1]
                val prorcpPath = if (matchValue.startsWith("/")) matchValue else "/prorcp/$matchValue"
                val prorcpUrl = "https://cloudnestra.com$prorcpPath"

                android.util.Log.d("FlickyStream", "Found prorcp URL: $prorcpUrl")

                try {
                    val prorcpResponse = httpClient.get(prorcpUrl, headers = mapOf(
                        "User-Agent" to userAgent,
                        "Referer" to referer
                    ))

                    android.util.Log.d("FlickyStream", "Prorcp response (${prorcpResponse.length} chars): ${prorcpResponse.take(1000)}")

                    // Look for file: "url" pattern with CDN placeholders
                    val filePattern = Regex("""file:\s*["']([^"']+)["']""")
                    filePattern.find(prorcpResponse)?.let { fileMatch ->
                        var url = fileMatch.groupValues[1]
                        android.util.Log.d("FlickyStream", "Found file URL (raw): $url")

                        // Replace CDN placeholders
                        cdnReplacements.forEach { (placeholder, domain) ->
                            url = url.replace(placeholder, domain)
                        }

                        // Unescape URL
                        url = url.replace("\\/", "/").replace("\\u002F", "/")

                        if (url.startsWith("http") && (url.contains(".m3u8") || url.contains("/pl/"))) {
                            android.util.Log.d("FlickyStream", "Found cloudnestra m3u8: $url")
                            links.add(VideoLink(
                                url = url,
                                quality = "Auto",
                                extractorName = "Cloudnestra",
                                isM3u8 = true,
                                referer = "https://cloudnestra.com/",
                                headers = mapOf("Referer" to "https://cloudnestra.com/")
                            ))
                        }
                    }

                    // Also extract any other m3u8 URLs
                    extractLinksFromHtml(prorcpResponse, links)

                } catch (e: Exception) {
                    android.util.Log.d("FlickyStream", "Prorcp fetch failed: ${e.message}")
                }

                break // Stop after first match
            }
        }
    }

    private fun extractJsVideoSources(html: String, links: MutableList<VideoLink>, referer: String) {
        // Look for common JS patterns that contain video URLs

        // Pattern: file: "url" or source: "url"
        val filePatterns = listOf(
            Regex("""["']?file["']?\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""["']?source["']?\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""["']?src["']?\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""["']?url["']?\s*[:=]\s*["']([^"']+\.m3u8[^"']*)["']"""),
            Regex("""["']?file["']?\s*[:=]\s*["']([^"']+\.mp4[^"']*)["']"""),
            Regex("""["']?source["']?\s*[:=]\s*["']([^"']+\.mp4[^"']*)["']"""),
        )

        filePatterns.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                if (!links.any { it.url == url }) {
                    android.util.Log.d("FlickyStream", "Found JS video source: $url")
                    links.add(VideoLink(
                        url = url,
                        quality = extractQuality(url),
                        extractorName = "FlickyStream",
                        isM3u8 = url.contains(".m3u8"),
                        referer = referer
                    ))
                }
            }
        }

        // Look for sources array
        val sourcesPattern = Regex("""sources\s*[:=]\s*\[([^\]]+)\]""")
        sourcesPattern.find(html)?.let { match ->
            val sourcesContent = match.groupValues[1]
            Regex("""["']?(https?://[^"'\s,\]]+)["']?""").findAll(sourcesContent).forEach { urlMatch ->
                val url = urlMatch.groupValues[1].replace("\\/", "/")
                if ((url.contains(".m3u8") || url.contains(".mp4")) && !links.any { it.url == url }) {
                    android.util.Log.d("FlickyStream", "Found source array URL: $url")
                    links.add(VideoLink(
                        url = url,
                        quality = extractQuality(url),
                        extractorName = "FlickyStream",
                        isM3u8 = url.contains(".m3u8"),
                        referer = referer
                    ))
                }
            }
        }
    }

    private suspend fun extractFromEmbed(embedUrl: String): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            android.util.Log.d("FlickyStream", "Extracting from: $embedUrl")
            val response = httpClient.get(embedUrl, headers = mapOf("Referer" to baseUrl))
            extractLinksFromHtml(response, links)
        } catch (e: Exception) {
            android.util.Log.e("FlickyStream", "Embed extract failed: $embedUrl", e)
        }

        return links
    }

    // CDN placeholder replacements (class-level constant)
    private val cdnPlaceholders = mapOf(
        "{v1}" to "quibblezoomfable.com",
        "{v2}" to "quibblezoomfable.com",
        "{v3}" to "quibblezoomfable.com",
        "{v4}" to "quibblezoomfable.com",
        "{v5}" to "quibblezoomfable.com"
    )

    private fun replaceCdnPlaceholders(url: String): String {
        var result = url
        cdnPlaceholders.forEach { (placeholder, domain) ->
            result = result.replace(placeholder, domain)
        }
        return result
    }

    private fun hasUnreplacedPlaceholders(url: String): Boolean {
        return url.contains("{v1}") || url.contains("{v2}") || url.contains("{v3}") ||
               url.contains("{v4}") || url.contains("{v5}") || url.contains("{v")
    }

    private fun extractLinksFromHtml(content: String, links: MutableList<VideoLink>) {
        // M3U8 streams
        Regex("""["']?(https?://[^"'\s]+\.m3u8[^"'\s]*)["']?""").findAll(content).forEach { match ->
            var url = match.groupValues[1].replace("\\/", "/").replace("\\u002F", "/")
            url = replaceCdnPlaceholders(url)
            // Skip URLs with unreplaced placeholders
            if (hasUnreplacedPlaceholders(url)) return@forEach
            if (!links.any { it.url == url }) {
                android.util.Log.d("FlickyStream", "Found m3u8: $url")
                val ref = if (url.contains("quibblezoomfable.com")) "https://cloudnestra.com/" else baseUrl
                links.add(VideoLink(
                    url = url,
                    quality = extractQuality(url),
                    extractorName = "FlickyStream",
                    isM3u8 = true,
                    referer = ref,
                    headers = if (url.contains("quibblezoomfable.com")) mapOf("Referer" to "https://cloudnestra.com/", "Origin" to "https://cloudnestra.com") else emptyMap()
                ))
            }
        }

        // Look for /pl/{encoded}/master.m3u8 pattern (quibblezoomfable CDN)
        Regex("""(https?://[a-z0-9]+\.quibblezoomfable\.com/pl/[A-Za-z0-9+/=_.%-]+/(?:master|list)\.m3u8)""").findAll(content).forEach { match ->
            val url = match.groupValues[1].replace("\\/", "/").replace("\\u002F", "/")
            if (!links.any { it.url == url }) {
                android.util.Log.d("FlickyStream", "Found CDN m3u8: $url")
                links.add(VideoLink(
                    url = url,
                    quality = "Auto",
                    extractorName = "FlickyStream",
                    isM3u8 = true,
                    referer = "https://cloudnestra.com/",
                    headers = mapOf("Referer" to "https://cloudnestra.com/", "Origin" to "https://cloudnestra.com")
                ))
            }
        }

        // MP4 direct links
        Regex("""["']?(https?://[^"'\s]+\.mp4[^"'\s]*)["']?""").findAll(content).forEach { match ->
            var url = match.groupValues[1].replace("\\/", "/").replace("\\u002F", "/")
            url = replaceCdnPlaceholders(url)
            if (hasUnreplacedPlaceholders(url)) return@forEach
            if (!links.any { it.url == url }) {
                links.add(VideoLink(
                    url = url,
                    quality = extractQuality(url),
                    extractorName = "FlickyStream",
                    isM3u8 = false,
                    referer = baseUrl
                ))
            }
        }

        // File/sources in JSON
        Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""").findAll(content).forEach { match ->
            var url = match.groupValues[1].replace("\\/", "/").replace("\\u002F", "/")
            url = replaceCdnPlaceholders(url)
            if (hasUnreplacedPlaceholders(url)) return@forEach
            if ((url.contains(".m3u8") || url.contains(".mp4") || url.contains("/pl/")) && !links.any { it.url == url }) {
                android.util.Log.d("FlickyStream", "Found file URL: $url")
                val ref = if (url.contains("quibblezoomfable.com")) "https://cloudnestra.com/" else baseUrl
                links.add(VideoLink(
                    url = url,
                    quality = extractQuality(url),
                    extractorName = "FlickyStream",
                    isM3u8 = url.contains(".m3u8") || url.contains("/pl/"),
                    referer = ref,
                    headers = if (url.contains("quibblezoomfable.com")) mapOf("Referer" to "https://cloudnestra.com/", "Origin" to "https://cloudnestra.com") else emptyMap()
                ))
            }
        }
    }

    private fun extractQuality(url: String): String {
        return when {
            url.contains("4k", true) || url.contains("2160") -> "4K"
            url.contains("1080") -> "1080p"
            url.contains("720") -> "720p"
            url.contains("480") -> "480p"
            url.contains("360") -> "360p"
            else -> "Auto"
        }
    }

    override suspend fun ping(): Boolean {
        return try {
            httpClient.ping(baseUrl) > 0
        } catch (e: Exception) {
            false
        }
    }

    @Serializable
    private data class FlickyItem(
        val id: Int? = null,
        val slug: String? = null,
        val title: String? = null,
        val name: String? = null,
        val poster: String? = null,
        val image: String? = null,
        val poster_path: String? = null,
        val year: Int? = null,
        val rating: String? = null,
        val vote_average: Float? = null,
        val release_date: String? = null,
        val overview: String? = null
    )
}
