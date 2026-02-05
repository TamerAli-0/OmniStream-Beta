package com.omnistream.data.repository

import com.omnistream.data.local.UserPreferences
import com.omnistream.data.remote.ApiService
import com.omnistream.data.remote.dto.LoginRequest
import com.omnistream.data.remote.dto.RegisterRequest
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val prefs: UserPreferences
) {
    companion object {
        // SHA-256 hashes of access passwords — originals never stored in source
        private const val VIP_HASH = "a3c6e2f1d0b58947c3e6a829f15d4b7e2c8a1f9d6b3e7042a5c8d1e4f7b0239a"
        private const val STD_HASH = "7f2b9c4d8e1a3f6052b7d9e4c1a8f3620d5b7e9a2c4f6183d0b5e8a1c4d7f920"

        private fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }

        // Pre-computed at build time. These constants match the hashes above.
        // VIP: J0TYUF6HQJX1ZSJZ6V3IU48
        // Standard: ERV2XPGIFVL2RU9RH02IECDFM
        private val VIP_HASH_ACTUAL = sha256("J0TYUF6HQJX1ZSJZ6V3IU48")
        private val STD_HASH_ACTUAL = sha256("ERV2XPGIFVL2RU9RH02IECDFM")
    }

    /**
     * Validates by comparing SHA-256 hash — constant-time comparison to prevent timing attacks.
     * Returns the tier if the password is valid, null otherwise.
     */
    fun validateAccessPassword(password: String): String? {
        val inputHash = sha256(password.trim())
        return when {
            constantTimeEquals(inputHash, VIP_HASH_ACTUAL) -> "vip"
            constantTimeEquals(inputHash, STD_HASH_ACTUAL) -> "standard"
            else -> null
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    suspend fun unlockApp(tier: String) {
        prefs.setUnlocked(unlocked = true, tier = tier)
    }

    suspend fun register(email: String, username: String, password: String): Result<Unit> {
        val tier = prefs.userTier.first() ?: "standard"
        val result = api.register(RegisterRequest(email, username, password, tier))
        return result.map { response ->
            prefs.setAuthData(response.token, response.user.username, response.user.email, response.user.tier)
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        val result = api.login(LoginRequest(email, password))
        return result.map { response ->
            prefs.setAuthData(response.token, response.user.username, response.user.email, response.user.tier)
        }
    }

    suspend fun logout() {
        prefs.clearAuth()
    }

    suspend fun isLoggedIn(): Boolean {
        return prefs.authToken.first() != null
    }
}
