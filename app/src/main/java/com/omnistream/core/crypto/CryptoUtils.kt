package com.omnistream.core.crypto

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Crypto utilities for handling encrypted video sources.
 * Based on CloudStream patterns and scraping tutorial.
 */
object CryptoUtils {

    /**
     * Vidzee API key decryption (AES-256-GCM)
     * Used to decrypt the key fetched from https://core.vidzee.wtf/api-key
     */
    fun vidzeeDecryptApiKey(encryptedBase64: String): String {
        val secret = "b3f2a9d4c6e1f8a7b"
        return try {
            val decoded = Base64.decode(encryptedBase64, Base64.DEFAULT)
            if (decoded.size <= 28) return ""

            val iv = decoded.copyOfRange(0, 12)
            val authTag = decoded.copyOfRange(12, 28)
            val ciphertext = decoded.copyOfRange(28, decoded.size)

            // SHA-256 hash of secret for key
            val keyHash = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))

            // Combine ciphertext + authTag for GCM
            val cipherWithTag = ciphertext + authTag

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyHash, "AES")
            val gcmSpec = GCMParameterSpec(128, iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            val decrypted = cipher.doFinal(cipherWithTag)

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("CryptoUtils", "vidzeeDecryptApiKey failed", e)
            ""
        }
    }

    /**
     * Vidzee link decryption (AES-256-CBC)
     * Format: base64(base64(iv):base64(ciphertext))
     * Key is padded to 32 bytes with null characters
     */
    fun vidzeeDecryptLink(encryptedLink: String, key: String): String {
        return try {
            // First base64 decode to get "iv:ciphertext" format
            val decoded = String(Base64.decode(encryptedLink, Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split(":")
            if (parts.size != 2) return ""

            val ivBase64 = parts[0]
            val ciphertextBase64 = parts[1]

            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.DEFAULT)

            // Pad key to 32 bytes with null characters
            val keyPadded = key.padEnd(32, '\u0000')
            val keyBytes = keyPadded.toByteArray(Charsets.UTF_8)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val decrypted = cipher.doFinal(ciphertext)

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("CryptoUtils", "vidzeeDecryptLink failed", e)
            ""
        }
    }

    /**
     * Base64 decode (standard and URL-safe)
     */
    fun base64Decode(encoded: String): String {
        return try {
            val cleaned = encoded
                .replace("\n", "")
                .replace("\r", "")
                .trim()

            String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            // Try URL-safe variant
            try {
                String(Base64.decode(encoded, Base64.URL_SAFE), Charsets.UTF_8)
            } catch (e2: Exception) {
                encoded
            }
        }
    }

    /**
     * Base64 encode
     */
    fun base64Encode(input: String): String {
        return Base64.encodeToString(input.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * AES CBC decryption (commonly used by video hosts)
     */
    fun aesDecrypt(
        cipherText: String,
        key: String,
        iv: String? = null
    ): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val ivBytes = iv?.toByteArray(Charsets.UTF_8) ?: keyBytes.copyOf(16)
        val ivSpec = IvParameterSpec(ivBytes.copyOf(16))

        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val decodedCipherText = Base64.decode(cipherText, Base64.DEFAULT)
        val decrypted = cipher.doFinal(decodedCipherText)

        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * AES ECB decryption (sometimes used)
     */
    fun aesEcbDecrypt(cipherText: String, key: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        val keyBytes = key.toByteArray(Charsets.UTF_8).copyOf(16)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        cipher.init(Cipher.DECRYPT_MODE, secretKey)

        val decodedCipherText = Base64.decode(cipherText, Base64.DEFAULT)
        val decrypted = cipher.doFinal(decodedCipherText)

        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Check if string looks like Base64
     */
    fun isBase64(input: String): Boolean {
        if (input.isBlank()) return false
        val base64Regex = Regex("^[A-Za-z0-9+/]*={0,2}$")
        return base64Regex.matches(input.replace("\n", "").replace("\r", ""))
    }

    /**
     * RC4 decrypt (used by some video hosts)
     */
    fun rc4Decrypt(cipherText: String, key: String): String {
        val data = if (isBase64(cipherText)) {
            Base64.decode(cipherText, Base64.DEFAULT)
        } else {
            cipherText.toByteArray()
        }

        val s = IntArray(256) { it }
        var j = 0

        for (i in 0 until 256) {
            j = (j + s[i] + key[i % key.length].code) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }

        val result = ByteArray(data.size)
        var i = 0
        j = 0

        for (k in data.indices) {
            i = (i + 1) % 256
            j = (j + s[i]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            result[k] = (data[k].toInt() xor s[(s[i] + s[j]) % 256]).toByte()
        }

        return String(result, Charsets.UTF_8)
    }

    /**
     * ROT13 decode (simple obfuscation)
     */
    fun rot13(input: String): String {
        return input.map { char ->
            when {
                char in 'a'..'z' -> 'a' + (char - 'a' + 13) % 26
                char in 'A'..'Z' -> 'A' + (char - 'A' + 13) % 26
                else -> char
            }
        }.joinToString("")
    }

    /**
     * Hex decode
     */
    fun hexDecode(hex: String): String {
        return hex.chunked(2)
            .map { it.toInt(16).toChar() }
            .joinToString("")
    }
}
