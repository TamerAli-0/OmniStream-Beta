package com.omnistream.source.manga

import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Chapter
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.MangaStatus
import com.omnistream.domain.model.Page
import com.omnistream.source.model.MangaSource

/**
 * ManhuaPlus source for manhua/manhwa reading.
 * Site: https://manhuaplus.com
 */
class ManhuaPlusSource(
    private val httpClient: OmniHttpClient
) : MangaSource {

    override val id = "manhuaplus"
    override val name = "ManhuaPlus"
    override val baseUrl = "https://manhuaplus.com"
    override val lang = "en"
    override val isNsfw = false

    override suspend fun getPopular(page: Int): List<Manga> {
        return try {
            val url = "$baseUrl/manga/?m_orderby=trending&page=$page"
            val doc = httpClient.getDocument(url)
            android.util.Log.d("ManhuaPlus", "Loading popular page $page")

            parseMangaList(doc)
        } catch (e: Exception) {
            android.util.Log.e("ManhuaPlus", "Failed to get popular", e)
            emptyList()
        }
    }

    override suspend fun getLatest(page: Int): List<Manga> {
        return try {
            val url = "$baseUrl/manga/?m_orderby=latest&page=$page"
            val doc = httpClient.getDocument(url)
            android.util.Log.d("ManhuaPlus", "Loading latest page $page")

            parseMangaList(doc)
        } catch (e: Exception) {
            android.util.Log.e("ManhuaPlus", "Failed to get latest", e)
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<Manga> {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/?s=$encodedQuery&post_type=wp-manga&paged=$page"
            val doc = httpClient.getDocument(url)
            android.util.Log.d("ManhuaPlus", "Searching: $query")

            parseSearchResults(doc)
        } catch (e: Exception) {
            android.util.Log.e("ManhuaPlus", "Search failed", e)
            emptyList()
        }
    }

    private fun parseMangaList(doc: org.jsoup.nodes.Document): List<Manga> {
        val mangaList = mutableListOf<Manga>()

        doc.select(".page-item-detail, .manga").forEach { el ->
            try {
                val link = el.selectFirst("a") ?: return@forEach
                val href = link.attr("href")
                if (href.isBlank()) return@forEach

                val title = el.selectFirst(".post-title a, h3 a, h5 a")?.text()?.trim()
                    ?: link.attr("title")?.trim()
                    ?: return@forEach

                val img = el.selectFirst("img")
                val cover = img?.attr("data-src")?.ifBlank { img.attr("src") }
                    ?: img?.attr("src")

                // Extract manga ID from URL
                val mangaId = Regex("""manga/([^/]+)""").find(href)?.groupValues?.get(1)
                    ?: return@forEach

                mangaList.add(Manga(
                    id = mangaId,
                    sourceId = id,
                    title = title,
                    url = href,
                    coverUrl = cover,
                    status = MangaStatus.ONGOING
                ))
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }

        android.util.Log.d("ManhuaPlus", "Parsed ${mangaList.size} manga")
        return mangaList
    }

    private fun parseSearchResults(doc: org.jsoup.nodes.Document): List<Manga> {
        val mangaList = mutableListOf<Manga>()

        doc.select(".c-tabs-item__content, .row.c-tabs-item__content").forEach { el ->
            try {
                val link = el.selectFirst(".post-title a, h3 a") ?: return@forEach
                val href = link.attr("href")
                if (href.isBlank()) return@forEach

                val title = link.text().trim()
                if (title.isBlank()) return@forEach

                val img = el.selectFirst("img")
                val cover = img?.attr("data-src")?.ifBlank { img.attr("src") }
                    ?: img?.attr("src")

                val mangaId = Regex("""manga/([^/]+)""").find(href)?.groupValues?.get(1)
                    ?: return@forEach

                mangaList.add(Manga(
                    id = mangaId,
                    sourceId = id,
                    title = title,
                    url = href,
                    coverUrl = cover,
                    status = MangaStatus.UNKNOWN
                ))
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }

        android.util.Log.d("ManhuaPlus", "Search found ${mangaList.size} manga")
        return mangaList
    }

    override suspend fun getDetails(manga: Manga): Manga {
        return try {
            val url = if (manga.url.startsWith("http")) manga.url else "$baseUrl/manga/${manga.id}/"
            val doc = httpClient.getDocument(url)
            android.util.Log.d("ManhuaPlus", "Getting details for: ${manga.title}")

            val title = doc.selectFirst(".post-title h1, .post-title h3")?.text()?.trim()
                ?: doc.selectFirst("h1")?.text()?.trim()
                ?: manga.title

            val cover = doc.selectFirst(".summary_image img")?.let { img ->
                img.attr("data-src").ifBlank { img.attr("src") }
            } ?: manga.coverUrl

            val description = doc.selectFirst(".summary__content, .description-summary")?.text()?.trim()

            val genres = doc.select(".genres-content a, .manga-tag a")
                .map { it.text().trim() }
                .filter { it.isNotBlank() }

            val statusText = doc.selectFirst(".post-status .summary-content, .post-content_item:contains(Status) .summary-content")
                ?.text()?.lowercase() ?: ""
            val status = when {
                statusText.contains("ongoing") -> MangaStatus.ONGOING
                statusText.contains("completed") -> MangaStatus.COMPLETED
                statusText.contains("hiatus") -> MangaStatus.HIATUS
                else -> manga.status
            }

            val author = doc.selectFirst(".author-content a, .post-content_item:contains(Author) .summary-content")
                ?.text()?.trim()

            manga.copy(
                title = title,
                coverUrl = cover,
                description = description,
                genres = genres,
                status = status,
                author = author
            )
        } catch (e: Exception) {
            android.util.Log.e("ManhuaPlus", "Failed to get details", e)
            manga
        }
    }

    override suspend fun getChapters(manga: Manga): List<Chapter> {
        return try {
            val url = if (manga.url.startsWith("http")) manga.url else "$baseUrl/manga/${manga.id}/"
            val doc = httpClient.getDocument(url)
            android.util.Log.d("ManhuaPlus", "Getting chapters for: ${manga.title}")

            val chapters = mutableListOf<Chapter>()

            doc.select(".wp-manga-chapter a, .version-chap a, li.wp-manga-chapter a").forEach { el ->
                val href = el.attr("href")
                val title = el.text().trim()

                if (href.isNotBlank() && title.isNotBlank() && href.contains("chapter")) {
                    // Extract chapter number
                    val match = Regex("""chapter[- ]?(\d+\.?\d*)""", RegexOption.IGNORE_CASE)
                        .find(title) ?: Regex("""chapter[- ]?(\d+\.?\d*)""", RegexOption.IGNORE_CASE).find(href)

                    val chapterNum = match?.groupValues?.get(1)?.toFloatOrNull() ?: (chapters.size + 1).toFloat()

                    // Create chapter ID from URL
                    val chapterId = href.replace(baseUrl, "").trim('/')

                    // Avoid duplicates
                    if (chapters.none { it.number == chapterNum }) {
                        chapters.add(Chapter(
                            id = chapterId,
                            mangaId = manga.id,
                            sourceId = id,
                            title = title,
                            number = chapterNum,
                            url = href
                        ))
                    }
                }
            }

            // Sort by chapter number ascending
            chapters.sortedBy { it.number }.also {
                android.util.Log.d("ManhuaPlus", "Found ${it.size} chapters")
            }
        } catch (e: Exception) {
            android.util.Log.e("ManhuaPlus", "Failed to get chapters", e)
            emptyList()
        }
    }

    override suspend fun getPages(chapter: Chapter): List<Page> {
        return try {
            val url = if (chapter.url.startsWith("http")) chapter.url else "$baseUrl/${chapter.id}/"
            val doc = httpClient.getDocument(url)
            android.util.Log.d("ManhuaPlus", "Getting pages for: ${chapter.title}")

            val pages = mutableListOf<Page>()

            doc.select(".wp-manga-chapter-img, .reading-content img, .page-break img").forEach { img ->
                val src = img.attr("data-src").ifBlank { img.attr("src") }.trim()

                // Filter out non-page images
                if (src.isNotBlank() &&
                    !src.contains("logo", ignoreCase = true) &&
                    !src.contains("icon", ignoreCase = true) &&
                    !src.contains("loading", ignoreCase = true)) {

                    pages.add(Page(
                        index = pages.size,
                        imageUrl = src,
                        referer = baseUrl
                    ))
                }
            }

            pages.also {
                android.util.Log.d("ManhuaPlus", "Found ${it.size} pages")
            }
        } catch (e: Exception) {
            android.util.Log.e("ManhuaPlus", "Failed to get pages", e)
            emptyList()
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
