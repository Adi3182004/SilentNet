package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recovery_posts",
    indices = [
        Index(value = ["category"]),
        Index(value = ["priority"]),
        Index(value = ["postId"], unique = true)
    ]
)
data class RecoveryPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: String,
    val authorNodeId: String,
    val authorAlias: String,
    val category: String, // Medical, Shelter, Food, etc.
    val content: String,
    val priority: Int, // 0: Normal, 1: High, 2: Critical
    val timestamp: Long,
    val expiration: Long,
    val isAnonymous: Boolean = false,
    val isLocal: Boolean = false,
    val signature: String? = null
)

@Entity(
    tableName = "recovery_groups",
    indices = [Index(value = ["groupNodeId"], unique = true)]
)
data class RecoveryGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupNodeId: String,
    val name: String,
    val description: String?,
    val creatorNodeId: String,
    val isJoined: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "lost_devices",
    indices = [Index(value = ["deviceId"], unique = true)]
)
data class LostDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String, // Permanent ID (secretly shared or derived)
    val deviceName: String, // Local nickname for owner
    val isLost: Boolean = false,
    val secret: String = java.util.UUID.randomUUID().toString(), // Secret for rotation
    val currentAnonymousId: String? = null, // Current rotating beacon ID
    val lastSeenTime: Long? = null,
    val lastSeenLocation: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "lostlink_reports",
    indices = [Index(value = ["deviceId"]), Index(value = ["reporterNodeId"])]
)
data class LostLinkReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String,
    val reporterNodeId: String,
    val timestamp: Long,
    val rssi: Int,
    val confidence: Double,
    val nodeInfo: String? = null // Optional info about the detecting node
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"])]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "search_results",
    indices = [Index(value = ["queryId"])]
)
data class SearchResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val queryId: String,
    val sourceNodeId: String,
    val targetType: String, // "Note", "File", "Report", "Bulletin", "Campus"
    val content: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis(),
    val rankingScore: Double = 0.0
)
