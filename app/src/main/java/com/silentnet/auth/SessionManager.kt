package com.silentnet.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "silentnet_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    init {
        migrateLegacyPrefs(context)
    }

    private fun migrateLegacyPrefs(context: Context) {
        val legacyPrefs = context.getSharedPreferences("silentnet_session", Context.MODE_PRIVATE)
        if (legacyPrefs.contains("username") && !encryptedPrefs.contains("username")) {
            val username = legacyPrefs.getString("username", null)
            val fullName = legacyPrefs.getString("fullName", null)
            val nickname = legacyPrefs.getString("nickname", null)
            val role = legacyPrefs.getString("role", "USER")
            val nodeId = legacyPrefs.getString("node_id", null)
            val isAnonymous = legacyPrefs.getBoolean("isAnonymous", false)

            encryptedPrefs.edit().apply {
                putString("username", username)
                putString("fullName", fullName)
                putString("nickname", nickname)
                putString("role", role)
                putString("node_id", nodeId)
                putBoolean("isAnonymous", isAnonymous)
                apply()
            }
            // Clear legacy prefs after successful migration
            legacyPrefs.edit().clear().apply()
            android.util.Log.d("SilentNetSecurity", "Session migrated to EncryptedSharedPreferences")
        }
    }

    fun currentUsername(): String? = encryptedPrefs.getString("username", null)

    fun currentFullName(): String? = encryptedPrefs.getString("fullName", null)

    fun currentNickname(): String? = encryptedPrefs.getString("nickname", null)

    fun currentRole(): String = encryptedPrefs.getString("role", "USER") ?: "USER"

    fun nodeId(): String {
        var id = encryptedPrefs.getString("node_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            encryptedPrefs.edit().putString("node_id", id).apply()
        }
        return id
    }

    fun hasSession(): Boolean = currentUsername() != null

    fun isAdmin(): Boolean = currentRole() == "ADMIN"

    fun saveSession(username: String, fullName: String, nickname: String?, role: String) {
        encryptedPrefs.edit()
            .putString("username", username)
            .putString("fullName", fullName)
            .putString("nickname", nickname)
            .putString("role", role)
            .apply()
    }

    fun isAnonymousMode(): Boolean = encryptedPrefs.getBoolean("isAnonymous", false)
    
    fun setAnonymousMode(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean("isAnonymous", enabled).apply()
    }

    fun clear() {
        val dbKey = encryptedPrefs.getString("db_secret_key", null)
        encryptedPrefs.edit().clear().apply()
        if (dbKey != null) {
            encryptedPrefs.edit().putString("db_secret_key", dbKey).apply()
        }
    }

    fun getOrCreateDatabaseKey(): ByteArray {
        val existing = encryptedPrefs.getString("db_secret_key", null)
        if (existing != null) {
            return android.util.Base64.decode(existing, android.util.Base64.DEFAULT)
        }
        val newKey = com.silentnet.security.KeystoreManager.generateRandomKey(32)
        encryptedPrefs.edit().putString("db_secret_key", android.util.Base64.encodeToString(newKey, android.util.Base64.DEFAULT)).apply()
        return newKey
    }
}
