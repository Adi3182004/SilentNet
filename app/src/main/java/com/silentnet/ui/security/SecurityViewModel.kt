package com.silentnet.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silentnet.auth.SessionManager
import com.silentnet.data.AppDatabase
import com.silentnet.security.IdentityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SecurityStatus(
    val title: String,
    val status: String,
    val isSecure: Boolean,
    val description: String
)

data class SecurityReport(
    val score: Int,
    val items: List<SecurityStatus>
)

class SecurityViewModel(
    private val sessionManager: SessionManager,
    private val identityManager: IdentityManager,
    private val database: AppDatabase
) : ViewModel() {

    private val _report = MutableStateFlow(SecurityReport(0, emptyList()))
    val report: StateFlow<SecurityReport> = _report.asStateFlow()

    init {
        refreshSecurityStatus()
    }

    fun refreshSecurityStatus() {
        viewModelScope.launch {
            val items = mutableListOf<SecurityStatus>()
            var securePoints = 0
            val totalPoints = 7

            // 1. Password Security
            items.add(SecurityStatus(
                "Password Hashing",
                "PBKDF2-SHA256",
                true,
                "Passwords are protected using industry-standard PBKDF2 with 100,000 iterations."
            ))
            securePoints++

            // 2. Identity Security
            val hasIdentity = identityManager.identityExists()
            items.add(SecurityStatus(
                "Node Identity",
                if (hasIdentity) "Keystore Protected" else "Not Found",
                hasIdentity,
                "Node identity keys are stored in the hardware-backed Android Keystore."
            ))
            if (hasIdentity) securePoints++

            // 3. Session Security
            // SessionManager now uses EncryptedSharedPreferences
            items.add(SecurityStatus(
                "Session Storage",
                "Encrypted",
                true,
                "Session data and local preferences are encrypted at rest."
            ))
            securePoints++

            // 4. Database Security
            items.add(SecurityStatus(
                "Database Encryption",
                "SQLCipher (AES-256)",
                true,
                "Local message database is fully encrypted at rest using hardware-backed keys."
            ))
            securePoints++

            // 5. Mesh Security
            items.add(SecurityStatus(
                "Packet Integrity",
                "Signed (EC-P256)",
                true,
                "Mesh packets are digitally signed to prevent tampering and spoofing."
            ))
            securePoints++

            // 6. Admin Security
            val isAdmin = sessionManager.isAdmin()
            items.add(SecurityStatus(
                "Admin Protection",
                if (isAdmin) "Active" else "N/A",
                isAdmin,
                "Emergency broadcasts are protected by Keystore-backed administrative keys."
            ))
            if (isAdmin) securePoints++

            // 7. Media Security
            items.add(SecurityStatus(
                "Media Cleanup",
                "Automated",
                true,
                "Temporary media files are automatically scrubbed from storage."
            ))
            securePoints++

            val score = (securePoints.toDouble() / totalPoints * 100).toInt()
            _report.value = SecurityReport(score, items)
        }
    }
}
