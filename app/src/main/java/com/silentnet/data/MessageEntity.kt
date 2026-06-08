package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["timestamp"]),
        Index(value = ["contactId", "remoteId"], unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val senderLabel: String,
    val body: String? = null,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentMime: String? = null,
    val isOutgoing: Boolean,
    val deliveryStatus: Int = 0, // 0: pending, 1: sent, 2: delivered, 3: read
    val priority: Int = 0,
    val remoteId: Long? = null,
    val isDeleted: Boolean = false,
    val isViewOnce: Boolean = false,
    val isConsumed: Boolean = false,
    val isEmergency: Boolean = false,
    val emergencySignature: String? = null,
    val emergencyTitle: String? = null,
    val isAcknowledged: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val groupId: String? = null,
    val groupKeyId: String? = null
)
