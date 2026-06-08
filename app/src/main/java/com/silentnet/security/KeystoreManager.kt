package com.silentnet.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages general-purpose symmetric keys in the Android Keystore.
 * Used for protecting session data, group keys, and other sensitive local secrets.
 */
object KeystoreManager {
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    fun getOrCreateSecretKey(alias: String): SecretKey {
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
            )
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE)
                .build()
            
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    fun encryptData(alias: String, data: ByteArray): String? {
        return try {
            val key = getOrCreateSecretKey(alias)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(data)
            
            // Format: IV:EncryptedData
            "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } catch (e: Exception) {
            Log.e("KeystoreManager", "Encryption failed for alias $alias: ${e.message}")
            null
        }
    }

    fun decryptData(alias: String, encryptedString: String): ByteArray? {
        return try {
            val parts = encryptedString.split(":")
            if (parts.size != 2) return null
            
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val encrypted = Base64.decode(parts[1], Base64.DEFAULT)
            
            val key = getOrCreateSecretKey(alias)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e("KeystoreManager", "Decryption failed for alias $alias: ${e.message}")
            null
        }
    }

    fun deleteKey(alias: String) {
        try {
            keyStore.deleteEntry(alias)
        } catch (e: Exception) {
            Log.e("KeystoreManager", "Failed to delete key $alias: ${e.message}")
        }
    }

    fun generateRandomKey(size: Int = 32): ByteArray {
        val key = ByteArray(size)
        java.security.SecureRandom().nextBytes(key)
        return key
    }
}
