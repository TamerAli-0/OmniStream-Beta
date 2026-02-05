package com.omnistream.source.movie

import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.HomeSection
import com.omnistream.domain.model.Video
import com.omnistream.domain.model.VideoLink
import com.omnistream.domain.model.VideoStatus
import com.omnistream.source.model.VideoSource
import com.omnistream.source.model.VideoType
import org.jsoup.Jsoup

/**
 * Goojara source for movies and TV shows.
 * Site: https://ww1.goojara.to
 */
class GoojaraSource(
    private val httpClient: OmniHttpClient
) : VideoSource {

    override val id = "goojara"
    override val name = "Goojara"
    override val baseUrl = "https://ww1.goojara.to"
    override val lang = "en"
    override val supportedTypes = setOf(VideoType.MOVIE, VideoType.TV_SERIES)

    private val imageBaseUrl = "https://md.goojara.to"

    override suspend fun getHomePage(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()

        try {
            // Get recent movies
            val recentMovies = fetchMovieList("$baseUrl/watch-movies-recent.html")
            if (recentMovies.isNotEmpty()) {
                sections.add(HomeSection("Recent Movies", recentMovies))
            }

            // Get popular movies
            val popularMovies = fetchMovieList("$baseUrl/watch-movies-popular.html")
            if (popularMovies.isNotEmpty()) {
                sections.add(HomeSection("Popular Movies", popularMovies))
            }

            // Get recent series
            val recentSeries = fetchSeriesList("$baseUrl/watch-series-recent.html")
            if (recentSeries.isNotEmpty()) {
                sections.add(HomeSection("Recent TV Series", recentSeries))
            }

        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Failed to load home page", e)
        }

        return sections
    }

    private suspend fun fetchMovieList(url: String): List<Video> {
        return try {
            val doc = httpClient.getDocument(url)
            android.util.Log.d("GoojaraSource", "Fetching: $url")

            // Parse movie items
            doc.select("a[href*='/m']").mapNotNull { element ->
                try {
                    val href = element.attr("href")
                    if (!href.startsWith("/m")) return@mapNotNull null

                    val movieId = href.substringAfter("/")
                    val title = element.attr("title").ifBlank { element.text() }
                    if (title.isBlank()) return@mapNotNull null

                    // Extract year from title if present
                    val yearMatch = Regex("""\((\d{4})\)""").find(title)
                    val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
                    val cleanTitle = title.replace(Regex("""\s*\(\d{4}\)\s*"""), "").trim()

                    val posterUrl = "$imageBaseUrl/$movieId.jpg"

                    Video(
                        id = movieId,
                        sourceId = id,
                        title = cleanTitle,
                        url = "$baseUrl$href",
                        type = VideoType.MOVIE,
                        posterUrl = posterUrl,
                        year = year
                    )
                } catch (e: Exception) {
                    null
                }
            }.distinctBy { it.id }.take(20).also {
                android.util.Log.d("GoojaraSource", "Found ${it.size} movies")
            }
        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Failed to fetch movie list", e)
            emptyList()
        }
    }

    private suspend fun fetchSeriesList(url: String): List<Video> {
        return try {
            val doc = httpClient.getDocument(url)

            doc.select("a[href*='/s']").mapNotNull { element ->
                try {
                    val href = element.attr("href")
                    if (!href.startsWith("/s")) return@mapNotNull null

                    val seriesId = href.substringAfter("/")
                    val title = element.attr("title").ifBlank { element.text() }
                    if (title.isBlank()) return@mapNotNull null

                    val yearMatch = Regex("""\((\d{4})\)""").find(title)
                    val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
                    val cleanTitle = title.replace(Regex("""\s*\(\d{4})\s*"""), "").trim()

                    val posterUrl = "$imageBaseUrl/$seriesId.jpg"

                    Video(
                        id = seriesId,
                        sourceId = id,
                        title = cleanTitle,
                        url = "$baseUrl$href",
                        type = VideoType.TV_SERIES,
                        posterUrl = posterUrl,
                        year = year
                    )
                } catch (e: Exception) {
                    null
                }
            }.distinctBy { it.id }.take(20)
        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Failed to fetch series list", e)
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<Video> {
        return try {
            // Goojara uses POST search
            val response = httpClient.post(
                url = "$baseUrl/xmre.php",
                data = mapOf("q" to query),
                headers = mapOf(
                    "X-Requested-With" to "XMLHttpRequest",
                    "Referer" to baseUrl
                )
            )

            val doc = Jsoup.parse(response)

            doc.select("a").mapNotNull { element ->
                try {
                    val href = element.attr("href")
                    val movieId = href.substringAfter("/")
                    val title = element.text().trim()
                    if (title.isBlank() || movieId.isBlank()) return@mapNotNull null

                    val isMovie = href.startsWith("/m")
                    val isSeries = href.startsWith("/s")
                    if (!isMovie && !isSeries) return@mapNotNull null

                    val yearMatch = Regex("""\((\d{4})\)""").find(title)
                    val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
                    val cleanTitle = title.replace(Regex("""\s*\(\d{4}\)\s*"""), "").trim()

                    Video(
                        id = movieId,
                        sourceId = id,
                        title = cleanTitle,
                        url = "$baseUrl$href",
                        type = if (isMovie) VideoType.MOVIE else VideoType.TV_SERIES,
                        posterUrl = "$imageBaseUrl/$movieId.jpg",
                        year = year
                    )
                } catch (e: Exception) {
                    null
                }
            }.distinctBy { it.id }
        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Search failed", e)
            emptyList()
        }
    }

    override suspend fun getDetails(video: Video): Video {
        return try {
            val doc = httpClient.getDocument(video.url)
            android.util.Log.d("GoojaraSource", "Getting details for: ${video.url}")

            val title = doc.selectFirst("h1, .title")?.text()?.trim() ?: video.title

            val description = doc.selectFirst("p.description, div.synopsis, .plot")?.text()?.trim()

            val year = doc.selectFirst("span.year, .info:contains(Year)")?.text()
                ?.let { Regex("""(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: video.year

            val genres = doc.select("a[href*='/genre/'], .genres a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            val rating = doc.selectFirst("span.rating, .imdb")?.text()
                ?.let { Regex("""([\d.]+)""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }

            video.copy(
                title = title,
                description = description,
                year = year,
                genres = genres,
                rating = rating
            )
        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Failed to get details", e)
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

        // For TV series, parse episode list
        return try {
            val doc = httpClient.getDocument(video.url)
            android.util.Log.d("GoojaraSource", "Getting episodes for: ${video.url}")

            val episodes = mutableListOf<Episode>()

            // Find episode links
            doc.select("a[href*='/e']").forEach { element ->
                try {
                    val href = element.attr("href")
                    if (!href.contains("/e")) return@forEach

                    val epId = href.substringAfter("/")
                    val epText = element.text().trim()

                    // Extract season and episode numbers
                    val match = Regex("""S(\d+)\s*E(\d+)""", RegexOption.IGNORE_CASE).find(epText)
                        ?: Regex("""(\d+)x(\d+)""").find(epText)

                    val season = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val epNum = match?.groupValues?.get(2)?.toIntOrNull()
                        ?: Regex("""(\d+)""").find(epText)?.groupValues?.get(1)?.toIntOrNull()
                        ?: return@forEach

                    episodes.add(
                        Episode(
                            id = epId,
                            videoId = video.id,
                            sourceId = id,
                            url = "$baseUrl$href",
                            title = epText,
                            number = epNum,
                            season = season
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid episodes
                }
            }

            episodes.sortedWith(compareBy({ it.season }, { it.number })).also {
                android.util.Log.d("GoojaraSource", "Found ${it.size} episodes")
            }
        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Failed to get episodes", e)
            emptyList()
        }
    }

    override suspend fun getLinks(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            val doc = httpClient.getDocument(episode.url)
            android.util.Log.d("GoojaraSource", "Getting links for: ${episode.url}")

            // Find iframe sources
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    links.addAll(extractFromEmbed(src))
                }
            }

            // Find direct video sources or player links
            doc.select("a[href*='embed'], a[href*='player'], a.server-link").forEach { link ->
                val href = link.attr("href")
                if (href.isNotBlank()) {
                    val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                    links.addAll(extractFromEmbed(fullUrl))
                }
            }

            // Look for video sources in scripts
            doc.select("script").forEach { script ->
                val content = script.data()
                extractLinksFromScript(content, links)
            }

        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Failed to get links", e)
        }

        return links.distinctBy { it.url }.also {
            android.util.Log.d("GoojaraSource", "Found ${it.size} video links")
        }
    }

    private suspend fun extractFromEmbed(embedUrl: String): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            val fullUrl = when {
                embedUrl.startsWith("//") -> "https:$embedUrl"
                embedUrl.startsWith("/") -> "$baseUrl$embedUrl"
                else -> embedUrl
            }

            val response = httpClient.get(fullUrl, headers = mapOf("Referer" to baseUrl))
            extractLinksFromScript(response, links)

        } catch (e: Exception) {
            android.util.Log.e("GoojaraSource", "Failed to extract from embed: $embedUrl", e)
        }

        return links
    }

    private fun extractLinksFromScript(content: String, links: MutableList<VideoLink>) {
        // Look for m3u8 links
        Regex("""["']?(https?://[^"'\s]+\.m3u8[^"'\s]*)["']?""").findAll(content).forEach { match ->
            val url = match.groupValues[1].replace("\\/", "/")
            links.add(VideoLink(
                url = url,
                quality = extractQuality(url),
                extractorName = "Goojara",
                isM3u8 = true,
                referer = baseUrl
            ))
        }

        // Look for mp4 links
        Regex("""["']?(https?://[^"'\s]+\.mp4[^"'\s]*)["']?""").findAll(content).forEach { match ->
            val url = match.groupValues[1].replace("\\/", "/")
            links.add(VideoLink(
                url = url,
                quality = extractQuality(url),
                extractorName = "Goojara",
                isM3u8 = false,
                referer = baseUrl
            ))
        }

        // Look for file/sources arrays
        Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""").findAll(content).forEach { match ->
            val url = match.groupValues[1].replace("\\/", "/")
            if (url.contains(".m3u8") || url.contains(".mp4")) {
                links.add(VideoLink(
                    url = url,
                    quality = extractQuality(url),
                    extractorName = "Goojara",
                    isM3u8 = url.contains(".m3u8"),
                    referer = baseUrl
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
            url.contains("master") || url.contains("index") -> "Auto"
            else -> "Unknown"
        }
    }

    override suspend fun ping(): Boolean {
        return try {
            httpClient.ping(baseUrl) > 0
        } catch (e: Exception) {
            false
        }
    }
}
