package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "walkie_channels",
    indices = [Index(value = ["channelId"], unique = true)]
)
data class WalkieChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelId: String,
    val name: String,
    val type: String, // "public", "team", "emergency", "group"
    val isJoined: Boolean = false,
    val channelKey: ByteArray? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "walkie_segments",
    indices = [
        Index(value = ["segmentId"], unique = true),
        Index(value = ["channelId"])
    ]
)
data class WalkieSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val segmentId: String,
    val channelId: String,
    val senderNodeId: String,
    val senderAlias: String,
    val filePath: String,
    val timestamp: Long,
    val duration: Long
)
