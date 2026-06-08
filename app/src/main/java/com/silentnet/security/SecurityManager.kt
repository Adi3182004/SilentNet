package com.silentnet.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Handles Emergency Security using Android Keystore-backed EC keys.
 * Replaces the legacy HMAC-based SecurityManager.
 */
object SecurityManager {
    private const val KEY_ALIAS = "silentnet_admin_emergency_v1"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    // In a production app, these would be pinned or fetched from a secure registry.
    private val trustedAdminPublicKeys = mutableSetOf<String>()

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    init {
        ensureAdminKeyExists()
    }

    fun setTrustedAdmins(keys: List<String>) {
        trustedAdminPublicKeys.clear()
        trustedAdminPublicKeys.addAll(keys)
        // Always trust self
        getAdminPublicKey()?.let { trustedAdminPublicKeys.add(it) }
    }

    fun registerTrustedAdmin(publicKeyBase64: String) {
        trustedAdminPublicKeys.add(publicKeyBase64)
    }

    fun isTrustedAdmin(publicKeyBase64: String): Boolean {
        return trustedAdminPublicKeys.contains(publicKeyBase64)
    }

    private fun ensureAdminKeyExists() {
        try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val kpg = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC,
                    KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
                
                kpg.initialize(spec)
                kpg.generateKeyPair()
                Log.d("SecurityManager", "Admin Emergency Key Generated")
            }
        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to ensure admin key: ${e.message}")
        }
    }

    fun signEmergency(content: String): String {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(entry.privateKey)
            signature.update(content.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to sign emergency: ${e.message}")
            throw RuntimeException("Emergency signing failed", e)
        }
    }

    fun verifyEmergency(content: String, signatureBase64: String): Boolean {
        if (signatureBase64.isEmpty()) return false
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
            val publicKey = entry.certificate.publicKey
            
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(content.toByteArray(Charsets.UTF_8))
            signature.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to verify emergency: ${e.message}")
            false
        }
    }

    /**
     * For cross-node verification, the admin public key would need to be distributed.
     * In this prototype, we assume the verifier has the admin's public key.
     */
    fun verifyRemoteEmergency(content: String, signatureBase64: String, adminPublicKeyBase64: String): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(adminPublicKeyBase64, Base64.DEFAULT)
            val kf = java.security.KeyFactory.getInstance("EC")
            val publicKey = kf.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
            
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(content.toByteArray(Charsets.UTF_8))
            signature.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e("SecurityManager", "Failed to verify remote emergency: ${e.message}")
            false
        }
    }

    fun getAdminPublicKey(): String? {
        return try {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            val publicKey = entry?.certificate?.publicKey ?: return null
            Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
