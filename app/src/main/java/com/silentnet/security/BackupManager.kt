package com.silentnet.security

import android.util.Base64
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val IV_LENGTH = 12

    /**
     * Encrypts the backup content using a key derived from the master secret.
     * In a production app, this would be derived from a user-provided backup password.
     */
    fun encryptBackup(content: String): String? {
        return try {
            val keyBytes = KeystoreManager.getOrCreateSecretKey("silentnet_backup_key").encoded
            if (keyBytes == null) {
                // Fallback if key is not extractable (which it isn't by default)
                // In this case, we use KeystoreManager.encryptData which handles it properly
                return KeystoreManager.encryptData("silentnet_backup_key", content.toByteArray())
            }
            
            val key = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(content.toByteArray())
            
            Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Use the more robust KeystoreManager.encryptData if the above fails
            KeystoreManager.encryptData("silentnet_backup_key", content.toByteArray())
        }
    }

    fun decryptBackup(encryptedBase64: String): String? {
        return try {
            val decrypted = KeystoreManager.decryptData("silentnet_backup_key", encryptedBase64)
            decrypted?.let { String(it) }
        } catch (e: Exception) {
            null
        }
    }
}
