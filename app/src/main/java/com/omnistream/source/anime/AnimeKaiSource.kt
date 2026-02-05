package com.omnistream.source.anime

import android.content.Context
import com.omnistream.core.network.OmniHttpClient
import com.omnistream.core.webview.WebViewExtractor
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.HomeSection
import com.omnistream.domain.model.Video
import com.omnistream.domain.model.VideoLink
import com.omnistream.domain.model.VideoStatus
import com.omnistream.source.model.VideoSource
import com.omnistream.source.model.VideoType
import org.jsoup.nodes.Element

/**
 * AnimeKai source for anime streaming.
 * Site: https://anikai.to (redirects from animekai.to)
 *
 * Uses WebView extraction for video links due to complex encryption.
 */
class AnimeKaiSource(
    private val context: Context,
    private val httpClient: OmniHttpClient
) : VideoSource {

    override val id = "animekai"
    override val name = "AnimeKai"
    override val baseUrl = "https://anikai.to"
    override val lang = "en"
    override val supportedTypes = setOf(VideoType.ANIME)

    private val webViewExtractor by lazy { WebViewExtractor(context) }

    override suspend fun getHomePage(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()

        try {
            android.util.Log.d("AnimeKaiSource", "Loading home page")

            // New releases
            val newReleases = fetchAnimePage("$baseUrl/new-releases")
            if (newReleases.isNotEmpty()) {
                sections.add(HomeSection("New Releases", newReleases))
            }

            // Ongoing anime
            val ongoing = fetchAnimePage("$baseUrl/ongoing")
            if (ongoing.isNotEmpty()) {
                sections.add(HomeSection("Ongoing", ongoing))
            }

            // Recent updates
            val recent = fetchAnimePage("$baseUrl/recent")
            if (recent.isNotEmpty()) {
                sections.add(HomeSection("Recently Updated", recent))
            }

        } catch (e: Exception) {
            android.util.Log.e("AnimeKaiSource", "Failed to load home page", e)
        }

        return sections
    }

    override suspend fun search(query: String, page: Int): List<Video> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/browser?keyword=$encodedQuery&page=$page"

        return try {
            fetchAnimePage(url)
        } catch (e: Exception) {
            android.util.Log.e("AnimeKaiSource", "Search failed", e)
            emptyList()
        }
    }

    private suspend fun fetchAnimePage(url: String): List<Video> {
        return try {
            val doc = httpClient.getDocument(url)
            android.util.Log.d("AnimeKaiSource", "Fetching: $url")

            // AnimeKai uses div.aitem for anime cards
            val animeItems = doc.select("div.aitem")
            android.util.Log.d("AnimeKaiSource", "Found ${animeItems.size} aitem elements")

            animeItems.mapNotNull { item ->
                parseAnimeCard(item)
            }.distinctBy { it.id }.also {
                android.util.Log.d("AnimeKaiSource", "Parsed ${it.size} anime")
            }
        } catch (e: Exception) {
            android.util.Log.e("AnimeKaiSource", "Failed to fetch anime page", e)
            emptyList()
        }
    }

    private fun parseAnimeCard(element: Element): Video? {
        try {
            // Get link from a.poster
            val posterLink = element.selectFirst("a.poster") ?: return null
            val href = posterLink.attr("href")
            if (!href.contains("/watch/")) return null

            val animeId = href.substringAfter("/watch/").substringBefore("/")
            if (animeId.isBlank()) return null

            // Get title from a.title
            val titleElement = element.selectFirst("a.title")
            val title = titleElement?.attr("title")?.ifBlank {
                titleElement.text()
            } ?: return null
            if (title.isBlank()) return null

            // Get poster URL from img (check data-src first, then src)
            val posterUrl = element.selectFirst("img")?.let { img ->
                img.attr("data-src").ifBlank { img.attr("src") }
            }

            // Get type and episode count from div.info
            val infoDiv = element.selectFirst("div.info")
            val typeText = infoDiv?.select("span b")?.lastOrNull()?.text()?.uppercase() ?: ""
            val type = when {
                typeText.contains("MOVIE") -> VideoType.MOVIE
                else -> VideoType.ANIME
            }

            // Episode count from span with just a number
            val episodeCount = infoDiv?.select("span b")?.firstOrNull()?.text()?.toIntOrNull()

            return Video(
                id = animeId,
                sourceId = id,
                title = title.trim(),
                url = "$baseUrl$href",
                type = type,
                posterUrl = posterUrl,
                episodeCount = episodeCount
            )
        } catch (e: Exception) {
            android.util.Log.e("AnimeKaiSource", "Failed to parse card", e)
            return null
        }
    }

    override suspend fun getDetails(video: Video): Video {
        return try {
            val doc = httpClient.getDocument(video.url)
            android.util.Log.d("AnimeKaiSource", "Getting details for: ${video.url}")

            // Title from h1 or data-jp attribute
            val titleElement = doc.selectFirst("h1[itemprop=name], h1.title")
            val title = titleElement?.text()?.trim() ?: video.title

            // Poster from the poster section
            val posterUrl = doc.selectFirst(".poster img[itemprop=image]")?.attr("src")
                ?: doc.selectFirst(".poster img")?.attr("src")
                ?: video.posterUrl

            // Description from .desc
            val description = doc.selectFirst(".desc.text-expand")?.text()?.trim()
                ?: doc.selectFirst(".description")?.text()?.trim()

            // Genres
            val genres = doc.select(".detail a[href*='/genres/']")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            // Status
            val statusText = doc.selectFirst(".detail div:contains(Status) span")?.text()?.lowercase() ?: ""
            val status = when {
                statusText.contains("ongoing") || statusText.contains("airing") -> VideoStatus.ONGOING
                statusText.contains("completed") || statusText.contains("finished") -> VideoStatus.COMPLETED
                else -> video.status
            }

            // Year from premiered or date aired
            val year = doc.selectFirst(".detail div:contains(Date aired) span")?.text()
                ?.let { Regex("""(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                ?: doc.selectFirst(".detail div:contains(Premiered) span")?.text()
                    ?.let { Regex("""(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

            video.copy(
                title = title,
                posterUrl = posterUrl,
                description = description,
                genres = genres,
                status = status,
                year = year
            )
        } catch (e: Exception) {
            android.util.Log.e("AnimeKaiSource", "Failed to get details", e)
            video
        }
    }

    override suspend fun getEpisodes(video: Video): List<Episode> {
        return try {
            val doc = httpClient.getDocument(video.url)
            android.util.Log.d("AnimeKaiSource", "Getting episodes for: ${video.url}")

            val episodes = mutableListOf<Episode>()

            // Extract episode count from the page
            // Format: <span class="sub"><svg>...</svg>12</span>
            // The number is the text content after the SVG
            var episodeCount = 0

            // Try to get SUB episode count
            val subSpan = doc.selectFirst(".info span.sub")
            if (subSpan != null) {
                val subText = subSpan.text().trim()
                episodeCount = subText.toIntOrNull() ?: 0
                android.util.Log.d("AnimeKaiSource", "Found SUB count: $episodeCount from '$subText'")
            }

            // If no SUB, try DUB
            if (episodeCount == 0) {
                val dubSpan = doc.selectFirst(".info span.dub")
                if (dubSpan != null) {
                    val dubText = dubSpan.text().trim()
                    episodeCount = dubText.toIntOrNull() ?: 0
                    android.util.Log.d("AnimeKaiSource", "Found DUB count: $episodeCount from '$dubText'")
                }
            }

            // Try other selectors
            if (episodeCount == 0) {
                // Try from the info div - look for any number
                val infoDiv = doc.selectFirst(".info")
                if (infoDiv != null) {
                    val infoText = infoDiv.text()
                    android.util.Log.d("AnimeKaiSource", "Info text: $infoText")
                    // Find numbers in the text
                    val numbers = Regex("""(\d+)""").findAll(infoText).map { it.value.toInt() }.toList()
                    if (numbers.isNotEmpty()) {
                        episodeCount = numbers.maxOrNull() ?: 0
                    }
                }
            }

            // Try from Episodes: span in detail
            if (episodeCount == 0) {
                val epSpan = doc.selectFirst(".detail div:contains(Episodes) span")
                episodeCount = epSpan?.text()?.trim()?.toIntOrNull() ?: 0
            }

            // Fallback to video.episodeCount or default
            if (episodeCount == 0) {
                episodeCount = video.episodeCount ?: 12
            }

            android.util.Log.d("AnimeKaiSource", "Final episode count: $episodeCount")

            // Get anime slug for building episode URLs
            val animeSlug = video.id

            // Create episode entries
            // The actual video extraction will happen via WebView when user clicks play
            for (i in 1..episodeCount) {
                episodes.add(Episode(
                    id = "${animeSlug}_ep$i",
                    videoId = video.id,
                    sourceId = id,
                    url = "${video.url}?ep=$i",
                    title = "Episode $i",
                    number = i
                ))
            }

            android.util.Log.d("AnimeKaiSource", "Created ${episodes.size} episodes")
            episodes.sortedBy { it.number }

        } catch (e: Exception) {
            android.util.Log.e("AnimeKaiSource", "Failed to get episodes", e)
            emptyList()
        }
    }

    /**
     * Get video links using WebView extraction.
     * This loads the episode page in a WebView and intercepts the m3u8 URL.
     */
    override suspend fun getLinks(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            android.util.Log.d("AnimeKaiSource", "Getting links via WebView for: ${episode.url}")

            // Use WebView to extract the video URL
            val result = webViewExtractor.extractAnimeKaiVideo(
                episodeUrl = episode.url,
                timeout = 45000 // 45 seconds timeout
            )

            if (result.videoUrl != null) {
                android.util.Log.d("AnimeKaiSource", "WebView extracted URL: ${result.videoUrl}")

                // Determine the referer based on the URL
                val referer = when {
                    result.videoUrl.contains("code29wave.site") -> "https://4spromax.site/"
                    result.referer != null -> result.referer
                    else -> "https://4spromax.site/"
                }

                links.add(VideoLink(
                    url = result.videoUrl,
                    quality = extractQuality(result.videoUrl),
                    extractorName = "AnimeKai",
                    referer = referer,
                    headers = mapOf(
                        "Referer" to referer,
                        "Origin" to referer.trimEnd('/')
                    ),
                    isM3u8 = result.videoUrl.contains(".m3u8")
                ))
            } else {
                android.util.Log.w("AnimeKaiSource", "WebView extraction returned no URL")
            }

        } catch (e: Exception) {
            android.util.Log.e("AnimeKaiSource", "Failed to get links", e)
        }

        return links.also {
            android.util.Log.d("AnimeKaiSource", "Found ${it.size} video links")
        }
    }

    private fun extractQuality(url: String): String {
        return when {
            url.contains("1080") -> "1080p"
            url.contains("720") -> "720p"
            url.contains("480") -> "480p"
            url.contains("360") -> "360p"
            url.contains("master") || url.contains("index") || url.contains("list") -> "Auto"
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
}
