package com.omnistream.source

import android.content.Context
import com.omnistream.core.network.OmniHttpClient
import com.omnistream.source.anime.AnimeKaiSource
// import com.omnistream.source.anime.GogoAnimeSource  // Paused - will revisit later
import com.omnistream.source.manga.MangaDexSource
import com.omnistream.source.manga.ManhuaPlusSource
import com.omnistream.source.model.MangaSource
import com.omnistream.source.movie.FlickyStreamSource
import com.omnistream.source.movie.GoojaraSource
import com.omnistream.source.movie.WatchFlixSource
import com.omnistream.source.model.SourceMetadata
import com.omnistream.source.model.VideoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all manga and video sources.
 * Handles source registration, speed testing, and health monitoring.
 */
@Singleton
class SourceManager @Inject constructor(
    private val context: Context,
    private val httpClient: OmniHttpClient
) {
    // Registered manga sources
    private val mangaSources = mutableMapOf<String, MangaSource>()

    // Registered video sources
    private val videoSources = mutableMapOf<String, VideoSource>()

    // Source speed results cache
    private val speedResults = mutableMapOf<String, SpeedTestResult>()

    init {
        // Register built-in sources
        registerBuiltInSources()
    }

    private fun registerBuiltInSources() {
        // Manga sources
        registerMangaSource(MangaDexSource(httpClient))      // Manga (API-based)
        registerMangaSource(ManhuaPlusSource(httpClient))    // Manhua/Manhwa

        // Video sources - Anime
        // registerVideoSource(GogoAnimeSource(httpClient))         // GogoAnime - PAUSED (will revisit later)
        registerVideoSource(AnimeKaiSource(context, httpClient))  // AnimeKai (WebView extraction, backup)

        // Video sources - Movies & TV
        registerVideoSource(FlickyStreamSource(httpClient))  // Movies & TV (vidzee.wtf decryption)
        registerVideoSource(WatchFlixSource(httpClient))     // Movies & TV (vidsrc-embed/cloudnestra chain)
    }

    fun registerMangaSource(source: MangaSource) {
        mangaSources[source.id] = source
    }

    fun registerVideoSource(source: VideoSource) {
        videoSources[source.id] = source
    }

    fun getMangaSource(id: String): MangaSource? = mangaSources[id]
    fun getVideoSource(id: String): VideoSource? = videoSources[id]

    fun getAllMangaSources(): List<MangaSource> = mangaSources.values.toList()
    fun getAllVideoSources(): List<VideoSource> = videoSources.values.toList()

    /**
     * Get source metadata for display in source manager UI.
     */
    fun getMangaSourceMetadata(): List<SourceMetadata> {
        return mangaSources.values.map { source ->
            SourceMetadata(
                id = source.id,
                name = source.name,
                lang = source.lang,
                isNsfw = source.isNsfw
            )
        }
    }

    /**
     * Test speed of all sources and return results sorted by latency.
     */
    suspend fun testAllSourceSpeeds(): List<SpeedTestResult> = coroutineScope {
        val results = mutableListOf<SpeedTestResult>()

        // Test manga sources
        val mangaTests = mangaSources.values.map { source ->
            async { testSourceSpeed(source.id, source.name, source.baseUrl) }
        }

        // Test video sources
        val videoTests = videoSources.values.map { source ->
            async { testSourceSpeed(source.id, source.name, source.baseUrl) }
        }

        results.addAll(mangaTests.awaitAll())
        results.addAll(videoTests.awaitAll())

        // Cache results
        results.forEach { speedResults[it.sourceId] = it }

        // Sort by latency (fastest first), broken sources last
        results.sortedWith(compareBy({ it.status == SourceStatus.BROKEN }, { it.latency }))
    }

    private suspend fun testSourceSpeed(
        sourceId: String,
        sourceName: String,
        baseUrl: String
    ): SpeedTestResult = withContext(Dispatchers.IO) {
        val samples = mutableListOf<Long>()
        var failures = 0

        // Take 3 samples
        repeat(3) {
            try {
                val latency = httpClient.ping(baseUrl)
                if (latency > 0) {
                    samples.add(latency)
                } else {
                    failures++
                }
            } catch (e: Exception) {
                failures++
            }
        }

        val avgLatency = if (samples.isNotEmpty()) samples.average().toLong() else -1L
        val reliability = (3 - failures) / 3f

        SpeedTestResult(
            sourceId = sourceId,
            sourceName = sourceName,
            latency = avgLatency,
            reliability = reliability,
            status = when {
                reliability < 0.5f -> SourceStatus.BROKEN
                avgLatency > 2000 -> SourceStatus.SLOW
                avgLatency > 800 -> SourceStatus.NORMAL
                else -> SourceStatus.FAST
            }
        )
    }

    fun getCachedSpeedResult(sourceId: String): SpeedTestResult? = speedResults[sourceId]

    /**
     * Get sources sorted by cached speed (fastest first).
     */
    fun getMangaSourcesSortedBySpeed(): List<MangaSource> {
        return mangaSources.values.sortedBy { source ->
            speedResults[source.id]?.latency ?: Long.MAX_VALUE
        }
    }
}

data class SpeedTestResult(
    val sourceId: String,
    val sourceName: String,
    val latency: Long,  // -1 if failed
    val reliability: Float,  // 0-1
    val status: SourceStatus
)

enum class SourceStatus {
    FAST,    // < 800ms
    NORMAL,  // 800-2000ms
    SLOW,    // > 2000ms
    BROKEN   // < 50% reliability
}
