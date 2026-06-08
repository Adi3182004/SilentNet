package com.silentnet.security

import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles End-to-End Encryption using ECDH (P-256) for key agreement
 * and AES-GCM for payload encryption.
 */
object CryptographyManager {

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    fun encryptPayload(payload: String, recipientPublicKeyBase64: String): String? {
        try {
            // 1. Generate Ephemeral Keypair
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val ephemeralKeyPair = kpg.generateKeyPair()

            // 2. Decode Recipient Public Key
            val kf = KeyFactory.getInstance("EC")
            val recipientPublicKey = kf.generatePublic(
                X509EncodedKeySpec(Base64.decode(recipientPublicKeyBase64, Base64.DEFAULT))
            )

            // 3. Perform ECDH to get Shared Secret
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(ephemeralKeyPair.private)
            ka.doPhase(recipientPublicKey, true)
            val sharedSecret = ka.generateSecret()

            // 4. Derive AES Key (using first 32 bytes of shared secret for AES-256)
            val aesKey = SecretKeySpec(sharedSecret.take(32).toByteArray(), "AES")

            // 5. Encrypt with AES-GCM
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

            // 6. Return Encrypted Bundle
            return JSONObject().apply {
                put("epk", Base64.encodeToString(ephemeralKeyPair.public.encoded, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun decryptPayload(encryptedBundle: String, myPrivateKey: PrivateKey): String? {
        try {
            val json = JSONObject(encryptedBundle)
            val epkBase64 = json.getString("epk")
            val ivBase64 = json.getString("iv")
            val ctBase64 = json.getString("ct")

            // 1. Decode Ephemeral Public Key
            val kf = KeyFactory.getInstance("EC")
            val ephemeralPublicKey = kf.generatePublic(
                X509EncodedKeySpec(Base64.decode(epkBase64, Base64.DEFAULT))
            )

            // 2. Perform ECDH to get Shared Secret
            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(myPrivateKey)
            ka.doPhase(ephemeralPublicKey, true)
            val sharedSecret = ka.generateSecret()

            // 3. Derive AES Key
            val aesKey = SecretKeySpec(sharedSecret.take(32).toByteArray(), "AES")

            // 4. Decrypt with AES-GCM
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val ciphertext = Base64.decode(ctBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val decrypted = cipher.doFinal(ciphertext)

            return String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun generateGroupKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        return key
    }

    fun encryptWithGroupKey(payload: String, groupKey: ByteArray): String? {
        try {
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val aesKey = SecretKeySpec(groupKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
            
            return JSONObject().apply {
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun decryptWithGroupKey(encryptedBundle: String, groupKey: ByteArray): String? {
        val decrypted = decryptBytesWithGroupKey(encryptedBundle, groupKey)
        return decrypted?.let { String(it, Charsets.UTF_8) }
    }

    fun encryptBytesWithGroupKey(data: ByteArray, groupKey: ByteArray): String? {
        try {
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val aesKey = SecretKeySpec(groupKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(data)
            
            return JSONObject().apply {
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun encryptFileWithGroupKey(inputFile: java.io.File, outputFile: java.io.File, groupKey: ByteArray): Boolean {
        return try {
            val data = inputFile.readBytes()
            val encrypted = encryptBytesWithGroupKey(data, groupKey)
            if (encrypted != null) {
                outputFile.writeText(encrypted)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun decryptFileWithGroupKey(inputFile: java.io.File, outputFile: java.io.File, groupKey: ByteArray): Boolean {
        return try {
            val encryptedBundle = inputFile.readText()
            val decrypted = decryptBytesWithGroupKey(encryptedBundle, groupKey)
            if (decrypted != null) {
                outputFile.writeBytes(decrypted)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }



    fun decryptBytesWithGroupKey(encryptedBundle: String, groupKey: ByteArray): ByteArray? {
        try {
            val json = JSONObject(encryptedBundle)
            val iv = Base64.decode(json.getString("iv"), Base64.DEFAULT)
            val ciphertext = Base64.decode(json.getString("ct"), Base64.DEFAULT)
            
            val aesKey = SecretKeySpec(groupKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun encryptFile(inputFile: java.io.File, outputFile: java.io.File, recipientPublicKeyBase64: String): Boolean {
        return try {
            val data = inputFile.readBytes()
            val encryptedBundle = encryptBytesForRecipient(data, recipientPublicKeyBase64)
            if (encryptedBundle != null) {
                outputFile.writeText(encryptedBundle)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun decryptFile(inputFile: java.io.File, outputFile: java.io.File, myPrivateKey: java.security.PrivateKey): Boolean {
        return try {
            val encryptedBundle = inputFile.readText()
            val decrypted = decryptBytesForRecipient(encryptedBundle, myPrivateKey)
            if (decrypted != null) {
                outputFile.writeBytes(decrypted)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun encryptBytesForRecipient(data: ByteArray, recipientPublicKeyBase64: String): String? {
        try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val ephemeralKeyPair = kpg.generateKeyPair()

            val kf = KeyFactory.getInstance("EC")
            val recipientPublicKey = kf.generatePublic(
                X509EncodedKeySpec(Base64.decode(recipientPublicKeyBase64, Base64.DEFAULT))
            )

            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(ephemeralKeyPair.private)
            ka.doPhase(recipientPublicKey, true)
            val sharedSecret = ka.generateSecret()

            val aesKey = SecretKeySpec(sharedSecret.take(32).toByteArray(), "AES")
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(data)

            return JSONObject().apply {
                put("epk", Base64.encodeToString(ephemeralKeyPair.public.encoded, Base64.NO_WRAP))
                put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                put("ct", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            }.toString()
        } catch (e: Exception) {
            return null
        }
    }

    fun decryptBytesForRecipient(encryptedBundle: String, myPrivateKey: java.security.PrivateKey): ByteArray? {
        try {
            val json = JSONObject(encryptedBundle)
            val epkBase64 = json.getString("epk")
            val ivBase64 = json.getString("iv")
            val ctBase64 = json.getString("ct")

            val kf = KeyFactory.getInstance("EC")
            val ephemeralPublicKey = kf.generatePublic(
                X509EncodedKeySpec(Base64.decode(epkBase64, Base64.DEFAULT))
            )

            val ka = KeyAgreement.getInstance("ECDH")
            ka.init(myPrivateKey)
            ka.doPhase(ephemeralPublicKey, true)
            val sharedSecret = ka.generateSecret()

            val aesKey = SecretKeySpec(sharedSecret.take(32).toByteArray(), "AES")
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            val ciphertext = Base64.decode(ctBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            return cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            return null
        }
    }
}
