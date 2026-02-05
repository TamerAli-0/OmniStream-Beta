package com.omnistream.data.anilist

import com.omnistream.core.network.OmniHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListApi @Inject constructor(
    private val httpClient: OmniHttpClient,
    private val authManager: AniListAuthManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    companion object {
        private const val GRAPHQL_URL = "https://graphql.anilist.co"
    }

    suspend fun getCurrentUser(): AniListUser? = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext null

        val query = """
            query {
                Viewer {
                    id
                    name
                    avatar {
                        large
                    }
                    bannerImage
                    about
                    options {
                        profileColor
                    }
                }
            }
        """.trimIndent()

        try {
            val response = httpClient.postJson(
                url = GRAPHQL_URL,
                json = """{"query":"${query.replace("\n", "\\n")}"}""",
                headers = mapOf(
                    "Authorization" to "Bearer $token"
                )
            )

            val jsonResponse = json.parseToJsonElement(response).jsonObject
            val data = jsonResponse["data"]?.jsonObject ?: return@withContext null
            val viewer = data["Viewer"]?.jsonObject ?: return@withContext null

            AniListUser(
                id = viewer["id"]?.toString()?.toIntOrNull() ?: 0,
                name = viewer["name"]?.toString()?.trim('"') ?: "",
                avatar = viewer["avatar"]?.jsonObject?.get("large")?.toString()?.trim('"'),
                bannerImage = viewer["bannerImage"]?.toString()?.trim('"'),
                about = viewer["about"]?.toString()?.trim('"'),
                profileColor = viewer["options"]?.jsonObject?.get("profileColor")?.toString()?.trim('"')
            )
        } catch (e: Exception) {
            android.util.Log.e("AniListApi", "Failed to get current user", e)
            null
        }
    }

    suspend fun updateMangaProgress(
        mangaId: Int,
        progress: Int,
        status: AniListStatus? = null,
        score: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext false

        val mutation = """
            mutation {
                SaveMediaListEntry(
                    mediaId: $mangaId,
                    progress: $progress,
                    ${if (status != null) "status: ${status.name}," else ""}
                    ${if (score != null) "score: $score," else ""}
                ) {
                    id
                    progress
                    status
                }
            }
        """.trimIndent()

        try {
            httpClient.postJson(
                url = GRAPHQL_URL,
                json = """{"query":"${mutation.replace("\n", "\\n")}"}""",
                headers = mapOf(
                    "Authorization" to "Bearer $token"
                )
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("AniListApi", "Failed to update manga progress", e)
            false
        }
    }

    suspend fun updateAnimeProgress(
        animeId: Int,
        progress: Int,
        status: AniListStatus? = null,
        score: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext false

        val mutation = """
            mutation {
                SaveMediaListEntry(
                    mediaId: $animeId,
                    progress: $progress,
                    ${if (status != null) "status: ${status.name}," else ""}
                    ${if (score != null) "score: $score," else ""}
                ) {
                    id
                    progress
                    status
                }
            }
        """.trimIndent()

        try {
            httpClient.postJson(
                url = GRAPHQL_URL,
                json = """{"query":"${mutation.replace("\n", "\\n")}"}""",
                headers = mapOf(
                    "Authorization" to "Bearer $token"
                )
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("AniListApi", "Failed to update anime progress", e)
            false
        }
    }

    suspend fun searchManga(title: String): List<AniListMedia> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext emptyList()

        val query = """
            query {
                Page(page: 1, perPage: 10) {
                    media(search: "$title", type: MANGA) {
                        id
                        title {
                            romaji
                            english
                        }
                        coverImage {
                            large
                        }
                        format
                    }
                }
            }
        """.trimIndent()

        try {
            val response = httpClient.postJson(
                url = GRAPHQL_URL,
                json = """{"query":"${query.replace("\n", "\\n")}"}""",
                headers = mapOf(
                    "Authorization" to "Bearer $token"
                )
            )

            val jsonResponse = json.parseToJsonElement(response).jsonObject
            val data = jsonResponse["data"]?.jsonObject ?: return@withContext emptyList()
            val page = data["Page"]?.jsonObject ?: return@withContext emptyList()
            val media = page["media"]?.jsonArray ?: return@withContext emptyList()

            media.mapNotNull { item ->
                val obj = item.jsonObject
                val titleObj = obj["title"]?.jsonObject
                AniListMedia(
                    id = obj["id"]?.toString()?.toIntOrNull() ?: 0,
                    title = titleObj?.get("english")?.toString()?.trim('"')
                        ?: titleObj?.get("romaji")?.toString()?.trim('"')
                        ?: "",
                    coverImage = obj["coverImage"]?.jsonObject?.get("large")?.toString()?.trim('"'),
                    format = obj["format"]?.toString()?.trim('"')
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AniListApi", "Failed to search manga", e)
            emptyList()
        }
    }

    suspend fun searchAnime(title: String): List<AniListMedia> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken() ?: return@withContext emptyList()

        val query = """
            query {
                Page(page: 1, perPage: 10) {
                    media(search: "$title", type: ANIME) {
                        id
                        title {
                            romaji
                            english
                        }
                        coverImage {
                            large
                        }
                        format
                    }
                }
            }
        """.trimIndent()

        try {
            val response = httpClient.postJson(
                url = GRAPHQL_URL,
                json = """{"query":"${query.replace("\n", "\\n")}"}""",
                headers = mapOf(
                    "Authorization" to "Bearer $token"
                )
            )

            val jsonResponse = json.parseToJsonElement(response).jsonObject
            val data = jsonResponse["data"]?.jsonObject ?: return@withContext emptyList()
            val page = data["Page"]?.jsonObject ?: return@withContext emptyList()
            val media = page["media"]?.jsonArray ?: return@withContext emptyList()

            media.mapNotNull { item ->
                val obj = item.jsonObject
                val titleObj = obj["title"]?.jsonObject
                AniListMedia(
                    id = obj["id"]?.toString()?.toIntOrNull() ?: 0,
                    title = titleObj?.get("english")?.toString()?.trim('"')
                        ?: titleObj?.get("romaji")?.toString()?.trim('"')
                        ?: "",
                    coverImage = obj["coverImage"]?.jsonObject?.get("large")?.toString()?.trim('"'),
                    format = obj["format"]?.toString()?.trim('"')
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AniListApi", "Failed to search anime", e)
            emptyList()
        }
    }

    /**
     * Get user's manga statistics including total chapters read
     */
    suspend fun getUserStatistics(): AniListStatistics? = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken()
        android.util.Log.d("AniListApi", "getUserStatistics - Token: ${token?.take(10)}...")

        if (token == null) {
            android.util.Log.e("AniListApi", "No access token available")
            return@withContext null
        }

        val query = """
            query {
                Viewer {
                    statistics {
                        manga {
                            chaptersRead
                            count
                        }
                        anime {
                            episodesWatched
                            count
                        }
                    }
                }
            }
        """.trimIndent()

        try {
            android.util.Log.d("AniListApi", "Sending statistics query to AniList...")
            val response = httpClient.postJson(
                url = GRAPHQL_URL,
                json = """{"query":"${query.replace("\n", "\\n")}"}""",
                headers = mapOf(
                    "Authorization" to "Bearer $token"
                )
            )

            android.util.Log.d("AniListApi", "Response received: ${response.take(200)}")

            val jsonResponse = json.parseToJsonElement(response).jsonObject
            val data = jsonResponse["data"]?.jsonObject
            if (data == null) {
                android.util.Log.e("AniListApi", "No data in response")
                return@withContext null
            }

            val viewer = data["Viewer"]?.jsonObject
            if (viewer == null) {
                android.util.Log.e("AniListApi", "No Viewer in response")
                return@withContext null
            }

            val stats = viewer["statistics"]?.jsonObject
            if (stats == null) {
                android.util.Log.e("AniListApi", "No statistics in response")
                return@withContext null
            }

            val mangaStats = stats["manga"]?.jsonObject
            val animeStats = stats["anime"]?.jsonObject

            val chaptersRead = mangaStats?.get("chaptersRead")?.toString()?.toIntOrNull() ?: 0
            val episodesWatched = animeStats?.get("episodesWatched")?.toString()?.toIntOrNull() ?: 0

            android.util.Log.d("AniListApi", "Parsed stats - Chapters: $chaptersRead, Episodes: $episodesWatched")

            AniListStatistics(
                chaptersRead = chaptersRead,
                mangaCount = mangaStats?.get("count")?.toString()?.toIntOrNull() ?: 0,
                episodesWatched = episodesWatched,
                animeCount = animeStats?.get("count")?.toString()?.toIntOrNull() ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("AniListApi", "Failed to get user statistics", e)
            null
        }
    }
}

@Serializable
data class AniListUser(
    val id: Int,
    val name: String,
    val avatar: String?,
    val bannerImage: String? = null,
    val about: String? = null,
    val profileColor: String? = null
)

@Serializable
data class AniListMedia(
    val id: Int,
    val title: String,
    val coverImage: String?,
    val format: String?
)

@Serializable
data class AniListStatistics(
    val chaptersRead: Int,
    val mangaCount: Int,
    val episodesWatched: Int,
    val animeCount: Int
)

enum class AniListStatus {
    CURRENT,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,
    REPEATING
}
