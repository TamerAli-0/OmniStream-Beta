package com.omnistream.data.remote

import com.omnistream.data.remote.dto.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiService @Inject constructor() {

    companion object {
        // Change this to your deployed server URL
        const val BASE_URL = "https://omnistream-api-q2rh.onrender.com/api"
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // --- Auth endpoints ---

    suspend fun register(request: RegisterRequest): Result<AuthResponse> = apiCall {
        val body = json.encodeToString(request).toRequestBody(jsonMediaType)
        val req = Request.Builder()
            .url("$BASE_URL/auth/register")
            .post(body)
            .build()
        val response = client.newCall(req).execute()
        handleResponse(response)
    }

    suspend fun login(request: LoginRequest): Result<AuthResponse> = apiCall {
        val body = json.encodeToString(request).toRequestBody(jsonMediaType)
        val req = Request.Builder()
            .url("$BASE_URL/auth/login")
            .post(body)
            .build()
        val response = client.newCall(req).execute()
        handleResponse(response)
    }

    suspend fun getMe(token: String): Result<UserDto> = apiCall {
        val req = Request.Builder()
            .url("$BASE_URL/auth/me")
            .addHeader("Authorization", "Bearer $token")
            .build()
        val response = client.newCall(req).execute()
        handleResponse(response)
    }

    // --- Sync endpoints ---

    suspend fun getSyncData(token: String): Result<SyncDataResponse> = apiCall {
        val req = Request.Builder()
            .url("$BASE_URL/sync")
            .addHeader("Authorization", "Bearer $token")
            .build()
        val response = client.newCall(req).execute()
        handleResponse(response)
    }

    suspend fun updateSyncData(token: String, data: SyncUpdateRequest): Result<SyncDataResponse> = apiCall {
        val body = json.encodeToString(data).toRequestBody(jsonMediaType)
        val req = Request.Builder()
            .url("$BASE_URL/sync")
            .addHeader("Authorization", "Bearer $token")
            .put(body)
            .build()
        val response = client.newCall(req).execute()
        handleResponse(response)
    }

    // --- App version endpoints ---

    suspend fun checkAppVersion(currentVersion: String, platform: String = "android"): Result<AppVersionResponse> = apiCall {
        val url = "$BASE_URL/app/version?platform=$platform&currentVersion=$currentVersion"
        val req = Request.Builder()
            .url(url)
            .get()
            .build()
        val response = client.newCall(req).execute()
        handleResponse(response)
    }

    // --- Helpers ---

    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> {
        return withContext(Dispatchers.IO) {
            try {
                // Retry up to 3 times with exponential backoff
                var lastException: Exception? = null
                repeat(3) { attempt ->
                    try {
                        return@withContext Result.success(block())
                    } catch (e: Exception) {
                        lastException = e
                        // Don't retry on authentication errors
                        if (e is ApiException && e.code in 400..499) {
                            throw e
                        }
                        // Wait before retry (exponential backoff: 2s, 4s, 8s)
                        if (attempt < 2) {
                            kotlinx.coroutines.delay((2000L * (1 shl attempt)))
                        }
                    }
                }
                // All retries failed
                Result.failure(lastException ?: Exception("Unknown error"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private inline fun <reified T> handleResponse(response: okhttp3.Response): T {
        val bodyStr = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            val errorMsg = try {
                json.decodeFromString<ErrorResponse>(bodyStr).error
            } catch (_: Exception) {
                "Request failed (${response.code})"
            }
            throw ApiException(response.code, errorMsg)
        }
        return json.decodeFromString(bodyStr)
    }
}

class ApiException(val code: Int, override val message: String) : Exception(message)
