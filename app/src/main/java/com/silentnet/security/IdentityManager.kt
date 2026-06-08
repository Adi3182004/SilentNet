package com.silentnet.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.spec.ECGenParameterSpec

/**
 * Manages the node's cryptographic identity using the Android Keystore.
 * Generates and stores a permanent EC P-256 keypair.
 * The private key never leaves the secure hardware/keystore.
 */
class IdentityManager {
    private val KEY_ALIAS = "silentnet_identity_v1"
    private val KEYSTORE_PROVIDER = "AndroidKeyStore"

    init {
        ensureIdentityExists()
    }

    private fun ensureIdentityExists() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateNewIdentity()
            } else {
                Log.d("SilentNetIdentity", "Identity Loaded")
            }
        } catch (e: Exception) {
            Log.e("SilentNetIdentity", "Identity Check Failed: ${e.message}")
        }
    }

    private fun generateNewIdentity() {
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )
            
            var purposes = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                purposes = purposes or KeyProperties.PURPOSE_AGREE_KEY
            }

            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                purposes
            ).run {
                setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                build()
            }
            kpg.initialize(parameterSpec)
            kpg.generateKeyPair()
            Log.d("SilentNetIdentity", "Identity Generated (EC P-256)")
        } catch (e: Exception) {
            Log.e("SilentNetIdentity", "Failed to generate identity: ${e.message}")
        }
    }

    fun getPublicKeyBase64(): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            val publicKey = entry?.certificate?.publicKey ?: return null
            Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("SilentNetIdentity", "Failed to retrieve public key: ${e.message}")
            null
        }
    }

    fun getPrivateKey(): java.security.PrivateKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            entry?.privateKey
        } catch (e: Exception) {
            Log.e("SilentNetIdentity", "Failed to retrieve private key: ${e.message}")
            null
        }
    }

    fun signData(data: ByteArray): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            val privateKey = entry?.privateKey ?: return null
            
            val signature = java.security.Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(data)
            Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("SilentNetIdentity", "Failed to sign data: ${e.message}")
            null
        }
    }
    
    fun verifySignature(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            val kf = java.security.KeyFactory.getInstance("EC")
            val publicKey = kf.generatePublic(java.security.spec.X509EncodedKeySpec(publicKeyBytes))
            
            val sig = java.security.Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e("SilentNetIdentity", "Failed to verify signature: ${e.message}")
            false
        }
    }
    
    fun identityExists(): Boolean {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        return keyStore.containsAlias(KEY_ALIAS)
    }
}
