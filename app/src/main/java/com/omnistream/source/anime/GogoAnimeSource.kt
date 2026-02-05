package com.omnistream.source.anime

import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Episode
import com.omnistream.domain.model.HomeSection
import com.omnistream.domain.model.Video
import com.omnistream.domain.model.VideoLink
import com.omnistream.domain.model.VideoStatus
import com.omnistream.source.model.VideoSource
import com.omnistream.source.model.VideoType
import org.jsoup.nodes.Element

/**
 * GogoAnime source for anime streaming.
 * Site: https://anitaku.pe (formerly gogoanime)
 */
class GogoAnimeSource(
    private val httpClient: OmniHttpClient
) : VideoSource {

    override val id = "gogoanime"
    override val name = "GogoAnime"
    override val baseUrl = "https://gogoanime.by"
    override val lang = "en"
    override val supportedTypes = setOf(VideoType.ANIME)

    private val ajaxUrl = "https://ajax.gogocdn.net"

    // Backup domains in case main is down
    private val backupDomains = listOf(
        "https://gogoanime.by",
        "https://gogoanime3.net",
        "https://anitaku.so"
    )

    override suspend fun getHomePage(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()

        try {
            val doc = httpClient.getDocument(baseUrl)
            android.util.Log.d("GogoAnimeSource", "Loading homepage")

            // Recent releases - new structure uses article.bs
            val recentEpisodes = doc.select("article.bs").mapNotNull { parseAnimeCard(it) }
            if (recentEpisodes.isNotEmpty()) {
                sections.add(HomeSection("Recent Episodes", recentEpisodes.distinctBy { it.id }.take(20)))
            }

            // Try ongoing anime page
            try {
                val ongoingDoc = httpClient.getDocument("$baseUrl/ongoing-anime/")
                val ongoing = ongoingDoc.select("article.bs").mapNotNull { parseAnimeCard(it) }
                if (ongoing.isNotEmpty()) {
                    sections.add(HomeSection("Ongoing Anime", ongoing.distinctBy { it.id }.take(20)))
                }
            } catch (e: Exception) {
                android.util.Log.e("GogoAnimeSource", "Failed to load ongoing", e)
            }

            // Try popular/trending
            try {
                val popularDoc = httpClient.getDocument("$baseUrl/popular/")
                val popular = popularDoc.select("article.bs").mapNotNull { parseAnimeCard(it) }
                if (popular.isNotEmpty()) {
                    sections.add(HomeSection("Popular Anime", popular.distinctBy { it.id }.take(20)))
                }
            } catch (e: Exception) {
                android.util.Log.e("GogoAnimeSource", "Failed to load popular", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("GogoAnimeSource", "Failed to load home page", e)
        }

        return sections
    }

    override suspend fun search(query: String, page: Int): List<Video> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        // Try different search URL formats
        val url = "$baseUrl/?s=$encodedQuery&page=$page"

        return try {
            val doc = httpClient.getDocument(url)
            android.util.Log.d("GogoAnimeSource", "Searching: $url")
            doc.select("article.bs").mapNotNull { parseAnimeCard(it) }
                .distinctBy { it.id }
        } catch (e: Exception) {
            android.util.Log.e("GogoAnimeSource", "Search failed", e)
            emptyList()
        }
    }

    private fun parseAnimeCard(element: Element): Video? {
        // New structure: article.bs > a.tip
        val linkElement = element.selectFirst("a.tip")
            ?: element.selectFirst("a")
            ?: return null

        val href = linkElement.attr("href")
        if (href.isBlank()) return null

        // Normalize URL by removing trailing slash
        val normalizedHref = href.trimEnd('/')

        // Extract anime ID from URL - handle episode URLs
        val animeId = when {
            normalizedHref.contains("/category/") -> normalizedHref.substringAfter("/category/").substringBefore("/")
            normalizedHref.contains("-episode-") -> normalizedHref.substringAfterLast("/").substringBefore("-episode-")
            else -> normalizedHref.substringAfterLast("/").ifBlank { return null }
        }

        if (animeId.isBlank()) return null

        // Get title from oldtitle attribute or title attribute
        val title = linkElement.attr("oldtitle").ifBlank {
            linkElement.attr("title").ifBlank {
                element.selectFirst("div.tt, div.ttt")?.text()?.trim()
                    ?: linkElement.text().trim()
            }
        }

        if (title.isBlank()) return null

        // Get poster from img.ts-post-image or any img
        val posterUrl = element.selectFirst("img.ts-post-image")?.attr("src")
            ?: element.selectFirst("img")?.let { img ->
                img.attr("src").ifBlank { img.attr("data-src") }
            }

        // Get episode info from URL or text
        val episodeCount = Regex("""episode[- ]?(\d+)""", RegexOption.IGNORE_CASE)
            .find(href)?.groupValues?.get(1)?.toIntOrNull()

        // Get type from div.typez
        val typeText = element.selectFirst("div.typez")?.text()?.uppercase() ?: ""
        val type = when {
            typeText.contains("MOVIE") -> VideoType.MOVIE
            else -> VideoType.ANIME
        }

        return Video(
            id = animeId,
            sourceId = id,
            title = cleanTitle(title),
            url = if (href.startsWith("http")) href else "$baseUrl$href",
            type = type,
            posterUrl = posterUrl,
            episodeCount = episodeCount
        )
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""\s*Episode\s*\d+.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(Dub\).*""", RegexOption.IGNORE_CASE), " (Dub)")
            .trim()
    }

    override suspend fun getDetails(video: Video): Video {
        return try {
            android.util.Log.d("GogoAnimeSource", "Getting details for: ${video.url}")
            val doc = httpClient.getDocument(video.url)

            // Title from div.infolimit h2 or h1
            val title = doc.selectFirst("div.infolimit h2")?.text()?.trim()
                ?: doc.selectFirst("h1.entry-title")?.text()?.trim()
                ?: doc.selectFirst("h2")?.text()?.trim()
                ?: video.title

            // Clean title (remove "Episode X English Subbed")
            val cleanedTitle = title.replace(Regex("""Episode\s*\d+.*""", RegexOption.IGNORE_CASE), "").trim()

            // Poster from div.thumb img
            val posterUrl = doc.selectFirst("div.thumb img.ts-post-image")?.attr("src")
                ?: doc.selectFirst("div.thumb img")?.attr("src")
                ?: doc.selectFirst("img.wp-post-image")?.attr("src")
                ?: video.posterUrl

            // Description - look for paragraph text
            val description = doc.selectFirst("div.entry-content p")?.text()?.trim()
                ?: doc.selectFirst("div.desc p")?.text()?.trim()
                ?: doc.select("p").firstOrNull { it.text().length > 100 }?.text()?.trim()

            // Info items - structure: div.spe span with b labels
            val infoText = doc.select("div.spe span").joinToString(" ") { it.text() }

            // Status
            val status = when {
                infoText.contains("Ongoing", true) -> VideoStatus.ONGOING
                infoText.contains("Completed", true) -> VideoStatus.COMPLETED
                else -> video.status
            }

            // Year from Released
            val year = Regex("""(?:Released|Aired)[:\s]*(\d{4})""", RegexOption.IGNORE_CASE)
                .find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(\d{4})""").find(infoText)?.groupValues?.get(1)?.toIntOrNull()

            // Episode count
            val episodeCount = Regex("""Episodes[:\s]*(\d+)""", RegexOption.IGNORE_CASE)
                .find(infoText)?.groupValues?.get(1)?.toIntOrNull()
                ?: video.episodeCount

            // Genres - look for genre links or tags
            var genres = doc.select("a[href*=genre], span.mgen a, div.genxed a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            if (genres.isEmpty()) {
                genres = doc.select("a")
                    .filter { element -> element.attr("href").contains("genre", true) }
                    .map { it.text().trim() }
                    .filter { it.isNotBlank() }
            }

            video.copy(
                title = cleanedTitle.ifBlank { video.title },
                posterUrl = posterUrl,
                description = description,
                genres = genres,
                status = status,
                year = year,
                episodeCount = episodeCount
            )
        } catch (e: Exception) {
            android.util.Log.e("GogoAnimeSource", "Failed to get details", e)
            video
        }
    }

    override suspend fun getEpisodes(video: Video): List<Episode> {
        return try {
            android.util.Log.d("GogoAnimeSource", "Getting episodes for: ${video.url}")
            val doc = httpClient.getDocument(video.url)

            val episodes = mutableListOf<Episode>()

            // New structure: div.episodes-container > div.episode-item
            val episodeItems = doc.select("div.episodes-container div.episode-item")

            if (episodeItems.isNotEmpty()) {
                episodeItems.forEach { item ->
                    val episodeNumber = item.attr("data-episode-number").toIntOrNull()
                        ?: return@forEach

                    val link = item.selectFirst("a")
                    val epUrl = link?.attr("href")?.trimEnd('/') ?: return@forEach

                    // Episode ID is the URL slug (e.g., "arne-no-jikenbo-episode-4-english-subbed")
                    val epId = epUrl.substringAfterLast("/")
                    if (epId.isBlank()) return@forEach

                    episodes.add(Episode(
                        id = epId,
                        videoId = video.id,
                        sourceId = id,
                        url = if (epUrl.startsWith("http")) epUrl else "$baseUrl$epUrl",
                        number = episodeNumber,
                        title = "Episode $episodeNumber"
                    ))
                }
            }

            // Fallback: look for episode links in other patterns
            if (episodes.isEmpty()) {
                val epLinks = doc.select("a[href*=-episode-]")
                epLinks.forEach { link ->
                    val epUrl = link.attr("href").trimEnd('/')
                    val epNum = Regex("""episode[- ]?(\d+)""", RegexOption.IGNORE_CASE)
                        .find(epUrl)?.groupValues?.get(1)?.toIntOrNull()
                        ?: return@forEach

                    val epId = epUrl.substringAfterLast("/")
                    if (epId.isBlank()) return@forEach

                    episodes.add(Episode(
                        id = epId,
                        videoId = video.id,
                        sourceId = id,
                        url = if (epUrl.startsWith("http")) epUrl else "$baseUrl$epUrl",
                        number = epNum,
                        title = "Episode $epNum"
                    ))
                }
            }

            episodes.distinctBy { it.number }.sortedBy { it.number }.also {
                android.util.Log.d("GogoAnimeSource", "Found ${it.size} episodes")
            }
        } catch (e: Exception) {
            android.util.Log.e("GogoAnimeSource", "Failed to get episodes", e)
            emptyList()
        }
    }

    override suspend fun getLinks(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            android.util.Log.d("GogoAnimeSource", "Getting links for URL: ${episode.url}")
            val doc = httpClient.getDocument(episode.url)
            android.util.Log.d("GogoAnimeSource", "Page title: ${doc.title()}")
            android.util.Log.d("GogoAnimeSource", "Page has ${doc.select("iframe").size} iframes")

            // GogoAnime uses iframe in div.player-embed
            val iframeSrc = doc.selectFirst("div.player-embed iframe")?.attr("src")
                ?: doc.selectFirst("div#player iframe")?.attr("src")
                ?: doc.selectFirst("iframe")?.attr("src")

            if (iframeSrc != null) {
                val iframeUrl = when {
                    iframeSrc.startsWith("//") -> "https:$iframeSrc"
                    iframeSrc.startsWith("/") -> "$baseUrl$iframeSrc"
                    else -> iframeSrc
                }
                android.util.Log.d("GogoAnimeSource", "Found iframe: $iframeUrl")

                // Load the iframe page and find the video tag
                try {
                    val iframeDoc = httpClient.getDocument(iframeUrl, headers = mapOf(
                        "Referer" to baseUrl
                    ))

                    // Look for video tag with src attribute (googlevideo.com)
                    val videoSrc = iframeDoc.selectFirst("video")?.attr("src")
                        ?: iframeDoc.selectFirst("video source")?.attr("src")

                    if (videoSrc != null && videoSrc.contains("googlevideo.com")) {
                        // Decode HTML entities in URL
                        val decodedUrl = videoSrc
                            .replace("&amp;", "&")
                            .replace("&#39;", "'")

                        android.util.Log.d("GogoAnimeSource", "Found video: $decodedUrl")

                        links.add(VideoLink(
                            url = decodedUrl,
                            quality = "720p",
                            extractorName = "Blogger",
                            isM3u8 = false,
                            referer = iframeUrl
                        ))
                    }

                    // Fallback: search for googlevideo URL in page source
                    if (links.isEmpty()) {
                        val html = iframeDoc.html()
                        val pattern = Regex("""(https?://[^"'\s]*googlevideo\.com/videoplayback[^"'\s]*)""")
                        pattern.find(html)?.let { match ->
                            val videoUrl = match.groupValues[1]
                                .replace("&amp;", "&")
                                .replace("\\u0026", "&")

                            android.util.Log.d("GogoAnimeSource", "Found video via regex: $videoUrl")

                            links.add(VideoLink(
                                url = videoUrl,
                                quality = "720p",
                                extractorName = "Blogger",
                                isM3u8 = false,
                                referer = iframeUrl
                            ))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GogoAnimeSource", "Failed to load iframe: ${e.message}")
                }
            } else {
                android.util.Log.w("GogoAnimeSource", "No iframe found on page")
            }
        } catch (e: Exception) {
            android.util.Log.e("GogoAnimeSource", "Failed to get links: ${e.message}", e)
        }

        return links.distinctBy { it.url }.also {
            android.util.Log.d("GogoAnimeSource", "Found ${it.size} video links")
        }
    }

    private suspend fun extractFromServer(serverUrl: String, serverName: String, referer: String = baseUrl): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            android.util.Log.d("GogoAnimeSource", "Extracting from $serverName: $serverUrl")
            val response = httpClient.get(serverUrl, headers = mapOf("Referer" to referer))

            // Look for m3u8 links
            val m3u8Pattern = Regex("""["']?(https?://[^"'\s]+\.m3u8[^"'\s]*)["']?""")
            m3u8Pattern.findAll(response).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                links.add(VideoLink(
                    url = url,
                    quality = extractQuality(url),
                    extractorName = serverName,
                    isM3u8 = true,
                    referer = referer
                ))
            }

            // Look for mp4 links
            val mp4Pattern = Regex("""["']?(https?://[^"'\s]+\.mp4[^"'\s]*)["']?""")
            mp4Pattern.findAll(response).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                links.add(VideoLink(
                    url = url,
                    quality = extractQuality(url),
                    extractorName = serverName,
                    isM3u8 = false,
                    referer = referer
                ))
            }

            // Look for sources array
            val sourcesPattern = Regex("""sources\s*[:=]\s*\[([^\]]+)\]""")
            sourcesPattern.find(response)?.let { match ->
                val filePattern = Regex("""["']?file["']?\s*:\s*["']([^"']+)["']""")
                val labelPattern = Regex("""["']?label["']?\s*:\s*["']([^"']+)["']""")

                filePattern.findAll(match.groupValues[1]).forEach { fileMatch ->
                    val fileUrl = fileMatch.groupValues[1].replace("\\/", "/")
                    val quality = labelPattern.find(match.groupValues[1])?.groupValues?.get(1)
                        ?: extractQuality(fileUrl)

                    links.add(VideoLink(
                        url = fileUrl,
                        quality = quality,
                        extractorName = serverName,
                        isM3u8 = fileUrl.contains(".m3u8"),
                        referer = serverUrl
                    ))
                }
            }
        } catch (e: Exception) {
            // Server extraction failed
        }

        return links
    }

    private fun extractQuality(url: String): String {
        return when {
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
            val latency = httpClient.ping(baseUrl)
            latency > 0
        } catch (e: Exception) {
            false
        }
    }
}
