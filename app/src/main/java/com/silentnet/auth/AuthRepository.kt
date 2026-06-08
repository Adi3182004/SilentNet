package com.silentnet.auth

import com.silentnet.data.UserDao
import com.silentnet.data.UserEntity
import com.silentnet.security.AuthUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository(
    private val userDao: UserDao,
    private val sessionManager: SessionManager
) {

    init {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            seedAdmin()
        }
    }

    private suspend fun seedAdmin() {
        val adminUsername = "adi31@admin"
        if (userDao.findByUsername(adminUsername) == null) {
            val salt = AuthUtils.generateSalt()
            // The default admin account is created with a random secure password on first run.
            // In a production environment, this should be set during a secure onboarding flow.
            val initialPassword = java.util.UUID.randomUUID().toString().substring(0, 12)
            android.util.Log.i("SilentNetSecurity", "Initial Admin Password generated: $initialPassword")
            
            val admin = UserEntity(
                username = adminUsername,
                fullName = "Aditya Admin",
                nickname = "College Admin",
                passwordHash = AuthUtils.hashPassword(initialPassword, salt),
                passwordSalt = salt,
                role = "ADMIN"
            )
            userDao.insert(admin)
        }
    }

    suspend fun register(
        username: String,
        fullName: String,
        nickname: String?,
        password: String
    ): AuthResult {
        return withContext(Dispatchers.IO) {
            val cleanUsername = username.trim().lowercase()
            val cleanFullName = fullName.trim()
            val cleanNickname = nickname?.trim()?.takeIf { it.isNotBlank() }
            val cleanPassword = password.trim()

            if (cleanUsername.length < 3) return@withContext AuthResult.Error("Username must be at least 3 characters")
            if (cleanFullName.length < 2) return@withContext AuthResult.Error("Full name is too short")
            if (cleanPassword.length < 6) return@withContext AuthResult.Error("Password must be at least 6 characters")

            val existing = userDao.findByUsername(cleanUsername)
            if (existing != null) return@withContext AuthResult.Error("Username already exists")

            val salt = AuthUtils.generateSalt()
            val user = UserEntity(
                username = cleanUsername,
                fullName = cleanFullName,
                nickname = cleanNickname,
                passwordHash = AuthUtils.hashPassword(cleanPassword, salt),
                passwordSalt = salt,
                role = "USER"
            )

            userDao.insert(user)
            sessionManager.saveSession(cleanUsername, cleanFullName, cleanNickname, user.role)
            AuthResult.Success(user)
        }
    }

    suspend fun login(username: String, password: String): AuthResult {
        return withContext(Dispatchers.IO) {
            val cleanUsername = username.trim().lowercase()
            val user = userDao.findByUsername(cleanUsername)
                ?: return@withContext AuthResult.Error("Account not found")

            val isValid: Boolean
            if (user.passwordSalt != null) {
                isValid = AuthUtils.verifyPassword(password.trim(), user.passwordSalt, user.passwordHash)
            } else {
                // Legacy SHA-256 Migration
                isValid = AuthUtils.sha256(password.trim()).equals(user.passwordHash)
                if (isValid) {
                    // Upgrade to PBKDF2
                    val newSalt = AuthUtils.generateSalt()
                    val upgradedUser = user.copy(
                        passwordHash = AuthUtils.hashPassword(password.trim(), newSalt),
                        passwordSalt = newSalt
                    )
                    userDao.insert(upgradedUser)
                    android.util.Log.d("SilentNetSecurity", "Password Migrated to PBKDF2 for ${user.username}")
                }
            }

            if (!isValid) {
                return@withContext AuthResult.Error("Wrong password")
            }

            sessionManager.saveSession(user.username, user.fullName, user.nickname, user.role)
            AuthResult.Success(user)
        }
    }
}
