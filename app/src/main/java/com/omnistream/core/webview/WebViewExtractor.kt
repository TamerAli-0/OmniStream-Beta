package com.omnistream.core.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log

/**
 * WebView-based video URL extractor.
 * Loads a page in a hidden WebView and intercepts network requests to find video URLs.
 */
class WebViewExtractor(private val context: Context) {

    companion object {
        private const val TAG = "WebViewExtractor"
        private const val DEFAULT_TIMEOUT = 30000L // 30 seconds
    }

    /**
     * Result of extraction
     */
    data class ExtractionResult(
        val videoUrl: String?,
        val referer: String?,
        val headers: Map<String, String> = emptyMap()
    )

    /**
     * Extract video URL by loading a page and intercepting m3u8/mp4 requests.
     *
     * @param url The page URL to load
     * @param referer Referer header to use
     * @param targetPatterns Patterns to match for video URLs (e.g., ".m3u8", "code29wave.site")
     * @param timeout Maximum time to wait in milliseconds
     */
    suspend fun extractVideoUrl(
        url: String,
        referer: String? = null,
        targetPatterns: List<String> = listOf(".m3u8", ".mp4"),
        timeout: Long = DEFAULT_TIMEOUT
    ): ExtractionResult = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<ExtractionResult>()
        var webView: WebView? = null

        try {
            webView = createWebView()
            var foundUrl: String? = null
            var foundReferer: String? = null

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val requestUrl = request?.url?.toString() ?: return null

                    Log.d(TAG, "Intercepted: $requestUrl")

                    // Check if this URL matches our target patterns
                    if (targetPatterns.any { pattern -> requestUrl.contains(pattern, ignoreCase = true) }) {
                        Log.d(TAG, "Found video URL: $requestUrl")
                        foundUrl = requestUrl
                        foundReferer = request.requestHeaders["Referer"]
                            ?: request.requestHeaders["referer"]
                            ?: referer

                        // Complete the result
                        if (!result.isCompleted) {
                            result.complete(ExtractionResult(
                                videoUrl = foundUrl,
                                referer = foundReferer,
                                headers = mapOf(
                                    "Referer" to (foundReferer ?: ""),
                                    "Origin" to (foundReferer?.trimEnd('/') ?: "")
                                )
                            ))
                        }
                    }

                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Page finished loading: $url")

                    // Give some extra time for JS to execute and make requests
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!result.isCompleted) {
                            // If we found a URL by now, use it; otherwise return null
                            result.complete(ExtractionResult(
                                videoUrl = foundUrl,
                                referer = foundReferer
                            ))
                        }
                    }, 5000) // Wait 5 more seconds for JS to execute
                }
            }

            // Set referer if provided
            val headers = mutableMapOf<String, String>()
            referer?.let { headers["Referer"] = it }

            Log.d(TAG, "Loading URL: $url")
            webView.loadUrl(url, headers)

            // Wait for result with timeout
            withTimeoutOrNull(timeout) {
                result.await()
            } ?: ExtractionResult(videoUrl = null, referer = null)

        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            ExtractionResult(videoUrl = null, referer = null)
        } finally {
            // Cleanup WebView on main thread
            webView?.let { view ->
                Handler(Looper.getMainLooper()).post {
                    view.stopLoading()
                    view.destroy()
                }
            }
        }
    }

    /**
     * Extract video URL specifically for AnimeKai/4spromax.site
     */
    suspend fun extractAnimeKaiVideo(
        episodeUrl: String,
        timeout: Long = DEFAULT_TIMEOUT
    ): ExtractionResult {
        Log.d(TAG, "Extracting AnimeKai video from: $episodeUrl")

        return extractVideoUrl(
            url = episodeUrl,
            referer = "https://anikai.to/",
            targetPatterns = listOf(
                "code29wave.site",  // The CDN domain
                ".m3u8",
                "master.m3u8",
                "index.m3u8"
            ),
            timeout = timeout
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                // Allow media playback
                mediaPlaybackRequiresUserGesture = false
            }

            // Disable hardware acceleration to avoid issues
            setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
        }
    }
}
