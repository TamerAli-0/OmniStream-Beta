package com.omnistream.core.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

/**
 * Jsoup extension functions for easier scraping.
 * Based on CloudStream/Kotatsu patterns.
 */

// Safe text extraction
fun Element?.textOrNull(): String? = this?.text()?.takeIf { it.isNotBlank() }
fun Element?.text(default: String = ""): String = this?.text()?.takeIf { it.isNotBlank() } ?: default

// Safe attribute extraction
fun Element?.attrOrNull(key: String): String? = this?.attr(key)?.takeIf { it.isNotBlank() }
fun Element?.attr(key: String, default: String = ""): String = this?.attr(key)?.takeIf { it.isNotBlank() } ?: default

// Safe href extraction with URL fixing
fun Element?.href(baseUrl: String = ""): String? {
    val href = this?.attr("href")?.takeIf { it.isNotBlank() } ?: return null
    return fixUrl(href, baseUrl)
}

// Safe src extraction with URL fixing
fun Element?.src(baseUrl: String = ""): String? {
    val src = this?.attr("src")
        ?: this?.attr("data-src")
        ?: this?.attr("data-lazy-src")
        ?: return null
    if (src.isBlank()) return null
    return fixUrl(src, baseUrl)
}

// Select first or null
fun Document.selectFirstOrNull(cssQuery: String): Element? = selectFirst(cssQuery)
fun Element.selectFirstOrNull(cssQuery: String): Element? = selectFirst(cssQuery)

// Select with map transformation
inline fun <T> Elements.mapNotNullCatching(transform: (Element) -> T?): List<T> {
    return mapNotNull { element ->
        try {
            transform(element)
        } catch (e: Exception) {
            null
        }
    }
}

// Fix relative URLs
fun fixUrl(url: String, baseUrl: String = ""): String {
    if (url.isBlank()) return url

    return when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> {
            if (baseUrl.isNotBlank()) {
                val base = baseUrl.removeSuffix("/")
                val domain = Regex("^(https?://[^/]+)").find(base)?.value ?: base
                "$domain$url"
            } else url
        }
        else -> {
            if (baseUrl.isNotBlank()) {
                val base = baseUrl.substringBeforeLast("/")
                "$base/$url"
            } else url
        }
    }
}

// Extract numbers from text (useful for chapter numbers)
fun String.extractNumber(): Float? {
    val regex = Regex("""(\d+(?:\.\d+)?)""")
    return regex.find(this)?.groupValues?.get(1)?.toFloatOrNull()
}

// Clean title (remove common suffixes/prefixes)
fun String.cleanTitle(): String {
    return this
        .replace(Regex("""\s*\(.*?\)\s*$"""), "") // Remove trailing (...)
        .replace(Regex("""^\s*\[.*?\]\s*"""), "") // Remove leading [...]
        .trim()
}

// Extract data from inline JavaScript
fun Document.extractFromScript(pattern: Regex): String? {
    return select("script").map { it.data() }
        .firstNotNullOfOrNull { script ->
            pattern.find(script)?.groupValues?.getOrNull(1)
        }
}
