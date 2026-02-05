package com.omnistream.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an app update available from GitHub Releases
 */
@Serializable
data class AppUpdate(
    @SerialName("tag_name")
    val version: String,

    @SerialName("name")
    val title: String,

    @SerialName("body")
    val releaseNotes: String?,

    @SerialName("published_at")
    val publishedAt: String,

    @SerialName("assets")
    val assets: List<ReleaseAsset>
) {
    /**
     * Get the APK download URL from release assets
     */
    fun getApkUrl(): String? = assets.firstOrNull {
        it.name.endsWith(".apk", ignoreCase = true)
    }?.downloadUrl

    /**
     * Get cleaned version number (removes 'v' prefix if present)
     */
    fun getVersionNumber(): String = version.removePrefix("v")
}

@Serializable
data class ReleaseAsset(
    @SerialName("name")
    val name: String,

    @SerialName("browser_download_url")
    val downloadUrl: String,

    @SerialName("size")
    val size: Long
)
