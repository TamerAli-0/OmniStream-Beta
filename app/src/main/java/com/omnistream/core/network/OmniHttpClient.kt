package com.omnistream.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main HTTP client for OmniStream with anti-detection headers.
 * Based on scraping tutorial best practices.
 */
@Singleton
class OmniHttpClient @Inject constructor() {

    companion object {
        // Standard browser User-Agent (Chrome on Windows)
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // Default headers to avoid detection
        // Note: Do NOT set Accept-Encoding - let OkHttp + BrotliInterceptor handle compression
        val DEFAULT_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1"
        )

        // Headers for AJAX/API requests
        val AJAX_HEADERS = mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to "en-US,en;q=0.9",
            "X-Requested-With" to "XMLHttpRequest"
        )
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BrotliInterceptor)  // Enable brotli decompression
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * GET request returning raw response body as String
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val requestHeaders = buildHeaders(headers, referer)
        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, response.message)
            }
            response.body?.string() ?: ""
        }
    }

    /**
     * GET request returning parsed Jsoup Document
     */
    suspend fun getDocument(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null
    ): Document = withContext(Dispatchers.IO) {
        val html = get(url, headers, referer)
        Jsoup.parse(html, url)
    }

    /**
     * GET request returning raw Response for advanced handling
     */
    suspend fun getRaw(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null
    ): Response = withContext(Dispatchers.IO) {
        val requestHeaders = buildHeaders(headers, referer)
        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders)
            .get()
            .build()

        client.newCall(request).execute()
    }

    /**
     * POST request with form data
     */
    suspend fun post(
        url: String,
        data: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val requestHeaders = buildHeaders(headers, referer)
        val formBody = data.entries.joinToString("&") { (k, v) ->
            "${java.net.URLEncoder.encode(k, "UTF-8")}=${java.net.URLEncoder.encode(v, "UTF-8")}"
        }

        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders)
            .post(formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, response.message)
            }
            response.body?.string() ?: ""
        }
    }

    /**
     * POST request with JSON body
     */
    suspend fun postJson(
        url: String,
        json: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null
    ): String = withContext(Dispatchers.IO) {
        val requestHeaders = buildHeaders(AJAX_HEADERS + headers, referer)

        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders)
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpException(response.code, response.message)
            }
            response.body?.string() ?: ""
        }
    }

    /**
     * HEAD request to check if URL is accessible (for speed testing)
     */
    suspend fun ping(url: String): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .head()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    System.currentTimeMillis() - start
                } else {
                    -1L
                }
            }
        } catch (e: Exception) {
            -1L
        }
    }

    private fun buildHeaders(
        customHeaders: Map<String, String>,
        referer: String?
    ): Headers {
        val combined = DEFAULT_HEADERS.toMutableMap()
        combined.putAll(customHeaders)
        referer?.let { combined["Referer"] = it }
        return Headers.Builder().apply {
            combined.forEach { (key, value) -> add(key, value) }
        }.build()
    }
}

class HttpException(val code: Int, message: String) : Exception("HTTP $code: $message")
