package com.omnistream.source.manga

import com.omnistream.core.network.OmniHttpClient
import com.omnistream.domain.model.Chapter
import com.omnistream.domain.model.Manga
import com.omnistream.domain.model.MangaStatus
import com.omnistream.domain.model.Page
import com.omnistream.source.model.MangaSource

/**
 * MangaKakalot/MangaNato source for manga reading.
 * Very stable HTML structure, rarely changes.
 */
class MangaKakalotSource(
    private val httpClient: OmniHttpClient
) : MangaSource {

    override val id = "mangakakalot"
    override val name = "MangaKakalot"
    override val baseUrl = "https://chapmanganato.to"
    override val lang = "en"
    override val isNsfw = false

    override suspend fun getPopular(page: Int): List<Manga> {
        val url = "$baseUrl/genre-all/$page?type=topview"
        return parseListPage(url)
    }

    override suspend fun getLatest(page: Int): List<Manga> {
        val url = "$baseUrl/genre-all/$page"
        return parseListPage(url)
    }

    override suspend fun search(query: String, page: Int): List<Manga> {
        val searchQuery = query.replace(" ", "_").lowercase()
        val url = "$baseUrl/search/story/$searchQuery?page=$page"
        return parseSearchPage(url)
    }

    private suspend fun parseListPage(url: String): List<Manga> {
        return try {
            val doc = httpClient.getDocument(url)
            android.util.Log.d("MangaKakalot", "Parsing list page: $url")

            doc.select("div.content-genres-item").mapNotNull { element ->
                try {
                    val linkEl = element.selectFirst("a.genres-item-img") ?: return@mapNotNull null
                    val href = linkEl.attr("href")
                    val mangaId = href.substringAfterLast("/")

                    val title = linkEl.attr("title").ifBlank {
                        element.selectFirst("h3 a")?.text()
                    } ?: return@mapNotNull null

                    val coverUrl = element.selectFirst("img")?.let { img ->
                        img.attr("src").ifBlank { img.attr("data-src") }
                    }

                    Manga(
                        id = mangaId,
                        sourceId = id,
                        title = title.trim(),
                        url = href,
                        coverUrl = coverUrl
                    )
                } catch (e: Exception) {
                    null
                }
            }.also {
                android.util.Log.d("MangaKakalot", "Found ${it.size} manga")
            }
        } catch (e: Exception) {
            android.util.Log.e("MangaKakalot", "Failed to parse list", e)
            emptyList()
        }
    }

    private suspend fun parseSearchPage(url: String): List<Manga> {
        return try {
            val doc = httpClient.getDocument(url)

            doc.select("div.search-story-item, div.story_item").mapNotNull { element ->
                try {
                    val linkEl = element.selectFirst("a.item-img, a.story_item_img") ?: return@mapNotNull null
                    val href = linkEl.attr("href")
                    val mangaId = href.substringAfterLast("/")

                    val title = linkEl.attr("title").ifBlank {
                        element.selectFirst("h3 a")?.text()
                    } ?: return@mapNotNull null

                    val coverUrl = element.selectFirst("img")?.attr("src")

                    Manga(
                        id = mangaId,
                        sourceId = id,
                        title = title.trim(),
                        url = href,
                        coverUrl = coverUrl
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MangaKakalot", "Search failed", e)
            emptyList()
        }
    }

    override suspend fun getDetails(manga: Manga): Manga {
        return try {
            val url = manga.url.ifBlank { "$baseUrl/manga-${manga.id}" }
            val doc = httpClient.getDocument(url)
            android.util.Log.d("MangaKakalot", "Getting details for: $url")

            val infoPanel = doc.selectFirst("div.panel-story-info, div.manga-info-top")

            val title = doc.selectFirst("h1, div.story-info-right h1")?.text()?.trim() ?: manga.title

            val coverUrl = doc.selectFirst("span.info-image img, div.manga-info-pic img")?.attr("src")
                ?: manga.coverUrl

            val description = doc.selectFirst("div.panel-story-info-description, div#noidungm")
                ?.text()?.replace("Description :", "")?.trim()

            // Parse info table
            val infoRows = doc.select("table.variations-tableInfo tr, li.story-info-right-extent")
            var author: String? = null
            var status: MangaStatus = MangaStatus.UNKNOWN
            val genres = mutableListOf<String>()

            infoRows.forEach { row ->
                val label = row.selectFirst("td.table-label, .stre-label")?.text()?.lowercase() ?: ""
                val value = row.selectFirst("td.table-value, .stre-value")

                when {
                    label.contains("author") -> author = value?.text()?.trim()
                    label.contains("status") -> {
                        val statusText = value?.text()?.lowercase() ?: ""
                        status = when {
                            statusText.contains("ongoing") -> MangaStatus.ONGOING
                            statusText.contains("completed") -> MangaStatus.COMPLETED
                            else -> MangaStatus.UNKNOWN
                        }
                    }
                    label.contains("genre") -> {
                        value?.select("a")?.forEach { genres.add(it.text().trim()) }
                    }
                }
            }

            manga.copy(
                title = title,
                coverUrl = coverUrl,
                description = description,
                author = author,
                status = status,
                genres = genres
            )
        } catch (e: Exception) {
            android.util.Log.e("MangaKakalot", "Failed to get details", e)
            manga
        }
    }

    override suspend fun getChapters(manga: Manga): List<Chapter> {
        return try {
            val url = manga.url.ifBlank { "$baseUrl/manga-${manga.id}" }
            val doc = httpClient.getDocument(url)
            android.util.Log.d("MangaKakalot", "Getting chapters for: $url")

            doc.select("ul.row-content-chapter li, div.chapter-list div.row").mapNotNull { element ->
                try {
                    val linkEl = element.selectFirst("a") ?: return@mapNotNull null
                    val href = linkEl.attr("href")
                    val chapterId = href.substringAfterLast("/")

                    val chapterText = linkEl.text().trim()
                    val chapterNumber = extractChapterNumber(chapterText) ?: return@mapNotNull null

                    val title = if (chapterText.contains(":")) {
                        chapterText.substringAfter(":").trim()
                    } else null

                    // Parse date
                    val dateText = element.selectFirst("span.chapter-time, span.chapter_time")?.text()
                    val uploadDate = parseDateText(dateText)

                    Chapter(
                        id = chapterId,
                        mangaId = manga.id,
                        sourceId = id,
                        url = href,
                        title = title,
                        number = chapterNumber,
                        uploadDate = uploadDate
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedByDescending { it.number }.also {
                android.util.Log.d("MangaKakalot", "Found ${it.size} chapters")
            }
        } catch (e: Exception) {
            android.util.Log.e("MangaKakalot", "Failed to get chapters", e)
            emptyList()
        }
    }

    override suspend fun getPages(chapter: Chapter): List<Page> {
        return try {
            val doc = httpClient.getDocument(chapter.url)
            android.util.Log.d("MangaKakalot", "Getting pages for: ${chapter.url}")

            doc.select("div.container-chapter-reader img, div.vung-doc img").mapIndexedNotNull { index, img ->
                val src = img.attr("src").ifBlank { img.attr("data-src") }
                if (src.isNotBlank() && (src.contains(".jpg") || src.contains(".png") || src.contains(".webp"))) {
                    Page(
                        index = index,
                        imageUrl = src,
                        referer = baseUrl
                    )
                } else null
            }.also {
                android.util.Log.d("MangaKakalot", "Found ${it.size} pages")
            }
        } catch (e: Exception) {
            android.util.Log.e("MangaKakalot", "Failed to get pages", e)
            emptyList()
        }
    }

    private fun extractChapterNumber(text: String): Float? {
        val patterns = listOf(
            Regex("""[Cc]hapter\s*(\d+(?:\.\d+)?)"""),
            Regex("""[Cc]h\.?\s*(\d+(?:\.\d+)?)"""),
            Regex("""(\d+(?:\.\d+)?)""")
        )
        for (pattern in patterns) {
            pattern.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { return it }
        }
        return null
    }

    private fun parseDateText(dateText: String?): Long? {
        if (dateText.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        val text = dateText.lowercase()

        return try {
            when {
                text.contains("ago") -> {
                    val num = Regex("""(\d+)""").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 1
                    when {
                        text.contains("min") -> now - num * 60 * 1000
                        text.contains("hour") -> now - num * 60 * 60 * 1000
                        text.contains("day") -> now - num * 24 * 60 * 60 * 1000
                        else -> now
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
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
