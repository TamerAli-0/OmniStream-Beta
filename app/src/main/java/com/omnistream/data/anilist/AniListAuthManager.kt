package com.omnistream.data.anilist

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AniListAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "anilist_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_AVATAR = "avatar"
        private const val KEY_TOKEN_EXPIRES = "token_expires"

        // OAuth config - Registered at https://anilist.co/settings/developer
        const val CLIENT_ID = "35573"
        const val REDIRECT_URI = "omnistream://anilist-callback"
        const val AUTH_URL = "https://anilist.co/api/v2/oauth/authorize"
        const val TOKEN_URL = "https://anilist.co/api/v2/oauth/token"
    }

    fun saveAccessToken(token: String, expiresInSeconds: Long = 31536000) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, token)
            .putLong(KEY_TOKEN_EXPIRES, System.currentTimeMillis() + (expiresInSeconds * 1000))
            .apply()
    }

    fun getAccessToken(): String? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val expires = prefs.getLong(KEY_TOKEN_EXPIRES, 0)

        // Check if token is expired
        if (token != null && System.currentTimeMillis() > expires) {
            logout() // Token expired, clear it
            return null
        }

        return token
    }

    fun saveUserInfo(userId: Int, username: String, avatar: String?) {
        prefs.edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_AVATAR, avatar)
            .apply()
    }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getAvatar(): String? = prefs.getString(KEY_AVATAR, null)

    fun isLoggedIn(): Boolean = !getAccessToken().isNullOrEmpty()

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun getAuthUrl(): String {
        // URL encode the redirect URI
        val encodedRedirectUri = java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")
        // Use authorization code flow (NOT implicit flow - AniList deprecated that)
        return "$AUTH_URL?client_id=$CLIENT_ID&redirect_uri=$encodedRedirectUri&response_type=code"
    }
}
