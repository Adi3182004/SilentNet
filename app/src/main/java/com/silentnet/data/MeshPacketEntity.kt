package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mesh_packets",
    indices = [
        Index(value = ["targetNodeId"]),
        Index(value = ["packetId"], unique = true)
    ]
)
data class MeshPacketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packetId: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val payloadType: String,
    val sPk: String?,
    val encryptedPacketJson: String, // The full mesh packet JSON string
    val ttl: Int,
    val hopCount: Int,
    val timestamp: Long,
    val expirationTime: Long,
    val messageRemoteId: Long? = null,
    val retryCount: Int = 0,
    val priority: Int = 0, // 0: Normal, 1: High (Emergency)
    val predictionScore: Double = 0.0 // Estimated delivery probability
)
