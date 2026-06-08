package com.silentnet.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trusted_admins")
data class TrustedAdminEntity(
    @PrimaryKey val publicKeyBase64: String,
    val alias: String,
    val addedAt: Long = System.currentTimeMillis()
)
