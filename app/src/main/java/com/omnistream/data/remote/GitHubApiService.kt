package com.omnistream.data.remote

import com.omnistream.domain.models.AppUpdate
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * GitHub API service for checking app updates
 */
interface GitHubApiService {

    /**
     * Get the latest release from GitHub repository
     * @param owner GitHub username/organization
     * @param repo Repository name
     */
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): AppUpdate
}
