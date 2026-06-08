package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["ownerUsername"]),
        Index(value = ["ownerUsername", "contactUsername"], unique = true)
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerUsername: String,
    val contactUsername: String,
    val alias: String,
    val accentSeed: Int = alias.lowercase().hashCode(),
    val autoReplyEnabled: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val publicKey: String? = null
)
