package com.omnistream.source.anime

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
import org.jsoup.nodes.Element

/**
 * HiAnime source for anime streaming.
 * Site: https://hianimez.to (formerly aniwatch.to, zoro.to)
 *
 * Uses HTML scraping with AJAX endpoints for video sources.
 */
class HiAnimeSource(
    private val httpClient: OmniHttpClient
) : VideoSource {

    override val id = "hianime"
    override val name = "HiAnime"
    override val baseUrl = "https://hianimez.to"
    override val lang = "en"
    override val supportedTypes = setOf(VideoType.ANIME)

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val AJAX_URL = "https://hianimez.to/ajax"
    }

    override suspend fun getHomePage(): List<HomeSection> {
        val doc = httpClient.getDocument(baseUrl)
        val sections = mutableListOf<HomeSection>()

        // Trending section
        val trending = doc.select("div#trending-home .swiper-slide, section.block_area-trending .item")
            .mapNotNull { parseAnimeCard(it) }
        if (trending.isNotEmpty()) {
            sections.add(HomeSection("Trending", trending))
        }

        // Latest Episodes
        val latestEpisodes = doc.select("section.block_area-recently .film_list-wrap .flw-item")
            .mapNotNull { parseAnimeCard(it) }
        if (latestEpisodes.isNotEmpty()) {
            sections.add(HomeSection("Latest Episodes", latestEpisodes))
        }

        // Top Airing
        val topAiring = doc.select("section.block_area-content .film_list-wrap .flw-item")
            .mapNotNull { parseAnimeCard(it) }
        if (topAiring.isNotEmpty()) {
            sections.add(HomeSection("Top Airing", topAiring))
        }

        return sections
    }

    override suspend fun search(query: String, page: Int): List<Video> {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/search?keyword=$encodedQuery&page=$page"
        val doc = httpClient.getDocument(url)

        return doc.select(".film_list-wrap .flw-item").mapNotNull { parseAnimeCard(it) }
    }

    private fun parseAnimeCard(element: Element): Video? {
        val linkElement = element.selectFirst("a.film-poster-ahref, a.dynamic-name, a[href*='/watch/']")
            ?: element.selectFirst("a[href*='/']")
            ?: return null

        val href = linkElement.attr("href")
        val animeId = extractAnimeId(href) ?: return null

        val title = element.selectFirst("h3.film-name a, .film-name, .dynamic-name")?.text()?.trim()
            ?: linkElement.attr("title").ifBlank { null }
            ?: return null

        val posterUrl = element.selectFirst("img")?.let { img ->
            img.attr("data-src").ifBlank { img.attr("src") }
        }

        // Extract episode info
        val epInfo = element.selectFirst(".tick-eps, .tick-sub, .tick-dub")?.text()
        val episodeCount = epInfo?.let {
            Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        // Extract type
        val typeText = element.selectFirst(".tick-item, .fdi-type")?.text()?.lowercase()
        val type = when {
            typeText?.contains("movie") == true -> VideoType.MOVIE
            typeText?.contains("ova") == true -> VideoType.ANIME
            typeText?.contains("special") == true -> VideoType.ANIME
            else -> VideoType.ANIME
        }

        return Video(
            id = animeId,
            sourceId = id,
            title = title,
            url = if (href.startsWith("http")) href else "$baseUrl$href",
            type = type,
            posterUrl = posterUrl,
            episodeCount = episodeCount
        )
    }

    private fun extractAnimeId(href: String): String? {
        // Patterns: /watch/anime-name-123 or /anime-name-123
        return Regex("""/(?:watch/)?([^/]+)$""").find(href)?.groupValues?.get(1)
    }

    override suspend fun getDetails(video: Video): Video {
        val url = if (video.url.contains("/watch/")) {
            video.url.replace("/watch/", "/")
        } else {
            video.url
        }

        val doc = httpClient.getDocument(url)

        val title = doc.selectFirst("h2.film-name, .anisc-detail h2")?.text()?.trim() ?: video.title

        val posterUrl = doc.selectFirst(".film-poster img, .anisc-poster img")?.let { img ->
            img.attr("src").ifBlank { img.attr("data-src") }
        } ?: video.posterUrl

        val description = doc.selectFirst(".film-description .text, .anisc-info .overview")
            ?.text()?.trim()

        val status = doc.select(".item-title:contains(Status) + .item-content, .anisc-info div:contains(Status) + span")
            .firstOrNull()?.text()?.trim()?.lowercase()?.let {
                when {
                    it.contains("airing") || it.contains("ongoing") -> VideoStatus.ONGOING
                    it.contains("finished") || it.contains("completed") -> VideoStatus.COMPLETED
                    it.contains("upcoming") -> VideoStatus.UPCOMING
                    else -> VideoStatus.UNKNOWN
                }
            } ?: video.status

        val genres = doc.select(".item-title:contains(Genres) + .item-content a, .anisc-info div:contains(Genres) ~ a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }

        val studio = doc.selectFirst(".item-title:contains(Studios) + .item-content a, .anisc-info div:contains(Studios) ~ a")
            ?.text()?.trim()

        val year = doc.selectFirst(".item-title:contains(Aired) + .item-content, .anisc-info div:contains(Aired) ~ span")
            ?.text()?.let {
                Regex("""(\d{4})""").find(it)?.groupValues?.get(1)?.toIntOrNull()
            }

        val rating = doc.selectFirst(".tick-rate, .anisc-info div:contains(MAL) ~ span")
            ?.text()?.toFloatOrNull()

        val duration = doc.selectFirst(".item-title:contains(Duration) + .item-content, .anisc-info div:contains(Duration) ~ span")
            ?.text()?.let {
                Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull()
            }

        return video.copy(
            title = title,
            posterUrl = posterUrl,
            description = description,
            status = status,
            genres = genres,
            studio = studio,
            year = year,
            rating = rating,
            duration = duration
        )
    }

    override suspend fun getEpisodes(video: Video): List<Episode> {
        // Extract the anime ID number for the AJAX call
        val animeIdNum = video.id.substringAfterLast("-")

        // HiAnime uses AJAX to load episodes
        val ajaxUrl = "$AJAX_URL/v2/episode/list/$animeIdNum"

        try {
            val response = httpClient.get(ajaxUrl, headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to video.url
            ))

            val ajaxResponse = json.decodeFromString<AjaxResponse>(response)
            val episodeHtml = ajaxResponse.html

            val doc = org.jsoup.Jsoup.parse(episodeHtml)

            return doc.select("a.ep-item, .ssl-item a").mapNotNull { element ->
                parseEpisode(element, video.id)
            }.sortedBy { it.number }
        } catch (e: Exception) {
            // Fallback: try to parse from main page
            return parseEpisodesFromPage(video)
        }
    }

    private suspend fun parseEpisodesFromPage(video: Video): List<Episode> {
        val doc = httpClient.getDocument(video.url)

        return doc.select("a.ep-item, .ssl-item a").mapNotNull { element ->
            parseEpisode(element, video.id)
        }.sortedBy { it.number }
    }

    private fun parseEpisode(element: Element, videoId: String): Episode? {
        val href = element.attr("href")
        val dataNumber = element.attr("data-number").ifBlank { element.attr("data-ep") }
        val dataId = element.attr("data-id").ifBlank {
            href.substringAfter("ep=").substringBefore("&")
        }

        val episodeNumber = dataNumber.toIntOrNull()
            ?: Regex("""[Ee]p(?:isode)?\s*(\d+)""").find(element.text())?.groupValues?.get(1)?.toIntOrNull()
            ?: return null

        val title = element.attr("title").ifBlank {
            element.selectFirst(".ep-name, .ssli-detail")?.text()?.trim()
        }

        val episodeId = dataId.ifBlank { "$videoId-ep-$episodeNumber" }

        return Episode(
            id = episodeId,
            videoId = videoId,
            sourceId = id,
            url = if (href.startsWith("http")) href else "$baseUrl$href",
            title = title,
            number = episodeNumber
        )
    }

    override suspend fun getLinks(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        // Get server list
        val episodeId = episode.id.substringAfterLast("-").ifBlank { episode.id }
        val serversUrl = "$AJAX_URL/v2/episode/servers?episodeId=$episodeId"

        try {
            val serversResponse = httpClient.get(serversUrl, headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to episode.url
            ))

            val serversAjax = json.decodeFromString<AjaxResponse>(serversResponse)
            val serversDoc = org.jsoup.Jsoup.parse(serversAjax.html)

            // Get available servers (sub and dub)
            val servers = serversDoc.select(".server-item, [data-server-id]").mapNotNull { server ->
                val serverId = server.attr("data-id").ifBlank { server.attr("data-server-id") }
                val serverName = server.text().trim()
                val serverType = if (server.closest(".servers-sub") != null) "sub" else "dub"
                if (serverId.isNotBlank()) Triple(serverId, serverName, serverType) else null
            }

            // Get streaming links from each server
            for ((serverId, serverName, serverType) in servers.take(4)) { // Limit to 4 servers
                try {
                    val sourcesUrl = "$AJAX_URL/v2/episode/sources?id=$serverId"
                    val sourcesResponse = httpClient.get(sourcesUrl, headers = mapOf(
                        "X-Requested-With" to "XMLHttpRequest",
                        "Referer" to episode.url
                    ))

                    val sourcesData = json.decodeFromString<SourcesResponse>(sourcesResponse)

                    if (sourcesData.link.isNotBlank()) {
                        // The link is typically an embed URL that needs extraction
                        val extractedLinks = extractFromEmbed(sourcesData.link)
                        links.addAll(extractedLinks.map { link ->
                            link.copy(
                                extractorName = "$serverName ($serverType)",
                                referer = baseUrl
                            )
                        })
                    }
                } catch (e: Exception) {
                    // Continue with other servers
                }
            }
        } catch (e: Exception) {
            // Fallback: try to find embed directly on page
            links.addAll(extractFromEpisodePage(episode))
        }

        return links
    }

    private suspend fun extractFromEmbed(embedUrl: String): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            val response = httpClient.get(embedUrl, headers = mapOf(
                "Referer" to baseUrl
            ))

            // Look for m3u8 links in the response
            val m3u8Pattern = Regex("""["']?(https?://[^"'\s]+\.m3u8[^"'\s]*)["']?""")
            val mp4Pattern = Regex("""["']?(https?://[^"'\s]+\.mp4[^"'\s]*)["']?""")

            m3u8Pattern.findAll(response).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                val quality = extractQuality(url)
                links.add(VideoLink(
                    url = url,
                    quality = quality,
                    extractorName = "HiAnime",
                    isM3u8 = true,
                    referer = embedUrl
                ))
            }

            mp4Pattern.findAll(response).forEach { match ->
                val url = match.groupValues[1].replace("\\/", "/")
                val quality = extractQuality(url)
                links.add(VideoLink(
                    url = url,
                    quality = quality,
                    extractorName = "HiAnime",
                    isM3u8 = false,
                    referer = embedUrl
                ))
            }

            // Look for encrypted sources (common pattern)
            val encryptedPattern = Regex("""sources\s*[:=]\s*\[([^\]]+)\]""")
            encryptedPattern.find(response)?.let { match ->
                val sourcesJson = "[${match.groupValues[1]}]"
                try {
                    // Try to parse as JSON array of source objects
                    val sources = json.decodeFromString<List<VideoSourceItem>>(sourcesJson)
                    sources.forEach { source ->
                        links.add(VideoLink(
                            url = source.file,
                            quality = source.label ?: extractQuality(source.file),
                            extractorName = "HiAnime",
                            isM3u8 = source.file.contains(".m3u8"),
                            referer = embedUrl
                        ))
                    }
                } catch (e: Exception) {
                    // Ignore parse errors
                }
            }
        } catch (e: Exception) {
            // Embed extraction failed
        }

        return links.distinctBy { it.url }
    }

    private suspend fun extractFromEpisodePage(episode: Episode): List<VideoLink> {
        val links = mutableListOf<VideoLink>()

        try {
            val doc = httpClient.getDocument(episode.url)

            // Look for iframe sources
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("src")
                if (src.isNotBlank()) {
                    links.addAll(extractFromEmbed(src))
                }
            }
        } catch (e: Exception) {
            // Page extraction failed
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

    // API response models
    @Serializable
    private data class AjaxResponse(
        val status: Boolean = true,
        val html: String = ""
    )

    @Serializable
    private data class SourcesResponse(
        val link: String = "",
        val type: String = ""
    )

    @Serializable
    private data class VideoSourceItem(
        val file: String,
        val label: String? = null,
        val type: String? = null
    )
}
