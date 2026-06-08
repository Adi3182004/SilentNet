package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_notes",
    indices = [
        Index(value = ["voiceNoteId"], unique = true),
        Index(value = ["sender"]),
        Index(value = ["recipient"]),
        Index(value = ["groupId"])
    ]
)
data class VoiceNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voiceNoteId: String,
    val sender: String,
    val recipient: String?,
    val groupId: String?,
    val duration: Long,
    val size: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String,
    val isEncrypted: Boolean = true,
    val deliveryState: Int = 0, // 0: Pending, 1: Relayed, 2: Delivered, 3: Played, 4: Failed
    val isFragmented: Boolean = false,
    val totalFragments: Int = 0,
    val receivedFragments: Int = 0
)
