package com.omnistream.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AppVersionResponse(
    val updateAvailable: Boolean,
    val forceUpdate: Boolean,
    val latestVersion: String,
    val minimumVersion: String? = null,
    val currentVersion: String,
    val updateTitle: String? = null,
    val updateMessage: String? = null,
    val features: List<String> = emptyList(),
    val downloadUrl: String? = null
)
